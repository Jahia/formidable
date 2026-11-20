package org.jahia.test.modules.formidable.samples.actions.form;

import org.jahia.modules.formidable.engine.api.DeferredActionException;
import org.jahia.modules.formidable.engine.api.DeferredActions;
import org.jahia.modules.formidable.engine.api.HeldSubmission;
import org.jahia.modules.formidable.engine.api.SubmissionHold;
import org.jahia.modules.formidable.engine.api.SubmissionHoldException;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

/**
 * A hold for the test suite, and not a quarantine: it stores nothing and checks nothing. It records the number of
 * files it was handed ({@link UploadObservations}), then releases the submission at once, inside the request, through
 * {@link DeferredActions}. When the response arrives, the actions after release have run.
 *
 * <p>A real hold stores the submission and returns: it keeps the bytes where no reader, no parser and no action
 * reaches them until every check passed them, and releases from a job. Never deploy this module on a platform that
 * holds real uploads: this hold releases every file unchecked.</p>
 */
@Component(service = SubmissionHold.class)
public class SampleSubmissionHold implements SubmissionHold {

    private DeferredActions deferredActions;

    @Reference
    public void setDeferredActions(DeferredActions deferredActions) {
        this.deferredActions = deferredActions;
    }

    @Override
    public void hold(HeldSubmission submission) throws SubmissionHoldException {
        UploadObservations.hold(submission.files().size());
        try {
            // nothing to resume: this hold keeps no record of the actions that ran
            deferredActions.run(submission, 0, index -> { });
        } catch (DeferredActionException e) {
            throw new SubmissionHoldException("The sample hold could not release the submission: " + e.getMessage(), e);
        }
    }
}
