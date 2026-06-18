package org.jahia.modules.formidable.engine.servlet;

import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.jahia.modules.formidable.engine.api.FormAction;
import org.jahia.modules.formidable.engine.api.FormActionException;
import org.jahia.modules.formidable.engine.actions.FormDataParser;
import org.jahia.modules.formidable.engine.api.SubmittedFile;
import org.jahia.modules.formidable.engine.config.FormidableConfigService;
import org.jahia.modules.formidable.engine.logic.ConditionalLogicEvaluator;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRSessionFactory;
import org.jahia.services.content.JCRSessionWrapper;
import org.jahia.services.content.JCRTemplate;
import org.jahia.services.usermanager.JahiaUserManagerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static org.jahia.modules.formidable.engine.util.FormidableJcrConstants.AUTHENTICATED_ONLY_FORM_MIXIN;
import static org.jahia.modules.formidable.engine.util.FormidableJcrConstants.CAPTCHA_PROTECTED_FORM_MIXIN;
import static org.jahia.modules.formidable.engine.util.FormidableJcrConstants.WORKSPACE_LIVE;

/**
 * Executes the form submission pipeline in enforced order.
 * Each step either completes successfully or throws {@link SubmissionException},
 * which {@link FormSubmitServlet} translates into an opaque JSON error response.
 *
 * Pipeline order (zero bytes of the request stream are consumed before step 8):
 *
 *   1.  verifyMultipart        — content-type guard
 *   2.  readRoutingParams      — fid (validated as UUID) + lang from URL query params
 *   3.  guardContentLength     — early reject oversized requests when Content-Length is present
 *   4.  resolveFormNode        — JCR lookup in "live" workspace
 *   5.  verifyAuthentication   — reject Guest if fmdbmix:authenticatedOnlyForm is present
 *   6.  verifyCaptcha          — only if fmdbmix:captchaProtectedForm is present
 *   7.  collectFormFieldInfo   — build whitelist + types + choices + accept + constraints from JCR
 *   8.  parseMultipart         — first and only read of the stream; unknown fields discarded inline
 *   9.  validateRequired       — post-parse check for required fields (catches absent fields)
 *   10. dispatchActions        — execute fmdb:actionList nodes in order
 */
class FormSubmissionPipeline {

    private static final Logger log = LoggerFactory.getLogger(FormSubmissionPipeline.class);

    private static final String ACTIONS_NODE = "actions";
    private static final String CAPTCHA_TOKEN_HEADER = "X-Formidable-Captcha-Token";
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

    private final FormidableConfigService config;
    private final List<FormAction> formActions;
    private final FieldMetadataCollectorAdapter fieldMetadataCollector;
    private final JcrTemplateProvider jcrTemplateProvider;
    private final MultipartParserAdapter multipartParser;
    private final CurrentUserSessionProvider currentUserSessionProvider;

    // State accumulated as the pipeline progresses
    private String formId;
    private Locale locale;
    private JCRSessionWrapper session;
    private JCRNodeWrapper formNode;
    private FormFieldMetadataCollector.Result fieldMetadata;
    private FormDataParser.ParseResult parsed;

    FormSubmissionPipeline(FormidableConfigService config, List<FormAction> formActions) {
        this(
                config,
                formActions,
                FormFieldMetadataCollector::collect,
                JCRTemplate::getInstance,
                FormDataParser::parseAll,
                locale -> JCRSessionFactory.getInstance().getCurrentUserSession(WORKSPACE_LIVE, locale)
        );
    }

    FormSubmissionPipeline(FormidableConfigService config,
                           List<FormAction> formActions,
                           FieldMetadataCollectorAdapter fieldMetadataCollector,
                           JcrTemplateProvider jcrTemplateProvider,
                           MultipartParserAdapter multipartParser,
                           CurrentUserSessionProvider currentUserSessionProvider) {
        this.config = config;
        this.formActions = formActions;
        this.fieldMetadataCollector = fieldMetadataCollector;
        this.jcrTemplateProvider = jcrTemplateProvider;
        this.multipartParser = multipartParser;
        this.currentUserSessionProvider = currentUserSessionProvider;
    }

    void run(HttpServletRequest req) throws SubmissionException {
        verifyMultipart(req);
        readRoutingParams(req);
        guardContentLength(req);
        resolveFormNode();
        verifyAuthentication();
        verifyCaptcha(req);
        collectFormFieldInfo();
        parseMultipart(req);
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
        locale = (langParam != null && !langParam.isBlank())
                ? Locale.forLanguageTag(langParam)
                : Locale.ENGLISH;
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

    private void validateRequired() throws SubmissionException {
        var logicEvaluator = new ConditionalLogicEvaluator(
                fieldMetadata.fieldLogicRules(),
                fieldMetadata.logicIdToFieldName(),
                fieldMetadata.fieldParentContainer(),
                parsed.parameters()
        );

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
        List<ResolvedAction> actions = resolveActionNodes();
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
                JCRTemplate.getInstance().doExecuteWithSystemSessionAsUser(null, WORKSPACE_LIVE, locale, systemSession -> {
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
                throw new SubmissionException(ErrorCode.FMDB_008,
                        "Action '" + nodeType + "' failed (" + executed + "/" + total + " actions completed): " + cause.getMessage(),
                        executed, total, cause);
            } catch (RepositoryException e) {
                throw new SubmissionException(ErrorCode.FMDB_008,
                        "Action '" + nodeType + "' failed (" + executed + "/" + total + " actions completed): " + e.getMessage(),
                        executed, total, e);
            }
        }
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
                                w.getPrimaryNodeTypeName()
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

    private record ResolvedAction(String id, String path, String nodeType) {}

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
