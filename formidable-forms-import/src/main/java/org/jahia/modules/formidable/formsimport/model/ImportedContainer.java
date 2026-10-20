package org.jahia.modules.formidable.formsimport.model;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * A step or a fieldset the import creates, with the elements it holds.
 *
 * @param nodeType {@code fmdb:step} or {@code fmdb:fieldset}
 * @param view the view the container renders with ({@code j:view}), {@link #MATRIX_VIEW} for the fieldset a
 *             Forms matrix becomes; null for the default view
 */
public record ImportedContainer(String name, String nodeType, Map<String, String> titles, List<ImportedElement> children, String view)
        implements ImportedElement {

    public static final String STEP = "fmdb:step";
    public static final String FIELDSET = "fmdb:fieldset";
    /** The Matrix view of a fieldset (formidable-elements, docs/styling/README.md, "The matrix view of a fieldset"). */
    public static final String MATRIX_VIEW = "matrix";

    /** A container rendered with its default view. */
    public ImportedContainer(String name, String nodeType, Map<String, String> titles, List<ImportedElement> children) {
        this(name, nodeType, titles, children, null);
    }

    /** Every field under the container, at any depth, in order. */
    public Stream<ImportedField> fields() {
        return children.stream().flatMap(child -> child instanceof ImportedField field
                ? Stream.of(field)
                : ((ImportedContainer) child).fields());
    }
}
