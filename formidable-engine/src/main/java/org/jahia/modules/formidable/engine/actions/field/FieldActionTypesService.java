package org.jahia.modules.formidable.engine.actions.field;

import org.jahia.modules.formidable.engine.api.FieldActionTypes;
import org.jahia.modules.formidable.engine.api.FmdbMixin;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.decorator.JCRSiteNode;
import org.jahia.services.content.nodetypes.ExtendedNodeType;
import org.jahia.services.content.nodetypes.NodeTypeRegistry;
import org.osgi.service.component.annotations.Component;

import javax.jcr.RepositoryException;
import javax.jcr.nodetype.NodeTypeIterator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The field-action types a site offers, as the Page Builder's chooser lists them — the platform's rule
 * ({@code org.jahia.utils.NodeTypesUtils.getContentTypesAsTree}, which content-editor's
 * {@code forms.contentTypesAsTree} applies to every create button): among the types registered by each module
 * enabled on the site and by their dependencies, those taking the mixin that are neither mixins nor abstract
 * and pass the component permission of the mixins declared under {@code jmix:accessControllableContent},
 * checked on the site node as the chooser checks it. The core's own types ({@code system-jahia}) are left out:
 * none takes a mixin of this module. Asked on every render of the zone, which is authoring only; the registry
 * answers from memory.
 */
@Component(service = FieldActionTypes.class, immediate = true)
public class FieldActionTypesService implements FieldActionTypes {

    private static final String ACCESS_CONTROLLABLE_CONTENT = "jmix:accessControllableContent";

    @Override
    public List<String> offeredUnder(JCRNodeWrapper actionList) throws RepositoryException {
        JCRSiteNode site = actionList.getResolveSite();
        if (site == null) {
            return List.of();
        }
        NodeTypeRegistry registry = NodeTypeRegistry.getInstance();
        List<String> offered = new ArrayList<>();
        for (String moduleId : site.getInstalledModulesWithAllDependencies()) {
            for (NodeTypeIterator types = registry.getNodeTypes(moduleId); types.hasNext(); ) {
                ExtendedNodeType type = (ExtendedNodeType) types.nextNodeType();
                if (type.isNodeType(FmdbMixin.FIELD_ACTION) && !type.isMixin() && !type.isAbstract()
                        && permitted(type, site)) {
                    offered.add(type.getName());
                }
            }
        }
        Collections.sort(offered);
        return offered;
    }

    /**
     * The platform's component permission, as the chooser applies it: a type whose supertypes include a mixin
     * declared under {@code jmix:accessControllableContent} is offered only when the user holds
     * {@code component-<mixin>} on the site node — there, not on the parent: {@code getContentTypesAsTree} checks
     * it on the site it resolved, so a role granted on a subtree alone does not count — and the last such mixin
     * found decides, as in the platform. No type of the engine or of its samples carries one; a project's may.
     */
    private static boolean permitted(ExtendedNodeType type, JCRSiteNode site) throws RepositoryException {
        Set<ExtendedNodeType> supertypes = new HashSet<>(Arrays.asList(type.getSupertypes()));
        if (supertypes.isEmpty()) {
            return true;
        }
        boolean allowed = true;
        NodeTypeIterator controlled = NodeTypeRegistry.getInstance().getNodeType(ACCESS_CONTROLLABLE_CONTENT).getDeclaredSubtypes();
        while (controlled.hasNext()) {
            ExtendedNodeType mixin = (ExtendedNodeType) controlled.nextNodeType();
            if (supertypes.contains(mixin)) {
                allowed = site.hasPermission("component-" + mixin.getName().replace(':', '_'));
            }
        }
        return allowed;
    }
}
