package org.jahia.modules.formidable.jexperience.engine.graphql;

import graphql.annotations.annotationTypes.GraphQLDescription;
import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLName;
import graphql.annotations.annotationTypes.GraphQLNonNull;

import java.util.List;

@GraphQLName("FormidableJExperienceProfileProperties")
@GraphQLDescription("The visitor profile properties a conditional-logic rule may read on a site")
public class GqlProfileProperties {

    static final GqlProfileProperties UNAVAILABLE = new GqlProfileProperties(false, List.of());

    private final boolean available;
    private final List<GqlProfileProperty> properties;

    GqlProfileProperties(boolean available, List<GqlProfileProperty> properties) {
        this.available = available;
        this.properties = properties;
    }

    @GraphQLField
    @GraphQLNonNull
    @GraphQLDescription("False when jCustomer cannot be asked: the list is then empty and says nothing about the schema")
    public boolean isAvailable() {
        return available;
    }

    @GraphQLField
    @GraphQLNonNull
    @GraphQLDescription("The properties, sorted by label")
    public List<GqlProfileProperty> getProperties() {
        return properties;
    }
}
