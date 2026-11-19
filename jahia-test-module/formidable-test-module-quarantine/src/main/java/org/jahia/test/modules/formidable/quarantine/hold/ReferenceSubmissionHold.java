package org.jahia.test.modules.formidable.quarantine.hold;

import org.jahia.modules.formidable.engine.api.HeldSubmission;
import org.jahia.modules.formidable.engine.api.SubmissionHold;
import org.jahia.modules.formidable.engine.api.SubmissionHoldException;
import org.jahia.test.modules.formidable.quarantine.store.QuarantineStore;
import org.jahia.test.modules.formidable.quarantine.store.QuarantineStoreException;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;
import java.util.Map;

/**
 * The reference hold of the test module. The engine binds the hold of the highest service ranking, so this one ranks
 * above the sample hold of {@code formidable-test-module-samples-java}, which declares no ranking (0).
 *
 * <p>It owns a form only when the "Checks" list of the form holds at least one check of this module
 * ({@code fmdbquar:remoteStubCheck}, {@code fmdbquar:inProcessStubCheck}). It stores such a submission in the
 * quarantine tree and returns: no check runs on the request thread. It hands any other form to the next registered
 * hold, by ranking, and refuses the submission when there is none. When the list of the form cannot be read, it
 * refuses too: the next hold may release unchecked.</p>
 *
 * <p>Never deploy this module in production: its checks are stubs.</p>
 */
@Component(name = ReferenceSubmissionHold.COMPONENT_NAME, service = SubmissionHold.class, immediate = true,
        property = {"service.ranking:Integer=" + ReferenceSubmissionHold.RANKING})
public class ReferenceSubmissionHold implements SubmissionHold {

    private static final Logger log = LoggerFactory.getLogger(ReferenceSubmissionHold.class);

    static final String COMPONENT_NAME = "org.jahia.test.modules.formidable.quarantine.hold.ReferenceSubmissionHold";
    /** Above the sample hold, which declares no ranking. */
    static final int RANKING = 100;

    private final OtherHolds otherHolds = new OtherHolds();
    private final FormChecks formChecks;
    private QuarantineStore store;

    public ReferenceSubmissionHold() {
        this(new JcrFormChecks());
    }

    ReferenceSubmissionHold(FormChecks formChecks) {
        this.formChecks = formChecks;
    }

    @Reference
    public void setStore(QuarantineStore store) {
        this.store = store;
    }

    /** Every other hold of the platform: the target leaves this component out. */
    @Reference(service = SubmissionHold.class, cardinality = ReferenceCardinality.MULTIPLE,
            policy = ReferencePolicy.DYNAMIC, unbind = "removeOtherHold", updated = "updatedOtherHold",
            target = "(!(component.name=" + COMPONENT_NAME + "))")
    public void addOtherHold(SubmissionHold hold, Map<String, Object> properties) {
        otherHolds.add(hold, properties);
    }

    /** A bound hold whose properties changed, such as its ranking. */
    public void updatedOtherHold(SubmissionHold hold, Map<String, Object> properties) {
        otherHolds.update(hold, properties);
    }

    public void removeOtherHold(SubmissionHold hold) {
        otherHolds.remove(hold);
    }

    @Override
    public void hold(HeldSubmission submission) throws SubmissionHoldException {
        String formId = submission.formId();
        if (owns(formId)) {
            store(submission);
            return;
        }
        SubmissionHold next = otherHolds.next().orElseThrow(() -> new SubmissionHoldException("Form " + formId
                + " has no check of the quarantine test module in its \"Checks\" list, and no other hold is registered"));
        log.debug("Form {} has no check of the quarantine test module: handed to {}", formId, next.getClass().getName());
        next.hold(submission);
    }

    private boolean owns(String formId) throws SubmissionHoldException {
        try {
            return formChecks.holdsACheckOfTheModule(formId);
        } catch (RepositoryException e) {
            throw new SubmissionHoldException("Could not read the \"Checks\" list of form " + formId
                    + ": the submission is refused", e);
        }
    }

    private void store(HeldSubmission submission) throws SubmissionHoldException {
        try {
            store.hold(submission);
        } catch (QuarantineStoreException e) {
            throw new SubmissionHoldException("The reference hold could not store the submission of form "
                    + submission.formId() + ": " + e.getMessage(), e);
        }
    }
}
