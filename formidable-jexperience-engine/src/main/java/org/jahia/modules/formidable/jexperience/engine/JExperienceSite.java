package org.jahia.modules.formidable.jexperience.engine;

import org.jahia.modules.jexperience.admin.ContextServerService;

/**
 * Whether a site is configured for jExperience: the one question the render filter and the
 * response enricher ask before doing anything for a form.
 */
final class JExperienceSite {

    private JExperienceSite() {
    }

    /**
     * True when jExperience holds settings for the site. A site without settings gets no status at
     * all, as opposed to an offline jCustomer, whose status says so; the tracker is only injected
     * where settings exist, so nothing of the integration makes sense elsewhere.
     */
    static boolean configured(ContextServerService service, String siteKey) {
        return service != null && siteKey != null && service.getContextServerStatus(siteKey) != null;
    }
}
