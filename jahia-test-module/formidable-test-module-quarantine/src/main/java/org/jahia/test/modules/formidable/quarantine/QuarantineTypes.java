package org.jahia.test.modules.formidable.quarantine;

import java.util.Set;

/**
 * The names of the node types this module declares in its CND: its stub checks. The quarantine tree and its types
 * belong to {@code formidable-quarantine}.
 */
public final class QuarantineTypes {

    /** The stub of a check that asks a remote service. */
    public static final String REMOTE_STUB_CHECK = "fmdbquar:remoteStubCheck";

    /** The stub of a check that reads the file inside the JVM. */
    public static final String IN_PROCESS_STUB_CHECK = "fmdbquar:inProcessStubCheck";

    /** The check types of this module. */
    public static final Set<String> CHECK_TYPES = Set.of(REMOTE_STUB_CHECK, IN_PROCESS_STUB_CHECK);

    private QuarantineTypes() {
    }

    /**
     * @param nodeTypeName the name of the primary type of a node of a "Checks" list
     * @return true when this module declares the type as one of its checks
     */
    public static boolean isCheckType(String nodeTypeName) {
        return nodeTypeName != null && CHECK_TYPES.contains(nodeTypeName);
    }
}
