package org.jahia.modules.formidable.jexperience.engine.graphql;

import graphql.annotations.annotationTypes.GraphQLDescription;
import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLName;
import graphql.annotations.annotationTypes.GraphQLNonNull;
import org.jahia.modules.formidable.jexperience.engine.profile.ProfilePropertyDescriptor;

@GraphQLName("FormidableJExperienceProfileProperty")
@GraphQLDescription("A property of the visitor's jExperience profile that a conditional-logic rule may read")
public class GqlProfileProperty {

    private final ProfilePropertyDescriptor descriptor;

    public GqlProfileProperty(ProfilePropertyDescriptor descriptor) {
        this.descriptor = descriptor;
    }

    @GraphQLField
    @GraphQLNonNull
    @GraphQLDescription("The property id, what the rule stores")
    public String getName() {
        return descriptor.name();
    }

    @GraphQLField
    @GraphQLNonNull
    @GraphQLDescription("What the author reads: the property's name in jCustomer, followed by its id when they differ")
    public String getLabel() {
        return descriptor.label();
    }

    @GraphQLField
    @GraphQLNonNull
    @GraphQLDescription("jCustomer's value type, in lower case")
    public String getValueType() {
        return descriptor.valueTypeId();
    }
}
