package org.jahia.modules.formidable.quarantine.run;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The quarantine policy of one form, as the mixin {@code fmdbqmix:quarantinePolicy} of the form in live sets it. A
 * form without the mixin has the {@link #DEFAULT} policy.
 *
 * @param onFail           what a {@code FAIL} verdict, and a reject by a manager, do
 * @param scanStatusName   the name of the value that the run adds to the values of the actions after release, or
 *                         {@code null} when the run adds none
 * @param scanStatusClean  the scan status of a release of the files, every check passed or a manager released them
 * @param scanStatusFailed the scan status of a release of the values alone
 * @param showValues       true to show the public values of a held submission to a manager
 * @param scanStatusTaken  true when a field of the form has the name of the scan status: the run refuses to write the
 *                         status over the value of a field
 */
public record FormPolicy(OnFail onFail, String scanStatusName, String scanStatusClean, String scanStatusFailed,
                         boolean showValues, boolean scanStatusTaken) {

    /** The policy of a form without the mixin: reject on a fail, no scan status, no value shown. */
    public static final FormPolicy DEFAULT = new FormPolicy(OnFail.REJECT, null, null, null, false, false);

    public FormPolicy {
        Objects.requireNonNull(onFail, "onFail");
        scanStatusName = scanStatusName == null || scanStatusName.isBlank() ? null : scanStatusName.trim();
        scanStatusClean = scanStatusClean == null ? "" : scanStatusClean;
        scanStatusFailed = scanStatusFailed == null ? "" : scanStatusFailed;
    }

    /** @return true when a fail, or a reject by a manager, releases the values alone */
    public boolean releasesValues() {
        return onFail == OnFail.RELEASE_VALUES;
    }

    /**
     * @param values the public values of the submission
     * @param clean  true for a release of the files, false for a release of the values alone
     * @return a copy of the values, in their order, with the scan status written over any value of its name; the
     *         values as they are when the policy names no scan status
     */
    public Map<String, List<String>> withScanStatus(Map<String, List<String>> values, boolean clean) {
        if (scanStatusName == null) {
            return values;
        }
        Map<String, List<String>> copy = new LinkedHashMap<>(values);
        copy.put(scanStatusName, List.of(clean ? scanStatusClean : scanStatusFailed));
        return Collections.unmodifiableMap(copy);
    }
}
