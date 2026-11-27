package org.jahia.modules.formidable.quarantine.api;

/**
 * What a check reads of a file. The chain refuses a list where an {@link #IN_PROCESS} check would read a file before
 * a {@link #REMOTE} check passed it. A {@link #METADATA} check may stand at any place in the list.
 */
public enum Scope {

    /** The bytes go to an external engine, such as a sandbox. Nothing parses them inside Jahia. */
    REMOTE,

    /** The bytes are parsed inside the JVM of Jahia. */
    IN_PROCESS,

    /**
     * The check reads the name, the detected MIME type and the size of the file, and never a byte. The run hands it a
     * file whose {@link QuarantinedFile#open()} throws.
     */
    METADATA
}
