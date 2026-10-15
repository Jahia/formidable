package org.jahia.modules.formidable.content.plan;

import org.jahia.modules.formidable.content.convert.PropertyShape;
import org.jahia.modules.formidable.engine.api.SubmittedFile;

import java.util.List;

/**
 * The files of one upload field, and the property of the created content that will reference them once stored.
 * Immutable.
 *
 * @param property the reference property that receives the stored files
 * @param files    the uploaded files of the field, in submission order, never empty
 */
public record FileGroup(PropertyShape property, List<SubmittedFile> files) {

    public FileGroup {
        files = List.copyOf(files);
    }
}
