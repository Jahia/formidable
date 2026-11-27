package org.jahia.modules.formidable.quarantine.schedule;

import org.jahia.modules.formidable.quarantine.store.QuarantineSites;
import org.jahia.modules.formidable.quarantine.store.QuarantineStoreException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/** One call of a scheduled job over each site that has a quarantine tree. */
final class EverySite {

    private static final Logger log = LoggerFactory.getLogger(EverySite.class);

    /** What the job does on one site. */
    @FunctionalInterface
    interface SiteTask {
        void apply(String siteKey) throws QuarantineStoreException;
    }

    private EverySite() {
    }

    /**
     * Applies the task to each site that has a quarantine tree. A site that fails is logged, and the call goes on with
     * the next site.
     *
     * @param what the name of the job, for the log
     */
    static void apply(QuarantineSites sites, String what, SiteTask task) {
        List<String> keys;
        try {
            keys = sites.withTree();
        } catch (QuarantineStoreException e) {
            if (log.isWarnEnabled()) {
                log.warn("The {} of the quarantine cannot list the sites: {}", what, e.getMessage());
            }
            return;
        }
        for (String siteKey : keys) {
            try {
                task.apply(siteKey);
            } catch (QuarantineStoreException e) {
                if (log.isWarnEnabled()) {
                    log.warn("The {} of the quarantine of site {} failed: {}", what, siteKey, e.getMessage());
                }
            } catch (RuntimeException e) {
                // the class alone: the message of an unexpected failure may quote a held value
                if (log.isWarnEnabled()) {
                    log.warn("The {} of the quarantine of site {} failed with {}", what, siteKey,
                            e.getClass().getName());
                }
            }
        }
    }
}
