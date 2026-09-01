package org.jahia.modules.formidable.engine.servlet;

import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.jahia.modules.formidable.engine.api.FormAction;
import org.jahia.modules.formidable.engine.api.FormActionException;
import org.jahia.modules.formidable.engine.actions.FormDataParser;
import org.jahia.modules.formidable.engine.api.SubmittedFile;
import org.jahia.modules.formidable.engine.config.FormidableConfigService;
import org.jahia.modules.formidable.engine.logic.ConditionalLogicEvaluator;
import org.jahia.modules.formidable.engine.logic.LogicStateDeclaration;
import org.jahia.modules.formidable.engine.options.FormidableOptionsSourceService;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRSessionFactory;
import org.jahia.services.content.JCRSessionWrapper;
import org.jahia.services.content.JCRTemplate;
import org.jahia.services.usermanager.JahiaUserManagerService;
import org.jahia.settings.readonlymode.ReadOnlyModeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import javax.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.jahia.modules.formidable.engine.util.FormidableJcrConstants.AUTHENTICATED_ONLY_FORM_MIXIN;
import static org.jahia.modules.formidable.engine.util.FormidableJcrConstants.CAPTCHA_PROTECTED_FORM_MIXIN;
import static org.jahia.modules.formidable.engine.util.FormidableJcrConstants.READ_ONLY_COMPATIBLE_ACTION_MIXIN;
import static org.jahia.modules.formidable.engine.util.FormidableJcrConstants.WORKSPACE_LIVE;

/**
 * Executes the form submission pipeline in enforced order.
 * Each step either completes successfully or throws {@link SubmissionException},
 * which {@link FormSubmitServlet} translates into an opaque JSON error response.
 *
 * Pipeline order (zero bytes of the request stream are consumed before step 9):
 *
 *   1.  verifyMultipart          — content-type guard
 *   2.  readRoutingParams        — fid (validated as UUID) + lang from URL query params
 *   3.  guardContentLength       — early reject oversized requests when Content-Length is present
 *   4.  resolveFormNode          — JCR lookup in "live" workspace
 *   5.  verifyPlatformWritable   — reject if the platform is read-only and an action lacks fmdbmix:readOnlyCompatibleAction
 *   6.  verifyAuthentication     — reject Guest if fmdbmix:authenticatedOnlyForm is present
 *   7.  verifyCaptcha            — only if fmdbmix:captchaProtectedForm is present
 *   8.  collectFormFieldInfo     — build whitelist + types + choices + accept + constraints from JCR
 *   9.  parseMultipart           — first and only read of the stream; unknown fields discarded inline
 *   10. validateLogicCoherence   — reject values for fields provably hidden by conditional logic
 *   11. validateRequired         — post-parse check for required fields (catches absent fields)
 *   12. dispatchActions          — execute fmdb:actionList nodes in order
 */
class FormSubmissionPipeline {

    private static final Logger log = LoggerFactory.getLogger(FormSubmissionPipeline.class);

    private static final String ACTIONS_NODE = "actions";
    private static final String CAPTCHA_TOKEN_HEADER = "X-Formidable-Captcha-Token";

    /**
     * Header carrying the browser's conditional-logic state declaration: base64-encoded
     * UTF-8 JSON (header values must stay ASCII; declared values may not be), parsed by
     * {@link LogicStateDeclaration}. Same transport pattern as the captcha token — request
     * metadata travels in headers, never among the form's own fields.
     */
    static final String LOGIC_STATE_HEADER = "X-Formidable-Logic-State";

    /**
     * A declaration only lists the provider references the form's rules read, so this is
     * already generous; anything larger is not a declaration and is ignored.
     */
    private static final int LOGIC_STATE_HEADER_MAX_CHARS = 16 * 1024;
    @FunctionalInterface
    interface FieldMetadataCollectorAdapter {
        FormFieldMetadataCollector.Result collect(String formId, Locale locale) throws RepositoryException;
    }

    @FunctionalInterface
    interface JcrTemplateProvider {
        JCRTemplate get();
    }

    @FunctionalInterface
    interface MultipartParserAdapter {
        FormDataParser.ParseResult parse(HttpServletRequest req,
                                         FormidableConfigService config,
                                         FormDataParser.FieldMetadata fieldMetadata)
                throws FormDataParser.ParseException;
    }

    @FunctionalInterface
    interface CurrentUserSessionProvider {
        JCRSessionWrapper get(Locale locale) throws RepositoryException;
    }

    @FunctionalInterface
    interface ReadOnlyStatusProvider {
        boolean isReadOnly();
    }

