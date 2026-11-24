package org.jahia.modules.formidable.engine.servlet;

import org.jahia.modules.formidable.engine.api.DeferredActionException;
import org.jahia.modules.formidable.engine.api.DeferredActions;
import org.jahia.modules.formidable.engine.api.FormAction;
import org.jahia.modules.formidable.engine.api.HeldSubmission;
import org.jahia.services.content.JCRTemplate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.IntConsumer;

/**
 * Runs the actions after release of a form through the dispatch of the submission pipeline, so that an action runs
 * after release as it runs in a request ({@link FormSubmissionPipeline#release}).
 */
@Component(service = DeferredActions.class)
public class DeferredActionsService implements DeferredActions {

    private final List<FormAction> formActions = new CopyOnWriteArrayList<>();
    private final FormSubmissionPipeline.JcrTemplateProvider jcrTemplateProvider;

    public DeferredActionsService() {
        this(JCRTemplate::getInstance);
    }

    DeferredActionsService(FormSubmissionPipeline.JcrTemplateProvider jcrTemplateProvider) {
        this.jcrTemplateProvider = jcrTemplateProvider;
    }

    @Reference(cardinality = ReferenceCardinality.MULTIPLE, policy = ReferencePolicy.DYNAMIC, unbind = "unbindFormAction")
    protected void bindFormAction(FormAction action) {
        formActions.add(action);
    }

    protected void unbindFormAction(FormAction action) {
        formActions.remove(action);
    }

    @Override
    public void run(HeldSubmission submission, int from, IntConsumer completed) throws DeferredActionException {
        Objects.requireNonNull(submission, "submission");
        Objects.requireNonNull(completed, "completed");
        try {
            FormSubmissionPipeline.release(formActions, jcrTemplateProvider, submission, from, completed);
        } catch (SubmissionException e) {
            boolean anActionFailed = e.hasActionProgress();
            throw new DeferredActionException(e.getMessage(),
                    anActionFailed ? e.actionsCompleted : DeferredActionException.NO_ACTION,
                    anActionFailed ? e.actionsTotal : DeferredActionException.NO_ACTION,
                    e.getCause());
        }
    }
}
