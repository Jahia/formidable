package org.jahia.test.modules.formidable.quarantine;

/**
 * The names of the node types this module declares in its CND: its stub checks. The quarantine tree and its types
 * belong to {@code formidable-quarantine}.
 */
public final class QuarantineTypes {

    /** The stub of a check that asks a remote service. */
    public static final String REMOTE_STUB_CHECK = "fmdbquar:remoteStubCheck";

    /** The stub of a check that reads the file inside the JVM. */
    public static final String IN_PROCESS_STUB_CHECK = "fmdbquar:inProcessStubCheck";

    private QuarantineTypes() {
    }
}
