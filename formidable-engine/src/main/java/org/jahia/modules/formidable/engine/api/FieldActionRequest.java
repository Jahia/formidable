package org.jahia.modules.formidable.engine.api;

import java.util.Locale;

/**
 * What a {@link FieldAction} judges: one candidate value and where it comes from. One shape for the pre-check the
 * browser asks while the visitor types and for the submission the pipeline judges.
 *
 * @param formId    the form's UUID, its identity everywhere in Formidable
 * @param fieldName the field's node name — the key of the submitted parameters, never a node id
 * @param value     the candidate value, as the browser sent it: a string, trimmed by nobody. <strong>One</strong>
 *                  value: a field the visitor answered several times over — a group of checkboxes, a list with
 *                  several selections — is judged one value at a time, the action run once per value. An action
 *                  never sees its siblings, and no action can therefore judge the set as a whole
 * @param locale    the visitor's locale, for a check whose answer depends on it (a postal address, a date)
 */
public record FieldActionRequest(String formId, String fieldName, String value, Locale locale) {
}
