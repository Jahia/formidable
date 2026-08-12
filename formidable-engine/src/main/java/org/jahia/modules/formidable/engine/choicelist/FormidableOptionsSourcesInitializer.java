package org.jahia.modules.formidable.engine.choicelist;

import org.jahia.modules.formidable.engine.config.FormidableConfigService;
import org.jahia.services.content.JCRSessionFactory;
import org.jahia.services.content.decorator.JCRUserNode;
import org.jahia.services.content.nodetypes.ExtendedPropertyDefinition;
import org.jahia.services.content.nodetypes.initializers.ChoiceListValue;
import org.jahia.services.content.nodetypes.initializers.ModuleChoiceListInitializer;
import org.jahia.services.preferences.user.UserPreferencesHelper;
import org.jahia.services.usermanager.JahiaUser;
import org.jahia.services.usermanager.JahiaUserManagerService;
import org.jahia.utils.i18n.Messages;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Populates the fmdb:optionsSourceKey choice list for sourced choice fields from the
 * options sources declared in org.jahia.modules.formidable.cfg.
 *
 * Only the curated allowlist is exposed — never the raw platform-wide initializer list,
 * most of which is context-dependent and meaningless as a form options source.
 *
 * Each entry exposes the source id as the stored JCR value and the admin-defined label
 * as the display name shown in the Content Editor. A label of the form
 * {@code <module>:<resource.key>} is resolved against that module's Java resource
 * bundle in the current user's UI language (falling back to the locale the editor
 * hands to initializers — the edited content language). The UI language is used on
 * purpose: it is what resolves the neighboring editor labels (displayValueKey is
 * translated client-side with the UI language), so all labels of a fieldset follow
 * the same language.
 *
 * Registered as: choicelist[formidableOptionsSources] in the CND.
 */
@Component(service = ModuleChoiceListInitializer.class)
public class FormidableOptionsSourcesInitializer implements ModuleChoiceListInitializer {

    private static final String KEY = "formidableOptionsSources";
    private static final Logger log = LoggerFactory.getLogger(FormidableOptionsSourcesInitializer.class);

    // '<module>:<resource.key>' — intentionally strict so a literal label containing a
    // colon (e.g. 'Type: TV') is never mistaken for a resource key.
    private static final Pattern LABEL_KEY_PATTERN = Pattern.compile("^([A-Za-z0-9_-]+):([A-Za-z0-9_.-]+)$");

    private FormidableConfigService configService;

    @Reference
    public void setConfigService(FormidableConfigService service) {
        this.configService = service;
    }

    @Override
    public List<ChoiceListValue> getChoiceListValues(ExtendedPropertyDefinition epd, String param,
            List<ChoiceListValue> values, Locale locale, Map<String, Object> context) {
        Collection<FormidableConfigService.OptionsSource> sources = configService.getOptionsSources();
        if (sources.isEmpty()) {
            log.warn("[FormidableOptionsSourcesInitializer] No options sources are configured. "
                    + "The choicelist '{}' for property '{}' will be empty.", KEY, epd.getName());
        }
        return sources.stream()
                .map(source -> new ChoiceListValue(resolveLabel(source.label(), locale), source.id()))
                .toList();
    }

    private static String resolveLabel(String label, Locale contentLocale) {
        var matcher = LABEL_KEY_PATTERN.matcher(label);
        if (!matcher.matches()) {
            return label;
        }
        try {
            return Messages.get("resources." + matcher.group(1), matcher.group(2),
                    uiLocale(contentLocale), label);
        } catch (Exception e) {
            log.debug("[FormidableOptionsSourcesInitializer] Could not resolve label key '{}'", label, e);
            return label;
        }
    }

    private static Locale uiLocale(Locale fallback) {
        Locale safeFallback = fallback != null ? fallback : Locale.ENGLISH;
        try {
            JahiaUser user = JCRSessionFactory.getInstance().getCurrentUser();
            if (user != null) {
                JCRUserNode userNode = JahiaUserManagerService.getInstance().lookupUserByPath(user.getLocalPath());
                if (userNode != null) {
                    return UserPreferencesHelper.getPreferredLocale(userNode, safeFallback);
                }
            }
        } catch (Exception e) {
            log.debug("[FormidableOptionsSourcesInitializer] Could not resolve the current user's UI locale", e);
        }

        return safeFallback;
    }

    @Override
    public void setKey(String key) {
        // Jahia injects the service key on registration; this initializer uses a fixed key.
    }

    @Override
    public String getKey() {
        return KEY;
    }
}
