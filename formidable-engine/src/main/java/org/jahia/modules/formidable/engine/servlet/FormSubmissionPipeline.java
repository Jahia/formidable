package org.jahia.modules.formidable.engine.servlet;

import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.jahia.modules.formidable.engine.actions.FormAction;
import org.jahia.modules.formidable.engine.actions.FormActionException;
import org.jahia.modules.formidable.engine.actions.FormDataParser;
import org.jahia.modules.formidable.engine.config.FormidableConfigService;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRSessionFactory;
import org.jahia.services.content.JCRSessionWrapper;
import org.jahia.services.usermanager.JahiaUserManagerService;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Value;
import javax.servlet.http.HttpServletRequest;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Executes the form submission pipeline in enforced order.
 * Each step either completes successfully or throws {@link SubmissionException},
 * which {@link FormSubmitServlet} translates into an opaque JSON error response.
 *
 * Pipeline order (zero bytes of the request stream are consumed before step 8):
 *
 *   1.  verifyMultipart        — content-type guard
 *   2.  readRoutingParams      — fid (validated as UUID) + lang from URL query params
 *   3.  guardContentLength     — reject oversized requests before touching the stream
 *   4.  resolveFormNode        — JCR lookup in "live" workspace
 *   5.  verifyAuthentication   — reject Guest if fmdbmix:requireAuthentication is present
 *   6.  verifyCaptcha          — only if fmdbmix:captcha mixin is present
 *   7.  collectFormFieldInfo   — build whitelist + types + choices + accept + constraints from JCR
 *   8.  parseMultipart         — first and only read of the stream; unknown fields discarded inline
 *   9.  validateRequired       — post-parse check for required fields (catches absent fields)
 *   10. dispatchActions        — execute fmdb:actionList nodes in order
 */
class FormSubmissionPipeline {

    private static final Logger log = LoggerFactory.getLogger(FormSubmissionPipeline.class);

    private static final String ACTIONS_NODE = "actions";
    private static final String FIELDS_NODE  = "fields";
    private static final Set<String> NON_SUBMITTABLE_FORM_ELEMENT_TYPES = Set.of(
            "fmdb:fieldset",
            "fmdb:inputButton"
    );

    private final FormidableConfigService config;
    private final List<FormAction> formActions;

    // State accumulated as the pipeline progresses
    private String formId;
    private Locale locale;
    private JCRSessionWrapper session;
    private JCRNodeWrapper formNode;
    private Set<String> allowedFieldNames;
    private Map<String, String> fieldTypes;
    private Map<String, Set<String>> allowedChoices;
    private Map<String, Set<String>> fieldAcceptTypes;
    private Map<String, FormDataParser.FieldConstraints> fieldConstraints;
    private FormDataParser.ParseResult parsed;

    FormSubmissionPipeline(FormidableConfigService config, List<FormAction> formActions) {
        this.config = config;
        this.formActions = formActions;
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
        req.setAttribute(FormDataParser.PARSED_FILES_ATTR, parsed.files());
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
        if (contentLength > config.getUploadMaxRequestSizeBytes()) {
            throw new SubmissionException(ErrorCode.FMDB_003,
                    "Content-Length " + contentLength + " exceeds limit " + config.getUploadMaxRequestSizeBytes());
        }
    }

    private void resolveFormNode() throws SubmissionException {
        try {
            session = JCRSessionFactory.getInstance().getCurrentUserSession("live", locale);
            formNode = session.getNodeByIdentifier(formId);
        } catch (Exception e) {
            log.warn("[FormSubmissionPipeline] Form node not found for id '{}': {}", formId, e.getMessage());
            throw new SubmissionException(ErrorCode.FMDB_004, "Form node not found: " + formId);
        }
    }

