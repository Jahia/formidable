package org.jahia.test.modules.formidable.samples.integrity;

import org.jahia.modules.contentintegrity.api.ContentIntegrityCheck;
import org.jahia.modules.contentintegrity.api.ContentIntegrityErrorList;
import org.jahia.services.content.JCRNodeWrapper;
import org.osgi.service.component.annotations.Component;

import static org.jahia.modules.formidable.engine.api.FormidableMixins.FORM_ROOT_MIXIN;
import static org.jahia.modules.formidable.engine.api.FormidableNodeTypes.FORM_RESULTS_NODE_TYPE;
import static org.jahia.modules.formidable.engine.api.FormidableNodeTypes.SUBMISSIONS_NODE_TYPE;
import static org.jahia.modules.formidable.engine.api.FormidableProperties.PARENT_FORM_PROPERTY;
import static org.jahia.modules.formidable.engine.api.FormidableProperties.SUBMISSIONS_NODE;

@Component(
        service = ContentIntegrityCheck.class,
        immediate = true,
        property = {
                ContentIntegrityCheck.ExecutionCondition.APPLY_ON_NT + "=" + FORM_RESULTS_NODE_TYPE,
                ContentIntegrityCheck.ExecutionCondition.APPLY_ON_SUBTREES + "=/sites"
        }
)
public class FormResultsParentIntegrityCheck extends AbstractFormidableIntegrityCheck {

    @Override
    public ContentIntegrityErrorList checkIntegrityBeforeChildren(JCRNodeWrapper node) {
        return mergeErrorLists(
                requireReferencedNodeType(node, PARENT_FORM_PROPERTY, FORM_ROOT_MIXIN),
                requireChildNodeType(node, SUBMISSIONS_NODE, SUBMISSIONS_NODE_TYPE)
        );
    }
}
