package org.jahia.test.modules.formidable.quarantine.ops;

import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRSessionWrapper;
import org.jahia.services.content.JCRTemplate;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;
import javax.jcr.Value;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Creates the role {@value Managers#ROLE} at activation, as the engine creates {@code fmdb-results-reader}: a role
 * that grants the permission {@value Managers#PERMISSION}, which the module declares in its
 * {@code permissions.xml}. An administrator grants the role to a user on a form, then publishes the form: the
 * operations check the permission on the live form. A role that exists keeps its other permissions, and gets the
 * permission of the module when it lacks it.
 */
@Component(immediate = true)
public class ManagerRoleInitializer {

    private static final Logger log = LoggerFactory.getLogger(ManagerRoleInitializer.class);

    static final String ROLE_PATH = "/roles/" + Managers.ROLE;
    static final String PERMISSION_NAMES = "j:permissionNames";

    @Activate
    public void activate() {
        try {
            JCRTemplate.getInstance().doExecuteWithSystemSessionAsUser(null, null, null, session -> {
                ensureRole(session);
                return null;
            });
        } catch (RepositoryException e) {
            log.error("Could not create the role {}: {}", Managers.ROLE, e.getMessage(), e);
        }
    }

    static void ensureRole(JCRSessionWrapper session) throws RepositoryException {
        if (session.nodeExists(ROLE_PATH)) {
            JCRNodeWrapper role = session.getNode(ROLE_PATH);
            List<String> names = permissionNames(role);
            if (!names.contains(Managers.PERMISSION)) {
                names.add(Managers.PERMISSION);
                role.setProperty(PERMISSION_NAMES, names.toArray(String[]::new));
                session.save();
                log.info("Gave the permission {} to the role {}", Managers.PERMISSION, Managers.ROLE);
            }
            return;
        }
        JCRNodeWrapper roles = session.getNode("/roles");
        session.checkout(roles);
        JCRNodeWrapper role = roles.addNode(Managers.ROLE, "jnt:role");
        role.setProperty("j:roleGroup", "edit-role");
        role.setProperty("j:privilegedAccess", false);
        role.setProperty(PERMISSION_NAMES, new String[]{Managers.PERMISSION});
        role.addMixin("mix:title");
        // through the i18n accessor, as the engine does: raw j:translation_* nodes stay invisible to a locale session
        role.getOrCreateI18N(Locale.ENGLISH).setProperty("jcr:title", "Quarantine manager (test module)");
        role.getOrCreateI18N(Locale.FRENCH).setProperty("jcr:title", "Gestionnaire de la quarantaine (module de test)");
        session.save();
        log.info("Created the role {} with the permission {}", Managers.ROLE, Managers.PERMISSION);
    }

    private static List<String> permissionNames(JCRNodeWrapper role) throws RepositoryException {
        List<String> names = new ArrayList<>();
        if (role.hasProperty(PERMISSION_NAMES)) {
            for (Value value : role.getProperty(PERMISSION_NAMES).getValues()) {
                names.add(value.getString());
            }
        }
        return names;
    }
}