    private void verifyAuthentication() throws SubmissionException {
        boolean requiresAuth;
        try {
            requiresAuth = formNode.isNodeType("fmdbmix:requireAuthentication");
        } catch (RepositoryException e) {
            log.warn("[FormSubmissionPipeline] Could not check fmdbmix:requireAuthentication on '{}': {}",
                    formNode.getPath(), e.getMessage());
            return;
        }
        if (!requiresAuth) return;

        if (JahiaUserManagerService.isGuest(JCRSessionFactory.getInstance().getCurrentUser())) {
            log.warn("[FormSubmissionPipeline] Anonymous submission rejected on authenticated form: {}", formId);
            throw new SubmissionException(ErrorCode.FMDB_009,
                    "Authentication required for form: " + formId);
        }
    }

    private void verifyCaptcha(HttpServletRequest req) throws SubmissionException {
        boolean hasCaptcha;
        try {
            hasCaptcha = formNode.isNodeType("fmdbmix:captcha");
        } catch (RepositoryException e) {
            log.warn("[FormSubmissionPipeline] Could not check fmdbmix:captcha on '{}': {}",
                    formNode.getPath(), e.getMessage());
            return;
        }
        if (!hasCaptcha) return;

        if (!config.isCaptchaConfigured()) {
            log.warn("[FormSubmissionPipeline] CAPTCHA mixin present on '{}' but not configured server-side — blocking.",
                    formNode.getPath());
            throw new SubmissionException(ErrorCode.FMDB_005,
                    "CAPTCHA required but not configured (form: " + formId + ")");
        }
        String token = req.getParameter("ct");
        if (!config.verifyCaptcha(token, req.getRemoteAddr())) {
            throw new SubmissionException(ErrorCode.FMDB_006,
                    "CAPTCHA token invalid or absent (form: " + formId + ")");
        }
    }

    private void collectFormFieldInfo() {
        allowedFieldNames = new HashSet<>();
        fieldTypes        = new HashMap<>();
        allowedChoices    = new HashMap<>();
        fieldAcceptTypes  = new HashMap<String, Set<String>>();
        fieldConstraints  = new HashMap<>();
        try {
            if (!formNode.hasNode(FIELDS_NODE)) {
                log.debug("[FormSubmissionPipeline] No '{}' child on form node '{}'",
                        FIELDS_NODE, formNode.getPath());
                return;
            }
            JCRNodeWrapper fieldList = formNode.getNode(FIELDS_NODE);
            NodeIterator it = fieldList.getNodes();
            while (it.hasNext()) {
                javax.jcr.Node child = it.nextNode();
                if (!(child instanceof JCRNodeWrapper w)) continue;
                collectAllowedFieldsRecursively(w);
            }
        } catch (RepositoryException e) {
            log.warn("[FormSubmissionPipeline] Could not collect form field info from '{}': {}",
                    formNode.getPath(), e.getMessage());
        }
        log.debug("[FormSubmissionPipeline] Allowed fields: {}", allowedFieldNames);
    }

    private void collectAllowedFieldsRecursively(JCRNodeWrapper node) {
        try {
            String nodeType = node.getPrimaryNodeTypeName();
            if (node.isNodeType("fmdbmix:formElement")
                    && !NON_SUBMITTABLE_FORM_ELEMENT_TYPES.contains(nodeType)) {
                registerAllowedField(node, nodeType);
            }

            NodeIterator it = node.getNodes();
            while (it.hasNext()) {
                javax.jcr.Node child = it.nextNode();
                if (child instanceof JCRNodeWrapper childNode) {
                    collectAllowedFieldsRecursively(childNode);
                }
            }
        } catch (RepositoryException e) {
            log.debug("[FormSubmissionPipeline] Cannot traverse node '{}': {}", node.getPath(), e.getMessage());
        }
    }

