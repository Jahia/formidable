package org.jahia.modules.formidable.engine.choicelist;

import org.jahia.modules.formidable.engine.options.FormidableOptionsSourceService;
import org.jahia.services.content.nodetypes.ExtendedPropertyDefinition;
import org.jahia.services.content.nodetypes.initializers.ChoiceListValue;
import org.jahia.services.content.nodetypes.initializers.ModuleChoiceListInitializer;
import org.json.JSONObject;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Editor-side preview of the options a declared source resolves to.
 *
 * Chained after {@link FormidableOptionsSourcesInitializer} on fmdb:optionsSourceKey
 * (choicelist[formidableOptionsSources,formidableOptionsPreview]). During a normal
 * form build no {@code sourceKey} context entry exists and the initializer passes the
 * incoming values through untouched, so the source picker is unaffected. The
 * SourcedOptions selector then calls jcontent's {@code forms.fieldConstraints} with a
 * {@code sourceKey} context entry carrying the picked source — possibly not saved
 * yet — and this initializer replaces the chain result with the options that source
 * resolves to, in the language received from the editor.
 *
 * A failing source propagates as an error (surfaced by the GraphQL call), never as an
 * empty preview that would look like a source without options.
 */
@Component(service = ModuleChoiceListInitializer.class)
public class FormidableOptionsPreviewInitializer implements ModuleChoiceListInitializer {

    private static final String KEY = "formidableOptionsPreview";
    static final String SOURCE_KEY_CONTEXT = "sourceKey";
    private static final Logger log = LoggerFactory.getLogger(FormidableOptionsPreviewInitializer.class);

    private FormidableOptionsSourceService optionsSourceService;

    @Reference
    public void setOptionsSourceService(FormidableOptionsSourceService service) {
        this.optionsSourceService = service;
    }

    @Override
    public List<ChoiceListValue> getChoiceListValues(ExtendedPropertyDefinition epd, String param,
            List<ChoiceListValue> values, Locale locale, Map<String, Object> context) {
        String sourceKey = readSourceKey(context);
        if (sourceKey == null || sourceKey.isBlank()) {
            return values;
        }

        String[] options = optionsSourceService.resolve(sourceKey, locale != null ? locale.toLanguageTag() : "en");
        List<ChoiceListValue> preview = new ArrayList<>(options.length);
        for (String option : options) {
            try {
                JSONObject parsed = new JSONObject(option);
                preview.add(new ChoiceListValue(parsed.optString("label", ""), parsed.optString("value", "")));
            } catch (Exception e) {
                log.debug("[FormidableOptionsPreviewInitializer] Skipping unparsable option of source '{}'", sourceKey, e);
            }
        }

        return preview;
    }

    // The GraphQL context entries reach initializers as List<String> values
    // (EditorFormServiceImpl copies ContextEntryInput.getValue() verbatim).
    private static String readSourceKey(Map<String, Object> context) {
        Object requested = context != null ? context.get(SOURCE_KEY_CONTEXT) : null;
        if (requested instanceof String value) {
            return value;
        }
        if (requested instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof String value) {
            return value;
        }

        return null;
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
