package org.jahia.modules.formidable.engine.permissions;

import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRTemplate;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;

/**
 * Creates the fmdb-results-reader role at module activation if it does not already exist.
 * The role is a child of the built-in reader role so it inherits jcr:read permissions.
 * Titles are set in English and French for the jContent Permissions UI.
 */
@Component(immediate = true)
public class FormResultsRoleInitializer {

    private static final Logger log = LoggerFactory.getLogger(FormResultsRoleInitializer.class);
    static final String ROLE_NAME = "fmdb-results-reader";
    private static final String ROLE_PATH = "/roles/reader/" + ROLE_NAME;

    @Activate
    public void activate() {
        try {
            JCRTemplate.getInstance().doExecuteWithSystemSessionAsUser(null, null, null, session -> {
                if (session.nodeExists(ROLE_PATH)) {
                    log.debug("[FormResultsRoleInitializer] Role '{}' already exists", ROLE_NAME);
                    return null;
                }

                JCRNodeWrapper readerRole = session.getNode("/roles/reader");
                session.checkout(readerRole);
                JCRNodeWrapper role = readerRole.addNode(ROLE_NAME, "jnt:role");
                role.setProperty("j:roleGroup", "live-role");
                role.setProperty("j:privilegedAccess", false);

                addI18nTitles(role);

                session.save();
                log.info("[FormResultsRoleInitializer] Created role '{}' under /roles/reader", ROLE_NAME);
                return null;
            });
        } catch (RepositoryException e) {
            log.error("[FormResultsRoleInitializer] Failed to create role '{}': {}", ROLE_NAME, e.getMessage(), e);
        }
    }

    private static void addI18nTitles(JCRNodeWrapper role) throws RepositoryException {
        role.addMixin("mix:title");

        JCRNodeWrapper en = role.addNode("j:translation_en", "jnt:translation");
        en.setProperty("jcr:language", "en");
        en.setProperty("jcr:title", "Form Results Reader");

        JCRNodeWrapper fr = role.addNode("j:translation_fr", "jnt:translation");
        fr.setProperty("jcr:language", "fr");
        fr.setProperty("jcr:title", "Lecteur des résultats de formulaire");
    }
}

