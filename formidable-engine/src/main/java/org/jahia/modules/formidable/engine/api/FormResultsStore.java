package org.jahia.modules.formidable.engine.api;

import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRSessionWrapper;

import javax.jcr.RepositoryException;
import javax.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * The results of the forms of a site, as the engine keeps them in live under the results root of the site
 * ({@value #RESULTS_ROOT_NAME}): one entry per form, its submissions split by day under it, its readers synced
 * from the form. What another module that writes results — an import from another form system — needs to
 * write them the way the engine does, and nothing of how the engine's own submissions get there.
 * <p>
 * An OSGi service of the engine: the engine alone implements it, and a module calls it. The caller is trusted, as
 * any code of the platform that opens a system session is: no method checks who calls it, nor what it is given.
 */
public interface FormResultsStore {

    /** The name of the results root under a site node. */
    String RESULTS_ROOT_NAME = "formidable-results";

    /** The results root of the site, created when the site has none yet. */
    JCRNodeWrapper getOrCreateResultsRoot(JCRNodeWrapper siteNode, JCRSessionWrapper session) throws RepositoryException;

    /**
     * Gives the node an ACL of its own, inheriting nothing from its parents, as every results entry has: its
     * readers are then only the ones granted on it.
     */
    void detachAcl(JCRNodeWrapper node) throws RepositoryException;

    /** Splits a submissions container by year, month and day of creation, as the engine does on the entries it creates. */
    void ensureAutoSplit(JCRNodeWrapper submissions) throws RepositoryException;

    /** The name of a submission node for the moment it was submitted. */
    String submissionNodeName(Instant submittedAt);

    /**
     * Stores one submission of a form the way the Save to JCR action does: under the results entry of the form,
     * created with its own ACL when the form has none yet, in the day split, in live and in a system session of its
     * own. The values and the files are written apart: a caller that keeps the files elsewhere, such as an action
     * that quarantines them, passes an empty list, and the submission holds the values alone. Nothing of the
     * submission pipeline runs here: the caller hands over values and files that the pipeline already accepted.
     *
     * @param formNode the form the submission answers
     * @param request  the submission request, read for the language, the referer and the time zone of the submitter
     * @param values   the accepted values, by field name; a blank value is not written
     * @param files    the uploads to store as files of the submission; empty to store none
     * @return the identifier of the submission node
     */
    String storeSubmission(JCRNodeWrapper formNode, HttpServletRequest request, Map<String, List<String>> values,
                           List<SubmittedFile> files) throws RepositoryException;

    /**
     * Hands the entry the readers of its form: the results-reader grants of the form, synced onto the entry.
     * A failure is logged, never thrown: the results stand, their readers are set again on the next sync.
     */
    void syncReaders(JCRNodeWrapper formNode, JCRNodeWrapper entry, JCRSessionWrapper session);
}
