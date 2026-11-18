package org.jahia.modules.formidable.engine.actions.form.storage;

import org.jahia.modules.formidable.engine.api.FormResultsStore;
import org.jahia.modules.formidable.engine.permissions.FormResultsAclSyncService;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRSessionWrapper;
import org.osgi.service.component.annotations.Component;

import javax.jcr.RepositoryException;
import java.time.Instant;

import static org.jahia.modules.formidable.engine.util.FormidableJcrConstants.ACL_NODE;
import static org.jahia.modules.formidable.engine.util.FormidableJcrConstants.ACL_NODE_TYPE;
import static org.jahia.modules.formidable.engine.util.FormidableJcrConstants.INHERIT_PROPERTY;

/**
 * The {@link FormResultsStore} of the engine: what {@link SaveToJcrFormAction} does to the results of a site,
 * offered to the modules that write results of their own, an import among them.
 */
@Component(service = FormResultsStore.class, immediate = true)
public class FormResultsStoreService implements FormResultsStore {

    @Override
    public JCRNodeWrapper getOrCreateResultsRoot(JCRNodeWrapper siteNode, JCRSessionWrapper session) throws RepositoryException {
        return SaveToJcrFormAction.getOrCreateResultsRoot(siteNode, session);
    }

    @Override
    public void detachAcl(JCRNodeWrapper node) throws RepositoryException {
        JCRNodeWrapper acl = node.addNode(ACL_NODE, ACL_NODE_TYPE);
        acl.setProperty(INHERIT_PROPERTY, false);
    }

    @Override
    public void ensureAutoSplit(JCRNodeWrapper submissions) throws RepositoryException {
        SaveToJcrFormAction.ensureAutoSplit(submissions);
    }

    @Override
    public String submissionNodeName(Instant submittedAt) {
        return SaveToJcrFormAction.submissionNodeName(submittedAt);
    }

    @Override
    public void syncReaders(JCRNodeWrapper formNode, JCRNodeWrapper entry, JCRSessionWrapper session) {
        FormResultsAclSyncService.syncAclToFormResults(formNode, entry, session);
    }
}
