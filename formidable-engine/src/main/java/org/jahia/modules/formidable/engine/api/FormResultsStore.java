package org.jahia.modules.formidable.engine.api;

import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRSessionWrapper;

import javax.jcr.RepositoryException;
import java.time.Instant;

/**
 * The results of the forms of a site, as the engine keeps them in live under the results root of the site
 * ({@value #RESULTS_ROOT_NAME}): one entry per form, its submissions split by day under it, its readers synced
 * from the form. What another module that writes results — an import from another form system — needs to
 * write them the way the engine does, and nothing of how the engine's own submissions get there.
 * <p>
 * An OSGi service of the engine.
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
     * Hands the entry the readers of its form: the results-reader grants of the form, synced onto the entry.
     * A failure is logged, never thrown: the results stand, their readers are set again on the next sync.
     */
    void syncReaders(JCRNodeWrapper formNode, JCRNodeWrapper entry, JCRSessionWrapper session);
}
