package org.jahia.modules.formidable.formsimport.model;

import java.util.List;
import java.util.Map;

/**
 * An action the import creates under the {@code actions} of a form.
 *
 * @param nodeType {@code fmdb:save2jcrAction}, {@code fmdb:emailNotificationAction}…
 * @param mixins the mixins added before the properties, whose definitions some of them need: the target mixin of a
 *               redirect ({@code fmdbmix:redirectToPage}, {@code fmdbmix:redirectToUrl})
 * @param properties single-valued properties by name: {@code to}, {@code from}…
 * @param i18nProperties i18n properties by name, then by language: {@code subject}…
 */
public record ImportedAction(String name, String nodeType, List<String> mixins, Map<String, String> properties,
                             Map<String, Map<String, String>> i18nProperties) {

    /** An action whose properties are all defined on its type. */
    public ImportedAction(String name, String nodeType, Map<String, String> properties, Map<String, Map<String, String>> i18nProperties) {
        this(name, nodeType, List.of(), properties, i18nProperties);
    }
}
