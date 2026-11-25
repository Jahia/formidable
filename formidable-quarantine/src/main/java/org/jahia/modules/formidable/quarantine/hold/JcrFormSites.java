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
import java.util.Optional;
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

    /** The module registry of the platform. */
    @FunctionalInterface
    interface ModuleRegistry {

        /** @return the identifiers of the modules the module depends on, or empty when no such module is deployed */
        Optional<List<String>> dependenciesOf(String moduleId);
    }

    private final ModuleRegistry registry;

    public JcrFormSites() {
        this(JcrFormSites::deployedDependencies);
    }

    JcrFormSites(ModuleRegistry registry) {
        this.registry = registry;
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
     * @throws RepositoryException when the form is under no site, or the modules of its site cannot be read, or the
     *                             answer depends on a module the platform does not know; a site always lists its
     *                             template set, so an empty list is a failed read too
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
        return pending.contains(MODULE_ID) || reachedThroughDependencies(pending);
    }

    /**
     * Walks the dependencies of every module the registry knows. The module found on the way owns the form, whatever
     * the modules the registry does not know. Without it, a module the registry does not know leaves the answer open:
     * that module could depend on this one, so the read fails.
     */
    private boolean reachedThroughDependencies(List<String> pending) throws RepositoryException {
        Set<String> seen = new LinkedHashSet<>(pending);
        String unknown = null;
        for (int i = 0; i < pending.size(); i++) {
            Optional<List<String>> dependencies = registry.dependenciesOf(pending.get(i));
            if (dependencies.isEmpty()) {
                unknown = pending.get(i);
                continue;
            }
            if (dependencies.get().contains(MODULE_ID)) {
                return true;
            }
            for (String dependency : dependencies.get()) {
                if (seen.add(dependency)) {
                    pending.add(dependency);
                }
            }
        }
        if (unknown != null) {
            throw new RepositoryException("The platform does not know the module " + unknown + " of the site");
        }
        return false;
    }

    /** The dependencies of a deployed module, as the registry of the platform resolves them. */
    private static Optional<List<String>> deployedDependencies(String moduleId) {
        JahiaTemplatesPackage module = ServicesRegistry.getInstance().getJahiaTemplateManagerService()
                .getTemplatePackageRegistry().lookupById(moduleId);
        if (module == null) {
            return Optional.empty();
        }
        List<String> ids = new ArrayList<>();
        for (JahiaTemplatesPackage dependency : module.getDependencies()) {
            ids.add(dependency.getId());
        }
        return Optional.of(ids);
    }
}
