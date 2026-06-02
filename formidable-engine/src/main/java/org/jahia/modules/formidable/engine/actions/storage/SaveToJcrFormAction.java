package org.jahia.modules.formidable.engine.actions.storage;

import org.jahia.modules.formidable.engine.actions.FormAction;
import org.jahia.modules.formidable.engine.actions.FormActionException;
import org.jahia.modules.formidable.engine.actions.SubmittedFile;
import org.jahia.modules.formidable.engine.permissions.FormResultsAclSyncService;
import org.jahia.services.content.JCRAutoSplitUtils;
import org.jahia.services.content.JCRContentUtils;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRSessionWrapper;
import org.jahia.services.content.JCRTemplate;
import org.osgi.service.component.annotations.Component;

import javax.jcr.Binary;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Value;
import javax.servlet.http.HttpServletRequest;
import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Calendar;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.jahia.modules.formidable.engine.util.FormidableJcrConstants.ACL_NODE;
import static org.jahia.modules.formidable.engine.util.FormidableJcrConstants.ACL_NODE_TYPE;
import static org.jahia.modules.formidable.engine.util.FormidableJcrConstants.FORM_NODE_TYPE;
import static org.jahia.modules.formidable.engine.util.FormidableJcrConstants.FORM_RESULTS_NODE_TYPE;
import static org.jahia.modules.formidable.engine.util.FormidableJcrConstants.INHERIT_PROPERTY;
import static org.jahia.modules.formidable.engine.util.FormidableJcrConstants.PARENT_FORM_PROPERTY;
import static org.jahia.modules.formidable.engine.util.FormidableJcrConstants.WORKSPACE_LIVE;

/**
 * Saves the submitted form data as child nodes under a site-level JCR results tree.
 */
@Component(service = FormAction.class)
public class SaveToJcrFormAction implements FormAction {
    private static final String RESULTS_ROOT_NAME = "formidable-results";
    private static final String SUBMISSION_ORIGIN = "formidable";
    private static final String SPLIT_CONFIG = "date,jcr:created,yyyy;date,jcr:created,MM;date,jcr:created,dd";
    private static final String SPLIT_NODE_TYPE = "fmdb:splittedSubmission";
    private static final String FILES_NODE_NAME = "files";
    private static final DateTimeFormatter SUBMISSION_NAME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    @Override
    public String getNodeType() {
        return "fmdb:save2jcrAction";
    }

