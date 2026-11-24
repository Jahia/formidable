package org.jahia.modules.formidable.engine.api;

import org.jahia.services.content.JCRNodeWrapper;

import javax.jcr.RepositoryException;
import java.util.List;

/**
 * The check types a contributor can add to the "Quarantine checks" list of a form on a site: the concrete node types
 * taking {@code fmdbmix:qualifier} that the Page Builder's type chooser lists under that list — the ones declared by
 * a module enabled on the site, or by a dependency of one. The engine ships no concrete type, so a site where no
 * module providing one is enabled has none. An OSGi service of the engine, read by the authoring zone of the list:
 * when the site has no type, the zone says so instead of drawing a create button that opens on nothing.
 */
public interface QualifierTypes {

    /**
     * @param qualifierList the form's checks list ({@code fmdb:qualifierList}), or any node of the site, read as the
     *                      current user — the platform's component permissions apply, checked on the site node as the
     *                      chooser checks them
     * @return the names of the check types the chooser lists there, sorted; empty when the site has none
     * @throws RepositoryException when the site or the types cannot be read
     */
    List<String> offeredUnder(JCRNodeWrapper qualifierList) throws RepositoryException;
}
