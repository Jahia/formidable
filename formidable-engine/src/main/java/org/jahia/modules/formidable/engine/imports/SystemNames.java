package org.jahia.modules.formidable.engine.imports;

import org.jahia.utils.Patterns;

import java.text.Normalizer;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;

/**
 * The system names of the fields an import creates (docs/architecture/forms-import.md, "The system
 * names"): generated from the label as the Content Editor generates one from a title, unique within the
 * form, and never one of the request parameters the submission pipeline reads for itself ({@code fid},
 * {@code lang}), which a field of that name would shadow.
 * <p>
 * {@link #generate} is {@code JCRContentUtils.generateNodeName} step for step, on Jahia's own
 * {@link Patterns}, rather than a call to it: that class initialises the Jahia runtime, which the
 * engine's unit tests do not have (the same reason {@code FormDataParser}'s allow-list test keeps away
 * from it), and the name rule is exactly what the tests must pin.
 */
public final class SystemNames {

    /** The length the Content Editor allows a generated system name. */
    public static final int MAX_LENGTH = 32;
    /** The request parameters of the submission pipeline that a field name must not take. */
    static final Set<String> RESERVED = Set.of("fid", "lang");

    private final Set<String> taken = new HashSet<>(RESERVED);

    /**
     * The name for a label, {@code your-first-name} for "Your First name", or the fallback when the
     * label gives nothing (empty, or made of characters the rule drops); then made unique: a name already
     * taken in this form gets {@code -1}, {@code -2}… as {@code findAvailableNodeName} does.
     */
    public String of(String label, String fallback) {
        String candidate = generate(label);
        if (candidate.isEmpty()) {
            candidate = fallback;
        }
        return reserve(candidate);
    }

    /** Registers a name chosen by the caller, so that a generated one never takes it. */
    public String reserve(String name) {
        String free = name;
        int suffix = 1;
        while (taken.contains(free)) {
            free = name + "-" + suffix++;
        }
        taken.add(free);
        return free;
    }

    /** The generated name alone, empty when the label yields none; no uniqueness. */
    public static String generate(String label) {
        if (label == null || label.isBlank()) {
            return "";
        }
        String name = Normalizer.normalize(label.trim().toLowerCase(), Normalizer.Form.NFKD);
        name = Patterns.ACCENTS.matcher(name).replaceAll("");
        name = replaceMappedChars(name);
        name = Patterns.NON_ALLOWED_CHARS.matcher(name).replaceAll("-");
        name = Patterns.CONSECUTIVE_DASHES.matcher(name).replaceAll("-");
        name = Patterns.HEADING_DASH.matcher(name).replaceAll("");
        if (name.length() > MAX_LENGTH) {
            name = name.substring(0, MAX_LENGTH);
        }
        return Patterns.TRAILING_DASH.matcher(name).replaceAll("");
    }

    /** The characters Jahia's charmap spells out ({@code ß} to {@code ss}…); the others become a dash. */
    private static String replaceMappedChars(String name) {
        Matcher matcher = Patterns.NON_ALLOWED_CHARS.matcher(name);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String mapped = Patterns.CHARMAP.getProperty(matcher.group(0));
            matcher.appendReplacement(out, Matcher.quoteReplacement(mapped != null ? mapped : "-"));
        }
        matcher.appendTail(out);
        return out.toString();
    }
}
