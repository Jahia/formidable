package org.jahia.modules.formidable.engine.logic;

import java.util.HashMap;
import java.util.Map;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Provider state declared by the browser at submit time: for each provider source type,
 * the current value of every reference the form's rules read — or its declared absence.
 *
 * This is a coherence declaration, not enforcement: the client controls it entirely. What
 * it buys is that a single declared state must stay coherent across every rule reading it
 * (complementary conditions can no longer both fail), and that a submitted value for a
 * field this state hides becomes detectable. A missing, oversized or malformed
 * declaration degrades to {@link #EMPTY}, which reproduces the historical fail-safe:
 * provider rules count as not satisfied, their target fields as hidden, and hidden
 * fields' required validation is skipped.
 */
public final class LogicStateDeclaration {

    private static final Logger log = LoggerFactory.getLogger(LogicStateDeclaration.class);

    /** Version this server understands; newer declarations degrade to EMPTY. */
    private static final int SUPPORTED_VERSION = 1;

    public static final LogicStateDeclaration EMPTY = new LogicStateDeclaration(Map.of());

    /**
     * sourceType → (reference → current value). A declared-absent reference is present in
     * the inner map with a null value; an undeclared one is not in the map at all.
     */
    private final Map<String, Map<String, String>> byProvider;

    private LogicStateDeclaration(Map<String, Map<String, String>> byProvider) {
        this.byProvider = byProvider;
    }

    /**
     * Parses the raw declaration field. Never throws: any input this server cannot
     * interpret — malformed JSON, unsupported version — yields {@link #EMPTY}.
     */
    public static LogicStateDeclaration parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return EMPTY;
        }

        try {
            JSONObject obj = new JSONObject(raw);
            if (obj.optInt("v", -1) != SUPPORTED_VERSION) {
                log.debug("[LogicStateDeclaration] Unsupported declaration version, ignoring");
                return EMPTY;
            }

            JSONObject providers = obj.optJSONObject("providers");
            if (providers == null) {
                return EMPTY;
            }

            Map<String, Map<String, String>> byProvider = new HashMap<>();
            for (String sourceType : providers.keySet()) {
                JSONObject refs = providers.optJSONObject(sourceType);
                if (refs == null) {
                    continue;
                }
                Map<String, String> values = new HashMap<>();
                for (String ref : refs.keySet()) {
                    Object value = refs.isNull(ref) ? null : refs.opt(ref);
                    if (value == null) {
                        values.put(ref, null);
                    } else if (value instanceof String stringValue) {
                        values.put(ref, stringValue);
                    }
                    // Non-string values are undeclared: the client only ever reads strings.
                }
                byProvider.put(sourceType, values);
            }

            return new LogicStateDeclaration(byProvider);
        } catch (RuntimeException e) {
            log.debug("[LogicStateDeclaration] Malformed declaration, ignoring: {}", e.getMessage());
            return EMPTY;
        }
    }

    /** Whether the declaration says anything about this reference. */
    public boolean isDeclared(String sourceType, String ref) {
        Map<String, String> refs = byProvider.get(sourceType);
        return refs != null && refs.containsKey(ref);
    }

    /**
     * The declared value of a reference, or null when it is declared absent. Only
     * meaningful when {@link #isDeclared} is true.
     */
    public String declaredValue(String sourceType, String ref) {
        Map<String, String> refs = byProvider.get(sourceType);
        return refs == null ? null : refs.get(ref);
    }
}
