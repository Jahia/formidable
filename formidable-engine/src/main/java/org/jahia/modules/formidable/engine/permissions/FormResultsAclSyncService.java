package org.jahia.modules.formidable.engine.permissions;

import org.jahia.services.content.JCRContentUtils;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRSessionWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Value;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Idempotent service that reads fmdb-results-reader ACEs from a fmdb:form node
 * and replicates them on the corresponding fmdb:formResults node.
 *
 * Propagation is unidirectional: form → formResults. The admin never touches
 * formResults ACLs directly.
 */
public final class FormResultsAclSyncService {

    private static final Logger log = LoggerFactory.getLogger(FormResultsAclSyncService.class);
    private static final String RESULTS_ROOT_NAME = "formidable-results";

    private FormResultsAclSyncService() {
    }

    /**
     * Synchronises fmdb-results-reader ACEs from the given form node to its
     * formResults node. Does nothing if formResults does not exist yet.
     *
     * @param formNode a fmdb:form node (live workspace)
     * @param session  the current system session (live workspace)
     */
    public static void syncAcl(JCRNodeWrapper formNode, JCRSessionWrapper session) {
        try {
            JCRNodeWrapper formResults = findFormResults(formNode);
            if (formResults == null) {
                log.debug("[AclSync] No formResults for form '{}', skipping", formNode.getPath());
                return;
            }

            doSync(formNode, formResults, session);
        } catch (RepositoryException e) {
            log.error("[AclSync] Failed to sync ACL for form '{}': {}", formNode.getPath(), e.getMessage(), e);
        }
    }

    /**
     * Synchronises ACEs onto a specific formResults node. Called by SaveToJcrFormAction
     * right after creating the formResults node, so we don't need to look it up.
     */
    public static void syncAclToFormResults(JCRNodeWrapper formNode, JCRNodeWrapper formResults,
                                            JCRSessionWrapper session) {
        try {
            doSync(formNode, formResults, session);
        } catch (RepositoryException e) {
            log.error("[AclSync] Failed to sync ACL for form '{}': {}", formNode.getPath(), e.getMessage(), e);
        }
    }

    private static void doSync(JCRNodeWrapper formNode, JCRNodeWrapper formResults,
                                JCRSessionWrapper session) throws RepositoryException {
        List<AceEntry> sourceAces = readAcesForRole(formNode);
        List<AceEntry> existingAces = readAcesForRole(formResults);

        Set<AceEntry> toAdd = new LinkedHashSet<>(sourceAces);
        toAdd.removeAll(new LinkedHashSet<>(existingAces));

        Set<AceEntry> toRemove = new LinkedHashSet<>(existingAces);
        toRemove.removeAll(new LinkedHashSet<>(sourceAces));

        if (toAdd.isEmpty() && toRemove.isEmpty()) {
            ensureInheritanceBroken(formResults, session);
            log.debug("[AclSync] ACL already in sync for form '{}'", formNode.getPath());
            return;
        }

        session.checkout(formResults);
        JCRNodeWrapper acl;
        if (formResults.hasNode("j:acl")) {
            acl = formResults.getNode("j:acl");
        } else {
            acl = formResults.addNode("j:acl", "jnt:acl");
        }

        // Always enforce broken inheritance so the site-level reader role cannot leak
        if (!acl.hasProperty("j:inherit") || acl.getProperty("j:inherit").getBoolean()) {
            acl.setProperty("j:inherit", false);
        }

        session.checkout(acl);

        for (AceEntry ace : toRemove) {
            removeAce(acl, ace);
        }

        for (AceEntry ace : toAdd) {
                addAce(acl, ace);
        }

        log.info("[AclSync] Synced ACL for form '{}': {} added, {} removed",
                formNode.getPath(), toAdd.size(), toRemove.size());
    }

    private static void ensureInheritanceBroken(JCRNodeWrapper formResults, JCRSessionWrapper session)
            throws RepositoryException {
        session.checkout(formResults);
        JCRNodeWrapper acl;
        if (formResults.hasNode("j:acl")) {
            acl = formResults.getNode("j:acl");
        } else {
            acl = formResults.addNode("j:acl", "jnt:acl");
        }

        if (!acl.hasProperty("j:inherit") || acl.getProperty("j:inherit").getBoolean()) {
            session.checkout(acl);
            acl.setProperty("j:inherit", false);
        }
    }

