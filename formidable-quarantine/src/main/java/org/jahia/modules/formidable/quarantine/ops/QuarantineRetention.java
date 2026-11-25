package org.jahia.modules.formidable.quarantine.ops;

import org.jahia.modules.formidable.quarantine.store.CheckStore;
import org.jahia.modules.formidable.quarantine.store.HeldRecord;
import org.jahia.modules.formidable.quarantine.store.HeldStates;
import org.jahia.modules.formidable.quarantine.store.QuarantineSites;
import org.jahia.modules.formidable.quarantine.store.QuarantineStore;
import org.jahia.modules.formidable.quarantine.store.QuarantineStoreException;
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
import java.util.OptionalLong;

/**
 * The retention of the quarantine tree of a site, an OSGi service: it purges each held submission in a final state,
 * {@code completed} or {@code rejected}, whose last change ({@link HeldRecord#changedAt()}) is older than the limit,
 * with its items, its values, its outcomes, its decision and its audit. A submission in any other state is never
 * purged, whatever its age: it still waits for the run or for a manager. It runs on its own schedule on the processing server ({@code schedule.PurgeSchedule}), and a spec starts it through its
 * endpoint.
 *
 * <p>The limit of a site is the platform setting {@code retentionDays}, or the setting of the site when it is lower: a
 * site can shorten the retention of its finished submissions, and never lengthen it.</p>
 */
@Component(service = QuarantineRetention.class, configurationPid = QuarantineRetention.PID, immediate = true)
@Designate(ocd = RetentionConfig.class)
public class QuarantineRetention {

    private static final Logger log = LoggerFactory.getLogger(QuarantineRetention.class);

    /** The PID of the configuration of the retention. */
    public static final String PID = "org.jahia.modules.formidable.quarantine.retention";
    static final int DEFAULT_RETENTION_DAYS = 30;
    /** The scheduled purge runs once a day. */
    static final long DEFAULT_PERIOD_SECONDS = 86_400;

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
    private QuarantineSites sites;

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

    @Reference
    public void setSites(QuarantineSites sites) {
        this.sites = sites;
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
        log.info("Retention of the quarantine configured: retentionDays={}", retentionDays);
    }

    /** @return the number of days the retention keeps a finished held submission, the setting of the platform */
    public int retentionDays() {
        return retentionDays;
    }

    /**
     * The limit of the site: the setting of the site when it is lower than the setting of the platform, else the
     * setting of the platform. A negative setting of the site is ignored, and so is a site that cannot be read: the
     * retention then keeps the longer limit of the platform.
     */
    public int retentionDaysOf(String siteKey) {
        int platform = retentionDays;
        OptionalLong site;
        try {
            site = sites.retentionDays(siteKey);
        } catch (QuarantineStoreException e) {
            log.warn("The retention of site {} cannot be read, the platform setting of {} days applies: {}", siteKey,
                    platform, e.getMessage());
            return platform;
        }
        if (site.isPresent() && site.getAsLong() >= 0 && site.getAsLong() < platform) {
            return (int) site.getAsLong();
        }
        return platform;
    }

    /**
     * Purges the completed and rejected held submissions of the site whose last change is older than the limit. A submission that
     * cannot be purged is reported, and the pass goes on.
     *
     * @param olderThanDays the limit for this pass, in place of {@code retentionDays}; {@code null} keeps the limit of
     *                      the site ({@link #retentionDaysOf})
     */
    public synchronized Report purge(String siteKey, Integer olderThanDays) throws QuarantineStoreException {
        int days = olderThanDays != null ? olderThanDays : retentionDaysOf(siteKey);
        Instant limit = clock.instant().minus(Duration.ofDays(days));
        List<String> purged = new ArrayList<>();
        List<String> failed = new ArrayList<>();
        for (String form : checkStore.forms(siteKey)) {
            for (HeldRecord held : store.list(siteKey, form)) {
                if (isExpired(held, limit)) {
                    purgeOne(held.path(), purged, failed);
                }
            }
        }
        log.info("Retention of the quarantine of site {}: {} finished held submissions purged, older than {} days",
                siteKey, purged.size(), days);
        return new Report(days, purged, failed);
    }

    /** A final state, {@code completed} or {@code rejected}, whose last change is older than the limit. */
    static boolean isExpired(HeldRecord held, Instant limit) {
        boolean finished = HeldStates.COMPLETED.equals(held.state()) || HeldStates.REJECTED.equals(held.state());
        return finished && held.changedAt() != null
                && held.changedAt().isBefore(limit);
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
