package org.jahia.modules.formidable.engine.files;

import org.apache.tika.Tika;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The one reading of the file types a file field accepts, shared by the view that renders the field and the parser
 * that receives its files, so that the visitor is never offered a type the server refuses.
 * <p>
 * A token is a MIME type ({@code application/pdf}), a wildcard ({@code image/*}) or an extension ({@code docx} or
 * {@code .docx}), which Apache Tika's registry turns into its MIME type. The administrator's list is what every file
 * field may accept: a field that declares none accepts the whole list, a field that declares some keeps those the
 * list still allows. An empty result accepts no file at all — never every file.
 */
public final class AllowedTypes {

    private static final Logger log = LoggerFactory.getLogger(AllowedTypes.class);
    private static final Tika TIKA = new Tika();
    /** What Tika answers for a name it knows nothing of. */
    private static final String UNKNOWN = "application/octet-stream";
    private static final Pattern MIME_TYPE = Pattern.compile("[a-z0-9][a-z0-9!#$&^_.+-]*/(\\*|[a-z0-9][a-z0-9!#$&^_.+-]*)");
    private static final Pattern EXTENSION = Pattern.compile("[a-z0-9][a-z0-9_+-]*");

    private AllowedTypes() {
    }

    /**
     * A token as the MIME type or wildcard it stands for, lower-cased; empty for a blank token, a malformed one or an
     * extension Tika does not know.
     */
    public static Optional<String> resolve(String token) {
        String value = token == null ? "" : token.trim().toLowerCase(Locale.ROOT);
        if (value.contains("/")) {
            return MIME_TYPE.matcher(value).matches() ? Optional.of(value) : Optional.empty();
        }
        String extension = value.startsWith(".") ? value.substring(1) : value;
        if (!EXTENSION.matcher(extension).matches()) {
            return Optional.empty();
        }
        String detected = TIKA.detect("file." + extension);
        return detected == null || UNKNOWN.equals(detected) ? Optional.empty() : Optional.of(detected);
    }

    /**
     * What a field accepts: its own tokens restricted to the allowed types, or every allowed type when it declares
     * none. A wildcard the list does not allow as such keeps the allowed types it covers. A token the list no longer
     * allows is dropped with a warning naming the field — the contributor's choice stays in the content, it is only
     * no longer honoured.
     *
     * @param accept  the field's tokens, as stored
     * @param allowed the administrator's types, already resolved
     * @param field   the field, for the warning
     */
    public static Set<String> forField(Collection<String> accept, Set<String> allowed, String field) {
        Set<String> result = new LinkedHashSet<>();
        if (accept == null || accept.stream().allMatch(token -> token == null || token.isBlank())) {
            result.addAll(allowed);
            return Collections.unmodifiableSet(result);
        }
        for (String token : accept) {
            if (token == null || token.isBlank()) {
                continue;
            }
            Optional<String> resolved = resolve(token);
            if (resolved.isEmpty()) {
                log.warn("[AllowedTypes] Field '{}' accepts '{}', which is not a file type: ignored", field, token);
                continue;
            }
            Set<String> kept = kept(resolved.get(), allowed);
            if (kept.isEmpty()) {
                log.warn("[AllowedTypes] Field '{}' accepts '{}', which the uploads configuration no longer allows: ignored",
                        field, token);
            }
            result.addAll(kept);
        }
        return Collections.unmodifiableSet(result);
    }

    /** Of one resolved token, what the allowed types let through: itself, or the allowed types a wildcard covers. */
    private static Set<String> kept(String type, Set<String> allowed) {
        if (permits(type, allowed)) {
            return Set.of(type);
        }
        if (!type.endsWith("/*")) {
            return Set.of();
        }
        String prefix = type.substring(0, type.length() - 1);
        Set<String> covered = new LinkedHashSet<>();
        allowed.stream().filter(each -> each.startsWith(prefix) && !each.endsWith("/*")).sorted().forEach(covered::add);
        return covered;
    }

    /**
     * Whether the allowed types let a MIME type through: listed as is, or covered by a wildcard of its top-level
     * type. No allowed type lets nothing through. A wildcard is let through only by itself.
     */
    public static boolean permits(String type, Set<String> allowed) {
        if (type == null) {
            return false;
        }
        int slash = type.indexOf('/');
        return allowed.contains(type) || slash > 0 && allowed.contains(type.substring(0, slash) + "/*");
    }
}
