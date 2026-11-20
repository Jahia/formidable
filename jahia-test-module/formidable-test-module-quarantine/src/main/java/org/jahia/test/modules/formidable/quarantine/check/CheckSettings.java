package org.jahia.test.modules.formidable.quarantine.check;

import java.util.Objects;

/**
 * The settings of one check of the "Checks" list of a form, as the wiring reads them from the check node. A plain
 * value: no qualifier sees a node of the repository.
 *
 * @param nodeType       the primary type of the check node, such as {@code fmdbquar:remoteStubCheck}
 * @param name           the name of the check node, unique in the list; outcomes are recorded under it
 * @param onInconclusive what an inconclusive answer means
 * @param onUnavailable  what a failure of the engine means
 */
public record CheckSettings(String nodeType, String name, OnInconclusive onInconclusive, OnUnavailable onUnavailable) {

    public CheckSettings {
        Objects.requireNonNull(nodeType, "nodeType");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(onInconclusive, "onInconclusive");
        Objects.requireNonNull(onUnavailable, "onUnavailable");
    }

    /**
     * @return settings with the defaults: {@link OnInconclusive#REVIEW} and {@link OnUnavailable#RETRY}
     */
    public static CheckSettings withDefaults(String nodeType, String name) {
        return new CheckSettings(nodeType, name, OnInconclusive.REVIEW, OnUnavailable.RETRY);
    }
}
