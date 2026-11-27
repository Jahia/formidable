package org.jahia.modules.formidable.quarantine.api;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The settings of one check of the "Checks" list of a form, as the wiring reads them from the check node. A plain
 * value: no qualifier sees a node of the repository.
 *
 * @param nodeType       the primary type of the check node, such as {@code acme:sandboxCheck}
 * @param name           the name of the check node, unique in the list; outcomes are recorded under it
 * @param onInconclusive what an inconclusive answer means
 * @param onUnavailable  what a failure of the engine means
 * @param properties     the properties of its own type that the check node holds, by name: the properties that its
 *                       primary type declares or inherits, except those whose name starts with {@code jcr:},
 *                       {@code j:} or {@code mix:}, and except {@code onInconclusive} and {@code onUnavailable}. Each
 *                       property maps to its values as strings, in their order: a single property to a list of one
 *                       value, a multiple property to a list of all its values, maybe empty. A property the node does
 *                       not hold is absent. Never null; neither the map nor a list can be changed.
 */
public record CheckSettings(String nodeType, String name, OnInconclusive onInconclusive, OnUnavailable onUnavailable,
                            Map<String, List<String>> properties) {

    public CheckSettings {
        Objects.requireNonNull(nodeType, "nodeType");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(onInconclusive, "onInconclusive");
        Objects.requireNonNull(onUnavailable, "onUnavailable");
        properties = copyOf(properties);
    }

    /** Settings with no property of the type of the check. */
    public CheckSettings(String nodeType, String name, OnInconclusive onInconclusive, OnUnavailable onUnavailable) {
        this(nodeType, name, onInconclusive, onUnavailable, Map.of());
    }

    /** A copy that nobody changes, in the order of the source; a null list of values reads as an empty one. */
    private static Map<String, List<String>> copyOf(Map<String, List<String>> source) {
        if (source == null) {
            return Map.of();
        }
        Map<String, List<String>> copy = new LinkedHashMap<>();
        source.forEach((name, values) -> copy.put(name, values == null ? List.of() : List.copyOf(values)));
        return Collections.unmodifiableMap(copy);
    }

    /**
     * @return settings with the defaults: {@link OnInconclusive#REVIEW} and {@link OnUnavailable#RETRY}, and no property
     */
    public static CheckSettings withDefaults(String nodeType, String name) {
        return new CheckSettings(nodeType, name, OnInconclusive.REVIEW, OnUnavailable.RETRY);
    }
}