    private final FormidableConfigService config;
    private final List<FormAction> formActions;
    private final FieldMetadataCollectorAdapter fieldMetadataCollector;
    private final JcrTemplateProvider jcrTemplateProvider;
    private final MultipartParserAdapter multipartParser;
    private final CurrentUserSessionProvider currentUserSessionProvider;
    private final ReadOnlyStatusProvider readOnlyStatusProvider;

    // State accumulated as the pipeline progresses
    private String formId;
    private Locale locale;
    private JCRSessionWrapper session;
    private JCRNodeWrapper formNode;
    private FormFieldMetadataCollector.Result fieldMetadata;
    private FormDataParser.ParseResult parsed;
    private ConditionalLogicEvaluator logicEvaluator;
    private List<ResolvedAction> resolvedActions;

    FormSubmissionPipeline(FormidableConfigService config, List<FormAction> formActions,
                           FormidableOptionsSourceService optionsSourceService,
                           ReadOnlyStatusProvider readOnlyStatusProvider) {
        this(
                config,
                formActions,
                (formId, locale) -> FormFieldMetadataCollector.collect(formId, locale, optionsSourceService),
                JCRTemplate::getInstance,
                FormDataParser::parseAll,
                locale -> JCRSessionFactory.getInstance().getCurrentUserSession(WORKSPACE_LIVE, locale),
                readOnlyStatusProvider
        );
    }

    FormSubmissionPipeline(FormidableConfigService config,
                           List<FormAction> formActions,
                           FieldMetadataCollectorAdapter fieldMetadataCollector,
                           JcrTemplateProvider jcrTemplateProvider,
                           MultipartParserAdapter multipartParser,
                           CurrentUserSessionProvider currentUserSessionProvider,
                           ReadOnlyStatusProvider readOnlyStatusProvider) {
        this.config = config;
        this.formActions = formActions;
        this.fieldMetadataCollector = fieldMetadataCollector;
        this.jcrTemplateProvider = jcrTemplateProvider;
        this.multipartParser = multipartParser;
        this.currentUserSessionProvider = currentUserSessionProvider;
        this.readOnlyStatusProvider = readOnlyStatusProvider;
    }

    void run(HttpServletRequest req) throws SubmissionException {
        verifyMultipart(req);
        readRoutingParams(req);
        guardContentLength(req);
        resolveFormNode();
        verifyPlatformWritable();
        verifyAuthentication();
        verifyCaptcha(req);
        collectFormFieldInfo();
        parseMultipart(req);
        validateLogicCoherence(req);
        validateRequired();
        dispatchActions(req);
    }

    // --- Steps ---

    private void verifyMultipart(HttpServletRequest req) throws SubmissionException {
        if (!ServletFileUpload.isMultipartContent(req)) {
            throw new SubmissionException(ErrorCode.FMDB_001, "Content-Type is not multipart/form-data");
        }
    }

    private void readRoutingParams(HttpServletRequest req) throws SubmissionException {
        formId = req.getParameter("fid");
        if (formId == null || formId.isBlank()) {
            throw new SubmissionException(ErrorCode.FMDB_002, "Missing required URL parameter 'fid'");
        }
        try {
            UUID.fromString(formId);
        } catch (IllegalArgumentException e) {
            throw new SubmissionException(ErrorCode.FMDB_002, "'fid' is not a valid UUID: " + formId);
        }
        String langParam = req.getParameter("lang");
        if (langParam == null || langParam.isBlank()) {
            locale = Locale.ENGLISH;
            return;
        }
        locale = Locale.forLanguageTag(langParam);
        // forLanguageTag never throws: garbage comes back as the empty ROOT locale,
        // which would silently drift through every locale-aware step. Reject it instead.
        if (locale.getLanguage().isEmpty()) {
            throw new SubmissionException(ErrorCode.FMDB_002, "'lang' is not a valid language tag: " + langParam);
        }
    }

    private void guardContentLength(HttpServletRequest req) throws SubmissionException {
        long contentLength = req.getContentLengthLong();
        // Early-reject optimization only: chunked requests legitimately report -1 here.
        // The definitive request-size enforcement still happens later in FormDataParser
        // via ServletFileUpload.setSizeMax(...) when the multipart stream is consumed.
        if (contentLength > config.getUploadMaxRequestSizeBytes()) {
            throw new SubmissionException(ErrorCode.FMDB_003,
                    "Content-Length " + contentLength + " exceeds limit " + config.getUploadMaxRequestSizeBytes());
        }
    }

    private void resolveFormNode() throws SubmissionException {
        try {
            session = currentUserSessionProvider.get(locale);
            formNode = session.getNodeByIdentifier(formId);
        } catch (RepositoryException e) {
            throw new SubmissionException(ErrorCode.FMDB_004, "Form node not found: " + formId, e);
        }
    }

