package org.jahia.test.modules.formidable.samples.actions.form;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * What the upload samples saw since the last reset, for the test suite to read through
 * {@link UploadObservationsServlet}: each call of {@link UploadObserverFormAction} and each call of
 * {@link SampleSubmissionHold}, in order. Counts and node names, never a value and never a byte of a file.
 *
 * <p>Kept in memory, on the node that ran the action: a test double, read on a single Jahia.</p>
 */
final class UploadObservations {

    /** The most calls of one kind kept: a suite that forgets to reset cannot grow the memory of the module. */
    static final int MAX_CALLS = 200;

    /**
     * One call of the observer action.
     *
     * @param action the name of the action node
     * @param list   the name of the list that holds it: {@code actions} or {@code deferredActions}
     * @param files  the number of files the action was handed
     */
    record ObserverCall(String action, String list, int files) {}

    private static final List<ObserverCall> OBSERVER = new CopyOnWriteArrayList<>();
    private static final List<Integer> HOLD = new CopyOnWriteArrayList<>();

    private UploadObservations() {
    }

    static void observer(String action, String list, int files) {
        if (OBSERVER.size() < MAX_CALLS) {
            OBSERVER.add(new ObserverCall(action, list, files));
        }
    }

    /** Records one call of the sample hold, with the number of files it was handed. */
    static void hold(int files) {
        if (HOLD.size() < MAX_CALLS) {
            HOLD.add(files);
        }
    }

    static void reset() {
        OBSERVER.clear();
        HOLD.clear();
    }

    static JSONObject json() {
        JSONArray observer = new JSONArray();
        for (ObserverCall call : OBSERVER) {
            observer.put(new JSONObject().put("action", call.action()).put("list", call.list()).put("files", call.files()));
        }
        return new JSONObject().put("observer", observer).put("hold", new JSONArray(HOLD));
    }
}
