package org.jahia.test.modules.formidable.samples.actions.form;

import org.jahia.modules.formidable.engine.api.ContextualFormAction;
import org.jahia.modules.formidable.engine.api.FormAction;
import org.jahia.modules.formidable.engine.api.FormActionContext;
import org.jahia.modules.formidable.engine.api.FormActionResult;
import org.jahia.modules.formidable.engine.api.SensitiveFields;
import org.jahia.services.content.JCRNodeWrapper;
import org.osgi.service.component.annotations.Component;

/**
 * A contextual action that opts in to the sensitive values of the form, the shape a sign-up or sign-in action copies
 * (docs/extension/how-to-create-form-action.md, "Step 6c: Receive sensitive values"): {@link #sensitiveFieldsConsumed}
 * returns {@link SensitiveFields#all()}, and the values arrive in {@link FormActionContext#sensitiveParameters()}.
 *
 * <p>It proves what it received without holding on to a value: two response headers, the number of sensitive fields
 * and whether the field named {@code password} held a non-blank value. Nothing is stored, logged or returned. A
 * test-only observable: a real action never tells the browser anything about a secret.</p>
 */
@Component(service = FormAction.class)
public class SensitiveConsumerFormAction implements ContextualFormAction {

    public static final String NODE_TYPE = "fmdbsample:sensitiveConsumerAction";
    static final String SENSITIVE_FIELDS_HEADER = "X-Fmdb-Sample-Sensitive-Fields";
    static final String PASSWORD_RECEIVED_HEADER = "X-Fmdb-Sample-Password-Received";
    static final String PASSWORD_FIELD = "password";

    @Override
    public String getNodeType() {
        return NODE_TYPE;
    }

    @Override
    public SensitiveFields sensitiveFieldsConsumed(JCRNodeWrapper actionNode) {
        return SensitiveFields.all();
    }

    @Override
    public FormActionResult execute(FormActionContext context) {
        // sensitiveParameter is the first non-blank value: null for an empty password
        boolean passwordReceived = context.sensitiveParameter(PASSWORD_FIELD) != null;
        context.response().setHeader(SENSITIVE_FIELDS_HEADER, String.valueOf(context.sensitiveParameters().size()));
        context.response().setHeader(PASSWORD_RECEIVED_HEADER, String.valueOf(passwordReceived));
        return FormActionResult.none();
    }
}
