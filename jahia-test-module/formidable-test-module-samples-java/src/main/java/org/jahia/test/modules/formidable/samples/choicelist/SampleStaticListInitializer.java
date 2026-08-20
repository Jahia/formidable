package org.jahia.test.modules.formidable.samples.choicelist;

import org.jahia.services.content.nodetypes.ExtendedPropertyDefinition;
import org.jahia.services.content.nodetypes.initializers.ChoiceListValue;
import org.jahia.services.content.nodetypes.initializers.ModuleChoiceListInitializer;
import org.jahia.utils.i18n.Messages;
import org.osgi.service.component.annotations.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Sample options-source initializer serving a static, localized list — the shape the
 * Formidable documentation recommends for declared sources: no Content Editor context,
 * no JCR read, the whole option universe comes from code and resource bundles, so the
 * result is safely cacheable across users and workspaces.
 *
 * The initializer parameter is the comma-separated list of values to serve. Each value
 * is labeled through the sample.staticList.&lt;value&gt; key of this module's resource
 * bundle in the requested locale, falling back to the raw value when no label is
 * declared. Example declaration:
 *   optionsSources=tv|TV screens|fmdbSampleStaticList|plasma,oled,led
 */
@Component(service = ModuleChoiceListInitializer.class)
public class SampleStaticListInitializer implements ModuleChoiceListInitializer {

    private static final String KEY = "fmdbSampleStaticList";
    private static final String BUNDLE = "resources.formidable-test-module-samples-java";
    private static final String LABEL_KEY_PREFIX = "sample.staticList.";

    @Override
    public List<ChoiceListValue> getChoiceListValues(ExtendedPropertyDefinition epd, String param,
            List<ChoiceListValue> values, Locale locale, Map<String, Object> context) {
        List<ChoiceListValue> choices = new ArrayList<>();
        for (String entry : param == null ? new String[0] : param.split(",")) {
            String value = entry.strip();
            if (!value.isEmpty()) {
                choices.add(new ChoiceListValue(label(value, locale), value));
            }
        }

        return choices;
    }

    private static String label(String value, Locale locale) {
        try {
            String label = Messages.get(BUNDLE, LABEL_KEY_PREFIX + value, locale);
            return label == null || label.isBlank() ? value : label;
        } catch (Exception e) {
            // No declared label: the raw value remains a readable fallback.
            return value;
        }
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
