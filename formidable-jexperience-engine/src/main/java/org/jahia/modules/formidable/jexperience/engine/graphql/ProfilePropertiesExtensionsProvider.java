package org.jahia.modules.formidable.jexperience.engine.graphql;

import org.jahia.modules.graphql.provider.dxm.DXGraphQLExtensionsProvider;
import org.osgi.service.component.annotations.Component;

import java.util.Collection;
import java.util.List;

/** Registers this module's GraphQL extension: the profile properties the conditional-logic editor offers. */
@Component(service = DXGraphQLExtensionsProvider.class, immediate = true)
public class ProfilePropertiesExtensionsProvider implements DXGraphQLExtensionsProvider {

    @Override
    public Collection<Class<?>> getExtensions() {
        return List.of(ProfilePropertiesQueryExtension.class);
    }
}