    /**
     * Rejects the submission when the platform is in read-only mode and at least one of the
     * form's actions is presumed to write to the repository — i.e. its node type does not
     * carry {@code fmdbmix:readOnlyCompatibleAction}. Runs before authentication and CAPTCHA:
     * during a maintenance window there is no point contacting the CAPTCHA provider for a
     * submission that cannot be persisted. Forms whose actions all declare read-only
     * compatibility (e.g. email-only forms) keep working normally.
     */
    private void verifyPlatformWritable() throws SubmissionException {
        if (!readOnlyStatusProvider.isReadOnly()) {
            return;
        }
        for (ResolvedAction action : actions()) {
            if (!action.readOnlyCompatible()) {
                throw new SubmissionException(ErrorCode.FMDB_014,
                        "Platform is in read-only mode and action '" + action.nodeType()
                                + "' of form '" + formId + "' is presumed to write to the repository");
            }
        }
    }

    private void verifyAuthentication() throws SubmissionException {
        boolean requiresAuth;
        try {
            requiresAuth = formNode.isNodeType(AUTHENTICATED_ONLY_FORM_MIXIN);
        } catch (RepositoryException e) {
            throw new SubmissionException(ErrorCode.FMDB_500,
                    "Cannot verify authentication requirement for form: " + formId,
                    e);
        }
        if (!requiresAuth) return;

        if (JahiaUserManagerService.isGuest(JCRSessionFactory.getInstance().getCurrentUser())) {
            log.warn("[FormSubmissionPipeline] Anonymous submission rejected on authenticated form.");
            throw new SubmissionException(ErrorCode.FMDB_009,
                    "Authentication required for form: " + formId);
        }
    }

    private void verifyCaptcha(HttpServletRequest req) throws SubmissionException {
        boolean hasCaptcha;
        try {
            hasCaptcha = formNode.isNodeType(CAPTCHA_PROTECTED_FORM_MIXIN);
        } catch (RepositoryException e) {
            throw new SubmissionException(ErrorCode.FMDB_500,
                    "Cannot verify CAPTCHA requirement for form: " + formId,
                    e);
        }
        if (!hasCaptcha) return;

        if (!config.isCaptchaVerificationConfigured()) {
            log.warn("[FormSubmissionPipeline] CAPTCHA mixin present on '{}' but server-side verification is not fully configured — blocking.",
                    formNode.getPath());
            throw new SubmissionException(ErrorCode.FMDB_005,
                    "CAPTCHA required but not configured (form: " + formId + ")");
        }
        String token = req.getHeader(CAPTCHA_TOKEN_HEADER);
        try {
            if (!config.verifyCaptcha(token, req.getRemoteAddr())) {
                throw new SubmissionException(ErrorCode.FMDB_006,
                        "CAPTCHA token invalid or absent (form: " + formId + ")");
            }
        } catch (FormidableConfigService.CaptchaVerificationException e) {
            throw new SubmissionException(
                    ErrorCode.FMDB_500,
                    "CAPTCHA verification failed for technical reasons (form: " + formId + ")",
                    e
            );
        }
    }

    private void collectFormFieldInfo() throws SubmissionException {
        try {
            fieldMetadata = fieldMetadataCollector.collect(formId, locale);
        } catch (RepositoryException e) {
            throw new SubmissionException(ErrorCode.FMDB_500,
                    "Cannot collect field metadata for form: " + formId,
                    e);
        }
    }

    private void parseMultipart(HttpServletRequest req) throws SubmissionException {
        try {
            parsed = multipartParser.parse(req, config, fieldMetadata.toParserMetadata());
        } catch (FormDataParser.ParseException e) {
            ErrorCode code = switch (e.failureType()) {
                case VALIDATION -> ErrorCode.FMDB_010;
                case TECHNICAL -> ErrorCode.FMDB_007;
                case CONFIGURATION -> ErrorCode.FMDB_500;
            };
            throw new SubmissionException(code, e.getMessage(), e);
        }
    }

