package org.jahia.modules.formidable.engine.actions.storage;

import org.jahia.modules.formidable.engine.actions.FormAction;
import org.jahia.modules.formidable.engine.actions.FormActionException;
import org.jahia.modules.formidable.engine.actions.FormDataParser;
import org.jahia.services.content.JCRAutoSplitUtils;
import org.jahia.services.content.JCRContentUtils;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRSessionWrapper;
import org.jahia.services.render.RenderContext;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.Binary;
import javax.jcr.RepositoryException;
import javax.jcr.Value;
import javax.servlet.http.HttpServletRequest;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Calendar;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Saves the submitted form data as child nodes under a site-level JCR results tree.
 */
@Component(service = FormAction.class)
public class SaveToJcrFormAction implements FormAction {

    private static final Logger log = LoggerFactory.getLogger(SaveToJcrFormAction.class);
    private static final String RESULTS_ROOT_NAME = "formidable-results";
    private static final String SUBMISSION_ORIGIN = "formidable";
    private static final String SUBMISSION_STATUS = "accepted";
    private static final String SPLIT_CONFIG = "date,jcr:created,yyyy;date,jcr:created,MM;date,jcr:created,dd";
    private static final String SPLIT_NODE_TYPE = "fmdb:splittedSubmission";

    @Override
    public String getNodeType() {
        return "fmdb:save2jcrAction";
    }

    @Override
    public void execute(
            JCRNodeWrapper actionNode,
            HttpServletRequest req,
            RenderContext renderContext,
            JCRSessionWrapper session,
            Map<String, List<String>> parameters
    ) throws FormActionException {
        JCRNodeWrapper formNode = resolveFormNode(actionNode);

        @SuppressWarnings("unchecked")
        List<FormDataParser.FormFile> files =
                (List<FormDataParser.FormFile>) req.getAttribute(FormDataParser.PARSED_FILES_ATTR);
        if (files == null) {
            throw FormActionException.serverError(
                    "Validated uploaded files are unavailable for fmdb:save2jcrAction.");
        }

        try {
            JCRNodeWrapper formResults = resolveOrCreateFormResults(formNode, session);
            JCRNodeWrapper submissions = formResults.getNode("submissions");
            ensureAutoSplit(submissions);

            JCRNodeWrapper submission = createSubmissionNode(submissions, req, session);
            session.save();

            JCRNodeWrapper movedSubmission = JCRAutoSplitUtils.applyAutoSplitRules(submission, submissions);
            if (movedSubmission != null) {
                submission = movedSubmission;
                session.save();
            }

            populateSubmissionData(submission, parameters, files, session);
            session.save();
        } catch (RepositoryException e) {
            log.error("[SaveToJcrFormAction] Failed to persist submission for form '{}'", safeNodePath(formNode), e);
            throw new FormActionException("Failed to save form data in JCR: " + e.getMessage(), 500, e);
        }
    }

    private static JCRNodeWrapper resolveFormNode(JCRNodeWrapper actionNode) throws FormActionException {
        try {
            JCRNodeWrapper actionListNode = (JCRNodeWrapper) actionNode.getParent();
            if (actionListNode == null) {
                throw FormActionException.serverError("The JCR storage action is not attached to an action list.");
            }

            JCRNodeWrapper formNode = (JCRNodeWrapper) actionListNode.getParent();
            if (formNode == null || !formNode.isNodeType("fmdb:form")) {
                throw FormActionException.serverError("The JCR storage action parent form could not be resolved.");
            }

            return formNode;
        } catch (RepositoryException e) {
            throw new FormActionException("Could not resolve form node for JCR storage action.", 500, e);
        }
    }

    private static JCRNodeWrapper resolveOrCreateFormResults(JCRNodeWrapper formNode, JCRSessionWrapper session)
            throws RepositoryException {
        JCRNodeWrapper siteNode = formNode.getResolveSite();
        JCRNodeWrapper resultsRoot;
        if (siteNode.hasNode(RESULTS_ROOT_NAME)) {
            resultsRoot = siteNode.getNode(RESULTS_ROOT_NAME);
        } else {
            session.checkout(siteNode);
            resultsRoot = siteNode.addNode(RESULTS_ROOT_NAME, "fmdb:resultsFolder");
            session.save();
        }

        String formResultsName = formNode.getIdentifier();
        if (resultsRoot.hasNode(formResultsName)) {
            return resultsRoot.getNode(formResultsName);
        }

        session.checkout(resultsRoot);
        JCRNodeWrapper formResults = resultsRoot.addNode(formResultsName, "fmdb:formResults");
        formResults.setProperty("jcr:title", resolveFormTitle(formNode));
        Value parentForm = session.getValueFactory().createValue(formNode);
        formResults.setProperty("parentForm", parentForm);
        if (formNode.getLanguage() != null && !formNode.getLanguage().isBlank()) {
            formResults.setProperty("buildingLang", formNode.getLanguage());
        }
        session.save();
        return formResults;
    }

