package org.jahia.test.modules.formidable.samples.integrity;

import org.jahia.modules.contentintegrity.api.ContentIntegrityCheck;
import org.jahia.modules.contentintegrity.api.ContentIntegrityErrorList;
import org.jahia.services.content.JCRNodeWrapper;
import org.osgi.service.component.annotations.Component;

import static org.jahia.modules.formidable.engine.api.FormidableNodeTypes.FORM_SUBMISSION_NODE_TYPE;
import static org.jahia.modules.formidable.engine.api.FormidableNodeTypes.SUBMISSION_DATA_NODE_TYPE;
import static org.jahia.modules.formidable.engine.api.FormidableProperties.DATA_NODE;

@Component(
        service = ContentIntegrityCheck.class,
        immediate = true,
        property = {
                ContentIntegrityCheck.ExecutionCondition.APPLY_ON_NT + "=" + FORM_SUBMISSION_NODE_TYPE,
                ContentIntegrityCheck.ExecutionCondition.APPLY_ON_SUBTREES + "=/sites"
        }
)
public class FormSubmissionStructureIntegrityCheck extends AbstractFormidableIntegrityCheck {

    @Override
    public ContentIntegrityErrorList checkIntegrityBeforeChildren(JCRNodeWrapper node) {
        return requireChildNodeType(node, DATA_NODE, SUBMISSION_DATA_NODE_TYPE);
    }
}
