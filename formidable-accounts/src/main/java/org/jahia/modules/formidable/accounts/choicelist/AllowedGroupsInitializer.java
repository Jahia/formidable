package org.jahia.modules.formidable.accounts.choicelist;

import org.jahia.modules.formidable.accounts.config.AccountsConfigService;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.decorator.JCRGroupNode;
import org.jahia.services.content.decorator.JCRSiteNode;
import org.jahia.services.content.nodetypes.ExtendedPropertyDefinition;
import org.jahia.services.content.nodetypes.initializers.ChoiceListValue;
import org.jahia.services.content.nodetypes.initializers.ModuleChoiceListInitializer;
import org.jahia.services.usermanager.JahiaGroupManagerService;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Feeds the Group property of the Create account action ({@code choicelist[formidableAccountsGroups]}) with the groups
 * the operator allowed in {@code allowedGroups} of {@code org.jahia.modules.formidable.accounts.cfg} that exist for
 * the action's site: a global group of that name, else the site's own. A contributor picks one; nobody types a group.
 * The stored value is the group's name, which the action resolves the same way and checks again at every sign-up
 * (allowed, global or this site's, no back-office access).
 *
 * <p>Threading: a stateless singleton; the settings are one immutable snapshot read per call.</p>
 */
@Component(service = ModuleChoiceListInitializer.class)
public class AllowedGroupsInitializer implements ModuleChoiceListInitializer {

    /** The key used in the CND: {@code choicelist[formidableAccountsGroups]}. */
    public static final String KEY = "formidableAccountsGroups";

    private static final Logger log = LoggerFactory.getLogger(AllowedGroupsInitializer.class);

    private AccountsConfigService config;
    private JahiaGroupManagerService groups;

    @Reference
    public void setConfig(AccountsConfigService config) {
        this.config = config;
    }

    @Reference
    public void setGroups(JahiaGroupManagerService groups) {
        this.groups = groups;
    }

    @Override
    public List<ChoiceListValue> getChoiceListValues(ExtendedPropertyDefinition epd, String param, List<ChoiceListValue> values,
                                                     Locale locale, Map<String, Object> context) {
        Object contextNode = context == null ? null : context.get("contextNode");
        if (!(contextNode instanceof JCRNodeWrapper node)) {
            return List.of();
        }
        List<ChoiceListValue> out = new ArrayList<>();
        try {
            JCRSiteNode site = node.getResolveSite();
            String siteKey = site == null ? null : site.getSiteKey();
            for (String name : config.settings().allowedGroups().stream().sorted().toList()) {
                JCRGroupNode group = groups.lookupGroup(null, name, node.getSession());
                if (group == null && siteKey != null) {
                    group = groups.lookupGroup(siteKey, name, node.getSession());
                }
                if (group != null) {
                    out.add(new ChoiceListValue(label(group), group.getName()));
                }
            }
        } catch (RepositoryException | RuntimeException e) {
            log.warn("[formidable-accounts] The allowed groups could not be listed.", e);
        }
        return out;
    }

    private static String label(JCRGroupNode group) {
        String title = group.getDisplayableName();
        return title == null || title.isBlank() || title.equals(group.getName()) ? group.getName() : title + " (" + group.getName() + ")";
    }

    @Override
    public void setKey(String key) {
        // fixed key
    }

    @Override
    public String getKey() {
        return KEY;
    }
}
