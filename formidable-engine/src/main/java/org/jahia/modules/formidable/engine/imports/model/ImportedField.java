package org.jahia.modules.formidable.engine.imports.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A field the import creates, as the writer will add it under the form: its node type, its system name,
 * its titles, and the properties the source could fill. Source-agnostic: what the field was in the
 * source system is kept in {@link #sourceId()}, {@link #sourceName()} and {@link #sourceType()}, which
 * the markers of docs/architecture/forms-import.md record on the node.
 *
 * @param name the system name, generated from the label
 * @param nodeType the Formidable type, {@code fmdb:inputText}
 * @param properties single-valued properties by name: {@code rows}, {@code minLength}, {@code multiple}…
 * @param i18nProperties i18n properties by name, then by language: {@code placeholder}, {@code helpText}…
 * @param options the manual options per language, each a JSON {@code {"value","label","selected"}}
 * @param optionsSourceKey the key of an options source, for a choice field fed by one, else null
 * @param mixins the mixins of other modules the field takes, for the properties of {@code properties} they declare
 * @param sourceId the identity of the field in the source system, its {@code jcr:uuid} for Forms
 * @param sourceName the node name of the field in the source system, {@code text-input_0_1}
 * @param sourceType the type of the field in the source system, {@code fcnt:inputDefinition}
 * @param report what the report says of this field: what was carried over worth knowing, what was not
 */
public record ImportedField(String name, String nodeType, Map<String, String> titles, boolean required,
                            Map<String, String> properties, Map<String, Map<String, String>> i18nProperties,
                            Map<String, List<String>> options, String optionsSourceKey, List<String> mixins,
                            String sourceId, String sourceName, String sourceType, List<ImportNote> report)
        implements ImportedElement {

    public static final String REQUIRED_PROPERTY = "required";
    /** A setting of the source the field type cannot hold: {@code setting}, {@code type}. */
    public static final String NOTE_SETTING = "field.setting";
    private static final String SETTING = "setting";
    private static final String TYPE = "type";

    /**
     * @param accepted the properties the node type declares: a property outside it is reported, not kept,
     *                 so that the writer never sets what the type cannot take
     */
    public static Builder builder(String name, String nodeType, Set<String> accepted) {
        return new Builder(name, nodeType, accepted);
    }

    /** Mutable while the converter fills it, then frozen into the record. */
    public static final class Builder {
        private final String name;
        private final String nodeType;
        private final Set<String> accepted;
        private Map<String, String> titles = Map.of();
        private boolean required;
        private final Map<String, String> properties = new LinkedHashMap<>();
        private final Map<String, Map<String, String>> i18nProperties = new LinkedHashMap<>();
        private Map<String, List<String>> options = Map.of();
        private String optionsSourceKey;
        private final Set<String> mixins = new LinkedHashSet<>();
        private String sourceId;
        private String sourceName;
        private String sourceType;
        private final List<ImportNote> report = new ArrayList<>();

        private Builder(String name, String nodeType, Set<String> accepted) {
            this.name = name;
            this.nodeType = nodeType;
            this.accepted = accepted;
        }

        public boolean accepts(String property) {
            return accepted.contains(property);
        }

        public Builder titles(Map<String, String> value) {
            titles = value;
            return this;
        }

        /** Marks the field required, or reports it when the type has no such setting. */
        public Builder required(boolean value) {
            if (value && !accepts(REQUIRED_PROPERTY)) {
                report(ImportNote.action(NOTE_SETTING, SETTING, REQUIRED_PROPERTY, TYPE, nodeType));
                return this;
            }
            required = value;
            return this;
        }

        /** Sets a property, or reports it when the type does not declare it. A blank value is skipped. */
        public Builder property(String key, String value) {
            if (value == null || value.isBlank()) {
                return this;
            }
            if (!accepts(key)) {
                return report(ImportNote.action(NOTE_SETTING, SETTING, key, TYPE, nodeType));
            }
            properties.put(key, value);
            return this;
        }

        /**
         * Sets a property a mixin of another module declares, which the type itself does not: the mixin is added
         * to the field before the property is written. The caller has checked that the mixin is registered and
         * reaches the type.
         */
        public Builder extension(String mixin, String key, String value) {
            if (value != null && !value.isBlank()) {
                mixins.add(mixin);
                properties.put(key, value);
            }
            return this;
        }

        /** Sets an i18n property, or reports it when the type does not declare it. Blank languages are skipped. */
        public Builder i18nProperty(String key, Map<String, String> byLanguage) {
            Map<String, String> kept = new LinkedHashMap<>();
            if (byLanguage != null) {
                byLanguage.forEach((language, value) -> {
                    if (value != null && !value.isBlank()) {
                        kept.put(language, value);
                    }
                });
            }
            if (kept.isEmpty()) {
                return this;
            }
            if (!accepts(key)) {
                return report(ImportNote.action(NOTE_SETTING, SETTING, key, TYPE, nodeType));
            }
            i18nProperties.put(key, kept);
            return this;
        }

        public Builder options(Map<String, List<String>> value) {
            options = value;
            return this;
        }

        public Builder optionsSourceKey(String value) {
            optionsSourceKey = value;
            return this;
        }

        public Builder source(String id, String nodeName, String type) {
            sourceId = id;
            sourceName = nodeName;
            sourceType = type;
            return this;
        }

        public Builder report(ImportNote note) {
            report.add(note);
            return this;
        }

        public ImportedField build() {
            return new ImportedField(name, nodeType, titles, required, Map.copyOf(properties),
                    Map.copyOf(i18nProperties), options, optionsSourceKey, List.copyOf(mixins), sourceId, sourceName,
                    sourceType, List.copyOf(report));
        }
    }
}
