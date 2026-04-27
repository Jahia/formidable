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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Executes the form submission pipeline in enforced order.
 * Each step either completes successfully or throws {@link SubmissionException},
 * which {@link FormSubmitServlet} translates into an opaque JSON error response.
 *
 * Pipeline order (zero bytes of the request stream are consumed before step 8):
 *
 *   1. verifyMultipart        — content-type guard
 *   2. readRoutingParams      — fid + lang from URL query params
 *   3. guardContentLength     — reject oversized requests before touching the stream
 *   4. resolveFormNode        — JCR lookup in "live" workspace
 *   5. verifyAuthentication   — reject Guest if fmdbmix:requireAuthentication is present
 *   6. verifyCaptcha          — only if fmdbmix:captcha mixin is present
 *   7. collectFormFieldInfo   — build field whitelist + per-field accept types from JCR
 *   8. parseMultipart         — first and only read of the stream; unknown fields discarded inline
 *   9. dispatchActions        — execute fmdb:actionList nodes in order
 */
class FormSubmissionPipeline {

    private static final Logger log = LoggerFactory.getLogger(FormSubmissionPipeline.class);

    private static final String ACTIONS_NODE = "actions";
    private static final String FIELDS_NODE  = "fields";

    private final FormidableConfigService config;
    private final List<FormAction> formActions;

    // State accumulated as the pipeline progresses
    private String formId;
    private Locale locale;
    private JCRSessionWrapper session;
    private JCRNodeWrapper formNode;
    private Set<String> allowedFieldNames;
    private Map<String, String> fieldAcceptTypes;
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

    private void verifyCaptcha(HttpServletRequest req) throws SubmissionException {        boolean hasCaptcha;
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
        fieldAcceptTypes = new HashMap<>();
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
                if (w.isNodeType("fmdb:step")) {
                    NodeIterator stepIt = w.getNodes();
                    while (stepIt.hasNext()) {
                        javax.jcr.Node stepChild = stepIt.nextNode();
                        if (stepChild instanceof JCRNodeWrapper sw) {
                            allowedFieldNames.add(sw.getName());
                            collectAcceptIfFile(sw, fieldAcceptTypes);
                        }
                    }
                } else {
                    allowedFieldNames.add(w.getName());
                    collectAcceptIfFile(w, fieldAcceptTypes);
                }
            }
        } catch (RepositoryException e) {
            log.warn("[FormSubmissionPipeline] Could not collect form field info from '{}': {}",
                    formNode.getPath(), e.getMessage());
        }
        log.debug("[FormSubmissionPipeline] Allowed fields: {}", allowedFieldNames);
    }

    private void parseMultipart(HttpServletRequest req) throws SubmissionException {
        try {
            parsed = FormDataParser.parseAll(req, config, fieldAcceptTypes, allowedFieldNames);
        } catch (FormDataParser.ParseException e) {
            throw new SubmissionException(ErrorCode.FMDB_007, e.getMessage());
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

    private static void collectAcceptIfFile(JCRNodeWrapper node, Map<String, String> result) {
        try {
            if (node.isNodeType("fmdb:inputFile") && node.hasProperty("accept")) {
                result.put(node.getName(), node.getProperty("accept").getString());
            }
        } catch (RepositoryException e) {
            log.debug("[FormSubmissionPipeline] Cannot read accept on '{}': {}", node.getPath(), e.getMessage());
        }
    }
}

