package org.jahia.modules.formidable.engine.actions.field;

import org.jahia.modules.formidable.engine.actions.common.OfferedTypes;
import org.jahia.modules.formidable.engine.api.FieldActionTypes;
import org.jahia.modules.formidable.engine.api.FmdbMixin;
import org.jahia.services.content.JCRNodeWrapper;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import javax.jcr.RepositoryException;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The field-action types a site offers, as the Page Builder's chooser lists them ({@link OfferedTypes} states the
 * platform's rule). Whether a type runs on a sensitive field is the {@link FieldActionDispatcher}'s rule, asked of
 * the shared runtime, so that a view and the engine never disagree on it.
 */
@Component(service = FieldActionTypes.class, immediate = true)
public class FieldActionTypesService implements FieldActionTypes {

    private final AtomicReference<FieldActionRuntime> runtime = new AtomicReference<>();

    @Reference
    public void setRuntime(FieldActionRuntime service) {
        runtime.set(service);
    }

    @Override
    public boolean consumesSensitiveValues(String fieldActionNodeType) {
        FieldActionRuntime shared = runtime.get();
        return shared != null && fieldActionNodeType != null && shared.dispatcher().consumesSensitiveValues(fieldActionNodeType);
    }

    @Override
    public List<String> offeredUnder(JCRNodeWrapper actionList) throws RepositoryException {
        return OfferedTypes.under(actionList, FmdbMixin.FIELD_ACTION);
    }
}
