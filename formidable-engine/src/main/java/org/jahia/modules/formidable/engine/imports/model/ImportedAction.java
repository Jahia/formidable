package org.jahia.modules.formidable.engine.imports.model;

import java.util.Map;

/**
 * An action the import creates under the {@code actions} of a form.
 *
 * @param nodeType {@code fmdb:save2jcrAction}, {@code fmdb:emailNotificationAction}…
 * @param properties single-valued properties by name: {@code to}, {@code from}…
 * @param i18nProperties i18n properties by name, then by language: {@code subject}…
 */
public record ImportedAction(String name, String nodeType, Map<String, String> properties,
                             Map<String, Map<String, String>> i18nProperties) {
}
