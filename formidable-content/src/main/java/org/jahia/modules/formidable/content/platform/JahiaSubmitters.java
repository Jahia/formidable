package org.jahia.modules.formidable.content.platform;

import org.jahia.services.usermanager.JahiaGroupManagerService;
import org.jahia.services.usermanager.JahiaUser;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * {@link Submitters} on Jahia's group service: membership by the user's path (the platform's cached lookup, nested
 * groups included) and the organization from the user's own properties.
 *
 * <p>Threading: a stateless singleton; the platform's lookups are thread-safe.</p>
 */
@Component(service = Submitters.class, immediate = true)
public class JahiaSubmitters implements Submitters {

    private static final Logger log = LoggerFactory.getLogger(JahiaSubmitters.class);
    private static final String ORGANIZATION = "j:organization";

    private JahiaGroupManagerService groups;

    /**
     * @param groups the platform's group service
     */
    @Reference
    public void setGroups(JahiaGroupManagerService groups) {
        this.groups = groups;
    }

    @Override
    public List<String> groupPaths(JahiaUser user) {
        if (user == null || user.getLocalPath() == null) {
            return List.of();
        }
        List<String> paths = groups.getMembershipByPath(user.getLocalPath());
        return paths == null ? List.of() : List.copyOf(paths);
    }

    @Override
    public String organization(JahiaUser user) {
        try {
            String value = user == null ? null : user.getProperty(ORGANIZATION);
            return value == null || value.isBlank() ? null : value.trim();
        } catch (RuntimeException e) {
            log.warn("[formidable-content] The organization of a user could not be read.", e);
            return null;
        }
    }
}
