package org.jahia.modules.formidable.engine.actions.common;

import org.jahia.modules.formidable.engine.api.FmdbMixin;
import org.jahia.modules.formidable.engine.api.QualifierTypes;
import org.jahia.services.content.JCRNodeWrapper;
import org.osgi.service.component.annotations.Component;

import javax.jcr.RepositoryException;
import java.util.List;

/** The check types a site offers, as the Page Builder's chooser lists them ({@link OfferedTypes} states the rule). */
@Component(service = QualifierTypes.class, immediate = true)
public class QualifierTypesService implements QualifierTypes {

    @Override
    public List<String> offeredUnder(JCRNodeWrapper qualifierList) throws RepositoryException {
        return OfferedTypes.under(qualifierList, FmdbMixin.QUALIFIER);
    }
}
