package org.jahia.modules.formidable.quarantine.store;

import java.time.Instant;
import java.util.List;
import java.util.OptionalLong;

/** The sites of the quarantine, as the scheduled run and the scheduled purge read them, an OSGi service. */
public interface QuarantineSites {

    /** The keys of the sites whose quarantine tree exists, in live. */
    List<String> withTree() throws QuarantineStoreException;

    /**
     * Whether the quarantine tree of the site holds a due submission: one in state {@code held}, or in
     * {@code checking} with no next attempt or a next attempt that is not after {@code now}. It writes nothing, so the
     * scheduled run takes the lock of a site only when it has something to do.
     */
    boolean anyDue(String siteKey, Instant now) throws QuarantineStoreException;

    /**
     * The retention that the settings of the site set, in days: the property {@code retentionDays} of the mixin
     * {@code fmdbqmix:quarantineSite} on the site node, in the default workspace, where the site settings are edited.
     *
     * @return empty when the site sets none
     */
    OptionalLong retentionDays(String siteKey) throws QuarantineStoreException;
}