    @Override
    public void execute(
            JCRNodeWrapper actionNode,
            HttpServletRequest req,
            JCRSessionWrapper session,
            Map<String, List<String>> parameters,
            List<SubmittedFile> files
    ) throws FormActionException {
        JCRNodeWrapper formNode = resolveFormNode(actionNode);
        String formNodeId;
        try {
            formNodeId = formNode.getIdentifier();
        } catch (RepositoryException e) {
            throw new FormActionException("Could not read form node identifier.", 500, e);
        }

        try {
            JCRTemplate.getInstance().doExecuteWithSystemSessionAsUser(null, WORKSPACE_LIVE, null, systemSession -> {
                JCRNodeWrapper sysFormNode = systemSession.getNodeByIdentifier(formNodeId);
                JCRNodeWrapper formResults = resolveOrCreateFormResults(sysFormNode, systemSession);
                JCRNodeWrapper submissions = formResults.getNode("submissions");
                ensureAutoSplit(submissions);

                JCRNodeWrapper submission = createSubmissionNode(submissions, req, systemSession);
                String submissionId = submission.getIdentifier();
                systemSession.save();

                JCRAutoSplitUtils.applyAutoSplitRules(submission, submissions);
                systemSession.save();

                submission = systemSession.getNodeByIdentifier(submissionId);

                populateSubmissionData(submission, parameters, files, systemSession);
                systemSession.save();
                return null;
            });
        } catch (RepositoryException e) {
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
            if (formNode == null || !formNode.isNodeType(FORM_NODE_TYPE)) {
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

        JCRNodeWrapper existingFormResults = findFormResultsByParentForm(resultsRoot, formNode);
        if (existingFormResults != null) {
            return renameFormResultsIfNeeded(existingFormResults, resultsRoot, formNode, session);
        }

        session.checkout(resultsRoot);
        String availableName = JCRContentUtils.findAvailableNodeName(resultsRoot, formNode.getName());
        JCRNodeWrapper formResults = resultsRoot.addNode(availableName, FORM_RESULTS_NODE_TYPE);
        Value parentForm = session.getValueFactory().createValue(formNode);
        formResults.setProperty(PARENT_FORM_PROPERTY, parentForm);
        if (formNode.getLanguage() != null && !formNode.getLanguage().isBlank()) {
            formResults.setProperty("buildingLang", formNode.getLanguage());
        }

        // Break ACL inheritance so public reader role does not grant access to results
        JCRNodeWrapper acl = formResults.addNode(ACL_NODE, ACL_NODE_TYPE);
        acl.setProperty(INHERIT_PROPERTY, false);

        session.save();

        FormResultsAclSyncService.syncAclToFormResults(formNode, formResults, session);
        session.save();

        return formResults;
    }

    private static JCRNodeWrapper findFormResultsByParentForm(JCRNodeWrapper resultsRoot, JCRNodeWrapper formNode)
            throws RepositoryException {
        String formIdentifier = formNode.getIdentifier();
        NodeIterator children = resultsRoot.getNodes();
        while (children.hasNext()) {
            javax.jcr.Node child = children.nextNode();
            if (!(child instanceof JCRNodeWrapper candidate)) {
                continue;
            }
            if (!candidate.isNodeType(FORM_RESULTS_NODE_TYPE) || !candidate.hasProperty(PARENT_FORM_PROPERTY)) {
                continue;
            }
            if (formIdentifier.equals(candidate.getProperty(PARENT_FORM_PROPERTY).getString())) {
                return candidate;
            }
        }
        return null;
    }

    private static JCRNodeWrapper renameFormResultsIfNeeded(
            JCRNodeWrapper formResults,
            JCRNodeWrapper resultsRoot,
            JCRNodeWrapper formNode,
            JCRSessionWrapper session
    ) throws RepositoryException {
        String expectedName = formNode.getName();
        if (expectedName.equals(formResults.getName())) {
            return formResults;
        }

        String availableName = JCRContentUtils.findAvailableNodeName(resultsRoot, expectedName);
        String targetPath = resultsRoot.getPath() + "/" + availableName;
        session.move(formResults.getPath(), targetPath);
        session.save();
        return session.getNode(targetPath);
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
        String submissionName = buildSubmissionNodeName();
        String availableName = JCRContentUtils.findAvailableNodeName(submissions, submissionName);
        JCRNodeWrapper submission = submissions.addNode(availableName, "fmdb:formSubmission");
        submission.setProperty("origin", SUBMISSION_ORIGIN);
        setOptionalProperty(submission, "locale", req.getParameter("lang"));
        setOptionalProperty(submission, "referer", req.getHeader("Referer"));
        return submission;
    }

    private static String buildSubmissionNodeName() {
        String timestamp = LocalDateTime.now().format(SUBMISSION_NAME_FORMATTER);
        String shortUuid = UUID.randomUUID().toString().substring(0, 3);
        return "submission-" + timestamp + "-" + shortUuid;
    }

    private static void populateSubmissionData(
            JCRNodeWrapper submission,
            Map<String, List<String>> parameters,
            List<SubmittedFile> files,
            JCRSessionWrapper session
    ) throws RepositoryException {
        session.checkout(submission);
        JCRNodeWrapper dataNode = submission.getNode("data");
        for (Map.Entry<String, List<String>> entry : parameters.entrySet()) {
            writeParameterValue(dataNode, entry.getKey(), entry.getValue());
        }

        persistSubmittedFiles(submission, files, session);
    }

    private static void writeParameterValue(JCRNodeWrapper dataNode, String fieldName, List<String> values)
            throws RepositoryException {
        List<String> nonBlankValues = normalizeSubmittedValues(values);
        if (nonBlankValues.isEmpty()) {
            return;
        }
        if (nonBlankValues.size() == 1) {
            dataNode.setProperty(fieldName, nonBlankValues.get(0));
        } else {
            dataNode.setProperty(fieldName, nonBlankValues.toArray(String[]::new));
        }
    }

    private static List<String> normalizeSubmittedValues(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream()
                .filter(v -> v != null && !v.isBlank())
                .toList();
    }

    private static void persistSubmittedFiles(
            JCRNodeWrapper submission,
            List<SubmittedFile> files,
            JCRSessionWrapper session
    ) throws RepositoryException {
        if (files.isEmpty()) {
            return;
        }
        JCRNodeWrapper filesNode = submission.hasNode(FILES_NODE_NAME)
                ? submission.getNode(FILES_NODE_NAME)
                : submission.addNode(FILES_NODE_NAME, "jnt:folder");
        for (SubmittedFile file : files) {
            JCRNodeWrapper fieldFolder = resolveOrCreateFieldFolder(filesNode, file.fieldName());
            addFileNode(fieldFolder, file, session);
        }
    }

    private static JCRNodeWrapper resolveOrCreateFieldFolder(JCRNodeWrapper filesNode, String fieldName)
            throws RepositoryException {
        return filesNode.hasNode(fieldName)
                ? filesNode.getNode(fieldName)
                : filesNode.addNode(fieldName, "jnt:folder");
    }

    private static void addFileNode(
            JCRNodeWrapper fieldFolder,
            SubmittedFile file,
            JCRSessionWrapper session
    ) throws RepositoryException {
        session.checkout(fieldFolder);
        String fileNodeName = JCRContentUtils.findAvailableNodeName(fieldFolder, file.originalName());
        JCRNodeWrapper fileNode = fieldFolder.addNode(fileNodeName, "jnt:file");
        JCRNodeWrapper contentNode = fileNode.addNode("jcr:content", "jnt:resource");

        ByteArrayInputStream input = new ByteArrayInputStream(file.data());
        Binary binary = session.getValueFactory().createBinary(input);
        try {
            contentNode.setProperty("jcr:data", binary);
        } finally {
            binary.dispose();
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
}
