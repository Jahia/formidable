package org.jahia.modules.formidable.quarantine.api;

/**
 * Where a check reads the bytes of a file. The chain refuses a list where an {@link #IN_PROCESS} check would read a
 * file before a {@link #REMOTE} check passed it.
 */
public enum Scope {

    /** The bytes go to an external engine, such as a sandbox. Nothing parses them inside Jahia. */
    REMOTE,

    /** The bytes are parsed inside the JVM of Jahia. */
    IN_PROCESS
}
