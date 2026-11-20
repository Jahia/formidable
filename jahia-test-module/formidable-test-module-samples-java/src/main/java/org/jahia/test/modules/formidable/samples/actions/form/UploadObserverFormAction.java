package org.jahia.test.modules.formidable.samples.actions.form;

import org.jahia.modules.formidable.engine.api.ContextualFormAction;
import org.jahia.modules.formidable.engine.api.FormAction;
import org.jahia.modules.formidable.engine.api.FormActionContext;
import org.jahia.modules.formidable.engine.api.FormActionException;
import org.jahia.modules.formidable.engine.api.FormActionResult;
import org.jahia.services.content.JCRNodeWrapper;
import org.osgi.service.component.annotations.Component;

import javax.jcr.RepositoryException;

/**
 * A contextual action that records the number of files it was handed, with the name of its node and of the list
 * that holds it ({@link UploadObservations}). A test-only observable, for the suite to read which actions of a form
 * receive its uploads.
 *
 * <p>Its type declares {@code fmdbmix:deferrableAction}: the action reads nothing of the request and writes nothing
 * to the response, so it runs after release as it runs in a request.</p>
 */
@Component(service = FormAction.class)
public class UploadObserverFormAction implements ContextualFormAction {

    public static final String NODE_TYPE = "fmdbsample:uploadObserverAction";

    @Override
    public String getNodeType() {
        return NODE_TYPE;
    }

    @Override
    public FormActionResult execute(FormActionContext context) throws FormActionException {
        JCRNodeWrapper actionNode = context.actionNode();
        try {
            UploadObservations.observer(actionNode.getName(), actionNode.getParent().getName(), context.files().size());
        } catch (RepositoryException e) {
            throw new FormActionException("The upload observer could not read the list of its node.", 500, e);
        }
        return FormActionResult.none();
    }
}
