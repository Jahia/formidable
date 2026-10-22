package org.jahia.modules.formidable.formsimport;

import org.jahia.modules.formidable.formsimport.jahiaforms.FormsDisplay;
import org.jahia.modules.formidable.formsimport.jahiaforms.ImportEnvironment;
import org.jahia.services.content.JCRContentUtils;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRSessionWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.ItemNotFoundException;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import javax.jcr.query.Query;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * The pages the redirects of the Forms forms relate to, read in the repository the import runs on
 * (docs/architecture/forms-import.md, "The redirects"): the page a {@code fcnt:redirectToAPageAction} names, looked up
 * in the site the import writes into, and the {@code fcnt:formDisplay} nodes that show a form, which carry the
 * redirect URL of {@code fcmix:formRedirectUrl}. A lookup that fails finds nothing: the redirect is reported.
 */
class ImportSitePages implements ImportEnvironment.SitePages {

    private static final Logger log = LoggerFactory.getLogger(ImportSitePages.class);

    static final String PAGE_TYPE = "jnt:page";
    static final String DISPLAY_TYPE = "fcnt:formDisplay";
    static final String REFERENCE_FORM = "referenceForm";
    static final String REDIRECT_URL = "redirectUrl";
    private static final String SITES = "/sites/";

    private final JCRSessionWrapper edit;
    private final JCRSessionWrapper live;
    private final String siteKey;

    /**
     * @param edit    a session on the edit workspace, where the forms are written and their redirects point
     * @param live    a session on the live workspace, where Forms reads the display of a submitted form
     * @param siteKey the site the import writes into
     */
    ImportSitePages(JCRSessionWrapper edit, JCRSessionWrapper live, String siteKey) {
        this.edit = edit;
        this.live = live;
        this.siteKey = siteKey;
    }

    /**
     * The page by identifier, or by path: the path as Forms stored it, else the same path in this site, so that a form
     * exported from one site and imported into its copy keeps its page.
     */
    @Override
    public Optional<String> pageOf(String reference) {
        if (reference == null || reference.isBlank()) {
            return Optional.empty();
        }
        String value = reference.trim();
        try {
            if (!value.startsWith("/")) {
                return pageOfSite(byIdentifier(value));
            }
            Optional<String> page = pageOfSite(byPath(value));
            return page.isPresent() ? page : pageOfSite(byPath(inThisSite(value)));
        } catch (RepositoryException | RuntimeException e) {
            log.warn("[FormsImport] The page {} of a redirect could not be looked up in site {}", value, siteKey, e);
            return Optional.empty();
        }
    }

    @Override
    public List<FormsDisplay> displaysOf(String formUuid) {
        if (formUuid == null || formUuid.isBlank()) {
            return List.of();
        }
        try {
            if (!live.getWorkspace().getNodeTypeManager().hasNodeType(DISPLAY_TYPE)) {
                return List.of();
            }
            String statement = "SELECT * FROM [" + DISPLAY_TYPE + "] AS d WHERE d.[" + REFERENCE_FORM + "] = '"
                    + formUuid.replace("'", "''") + "'";
            NodeIterator found = live.getWorkspace().getQueryManager().createQuery(statement, Query.JCR_SQL2).execute().getNodes();
            List<FormsDisplay> displays = new ArrayList<>();
            while (found.hasNext()) {
                JCRNodeWrapper display = (JCRNodeWrapper) found.nextNode();
                JCRNodeWrapper page = JCRContentUtils.getParentOfType(display, PAGE_TYPE);
                displays.add(new FormsDisplay(page == null ? display.getPath() : page.getPath(),
                        display.hasProperty(REDIRECT_URL) ? display.getProperty(REDIRECT_URL).getString() : null));
            }
            displays.sort(Comparator.comparing(FormsDisplay::page));
            return displays;
        } catch (RepositoryException | RuntimeException e) {
            log.warn("[FormsImport] The displays of the Forms form {} could not be read", formUuid, e);
            return List.of();
        }
    }

    private JCRNodeWrapper byIdentifier(String identifier) throws RepositoryException {
        try {
            return edit.getNodeByIdentifier(identifier);
        } catch (ItemNotFoundException e) {
            return null;
        }
    }

    private JCRNodeWrapper byPath(String path) throws RepositoryException {
        return path != null && edit.nodeExists(path) ? edit.getNode(path) : null;
    }

    /** {@code /sites/<other>/rest} as {@code /sites/<siteKey>/rest}; null for a path outside {@code /sites}. */
    private String inThisSite(String path) {
        if (!path.startsWith(SITES)) {
            return null;
        }
        int end = path.indexOf('/', SITES.length());
        return ImportJcr.site(siteKey) + (end < 0 ? "" : path.substring(end));
    }

    private Optional<String> pageOfSite(JCRNodeWrapper node) throws RepositoryException {
        if (node == null || !node.isNodeType(PAGE_TYPE) || !node.getPath().startsWith(ImportJcr.site(siteKey) + "/")) {
            return Optional.empty();
        }
        return Optional.of(node.getIdentifier());
    }
}
