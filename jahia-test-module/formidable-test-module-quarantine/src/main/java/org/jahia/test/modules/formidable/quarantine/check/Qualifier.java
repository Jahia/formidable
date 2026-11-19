package org.jahia.test.modules.formidable.quarantine.check;

/**
 * A check of the quarantine: the service behind one node type of the "Checks" list of a form. The wiring registers
 * each implementation as an OSGi service and matches it to the check nodes by {@link #getNodeType()}.
 * <p>
 * An implementation never sees a node of the repository: the wiring reads the check node into {@link CheckSettings}.
 */
public interface Qualifier {

    /** @return the node type of the checks this qualifier serves, such as {@code fmdbquar:remoteStubCheck} */
    String getNodeType();

    /** @return where the check reads the bytes */
    Scope scope();

    /**
     * @return true when the check applies to the file, such as a PDF check to a PDF; the decision reads the metadata
     *         of the file, never its bytes
     */
    boolean appliesTo(QuarantinedFile file, CheckSettings settings);

    /**
     * Hands the file to the check.
     *
     * @return a ticket to poll, or an outcome at once
     */
    Submitted submit(QuarantinedFile file, CheckSettings settings);

    /**
     * @param ticket a ticket that {@link #submit} returned
     * @return {@link Outcome.Pending} while the engine works, then a result or a failure
     */
    Outcome poll(String ticket);
}
