package org.jahia.modules.formidable.quarantine.hold;

import org.jahia.data.templates.JahiaTemplatesPackage;
import org.jahia.registries.ServicesRegistry;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRTemplate;
import org.jahia.services.content.decorator.JCRSiteNode;

import javax.jcr.RepositoryException;
import javax.jcr.Value;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Reads the site of a form in live, with a system session: the visitor who submits cannot read it. Only the
 * repository decides, through the modules of the site and their dependencies, never the check services registered at
 * that moment.
 */
public final class JcrFormSites implements FormSites {

    /** The identifier of this module, as a site lists it among its modules. */
    public static final String MODULE_ID = "formidable-quarantine";
    /** The property of a site that lists its modules, each as {@code <id>} or {@code <id>:<version>}. */
    static final String INSTALLED_MODULES = "j:installedModules";
    private static final String LIVE_WORKSPACE = "live";

    /** The modules a module depends on, by identifier, as the platform resolves them. */
    @FunctionalInterface
    interface ModuleDependencies {
        List<String> of(String moduleId);
    }

    private final ModuleDependencies dependencies;

    public JcrFormSites() {
        this(JcrFormSites::platformDependencies);
    }

    JcrFormSites(ModuleDependencies dependencies) {
        this.dependencies = dependencies;
    }

    @Override
    public boolean siteHasTheModule(String formId) throws RepositoryException {
        return JCRTemplate.getInstance().doExecuteWithSystemSessionAsUser(null, LIVE_WORKSPACE, null,
                session -> hasTheModule(session.getNodeByIdentifier(formId)));
    }

    /**
     * The rule of ownership: the site of the form has the module, enabled on the site or through a module of the site
     * that depends on it, as Jahia resolves the modules of a site everywhere else. The list is read from the property
     * of the site itself: {@code JCRSiteNode#getInstalledModules()} answers an empty list when the read fails, which
     * would hand the form to a hold that may release it unchecked.
     *
     * @throws RepositoryException when the form is under no site, or the modules of its site cannot be read; a site
     *                             always lists its template set, so an empty list is a failed read too
     */
    boolean hasTheModule(JCRNodeWrapper form) throws RepositoryException {
        JCRSiteNode site = form.getResolveSite();
        if (site == null) {
            throw new RepositoryException("The form " + form.getIdentifier() + " is under no site");
        }
        Value[] values = site.getProperty(INSTALLED_MODULES).getValues();
        if (values.length == 0) {
            throw new RepositoryException("The site " + site.getPath() + " lists no module");
        }
        List<String> pending = new ArrayList<>();
        for (Value value : values) {
            String entry = value.getString();
            int colon = entry.indexOf(':');
            pending.add(colon < 0 ? entry : entry.substring(0, colon));
        }
        return withDependencies(pending).contains(MODULE_ID);
    }

    private Set<String> withDependencies(List<String> pending) {
        Set<String> modules = new LinkedHashSet<>(pending);
        for (int i = 0; i < pending.size(); i++) {
            for (String dependency : dependencies.of(pending.get(i))) {
                if (modules.add(dependency)) {
                    pending.add(dependency);
                }
            }
        }
        return modules;
    }

    /** The dependencies that the module registry of the platform knows; none for a module it does not know. */
    private static List<String> platformDependencies(String moduleId) {
        JahiaTemplatesPackage module = ServicesRegistry.getInstance().getJahiaTemplateManagerService()
                .getTemplatePackageRegistry().lookupById(moduleId);
        if (module == null) {
            return List.of();
        }
        List<String> ids = new ArrayList<>();
        for (JahiaTemplatesPackage dependency : module.getDependencies()) {
            ids.add(dependency.getId());
        }
        return ids;
    }
}
