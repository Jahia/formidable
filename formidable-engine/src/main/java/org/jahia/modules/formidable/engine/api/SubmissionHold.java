package org.jahia.modules.formidable.engine.api;

/**
 * The service that holds a submission with its uploads until they are qualified. An extension module registers it
 * as an OSGi service; the engine ships none.
 *
 * <p>On a form that holds its uploads in quarantine ({@code fmdbmix:quarantine}), the pipeline hands the uploads to
 * no action of the request. It calls {@link #hold} once the actions of the request ran, with the values and the
 * uploads. Without a registered hold, the pipeline refuses a submission that carries an upload. A submission with no
 * upload is not held: its actions after release run in the request.</p>
 *
 * <p>A hold takes on what the engine cannot enforce. Until a file is qualified, it keeps the bytes where no reader,
 * no parser and no action reaches them, and sends them nowhere but to the checks. It releases a submission through
 * {@link DeferredActions#run}, with the files it qualified.</p>
 *
 * <p>The engine binds one hold. When several are registered, the one with the highest service ranking holds.</p>
 */
public interface SubmissionHold {

    /**
     * Stores the submission and returns: the request waits for this call, so no check runs here.
     *
     * @throws SubmissionHoldException when the submission could not be stored: the pipeline refuses it
     */
    void hold(HeldSubmission submission) throws SubmissionHoldException;
}
