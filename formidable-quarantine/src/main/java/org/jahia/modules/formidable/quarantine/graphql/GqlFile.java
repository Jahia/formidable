package org.jahia.modules.formidable.quarantine.graphql;

import graphql.annotations.annotationTypes.GraphQLDescription;
import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLName;
import graphql.annotations.annotationTypes.GraphQLNonNull;
import org.jahia.modules.formidable.quarantine.store.ItemRecord;

import java.util.List;

@GraphQLName("FmdbqFile")
@GraphQLDescription("One file of a held submission, and the outcome of each check for it: never a byte")
public class GqlFile {

    private final ItemRecord item;
    private final List<GqlCheckOutcome> checks;

    GqlFile(ItemRecord item, List<GqlCheckOutcome> checks) {
        this.item = item;
        this.checks = List.copyOf(checks);
    }

    @GraphQLField
    @GraphQLNonNull
    @GraphQLDescription("The name of the item, item0 for the first file")
    public String getItem() {
        return item.name();
    }

    @GraphQLField
    @GraphQLDescription("The file field of the upload")
    public String getFieldName() {
        return item.fieldName();
    }

    @GraphQLField
    @GraphQLDescription("The sanitized original name of the file")
    public String getName() {
        return item.fileName();
    }

    @GraphQLField
    @GraphQLDescription("The detected MIME type")
    public String getMimeType() {
        return item.mimeType();
    }

    @GraphQLField
    @GraphQLDescription("The number of bytes at intake")
    public int getSize() {
        // the engine bounds an upload far below 2 GiB
        return (int) Math.min(item.size(), Integer.MAX_VALUE);
    }

    @GraphQLField
    @GraphQLNonNull
    @GraphQLDescription("The SHA-256 computed at intake, lower-case hexadecimal")
    public String getSha256() {
        return item.sha256();
    }

    @GraphQLField
    @GraphQLDescription("False once the bytes were deleted")
    public boolean isBytesPresent() {
        return item.binaryPresent();
    }

    @GraphQLField
    @GraphQLNonNull
    @GraphQLDescription("The last outcome of each check that ran on the file")
    public List<@GraphQLNonNull GqlCheckOutcome> getChecks() {
        return checks;
    }
}
