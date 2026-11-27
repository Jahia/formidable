package org.jahia.modules.formidable.quarantine.ops;

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
import java.util.Map;

/**
 * Creates the site role {@value Managers#ROLE} at activation, as the engine creates {@code fmdb-results-reader}: a
 * role of the group {@code site-role}, granted on a site node ({@code jnt:virtualsite}), that grants the permission
 * {@value Managers#PERMISSION}, which the module declares in its {@code permissions.xml}. An administrator grants the
 * role to a user or a group on the site, and publishes the site: the GraphQL API and the operations check the
 * permission on the live site node, so a grant and a revocation both apply once the site is published. The role gives
 * no privileged access: its holder does not join the privileged
 * group of the site, so it reads no draft of the site.
 *
 * <p>A role that exists keeps its other permissions, and gets the permission of the module when it lacks it. A role
 * whose group is not {@code site-role}, the role of an earlier version granted on a form, becomes a site role, and
 * loses its privileged access then, once. A site role is otherwise left as it is, its node types and its access
 * included: the module never sets privileged access, and never undoes what an administrator set.</p>
 */
@Component(immediate = true)
public class ManagerRoleInitializer {

    private static final Logger log = LoggerFactory.getLogger(ManagerRoleInitializer.class);

    static final String ROLE_PATH = "/roles/" + Managers.ROLE;
    static final String PERMISSION_NAMES = "j:permissionNames";
    static final String ROLE_GROUP = "j:roleGroup";
    static final String NODE_TYPES = "j:nodeTypes";
    static final String PRIVILEGED_ACCESS = "j:privilegedAccess";
    static final String SITE_ROLE = "site-role";
    static final String SITE_TYPE = "jnt:virtualsite";
    /** The title of the role in each language of the labels of the module. */
    static final Map<Locale, String> TITLES = Map.of(
            Locale.ENGLISH, "Quarantine manager",
            Locale.FRENCH, "Gestionnaire de la quarantaine",
            Locale.GERMAN, "Quarantäne-Verwalter",
            Locale.forLanguageTag("es"), "Gestor de la cuarentena");

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
            List<String> names = strings(role, PERMISSION_NAMES);
            boolean lacksPermission = !names.contains(Managers.PERMISSION);
            boolean siteRole = isSiteRole(role);
            if (lacksPermission || !siteRole) {
                session.checkout(role);
                if (lacksPermission) {
                    names.add(Managers.PERMISSION);
                    role.setProperty(PERMISSION_NAMES, names.toArray(String[]::new));
                }
                if (!siteRole) {
                    makeSiteRole(role);
                }
                session.save();
                log.info("Made {} a site role with the permission {}", Managers.ROLE, Managers.PERMISSION);
            }
            return;
        }
        JCRNodeWrapper roles = session.getNode("/roles");
        session.checkout(roles);
        JCRNodeWrapper role = roles.addNode(Managers.ROLE, "jnt:role");
        makeSiteRole(role);
        role.setProperty(PERMISSION_NAMES, new String[]{Managers.PERMISSION});
        role.addMixin("mix:title");
        // through the i18n accessor, as the engine does: raw j:translation_* nodes stay invisible to a locale session
        for (Map.Entry<Locale, String> title : TITLES.entrySet()) {
            role.getOrCreateI18N(title.getKey()).setProperty("jcr:title", title.getValue());
        }
        session.save();
        log.info("Created the site role {} with the permission {}", Managers.ROLE, Managers.PERMISSION);
    }

    /** The group decides: an administrator may change the node types or the access of a site role, and keeps them. */
    private static boolean isSiteRole(JCRNodeWrapper role) throws RepositoryException {
        return role.hasProperty(ROLE_GROUP) && SITE_ROLE.equals(role.getProperty(ROLE_GROUP).getString());
    }

    /** A site role with no privileged access: written when the role is created, or converted once. */
    private static void makeSiteRole(JCRNodeWrapper role) throws RepositoryException {
        role.setProperty(ROLE_GROUP, SITE_ROLE);
        role.setProperty(NODE_TYPES, new String[]{SITE_TYPE});
        role.setProperty(PRIVILEGED_ACCESS, false);
    }

    private static List<String> strings(JCRNodeWrapper role, String property) throws RepositoryException {
        List<String> names = new ArrayList<>();
        if (role.hasProperty(property)) {
            for (Value value : role.getProperty(property).getValues()) {
                names.add(value.getString());
            }
        }
        return names;
    }
}
