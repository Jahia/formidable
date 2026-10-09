package org.jahia.modules.formidable.engine.imports.model;

/**
 * A file a visitor uploaded with a submission, to be written under {@code files/<fieldName>/<fileName>}.
 * The binary is not held here: the writer opens it from the export by {@link #sourcePath()}.
 *
 * @param fieldName the system name of the field the file belongs to
 * @param sourcePath where the file sits in the source export
 */
public record ImportedFile(String fieldName, String fileName, String mimeType, String sourcePath) {
}
