package org.jahia.modules.formidable.engine.api;

import org.jahia.services.content.JCRNodeWrapper;

import javax.jcr.RepositoryException;
import java.util.List;

/**
 * The field-action types a contributor can add under a field of a site: the concrete node types taking
 * {@code fmdbmix:fieldAction} that the Page Builder's type chooser lists under the field's action list on that
 * site — the ones declared by a module enabled on the site, or by a dependency of one, which is the platform's
 * rule for the types a site can create. The engine ships no concrete type, so a site where no module providing
 * one is enabled has none. An OSGi service of the engine, read by the authoring zone of a field's action list:
 * when the site has no type, the zone says so instead of drawing a create button that opens on nothing.
 */
public interface FieldActionTypes {

    /**
     * @param actionList the field's action list ({@code fmdb:fieldActionList}), or any node of the site, read as
     *                   the current user — the platform's component permissions apply, checked on the site node
     *                   as the chooser checks them
     * @return the names of the field-action types the chooser lists there, sorted; empty when the site has none
     * @throws RepositoryException when the site or the types cannot be read
     */
    List<String> offeredUnder(JCRNodeWrapper actionList) throws RepositoryException;

    /**
     * Whether a field action of this type runs on a sensitive field ({@code fmdbmix:sensitiveField}): the Java
     * {@link FieldAction} registered for the type opts in ({@link FieldAction#consumesSensitiveValues()}). The views
     * ask it to decide which actions of a sensitive field the browser pre-checks. A type no deployed module registers
     * answers {@code false}, so the browser does not send the password to a check that can only answer unavailable;
     * the submission still judges it with the contributor's outage rule.
     *
     * @param fieldActionNodeType the field-action node type, for example {@code myco:passwordPolicyAction}
     * @return {@code true} when the registered action opts in; {@code false} when it does not, or none is registered.
     *         {@code false} by default, so an implementation that does not answer leaves the action out
     */
    default boolean consumesSensitiveValues(String fieldActionNodeType) {
        return false;
    }
}
