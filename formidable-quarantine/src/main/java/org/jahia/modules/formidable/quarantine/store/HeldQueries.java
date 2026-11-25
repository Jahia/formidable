package org.jahia.modules.formidable.quarantine.store;

import java.util.List;
import java.util.Set;

/**
 * The held submissions of a site, read by a query of the repository rather than by a walk of the tree: a page of
 * them, and their number in one state. The GraphQL API reads them after the permission check on the site. A held
 * submission counts for a site when it lies below the quarantine root of the site. A page holds only those that also
 * name that site, as an opening by identifier requires.
 */
public interface HeldQueries {

    /**
     * One page of held submissions, oldest first, and the number of those that match on every page.
     *
     * @param total   the number of held submissions that match
     * @param records the held submissions of the page
     */
    record Page(long total, List<HeldRecord> records) {
        public Page {
            records = List.copyOf(records);
        }
    }

    /**
     * @param siteKey the key of the site, which the caller checked
     * @param formId  the identifier of a form of the site, which the caller checked, or {@code null} for every form
     * @param states  the stored names of the states to keep, or empty for every state
     * @param offset  the index of the first held submission of the page
     * @param limit   the size of the page
     */
    Page page(String siteKey, String formId, Set<String> states, int offset, int limit)
            throws QuarantineStoreException;

    /** The number of held submissions of the site in the state, by its stored name. */
    long count(String siteKey, String state) throws QuarantineStoreException;
}
