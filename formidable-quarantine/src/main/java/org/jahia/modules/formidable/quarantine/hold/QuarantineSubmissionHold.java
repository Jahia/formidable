package org.jahia.modules.formidable.quarantine.hold;

import org.jahia.modules.formidable.engine.api.HeldSubmission;
import org.jahia.modules.formidable.engine.api.SubmissionHold;
import org.jahia.modules.formidable.engine.api.SubmissionHoldException;
import org.jahia.modules.formidable.quarantine.store.QuarantineStore;
import org.jahia.modules.formidable.quarantine.store.QuarantineStoreException;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The hold of the module, at the ranking {@value #RANKING}. A hold that wants some forms, such as the sample hold of
 * {@code formidable-test-module-samples-java}, ranks above it: the engine binds the hold of the highest ranking, which
 * hands any form it does not claim down to this one.
 *
 * <p>It owns every form of a site that has the module, whatever the "Checks" list of the form holds, and reads only
 * the repository to decide (docs/architecture/quarantine.md, "Ownership of a form"). It stores such a submission in
 * the quarantine tree and returns: no check runs on the request thread. It hands any other form to the next hold
 * below its ranking, and refuses the submission when there is none. When the form or its site cannot be read, it
 * refuses too: the next hold may release unchecked.</p>
 */
@Component(name = QuarantineSubmissionHold.COMPONENT_NAME, service = SubmissionHold.class, immediate = true,
        property = {"service.ranking:Integer=" + QuarantineSubmissionHold.RANKING})
public class QuarantineSubmissionHold implements SubmissionHold {

    private static final Logger log = LoggerFactory.getLogger(QuarantineSubmissionHold.class);

    static final String COMPONENT_NAME = "org.jahia.modules.formidable.quarantine.hold.QuarantineSubmissionHold";
    /** The ranking it declares. A hold that claims some forms ranks above it. */
    static final int RANKING = 100;

    private final OtherHolds otherHolds = new OtherHolds();
    /** The ranking the component is bound with, which a configuration of the component may change. */
    private final AtomicInteger boundRanking = new AtomicInteger(RANKING);
    private final FormSites formSites;
    private QuarantineStore store;

    public QuarantineSubmissionHold() {
        this(new JcrFormSites());
    }

    QuarantineSubmissionHold(FormSites formSites) {
        this.formSites = formSites;
    }

    /** Reads the ranking the component is bound with: a form goes only to a hold that ranks below it. */
    @Activate
    @Modified
    public void configure(Map<String, Object> properties) {
        boundRanking.set(OtherHolds.ranking(properties.get(OtherHolds.SERVICE_RANKING)));
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
        SubmissionHold next = otherHolds.nextBelow(boundRanking.get()).orElseThrow(() -> new SubmissionHoldException(
                "The site of form " + formId + " does not have the module " + JcrFormSites.MODULE_ID
                        + ", and no other hold ranks below it"));
        log.debug("The site of form {} does not have the module: handed to {}", formId, next.getClass().getName());
        next.hold(submission);
    }

    private boolean owns(String formId) throws SubmissionHoldException {
        try {
            return formSites.siteHasTheModule(formId);
        } catch (RepositoryException e) {
            throw new SubmissionHoldException("Could not read the site of form " + formId
                    + ": the submission is refused", e);
        }
    }

    private void store(HeldSubmission submission) throws SubmissionHoldException {
        try {
            store.hold(submission);
        } catch (QuarantineStoreException e) {
            throw new SubmissionHoldException("The quarantine could not store the submission of form "
                    + submission.formId() + ": " + e.getMessage(), e);
        }
    }
}
