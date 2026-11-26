package org.jahia.modules.formidable.quarantine.graphql;

import graphql.annotations.annotationTypes.GraphQLDescription;
import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLName;
import graphql.annotations.annotationTypes.GraphQLNonNull;

import java.util.List;

@GraphQLName("FmdbqValue")
@GraphQLDescription("The public value of one field of a held submission: never a sensitive value")
public class GqlValue {

    private final String name;
    private final List<String> values;

    GqlValue(String name, List<String> values) {
        this.name = name;
        this.values = List.copyOf(values);
    }

    @GraphQLField
    @GraphQLNonNull
    @GraphQLDescription("The name of the field")
    public String getName() {
        return name;
    }

    @GraphQLField
    @GraphQLNonNull
    @GraphQLDescription("The values of the field, in the order of the submission")
    public List<@GraphQLNonNull String> getValues() {
        return values;
    }
}
