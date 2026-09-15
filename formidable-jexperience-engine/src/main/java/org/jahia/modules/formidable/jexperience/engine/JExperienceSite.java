package org.jahia.modules.formidable.jexperience.engine;

import org.jahia.modules.jexperience.admin.ContextServerService;
import org.jahia.services.content.decorator.JCRSiteNode;

/**
 * Whether a site's pages carry jExperience's tracker: the one question the render filter and the
 * response enricher ask before contributing anything for a form.
 */
final class JExperienceSite {

    /**
     * The module the site must have, spelt as jExperience's own {@code ContextActivatorFilter}
     * spells it when it decides whether a page gets the tracker at all.
     */
    static final String MODULE = "jexperience";

    private JExperienceSite() {
    }

    /**
     * True when jExperience is enabled on the site <strong>and</strong> holds settings for it. Both
     * conditions stand between a page and {@code wem.min.js}, and jExperience puts them there
     * itself: its context filter refuses a site whose installed modules do not name jExperience,
     * and its script filter takes the tracker's URL from the settings, so a site that is connected
     * to nothing gets no tracker either. Without a tracker there is no listener for what this
     * module writes, so writing it is pure weight in the page and in the answer.
     *
     * <p>Neither half stands alone. The settings are not a per-site answer: jExperience falls back
     * to the platform-wide settings for a site that has none, so on a platform holding a global
     * configuration every site would claim to be configured, jExperience enabled on it or not. And
     * enablement alone says nothing about a jCustomer ever having been connected.</p>
     *
     * <p>Both halves are reads from memory — a list held by the site node, then a map lookup in
     * jExperience's settings service — which is what makes this affordable on every accepted
     * submission and every render cache miss.</p>
     */
    static boolean tracked(JCRSiteNode site, ContextServerService service) {
        return site != null
                && site.getInstalledModules().contains(MODULE)
                && configured(service, site.getSiteKey());
    }

    /**
     * True when jExperience holds settings for the site — which is a weaker question than
     * {@link #tracked}, and the right one for reaching jCustomer rather than for contributing to a
     * page: the mapping rule of a form is written through the same settings, whatever site sends
     * the visitor. A site without settings gets no status at all, as opposed to a jCustomer that is
     * offline, whose status says so.
     */
    static boolean configured(ContextServerService service, String siteKey) {
        return service != null && siteKey != null && service.getContextServerStatus(siteKey) != null;
    }
}
