package org.jahia.modules.formidable.quarantine.graphql;

import org.jahia.modules.graphql.provider.dxm.DXGraphQLExtensionsProvider;
import org.osgi.service.component.annotations.Component;

import java.util.Collection;
import java.util.List;

/** Registers the GraphQL API of the quarantine: the query and the mutation {@code formidableQuarantine}. */
@Component(service = DXGraphQLExtensionsProvider.class, immediate = true)
public class QuarantineGraphQLExtensions implements DXGraphQLExtensionsProvider {

    @Override
    public Collection<Class<?>> getExtensions() {
        return List.of(QuarantineQueryExtension.class, QuarantineMutationExtension.class);
    }
}