    /**
     * Rejects a submission that carries a value for a field the server can prove was
     * hidden. The verdict is only acted on when it is a measurement — computed from
     * submitted values and, for provider rules, from the state the browser declared in
     * the {@value #LOGIC_STATE_HEADER} header. An honest browser cannot trip this: a
     * hidden field's controls are disabled and disabled controls are not submitted. A
     * fail-safe verdict (no declaration, unknown operator…) keeps today's behaviour:
     * required validation skipped, values kept.
     *
     * This is a coherence check, not enforcement — the declaration is forgeable. What it
     * guarantees is that one single declared state backs every rule reading it, and that
     * a value smuggled into a provably hidden field is detected instead of stored.
     */
    private void validateLogicCoherence(HttpServletRequest req) throws SubmissionException {
        logicEvaluator = new ConditionalLogicEvaluator(
                fieldMetadata.fieldLogicRules(),
                fieldMetadata.logicIdToFieldName(),
                fieldMetadata.fieldParentContainers(),
                parsed.parameters(),
                readLogicStateDeclaration(req)
        );

        Set<String> submittedFieldNames = new LinkedHashSet<>();
        for (Map.Entry<String, List<String>> entry : parsed.parameters().entrySet()) {
            if (entry.getValue().stream().anyMatch(value -> value != null && !value.isBlank())) {
                submittedFieldNames.add(entry.getKey());
            }
        }
        for (FormDataParser.FormFile file : parsed.files()) {
            submittedFieldNames.add(file.fieldName());
        }

        for (String fieldName : submittedFieldNames) {
            if (logicEvaluator.visibility(fieldName) == ConditionalLogicEvaluator.Visibility.HIDDEN_MEASURED) {
                log.warn("[FormSubmissionPipeline] Field '{}' is provably hidden by conditional logic "
                        + "but the submission carries a value for it.", fieldName);
                throw new SubmissionException(ErrorCode.FMDB_013,
                        "Field '" + fieldName + "' is hidden by conditional logic but the submission "
                                + "carries a value for it.");
            }
        }
    }

    private static LogicStateDeclaration readLogicStateDeclaration(HttpServletRequest req) {
        String header = req.getHeader(LOGIC_STATE_HEADER);
        if (header == null || header.isBlank() || header.length() > LOGIC_STATE_HEADER_MAX_CHARS) {
            return LogicStateDeclaration.EMPTY;
        }

        try {
            String json = new String(Base64.getDecoder().decode(header), StandardCharsets.UTF_8);
            return LogicStateDeclaration.parse(json);
        } catch (IllegalArgumentException e) {
            // Anything unreadable is simply no declaration: the fail-safe applies, the
            // submission is never failed over its metadata.
            log.debug("[FormSubmissionPipeline] Undecodable logic state header, ignoring");
            return LogicStateDeclaration.EMPTY;
        }
    }

    private void validateRequired() throws SubmissionException {
        for (Map.Entry<String, FormDataParser.FieldInfo> entry : fieldMetadata.fieldInfos().entrySet()) {
            String fieldName = entry.getKey();
            FormDataParser.FieldInfo fieldInfo = entry.getValue();
            FormDataParser.FieldConstraints constraints = fieldInfo.constraints();

            if (constraints != null && constraints.required()) {
                if (logicEvaluator.isHidden(fieldName)) {
                    log.debug("[FormSubmissionPipeline] Skipping required validation for hidden field '{}'", fieldName);
                } else {
                    validateRequiredField(fieldName, fieldInfo);
                }
            }
        }
    }

    private void validateRequiredField(String fieldName, FormDataParser.FieldInfo fieldInfo)
            throws SubmissionException {
        if (fieldInfo.fileField()) {
            validateRequiredFileField(fieldName);
            return;
        }

        validateRequiredParameterField(fieldName);
    }

    private void validateRequiredFileField(String fieldName) throws SubmissionException {
        boolean hasFile = parsed.files().stream()
                .anyMatch(f -> fieldName.equals(f.fieldName()));
        if (hasFile) {
            return;
        }

        log.warn("[FormSubmissionPipeline] Required file field '{}' has no uploaded file.", fieldName);
        throw new SubmissionException(ErrorCode.FMDB_010,
                "Required file field '" + fieldName + "' has no uploaded file.");
    }

    private void validateRequiredParameterField(String fieldName) throws SubmissionException {
        List<String> values = parsed.parameters().get(fieldName);
        if (values != null && !values.isEmpty() && values.stream().anyMatch(value -> !value.isBlank())) {
            return;
        }

        log.warn("[FormSubmissionPipeline] Required field '{}' is missing or empty.", fieldName);
        throw new SubmissionException(ErrorCode.FMDB_010,
                "Required field '" + fieldName + "' is missing or empty.");
    }

