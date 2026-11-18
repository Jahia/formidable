package org.jahia.test.modules.formidable.samples.actions.form;

import org.jahia.modules.formidable.engine.api.ContextualFormAction;
import org.jahia.modules.formidable.engine.api.FormAction;
import org.jahia.modules.formidable.engine.api.FormActionContext;
import org.jahia.modules.formidable.engine.api.FormActionResult;
import org.osgi.service.component.annotations.Component;

/**
 * A contextual action that reports the number of files it was handed in a response header. A test-only observable,
 * for the suite to read which actions of a form receive its uploads.
 */
@Component(service = FormAction.class)
public class UploadObserverFormAction implements ContextualFormAction {

    public static final String NODE_TYPE = "fmdbsample:uploadObserverAction";
    static final String FILES_HEADER = "X-Fmdb-Sample-Observer-Files";

    @Override
    public String getNodeType() {
        return NODE_TYPE;
    }

    @Override
    public FormActionResult execute(FormActionContext context) {
        context.response().setHeader(FILES_HEADER, String.valueOf(context.files().size()));
        return FormActionResult.none();
    }
}