    private void registerAllowedField(JCRNodeWrapper node, String nodeType) {
        try {
            String name = node.getName();
            if (!allowedFieldNames.add(name)) {
                log.warn("[FormSubmissionPipeline] Duplicate submitted field name '{}' detected; later metadata will overwrite earlier entries.", name);
            }
            fieldTypes.put(name, nodeType);

            switch (nodeType) {
                case "fmdb:checkbox", "fmdb:radio" -> collectChoices(node, name, "choices");
                case "fmdb:select"                 -> collectChoices(node, name, "options");
                case "fmdb:inputFile"              -> {
                    if (node.hasProperty("accept")) {
                        Set<String> accepted = java.util.Arrays.stream(node.getProperty("accept").getValues())
                                .map(v -> { try { return v.getString().trim(); } catch (Exception e2) { return ""; } })
                                .filter(s -> !s.isBlank())
                                .map(FormDataParser::resolveAcceptToken)
                                .collect(java.util.stream.Collectors.toSet());
                        if (!accepted.isEmpty()) fieldAcceptTypes.put(name, accepted);
                    }
                }
            }

            FormDataParser.FieldConstraints c = readConstraints(node, nodeType);
            if (c != null) fieldConstraints.put(name, c);
        } catch (RepositoryException e) {
            log.debug("[FormSubmissionPipeline] Cannot collect metadata for '{}': {}", node.getPath(), e.getMessage());
        }
    }

    /**
     * Reads field constraint properties from JCR (required, minLength, maxLength, pattern, min, max).
     * Only collects what is relevant for the given node type.
     */
    private FormDataParser.FieldConstraints readConstraints(JCRNodeWrapper node, String nodeType) {
        try {
            boolean required  = readBooleanProperty(node, "required");
            long minLength    = readLongProperty(node, "minLength");
            long maxLength    = readLongProperty(node, "maxLength");
            String pattern    = readStringProperty(node, "pattern");
            String minDate    = null;
            String maxDate    = null;

            if ("fmdb:inputDate".equals(nodeType)) {
                minDate = readDateAsIso(node, "min", false);
                maxDate = readDateAsIso(node, "max", false);
            } else if ("fmdb:inputDatetimeLocal".equals(nodeType)) {
                minDate = readDateAsIso(node, "min", true);
                maxDate = readDateAsIso(node, "max", true);
            }

            // Only create a constraints entry if at least one constraint is defined
            if (!required && minLength < 0 && maxLength < 0 && pattern == null
                    && minDate == null && maxDate == null) {
                return null;
            }
            return new FormDataParser.FieldConstraints(required, minLength, maxLength, pattern, minDate, maxDate);
        } catch (RepositoryException e) {
            log.debug("[FormSubmissionPipeline] Cannot read constraints for '{}': {}", node.getPath(), e.getMessage());
            return null;
        }
    }

    /**
     * Post-parse check: verifies that every field marked required=true is present
     * in the parsed output. This catches fields the browser never submitted (e.g.
     * unchecked checkboxes, or a multipart body crafted to omit a field entirely).
     */
    private void validateRequired() throws SubmissionException {
        for (Map.Entry<String, FormDataParser.FieldConstraints> entry : fieldConstraints.entrySet()) {
            if (!entry.getValue().required()) continue;
            String fieldName = entry.getKey();
            String type      = fieldTypes.getOrDefault(fieldName, "");

            if ("fmdb:inputFile".equals(type)) {
                boolean hasFile = parsed.files().stream()
                        .anyMatch(f -> fieldName.equals(f.fieldName()));
                if (!hasFile) {
                    log.warn("[FormSubmissionPipeline] Required file field '{}' has no uploaded file.", fieldName);
                    throw new SubmissionException(ErrorCode.FMDB_010,
                            "Required file field '" + fieldName + "' has no uploaded file.");
                }
            } else {
                List<String> values = parsed.parameters().get(fieldName);
                if (values == null || values.isEmpty() || values.stream().allMatch(String::isBlank)) {
                    log.warn("[FormSubmissionPipeline] Required field '{}' is missing or empty.", fieldName);
                    throw new SubmissionException(ErrorCode.FMDB_010,
                            "Required field '" + fieldName + "' is missing or empty.");
                }
            }
        }
    }

    private void parseMultipart(HttpServletRequest req) throws SubmissionException {
        try {
            FormDataParser.FieldMetadata meta = new FormDataParser.FieldMetadata(
                    allowedFieldNames, fieldTypes, allowedChoices, fieldAcceptTypes, fieldConstraints);
            parsed = FormDataParser.parseAll(req, config, meta);
        } catch (FormDataParser.ParseException e) {
            ErrorCode code = e.isValidation() ? ErrorCode.FMDB_010 : ErrorCode.FMDB_007;
            throw new SubmissionException(code, e.getMessage());
        }
    }

