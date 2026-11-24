package org.jahia.test.modules.formidable.samples.actions.form;

import org.jahia.modules.formidable.engine.api.DeferredActionException;
import org.jahia.modules.formidable.engine.api.DeferredActions;
import org.jahia.modules.formidable.engine.api.HeldSubmission;
import org.jahia.modules.formidable.engine.api.SubmissionHold;
import org.jahia.modules.formidable.engine.api.SubmissionHoldException;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;

import javax.jcr.RepositoryException;
import java.util.Map;

/**
 * A hold for the test suite, and not a quarantine: it stores nothing and checks nothing. It records the number of
 * files it was handed ({@link UploadObservations}), then releases the submission at once, inside the request, through
 * {@link DeferredActions}. When the response arrives, the actions after release have run.
 *
 * <p>It claims only a form whose "Quarantine checks" list holds a {@value SampleChecks#SAMPLE_CHECK}, and ranks
 * {@value #RANKING}, above the hold of {@code formidable-quarantine} (100): a hold that wants some forms ranks above
 * it and claims them by check type (docs/architecture/quarantine.md, "Ownership of a form"). It hands any other form
 * to the next hold below its ranking, and refuses the submission when there is none, or when it cannot read the
 * list.</p>
 *
 * <p>A real hold stores the submission and returns: it keeps the bytes where no reader, no parser and no action
 * reaches them until every check passed them, and releases from a job. Never deploy this module on a platform that
 * holds real uploads: this hold releases every file unchecked.</p>
 */
@Component(name = SampleSubmissionHold.COMPONENT_NAME, service = SubmissionHold.class,
        property = {"service.ranking:Integer=" + SampleSubmissionHold.RANKING})
public class SampleSubmissionHold implements SubmissionHold {

    static final String COMPONENT_NAME = "org.jahia.test.modules.formidable.samples.actions.form.SampleSubmissionHold";
    /** Above the hold of formidable-quarantine, which ranks 100. */
    static final int RANKING = 200;

    private final LowerHolds lowerHolds = new LowerHolds();
    private final SampleChecks sampleChecks;
    private DeferredActions deferredActions;

    public SampleSubmissionHold() {
        this(SampleChecks.ofThePlatform());
    }

    SampleSubmissionHold(SampleChecks sampleChecks) {
        this.sampleChecks = sampleChecks;
    }

    @Reference
    public void setDeferredActions(DeferredActions deferredActions) {
        this.deferredActions = deferredActions;
    }

    /** Every other hold of the platform: the target leaves this component out. */
    @Reference(service = SubmissionHold.class, cardinality = ReferenceCardinality.MULTIPLE,
            policy = ReferencePolicy.DYNAMIC, unbind = "removeOtherHold", updated = "updatedOtherHold",
            target = "(!(component.name=" + COMPONENT_NAME + "))")
    public void addOtherHold(SubmissionHold hold, Map<String, Object> properties) {
        lowerHolds.add(hold, properties);
    }

    /** A bound hold whose properties changed, such as its ranking. */
    public void updatedOtherHold(SubmissionHold hold, Map<String, Object> properties) {
        lowerHolds.update(hold, properties);
    }

    public void removeOtherHold(SubmissionHold hold) {
        lowerHolds.remove(hold);
    }

    @Override
    public void hold(HeldSubmission submission) throws SubmissionHoldException {
        String formId = submission.formId();
        if (!claims(formId)) {
            SubmissionHold next = lowerHolds.nextBelow(RANKING).orElseThrow(() -> new SubmissionHoldException(
                    "Form " + formId + " has no " + SampleChecks.SAMPLE_CHECK + " in its checks, and no other hold "
                            + "ranks below the sample hold"));
            next.hold(submission);
            return;
        }
        UploadObservations.hold(submission.files().size());
        try {
            // nothing to resume: this hold keeps no record of the actions that ran
            deferredActions.run(submission, 0, index -> { });
        } catch (DeferredActionException e) {
            throw new SubmissionHoldException("The sample hold could not release the submission: " + e.getMessage(), e);
        }
    }

    private boolean claims(String formId) throws SubmissionHoldException {
        try {
            return sampleChecks.holdsASampleCheck(formId);
        } catch (RepositoryException e) {
            throw new SubmissionHoldException("Could not read the checks of form " + formId
                    + ": the submission is refused", e);
        }
    }
}
