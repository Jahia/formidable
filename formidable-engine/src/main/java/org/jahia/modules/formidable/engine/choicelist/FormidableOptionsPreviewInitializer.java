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
 * Also chained alone on fmdb:optionsNodeType (fmdbmix:contentOptions) for the
 * content-mode preview: a {@code rootNode} + {@code nodeType} context pair — with an
 * optional {@code workspace} — previews the contents that configuration resolves to,
 * the live workspace being resolved through a guest session so the editor sees exactly
 * what a visitor will get.
 *
 * A failing source propagates as an error (surfaced by the GraphQL call), never as an
 * empty preview that would look like a source without options.
 */
@Component(service = ModuleChoiceListInitializer.class)
public class FormidableOptionsPreviewInitializer implements ModuleChoiceListInitializer {

    private static final String KEY = "formidableOptionsPreview";
    static final String SOURCE_KEY_CONTEXT = "sourceKey";
    static final String ROOT_NODE_CONTEXT = "rootNode";
    static final String NODE_TYPE_CONTEXT = "nodeType";
    static final String WORKSPACE_CONTEXT = "workspace";
    /** Marker value of the single preview entry returned when the query cap is exceeded. */
    public static final String CAP_EXCEEDED_MARKER = "__fmdbCapExceeded__";
    private static final Logger log = LoggerFactory.getLogger(FormidableOptionsPreviewInitializer.class);

    private FormidableOptionsSourceService optionsSourceService;

    @Reference
    public void setOptionsSourceService(FormidableOptionsSourceService service) {
        this.optionsSourceService = service;
    }

    @Override
    public List<ChoiceListValue> getChoiceListValues(ExtendedPropertyDefinition epd, String param,
            List<ChoiceListValue> values, Locale locale, Map<String, Object> context) {
        String languageTag = locale != null ? locale.toLanguageTag() : "en";

        // Content-mode preview: a picked root and a content type, resolved for the
        // requested workspace (live goes through a guest session server-side).
        String rootNode = readContextValue(context, ROOT_NODE_CONTEXT);
        String nodeType = readContextValue(context, NODE_TYPE_CONTEXT);
        if (rootNode != null && !rootNode.isBlank() && nodeType != null && !nodeType.isBlank()) {
            String workspace = readContextValue(context, WORKSPACE_CONTEXT);
            try {
                return toPreview(optionsSourceService.resolveContentPreview(
                        rootNode, nodeType, workspace == null ? "default" : workspace, languageTag));
            } catch (org.jahia.modules.formidable.engine.options.OptionsQueryCapExceededException e) {
                // The cap is contributor-actionable: surfaced as a typed marker (value)
                // carrying the limit (label), not as an opaque GraphQL error.
                return List.of(new ChoiceListValue(String.valueOf(e.getLimit()), CAP_EXCEEDED_MARKER));
            } catch (javax.jcr.RepositoryException e) {
                throw new IllegalStateException("Content options preview failed: " + e.getMessage(), e);
            }
        }

        String sourceKey = readContextValue(context, SOURCE_KEY_CONTEXT);
        if (sourceKey == null || sourceKey.isBlank()) {
            return values;
        }

        return toPreview(optionsSourceService.resolve(sourceKey, languageTag));
    }

    private static List<ChoiceListValue> toPreview(String[] options) {
        List<ChoiceListValue> preview = new ArrayList<>(options.length);
        for (String option : options) {
            try {
                JSONObject parsed = new JSONObject(option);
                preview.add(new ChoiceListValue(parsed.optString("label", ""), parsed.optString("value", "")));
            } catch (Exception e) {
                log.debug("[FormidableOptionsPreviewInitializer] Skipping unparsable option", e);
            }
        }

        return preview;
    }

    // The GraphQL context entries reach initializers as List<String> values
    // (EditorFormServiceImpl copies ContextEntryInput.getValue() verbatim).
    private static String readContextValue(Map<String, Object> context, String key) {
        Object requested = context != null ? context.get(key) : null;
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
