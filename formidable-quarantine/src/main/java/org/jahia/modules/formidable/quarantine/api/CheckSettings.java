package org.jahia.modules.formidable.quarantine.api;

import java.util.Collections;
import java.util.LinkedHashMap;
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
 * @param properties     the properties of its own type that the check node holds, by name, as strings: the properties
 *                       that its primary type declares or inherits, except those whose name starts with {@code jcr:},
 *                       {@code j:} or {@code mix:}, and except {@code onInconclusive} and {@code onUnavailable}. The
 *                       values of a multiple property are joined with a comma ({@code ,}), in their order: a value
 *                       that holds a comma cannot be told apart. A property the node does not hold is absent. Never
 *                       null; it cannot be changed.
 */
public record CheckSettings(String nodeType, String name, OnInconclusive onInconclusive, OnUnavailable onUnavailable,
                            Map<String, String> properties) {

    /** The separator of the values of a multiple property in {@link #properties()}. */
    public static final String VALUE_SEPARATOR = ",";

    public CheckSettings {
        Objects.requireNonNull(nodeType, "nodeType");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(onInconclusive, "onInconclusive");
        Objects.requireNonNull(onUnavailable, "onUnavailable");
        properties = properties == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(properties));
    }

    /** Settings with no property of the type of the check. */
    public CheckSettings(String nodeType, String name, OnInconclusive onInconclusive, OnUnavailable onUnavailable) {
        this(nodeType, name, onInconclusive, onUnavailable, Map.of());
    }

    /**
     * @return settings with the defaults: {@link OnInconclusive#REVIEW} and {@link OnUnavailable#RETRY}, and no property
     */
    public static CheckSettings withDefaults(String nodeType, String name) {
        return new CheckSettings(nodeType, name, OnInconclusive.REVIEW, OnUnavailable.RETRY);
    }
}
