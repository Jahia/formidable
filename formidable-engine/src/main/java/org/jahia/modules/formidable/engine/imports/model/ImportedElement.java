package org.jahia.modules.formidable.engine.imports.model;

/**
 * What the import places under the {@code fields} of a form: a field, or a container (a step, a
 * fieldset) that holds fields and containers of its own.
 */
public sealed interface ImportedElement permits ImportedField, ImportedContainer {

    /** The node name. */
    String name();
}
