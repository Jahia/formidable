package org.jahia.modules.formidable.quarantine.store;

import java.util.List;
import java.util.Set;

/**
 * The held submissions of a site, read by a query of the repository rather than by a walk of the tree: a page of
 * them, and their number. The GraphQL API reads them after the permission check on the site. A held
 * submission counts for a site when it lies below the quarantine root of the site. A page holds only those that also
 * name that site, as an opening by identifier requires.
 */
public interface HeldQueries {

    /**
     * The number of held submissions of the site, of one form when asked, in one of the states when asked: the total
     * of the pages of {@link #page}, over every held record of the site that matches.
     *
     * @param siteKey the key of the site, which the caller checked
     * @param formId  the identifier of a form of the site, which the caller checked, or {@code null} for every form
     * @param states  the stored names of the states to keep, or empty for every state
     */
    long count(String siteKey, String formId, Set<String> states) throws QuarantineStoreException;

    /**
     * One page of held submissions, by their time of intake: those of {@link #count} that name the site.
     *
     * @param offset      the index of the first held submission of the page
     * @param limit       the size of the page
     * @param newestFirst {@code true} for the newest first, {@code false} for the oldest first
     */
    List<HeldRecord> page(String siteKey, String formId, Set<String> states, int offset, int limit, boolean newestFirst)
            throws QuarantineStoreException;

    /**
     * The identifiers of the forms that have a folder below the quarantine root of the site, in no particular order: a
     * form whose held submissions the retention purged may still have one, so a caller counts each form.
     *
     * @param siteKey the key of the site, which the caller checked
     */
    List<String> formIds(String siteKey) throws QuarantineStoreException;
}
