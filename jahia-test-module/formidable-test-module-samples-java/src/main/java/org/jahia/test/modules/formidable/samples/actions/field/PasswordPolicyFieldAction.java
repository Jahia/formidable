package org.jahia.test.modules.formidable.samples.actions.field;

import org.jahia.modules.formidable.engine.api.FieldAction;
import org.jahia.modules.formidable.engine.api.FieldActionRequest;
import org.jahia.modules.formidable.engine.api.FieldActionResult;
import org.jahia.services.content.JCRNodeWrapper;
import org.osgi.service.component.annotations.Component;

/**
 * A field action that opts in to sensitive values ({@link #consumesSensitiveValues()}), the shape of a password
 * policy: on a sensitive field, such as {@code fmdbext:password}, the engine runs only the field actions that opted in
 * (docs/extension/how-to-create-field-action.md, "On a sensitive field"). It refuses a value without a digit.
 *
 * <p>The duty that comes with the opt-in: the value is judged here and goes nowhere else. The {@code detail} of a
 * refusal names the rule, never the value, and the class logs nothing.</p>
 */
@Component(service = FieldAction.class)
public class PasswordPolicyFieldAction implements FieldAction {

    public static final String NODE_TYPE = "fmdbsample:passwordPolicyAction";
    static final String NO_DIGIT = "no digit";

    @Override
    public String getNodeType() {
        return NODE_TYPE;
    }

    @Override
    public boolean consumesSensitiveValues() {
        return true;
    }

    @Override
    public FieldActionResult execute(JCRNodeWrapper actionNode, FieldActionRequest request) {
        String value = request.value() == null ? "" : request.value();
        return value.chars().anyMatch(Character::isDigit) ? FieldActionResult.accept() : FieldActionResult.reject(NO_DIGIT);
    }
}
