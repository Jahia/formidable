package org.jahia.modules.formidable.engine.imports.jahiaforms;

/**
 * The export cannot be read as a Jahia Forms export: not a zip, no {@code repository.xml} in it, no
 * {@code formFactory} root, or no result at all. The message is written for the administrator who
 * dropped the file, and names the procedure that gives a usable export.
 */
public class FormsExportException extends Exception {

    static final String PROCEDURE = "Export the site's formFactory node from the Repository explorer with "
            + "\"Export Zip with live content\".";

    public FormsExportException(String message) {
        super(message);
    }

    public FormsExportException(String message, Throwable cause) {
        super(message, cause);
    }
}
