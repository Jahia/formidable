package org.jahia.modules.formidable.engine.config.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

/**
 * What every entry of an administrator's list shares, once the list is one configuration file per entry — a
 * forward target, an options source: the entry's {@code id} setting, required, and the
 * raw properties DS handed over, from which the entry says whether the Felix console created it (no file behind
 * it) and gives its settings back for a file. The concrete component, its DS annotations and its own reading
 * live in the subclass: DS reads the lifecycle annotations of the component class itself.
 */
public abstract class FactoryEntry {

    /**
     * What an id may hold: letters, digits, dashes and underscores, as the console's form says. The id goes into the
     * file's name, and Jahia's configuration service finds an entry by the start of that name followed by a dot — an
     * id with a dot would collide with another's ({@code crm} and {@code crm.eu}), and would be overwritten by it.
     */
    private static final Pattern VALID_ID = Pattern.compile("[A-Za-z0-9_-]+");

    private static final Logger log = LoggerFactory.getLogger(FactoryEntry.class);

    private final AtomicReference<String> id = new AtomicReference<>("");
    private final AtomicReference<Map<String, Object>> properties = new AtomicReference<>(Map.of());

    /** Whether an id is one an entry may carry: not blank, letters, digits, dashes and underscores only. */
    public static boolean validId(String id) {
        return id != null && VALID_ID.matcher(id).matches();
    }

    /**
     * Keeps what DS handed over; the subclass calls it first from its own {@code @Activate}/{@code @Modified}. An id
     * that is not letters, digits, dashes and underscores is dropped with a warning: the entry then counts for nothing.
     */
    protected final void configured(String configuredId, Map<String, Object> raw) {
        properties.set(raw == null ? Map.of() : Map.copyOf(raw));
        String trimmed = configuredId == null ? "" : configuredId.trim();
        if (!trimmed.isEmpty() && !validId(trimmed)) {
            String pid = pid();
            log.warn("[FactoryEntry] The configuration {} sets the id '{}': an id is letters, digits, dashes and underscores only; "
                    + "the entry counts for nothing", pid, trimmed);
            trimmed = "";
        }
        id.set(trimmed);
    }

    /** The id the entry sets; empty when it sets none, or none valid — the entry then counts for nothing. */
    public final String id() {
        return id.get();
    }

    /** The configuration's PID. */
    public final String pid() {
        return String.valueOf(properties.get().get("service.pid"));
    }

    /**
     * Whether the Felix console created this configuration: no file behind it, and a PID the console generated
     * rather than a named one — the one kind that is turned into a file.
     */
    public final boolean createdWithoutFile(String factoryPid) {
        Map<String, Object> raw = properties.get();
        Object pid = raw.get("service.pid");
        return !raw.containsKey("felix.fileinstall.filename") && pid != null
                && !String.valueOf(pid).startsWith(factoryPid + "~");
    }

    /** The entry's settings, as text, in the order of {@code keys} — what its file lists. */
    public final Map<String, String> settings(List<String> keys) {
        Map<String, String> settings = new LinkedHashMap<>();
        Map<String, Object> raw = properties.get();
        keys.forEach(key -> {
            Object value = raw.get(key);
            if (value != null) {
                settings.put(key, String.valueOf(value));
            }
        });
        return settings;
    }
}
