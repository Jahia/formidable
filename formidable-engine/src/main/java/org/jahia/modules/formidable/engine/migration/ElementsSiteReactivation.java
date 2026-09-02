package org.jahia.modules.formidable.engine.migration;

import org.jahia.registries.ServicesRegistry;
import org.jahia.services.content.JCRNodeIteratorWrapper;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRSessionWrapper;
import org.jahia.services.content.JCRTemplate;
import org.jahia.services.content.decorator.JCRSiteNode;
import org.jahia.services.observation.JahiaEventListener;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;
import javax.jcr.query.Query;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Re-enables formidable-elements on the sites that carry forms but lost the module from
 * their installed list. The 0.3 → 0.4 upgrade goes through an uninstall/reinstall (the
 * module identity changed): the uninstall drops the module from every site's
 * {@code j:installedModules}, the new-identity install does not re-add it, and the site
 * then renders broken forms and a broken editor while the definitions and the migrated
 * content are perfectly fine — the single most misleading state of the whole upgrade.
 *
 * <p>The site node keeps no trace of the departed module, so the reliable signal is the
 * CONTENT: a site holding {@code fmdb:form} nodes used the module. Keyed on that state,
 * so re-running is a no-op once every form-bearing site has the module back, and a site
 * whose admin deliberately removed both the module and its forms is never touched.
 *
 * <p>Lifecycle: upgrade healing introduced in 0.4.0, to be removed in 0.5 with the other
 * startup migrations — see docs/upgrade-notes.md, "Startup migrations".
 */
@Component(service = {ElementsSiteReactivation.class, JahiaEventListener.class}, immediate = true)
public class ElementsSiteReactivation extends ElementsRedeployRetriggeredMigration {

    private static final Logger log = LoggerFactory.getLogger(ElementsSiteReactivation.class);

    private static final String FORM_TYPE = "fmdb:form";

    @Activate
    public void activate() {
        run();
    }

    @Override
    void run() {
        try {
            JCRTemplate.getInstance().doExecuteWithSystemSessionAsUser(null, "default", null, session -> {
                for (String sitePath : sitesNeedingReactivation(session)) {
                    reactivate(sitePath);
                }
                return null;
            });
        } catch (RepositoryException e) {
            log.error("[ElementsSiteReactivation] Could not check the sites' installed modules: {}", e.getMessage(), e);
        }
    }

    /** The paths of the sites that hold at least one form but no longer list the elements module. */
    private static Set<String> sitesNeedingReactivation(JCRSessionWrapper session) throws RepositoryException {
        // The form type belongs to the elements module: on an instance where it never
        // registered (engine-only, or the engine-first upgrade step) there is nothing
        // to look for yet, and querying an unregistered type would throw.
        if (!session.getWorkspace().getNodeTypeManager().hasNodeType(FORM_TYPE)) {
            log.debug("[ElementsSiteReactivation] Type {} is not registered, nothing to check", FORM_TYPE);
            return Set.of();
        }

        Query query = session.getWorkspace().getQueryManager()
                .createQuery("SELECT * FROM [" + FORM_TYPE + "] WHERE ISDESCENDANTNODE('/sites')", Query.JCR_SQL2);
        JCRNodeIteratorWrapper forms = (JCRNodeIteratorWrapper) query.execute().getNodes();

        Set<String> sitePaths = new LinkedHashSet<>();
        while (forms.hasNext()) {
            JCRNodeWrapper form = (JCRNodeWrapper) forms.nextNode();
            try {
                String orphanedSitePath = orphanedSitePath(form);
                if (orphanedSitePath != null) {
                    sitePaths.add(orphanedSitePath);
                }
            } catch (RepositoryException e) {
                log.warn("[ElementsSiteReactivation] Could not resolve the site of form '{}': {}",
                        form.getPath(), e.getMessage());
            }
        }
        return sitePaths;
    }

    /**
     * The path of the form's site when that site lost the elements module from its
     * installed list, null when the site is healthy (or the form is siteless).
     * Visible for tests: this is the selection rule.
     */
    static String orphanedSitePath(JCRNodeWrapper form) throws RepositoryException {
        JCRSiteNode site = form.getResolveSite();
        if (site == null || site.getInstalledModules().contains(ELEMENTS_MODULE_ID)) {
            return null;
        }
        return site.getPath();
    }

    /**
     * One site at a time: a failure must never keep the later sites broken. The remedy
     * is the exact gesture docs/upgrade-notes.md step 4 asks of the operator.
     */
    private static void reactivate(String sitePath) {
        try {
            ServicesRegistry.getInstance().getJahiaTemplateManagerService()
                    .installModule(ELEMENTS_MODULE_ID, sitePath, "root");
            log.info("[ElementsSiteReactivation] Re-enabled {} on site '{}': the site holds forms but had "
                    + "lost the module from its installed list (0.3 -> 0.4 module-identity change)",
                    ELEMENTS_MODULE_ID, sitePath);
        } catch (RepositoryException | RuntimeException e) {
            log.error("[ElementsSiteReactivation] Could not re-enable {} on site '{}' — do it manually "
                    + "(docs/upgrade-notes.md step 4): {}", ELEMENTS_MODULE_ID, sitePath, e.getMessage(), e);
        }
    }
}