    static JCRNodeWrapper findFormResults(JCRNodeWrapper formNode)
            throws RepositoryException {
        JCRNodeWrapper siteNode = formNode.getResolveSite();
        if (!siteNode.hasNode(RESULTS_ROOT_NAME)) {
            return null;
        }

        JCRNodeWrapper resultsRoot = siteNode.getNode(RESULTS_ROOT_NAME);
        String formIdentifier = formNode.getIdentifier();
        NodeIterator children = resultsRoot.getNodes();
        while (children.hasNext()) {
            javax.jcr.Node child = children.nextNode();
            if (!(child instanceof JCRNodeWrapper candidate)) {
                continue;
            }

            if (!candidate.isNodeType("fmdb:formResults") || !candidate.hasProperty("parentForm")) {
                continue;
            }

            if (formIdentifier.equals(candidate.getProperty("parentForm").getString())) {
                return candidate;
            }
        }

        return null;
    }

    private static List<AceEntry> readAcesForRole(JCRNodeWrapper node) throws RepositoryException {
        List<AceEntry> result = new ArrayList<>();
        if (!node.hasNode("j:acl")) {
            return result;
        }

        JCRNodeWrapper acl = node.getNode("j:acl");
        NodeIterator children = acl.getNodes();
        while (children.hasNext()) {
            javax.jcr.Node child = children.nextNode();
            if (!(child instanceof JCRNodeWrapper ace)) {
                continue;
            }

            if (!ace.isNodeType("jnt:ace")) {
                continue;
            }

            if (!ace.hasProperty("j:roles") || !ace.hasProperty("j:principal") || !ace.hasProperty("j:aceType")) {
                continue;
            }

            Value[] roles = ace.getProperty("j:roles").getValues();
            boolean hasTargetRole = false;
            for (Value role : roles) {
                if (FormResultsRoleInitializer.ROLE_NAME.equals(role.getString())) {
                    hasTargetRole = true;
                    break;
                }
            }

            if (!hasTargetRole) {
                continue;
            }

            String aceType = ace.getProperty("j:aceType").getString();
            String principal = ace.getProperty("j:principal").getString();
            boolean isProtected = ace.hasProperty("j:protected") && ace.getProperty("j:protected").getBoolean();
            result.add(new AceEntry(aceType, principal, isProtected));
        }

        return result;
    }

    private static void addAce(JCRNodeWrapper acl, AceEntry ace) throws RepositoryException {
        String aceName = JCRContentUtils.findAvailableNodeName(acl, ace.aceType() + "_" + ace.principal()
                .replace(":", "_").replace("/", "_"));
        JCRNodeWrapper aceNode = acl.addNode(aceName, "jnt:ace");
        aceNode.setProperty("j:aceType", ace.aceType());
        aceNode.setProperty("j:principal", ace.principal());
        aceNode.setProperty("j:roles", new String[]{FormResultsRoleInitializer.ROLE_NAME});
        aceNode.setProperty("j:protected", ace.isProtected());
    }

    private static void removeAce(JCRNodeWrapper acl, AceEntry ace) throws RepositoryException {
        NodeIterator children = acl.getNodes();
        while (children.hasNext()) {
            javax.jcr.Node child = children.nextNode();
            if (!(child instanceof JCRNodeWrapper aceNode)) {
                continue;
            }

            if (!aceNode.isNodeType("jnt:ace")) {
                continue;
            }

            if (!aceNode.hasProperty("j:aceType") || !aceNode.hasProperty("j:principal")
                    || !aceNode.hasProperty("j:roles")) {
                continue;
            }

            String aceType = aceNode.getProperty("j:aceType").getString();
            String principal = aceNode.getProperty("j:principal").getString();
            if (!ace.aceType().equals(aceType) || !ace.principal().equals(principal)) {
                continue;
            }

            Value[] roles = aceNode.getProperty("j:roles").getValues();
            for (Value role : roles) {
                if (FormResultsRoleInitializer.ROLE_NAME.equals(role.getString())) {
                    aceNode.remove();
                    break;
                }
            }
        }
    }


    record AceEntry(String aceType, String principal, boolean isProtected) {
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof AceEntry that)) return false;
            return Objects.equals(aceType, that.aceType) && Objects.equals(principal, that.principal);
        }

        @Override
        public int hashCode() {
            return Objects.hash(aceType, principal);
        }
    }
}

