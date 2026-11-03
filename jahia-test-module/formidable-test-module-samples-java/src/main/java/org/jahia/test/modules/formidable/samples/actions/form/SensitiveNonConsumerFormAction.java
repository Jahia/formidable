package org.jahia.test.modules.formidable.samples.actions.form;

import org.jahia.modules.formidable.engine.api.ContextualFormAction;
import org.jahia.modules.formidable.engine.api.FormAction;
import org.jahia.modules.formidable.engine.api.FormActionContext;
import org.jahia.modules.formidable.engine.api.FormActionResult;
import org.osgi.service.component.annotations.Component;

import java.util.TreeSet;

/**
 * A contextual action that does not opt in to sensitive values: it keeps the default
 * {@link #sensitiveFieldsConsumed}, which consumes none. It reports what it was handed as two response headers, the
 * number of sensitive fields (always 0) and the names of the public fields, sorted and comma-separated: names only,
 * never a value. A test-only observable, for the suite to put it on the form of a password next to a consumer.
 */
@Component(service = FormAction.class)
public class SensitiveNonConsumerFormAction implements ContextualFormAction {

    public static final String NODE_TYPE = "fmdbsample:sensitiveNonConsumerAction";
    static final String SENSITIVE_FIELDS_HEADER = "X-Fmdb-Sample-Non-Consumer-Sensitive-Fields";
    static final String FIELDS_HEADER = "X-Fmdb-Sample-Non-Consumer-Fields";

    @Override
    public String getNodeType() {
        return NODE_TYPE;
    }

    @Override
    public FormActionResult execute(FormActionContext context) {
        context.response().setHeader(SENSITIVE_FIELDS_HEADER, String.valueOf(context.sensitiveParameters().size()));
        context.response().setHeader(FIELDS_HEADER, String.join(",", new TreeSet<>(context.parameters().keySet())));
        return FormActionResult.none();
    }
}
