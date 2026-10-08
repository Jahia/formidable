package org.jahia.modules.formidable.engine.imports.model;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * A step or a fieldset the import creates, with the elements it holds.
 *
 * @param nodeType {@code fmdb:step} or {@code fmdb:fieldset}
 */
public record ImportedContainer(String name, String nodeType, Map<String, String> titles, List<ImportedElement> children)
        implements ImportedElement {

    public static final String STEP = "fmdb:step";
    public static final String FIELDSET = "fmdb:fieldset";

    /** Every field under the container, at any depth, in order. */
    public Stream<ImportedField> fields() {
        return children.stream().flatMap(child -> child instanceof ImportedField field
                ? Stream.of(field)
                : ((ImportedContainer) child).fields());
    }
}