    private void dispatchActions(HttpServletRequest req) throws SubmissionException {
        for (JCRNodeWrapper actionNode : resolveActionNodes(formNode)) {
            String nodeType;
            try {
                nodeType = actionNode.getPrimaryNodeTypeName();
            } catch (RepositoryException e) {
                log.warn("[FormSubmissionPipeline] Cannot read node type for action '{}', skipping.",
                        actionNode.getPath());
                continue;
            }
            FormAction handler = formActions.stream()
                    .filter(a -> nodeType.equals(a.getNodeType()))
                    .findFirst()
                    .orElse(null);
            if (handler == null) {
                log.warn("[FormSubmissionPipeline] No handler for action type '{}', skipping.", nodeType);
                continue;
            }
            try {
                handler.execute(actionNode, req, null, session, parsed.parameters());
            } catch (FormActionException e) {
                throw new SubmissionException(ErrorCode.FMDB_008,
                        "Action '" + nodeType + "' failed: " + e.getMessage());
            }
        }
    }

    // --- Helpers ---

    /**
     * Reads a multi-value choices/options property and extracts the "value" field
     * from each JSON entry produced by SelectOptionsCmp.
     * Expected format per entry: {"value":"foo","label":"Foo","selected":true}
     */
    private void collectChoices(JCRNodeWrapper node, String fieldName, String propName) {
        try {
            if (!node.hasProperty(propName)) return;
            Value[] values = node.getProperty(propName).getValues();
            Set<String> choices = new HashSet<>();
            for (Value v : values) {
                try {
                    JSONObject obj = new JSONObject(v.getString());
                    String val = obj.optString("value", "").trim();
                    if (!val.isEmpty()) choices.add(val);
                } catch (Exception e) {
                    log.debug("[FormSubmissionPipeline] Could not parse choice JSON for field '{}'", fieldName);
                }
            }
            if (!choices.isEmpty()) allowedChoices.put(fieldName, choices);
        } catch (RepositoryException e) {
            log.debug("[FormSubmissionPipeline] Cannot read '{}' on '{}': {}", propName, node.getPath(), e.getMessage());
        }
    }

    private static boolean readBooleanProperty(JCRNodeWrapper node, String prop) throws RepositoryException {
        return node.hasProperty(prop) && node.getProperty(prop).getBoolean();
    }

    private static long readLongProperty(JCRNodeWrapper node, String prop) throws RepositoryException {
        return node.hasProperty(prop) ? node.getProperty(prop).getLong() : -1L;
    }

    private static String readStringProperty(JCRNodeWrapper node, String prop) throws RepositoryException {
        if (!node.hasProperty(prop)) return null;
        String v = node.getProperty(prop).getString();
        return v.isBlank() ? null : v;
    }

    /**
     * Reads a JCR date property and converts it to an ISO-8601 string
     * compatible with submitted form values (yyyy-MM-dd or yyyy-MM-ddTHH:mm).
     */
    private static String readDateAsIso(JCRNodeWrapper node, String prop, boolean includeTime)
            throws RepositoryException {
        if (!node.hasProperty(prop)) return null;
        Calendar cal = node.getProperty(prop).getDate();
        var ldt = cal.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
        return includeTime
                ? ldt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm"))
                : ldt.toLocalDate().toString();
    }

    private static List<JCRNodeWrapper> resolveActionNodes(JCRNodeWrapper formNode) {
        List<JCRNodeWrapper> result = new ArrayList<>();
        try {
            if (!formNode.hasNode(ACTIONS_NODE)) return result;
            JCRNodeWrapper actionList = formNode.getNode(ACTIONS_NODE);
            NodeIterator it = actionList.getNodes();
            while (it.hasNext()) {
                javax.jcr.Node child = it.nextNode();
                if (child instanceof JCRNodeWrapper w) result.add(w);
            }
        } catch (RepositoryException e) {
            log.warn("[FormSubmissionPipeline] Could not read actions from form node '{}'",
                    formNode.getPath(), e);
        }
        return result;
    }
}
