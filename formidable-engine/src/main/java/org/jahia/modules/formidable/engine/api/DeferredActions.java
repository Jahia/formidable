package org.jahia.modules.formidable.engine.api;

import java.util.function.IntConsumer;

/**
 * Runs the actions after release of a form: the nodes of its {@code deferredActions} list, in order. An OSGi
 * service of the engine: the engine alone implements it, and a {@link SubmissionHold} calls it when it releases a
 * submission. The caller is trusted, as any code of the platform that opens a system session is.
 *
 * <p>An action runs here as it runs in a request: the sensitive values reach only the action that opts in, and a
 * {@link ContextualFormAction} receives its context. Three things differ, because the HTTP request is gone:</p>
 * <ul>
 *   <li>the request an action receives answers the values of {@link SubmissionOrigin} and nothing else, and the
 *       response answers nothing: any other call throws {@link DetachedRequestException};</li>
 *   <li>the session of the visitor is {@code null};</li>
 *   <li>the result of a contextual action, a redirect or a message, is dropped: no browser waits for it.</li>
 * </ul>
 *
 * <p>A node of the list whose type does not declare {@code fmdbmix:deferrableAction} refuses the whole run, before
 * the first action.</p>
 */
public interface DeferredActions {

    /**
     * Runs the actions after release of the form of the submission, from the action at index {@code from}.
     *
     * @param submission the released submission, with the files that the hold qualified
     * @param from       the index of the first action to run: 0 for a first run, the index of the action that failed
     *                   to resume a run
     * @param completed  called with the index of each action once it ran, before the next one starts: a hold records
     *                   it, so that a run it resumes repeats no action
     * @throws DeferredActionException when an action failed, or the list was refused: the actions after it did not run
     */
    void run(HeldSubmission submission, int from, IntConsumer completed) throws DeferredActionException;
}