    private static void ensureAutoSplit(JCRNodeWrapper submissions) throws RepositoryException {
        if (!submissions.isNodeType("jmix:autoSplitFolders")) {
            JCRAutoSplitUtils.enableAutoSplitting(submissions, SPLIT_CONFIG, SPLIT_NODE_TYPE);
        }
    }

    private static JCRNodeWrapper createSubmissionNode(
            JCRNodeWrapper submissions,
            HttpServletRequest req,
            JCRSessionWrapper session
    ) throws RepositoryException {
        session.checkout(submissions);
        String submissionName = UUID.randomUUID().toString();
        JCRNodeWrapper submission = submissions.addNode(submissionName, "fmdb:formSubmission");
        submission.setProperty("origin", SUBMISSION_ORIGIN);
        submission.setProperty("status", SUBMISSION_STATUS);
        setOptionalProperty(submission, "ipAddress", req.getRemoteAddr());
        setOptionalProperty(submission, "locale", req.getParameter("lang"));
        setOptionalProperty(submission, "submitterUsername", session.getUserID());
        setOptionalProperty(submission, "userAgent", req.getHeader("User-Agent"));
        setOptionalProperty(submission, "referer", req.getHeader("Referer"));
        return submission;
    }

    private static void populateSubmissionData(
            JCRNodeWrapper submission,
            Map<String, List<String>> parameters,
            List<FormDataParser.FormFile> files,
            JCRSessionWrapper session
    ) throws RepositoryException {
        session.checkout(submission);
        JCRNodeWrapper dataNode = submission.getNode("data");
        for (Map.Entry<String, List<String>> entry : parameters.entrySet()) {
            List<String> values = entry.getValue();
            if (values == null || values.isEmpty()) {
                continue;
            }
            if (values.size() == 1) {
                dataNode.setProperty(entry.getKey(), values.get(0));
            } else {
                dataNode.setProperty(entry.getKey(), values.toArray(String[]::new));
            }
        }

        JCRNodeWrapper filesNode = submission.getNode("files");
        for (FormDataParser.FormFile file : files) {
            JCRNodeWrapper fieldFolder = filesNode.hasNode(file.fieldName())
                    ? filesNode.getNode(file.fieldName())
                    : filesNode.addNode(file.fieldName(), "jnt:folder");
            addFileNode(fieldFolder, file, session);
        }
    }

    private static void addFileNode(
            JCRNodeWrapper fieldFolder,
            FormDataParser.FormFile file,
            JCRSessionWrapper session
    ) throws RepositoryException {
        session.checkout(fieldFolder);
        String fileNodeName = JCRContentUtils.findAvailableNodeName(fieldFolder, file.storageName());
        JCRNodeWrapper fileNode = fieldFolder.addNode(fileNodeName, "jnt:file");
        JCRNodeWrapper contentNode = fileNode.addNode("jcr:content", "jnt:resource");

        try (ByteArrayInputStream input = new ByteArrayInputStream(file.data())) {
            Binary binary = session.getValueFactory().createBinary(input);
            contentNode.setProperty("jcr:data", binary);
            binary.dispose();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        contentNode.setProperty("jcr:mimeType", file.mimeType());
        contentNode.setProperty("jcr:lastModified", Calendar.getInstance());
    }

    private static void setOptionalProperty(JCRNodeWrapper node, String propertyName, String value)
            throws RepositoryException {
        if (value != null && !value.isBlank()) {
            node.setProperty(propertyName, value);
        }
    }

    private static String resolveFormTitle(JCRNodeWrapper formNode) {
        try {
            if (formNode.hasProperty("jcr:title")) {
                String title = formNode.getProperty("jcr:title").getString();
                if (title != null && !title.isBlank()) {
                    return title;
                }
            }
            String displayableName = formNode.getDisplayableName();
            if (displayableName != null && !displayableName.isBlank()) {
                return displayableName;
            }
        } catch (RepositoryException e) {
            log.debug("[SaveToJcrFormAction] Could not resolve form title for '{}': {}", safeNodePath(formNode), e.getMessage());
        }
        return formNode.getName();
    }

    private static String safeNodePath(JCRNodeWrapper node) {
        return node.getPath();
    }
}
