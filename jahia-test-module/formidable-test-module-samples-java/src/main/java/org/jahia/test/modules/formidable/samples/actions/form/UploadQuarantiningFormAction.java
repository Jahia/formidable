package org.jahia.test.modules.formidable.samples.actions.form;

import org.jahia.modules.formidable.engine.api.ContextualFormAction;
import org.jahia.modules.formidable.engine.api.FormAction;
import org.jahia.modules.formidable.engine.api.FormActionContext;
import org.jahia.modules.formidable.engine.api.FormActionResult;
import org.jahia.services.content.JCRNodeWrapper;
import org.osgi.service.component.annotations.Component;

/**
 * A contextual action that declares it quarantines the uploads of the form: {@link #quarantinesUploads} returns
 * {@code true}, so the pipeline hands the uploads to it and to no action that did not opt in
 * (docs/architecture/form-submission-flow.md, "The split of the uploads").
 *
 * <p>A test double, and not a quarantine: it keeps no byte. It reports the number of files it was handed in a response
 * header, for the suite to read next to the other actions of the form. A real quarantining action holds the bytes
 * where no reader, no parser and no other action reaches them.</p>
 */
@Component(service = FormAction.class)
public class UploadQuarantiningFormAction implements ContextualFormAction {

    public static final String NODE_TYPE = "fmdbsample:uploadQuarantiningAction";
    static final String FILES_HEADER = "X-Fmdb-Sample-Quarantining-Files";

    @Override
    public String getNodeType() {
        return NODE_TYPE;
    }

    @Override
    public boolean quarantinesUploads(JCRNodeWrapper actionNode) {
        return true;
    }

    @Override
    public FormActionResult execute(FormActionContext context) {
        context.response().setHeader(FILES_HEADER, String.valueOf(context.files().size()));
        return FormActionResult.none();
    }
}
