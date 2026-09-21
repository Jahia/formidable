package org.jahia.modules.formidable.engine.api;

import java.util.Locale;

/**
 * What a {@link FieldAction} judges: one candidate value and where it comes from. The same shape reaches
 * a Java action and, as JSON in the {@code formidable.fieldAction} request attribute, a JavaScript one.
 *
 * @param formId    the form's UUID, its identity everywhere in Formidable
 * @param fieldName the field's node name — the key of the submitted parameters, never a node id
 * @param value     the candidate value, as the browser sent it: a string, trimmed by nobody
 * @param locale    the visitor's locale, for a check whose answer depends on it (a postal address, a date)
 */
public record FieldActionRequest(String formId, String fieldName, String value, Locale locale) {
}