    private void dispatchActions(HttpServletRequest req) throws SubmissionException {
        List<ResolvedAction> actions = actions();
        List<SubmittedFile> submittedFiles = toSubmittedFiles(parsed.files());
        int total = actions.size();
        int executed = 0;

        for (ResolvedAction action : actions) {
            String nodeType = action.nodeType();
            FormAction handler = formActions.stream()
                    .filter(a -> nodeType.equals(a.getNodeType()))
                    .findFirst()
                    .orElse(null);
            if (handler == null) {
                throw new SubmissionException(
                        ErrorCode.FMDB_008,
                        "Action '" + nodeType + "' failed (" + executed + "/" + total
                                + " actions completed): no handler is registered for this action type.",
                        executed,
                        total
                );
            }
            try {
                jcrTemplateProvider.get().doExecuteWithSystemSessionAsUser(null, WORKSPACE_LIVE, locale, systemSession -> {
                    JCRNodeWrapper actionNode = systemSession.getNodeByIdentifier(action.id());
                    try {
                        handler.execute(actionNode, req, session, parsed.parameters(), submittedFiles);
                    } catch (FormActionException e) {
                        throw new WrappedFormActionException(e);
                    }
                    return null;
                });
                executed++;
            } catch (WrappedFormActionException e) {
                FormActionException cause = e.getFormActionException();
                throw actionFailure(nodeType, executed, total, cause);
            } catch (RepositoryException e) {
                throw actionFailure(nodeType, executed, total, e);
            } catch (RuntimeException e) {
                if (!isReadOnlyRejection(e)) {
                    throw e;
                }
                throw actionFailure(nodeType, executed, total, e);
            }
        }
    }

    /**
     * The declarative mixin is a contract, not a proof: an action without
     * {@code fmdbmix:readOnlyCompatibleAction} whose form slipped past the render/submit
     * guards (mode switched mid-request, lying declaration) still hits the repository's
     * own read-only rejection. Detect it anywhere in the cause chain so the client gets
     * the maintenance code instead of a generic action failure.
     */
    private SubmissionException actionFailure(String nodeType, int executed, int total, Throwable cause) {
        ErrorCode code = isReadOnlyRejection(cause) ? ErrorCode.FMDB_014 : ErrorCode.FMDB_008;
        return new SubmissionException(code,
                "Action '" + nodeType + "' failed (" + executed + "/" + total + " actions completed): " + cause.getMessage(),
                executed, total, cause);
    }

    private static boolean isReadOnlyRejection(Throwable t) {
        for (Throwable current = t; current != null; current = current.getCause()) {
            if (current instanceof ReadOnlyModeException) {
                return true;
            }
        }
        return false;
    }

    private static List<SubmittedFile> toSubmittedFiles(List<FormDataParser.FormFile> parsedFiles) {
        List<SubmittedFile> submittedFiles = new ArrayList<>(parsedFiles.size());
        for (FormDataParser.FormFile file : parsedFiles) {
            submittedFiles.add(new SubmittedFile(
                    file.fieldName(),
                    file.originalName(),
                    file.mimeType(),
                    file.data()
            ));
        }
        return List.copyOf(submittedFiles);
    }

    private List<ResolvedAction> actions() throws SubmissionException {
        if (resolvedActions == null) {
            resolvedActions = resolveActionNodes();
        }
        return resolvedActions;
    }

    private List<ResolvedAction> resolveActionNodes() throws SubmissionException {
        List<ResolvedAction> result = new ArrayList<>();
        try {
            jcrTemplateProvider.get().doExecuteWithSystemSessionAsUser(null, WORKSPACE_LIVE, locale, systemSession -> {
                JCRNodeWrapper systemFormNode = systemSession.getNodeByIdentifier(formId);
                if (!systemFormNode.hasNode(ACTIONS_NODE)) {
                    return null;
                }

                JCRNodeWrapper actionList = systemFormNode.getNode(ACTIONS_NODE);
                NodeIterator it = actionList.getNodes();
                while (it.hasNext()) {
                    javax.jcr.Node child = it.nextNode();
                    if (child instanceof JCRNodeWrapper w) {
                        result.add(new ResolvedAction(
                                w.getIdentifier(),
                                w.getPath(),
                                w.getPrimaryNodeTypeName(),
                                w.isNodeType(READ_ONLY_COMPATIBLE_ACTION_MIXIN)
                        ));
                    }
                }
                return null;
            });
        } catch (RepositoryException e) {
            throw new SubmissionException(ErrorCode.FMDB_012,
                    "Could not read action list for form: " + formId,
                    e);
        }
        return result;
    }

    // --- Internal types ---

    record ResolvedAction(String id, String path, String nodeType, boolean readOnlyCompatible) {}

    private static final class WrappedFormActionException extends RuntimeException {
        private final FormActionException formActionException;

        private WrappedFormActionException(FormActionException formActionException) {
            super(formActionException);
            this.formActionException = formActionException;
        }

        private FormActionException getFormActionException() {
            return formActionException;
        }
    }
}
