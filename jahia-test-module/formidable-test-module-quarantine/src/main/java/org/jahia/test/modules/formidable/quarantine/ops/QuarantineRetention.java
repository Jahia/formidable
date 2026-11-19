package org.jahia.test.modules.formidable.quarantine.ops;

import org.jahia.test.modules.formidable.quarantine.store.CheckStore;
import org.jahia.test.modules.formidable.quarantine.store.HeldRecord;
import org.jahia.test.modules.formidable.quarantine.store.HeldStates;
import org.jahia.test.modules.formidable.quarantine.store.QuarantineStore;
import org.jahia.test.modules.formidable.quarantine.store.QuarantineStoreException;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.metatype.annotations.Designate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * The retention of the quarantine tree of a site, an OSGi service: it purges each {@code rejected} held submission
 * whose last change ({@link HeldRecord#changedAt()}) is older than the limit, with its items, its values, its outcomes,
 * its decision and its audit. Any other state is kept: a completed submission holds no value and no byte any more. It
 * has no scheduler: a spec starts it through its endpoint.
 */
@Component(service = QuarantineRetention.class, configurationPid = QuarantineRetention.PID, immediate = true)
@Designate(ocd = RetentionConfig.class)
public class QuarantineRetention {

    private static final Logger log = LoggerFactory.getLogger(QuarantineRetention.class);

    /** The PID of the configuration of the retention. */
    public static final String PID = "org.jahia.test.modules.formidable.quarantine.retention";
    static final int DEFAULT_RETENTION_DAYS = 30;

    /** What a pass purged, and what it could not purge. */
    public record Report(int retentionDays, List<String> purged, List<String> failed) {
        public Report {
            purged = List.copyOf(purged);
            failed = List.copyOf(failed);
        }
    }

    private final Clock clock;
    private volatile int retentionDays = DEFAULT_RETENTION_DAYS;
    private QuarantineStore store;
    private CheckStore checkStore;

    public QuarantineRetention() {
        this(Clock.systemUTC());
    }

    QuarantineRetention(Clock clock) {
        this.clock = clock;
    }

    @Reference
    public void setStore(QuarantineStore store) {
        this.store = store;
    }

    @Reference
    public void setCheckStore(CheckStore checkStore) {
        this.checkStore = checkStore;
    }

    @Activate
    @Modified
    public void configure(RetentionConfig config) {
        int days = config.retentionDays();
        if (days < 0) {
            log.warn("retentionDays={} is negative: the retention keeps {} days", days, DEFAULT_RETENTION_DAYS);
            days = DEFAULT_RETENTION_DAYS;
        }
        retentionDays = days;
        log.info("Retention of the quarantine test module configured: retentionDays={}", retentionDays);
    }

    /** @return the number of days the retention keeps a rejected held submission */
    public int retentionDays() {
        return retentionDays;
    }

    /**
     * Purges the rejected held submissions of the site whose last change is older than the limit. A submission that
     * cannot be purged is reported, and the pass goes on.
     *
     * @param olderThanDays the limit for this pass, in place of {@code retentionDays}; {@code null} keeps the setting
     */
    public synchronized Report purge(String siteKey, Integer olderThanDays) throws QuarantineStoreException {
        int days = olderThanDays != null ? olderThanDays : retentionDays;
        Instant limit = clock.instant().minus(Duration.ofDays(days));
        List<String> purged = new ArrayList<>();
        List<String> failed = new ArrayList<>();
        for (String form : checkStore.forms(siteKey)) {
            for (HeldRecord record : store.list(siteKey, form)) {
                if (isExpired(record, limit)) {
                    purgeOne(record.path(), purged, failed);
                }
            }
        }
        log.info("Retention of the quarantine of site {}: {} rejected held submissions purged, older than {} days",
                siteKey, purged.size(), days);
        return new Report(days, purged, failed);
    }

    static boolean isExpired(HeldRecord record, Instant limit) {
        return HeldStates.REJECTED.equals(record.state()) && record.changedAt() != null
                && record.changedAt().isBefore(limit);
    }

    private void purgeOne(String path, List<String> purged, List<String> failed) {
        try {
            store.purge(path);
            purged.add(path);
        } catch (QuarantineStoreException e) {
            log.warn("Could not purge the held submission at {}: {}", path, e.getMessage());
            failed.add(path);
        }
    }
}
