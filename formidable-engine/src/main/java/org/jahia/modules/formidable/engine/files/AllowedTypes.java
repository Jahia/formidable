package org.jahia.modules.formidable.engine.files;

import org.apache.tika.Tika;
import org.apache.tika.mime.MediaType;
import org.apache.tika.mime.MediaTypeRegistry;
import org.apache.tika.mime.MimeTypes;
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
    private static final MediaTypeRegistry REGISTRY = MimeTypes.getDefaultMimeTypes().getMediaTypeRegistry();
    /** What Tika answers for a name it knows nothing of. */
    private static final String UNKNOWN = "application/octet-stream";
    private static final Pattern MIME_TYPE = Pattern.compile("[a-z0-9][a-z0-9!#$&^_.+-]*/(\\*|[a-z0-9][a-z0-9!#$&^_.+-]*)");
    private static final Pattern EXTENSION = Pattern.compile("[a-z0-9][a-z0-9_+-]*");

    private AllowedTypes() {
    }

    /**
     * A token as the MIME type or wildcard it stands for, lower-cased — an alias as its canonical type, the one Tika
     * detects a file as —; empty for a blank token, a malformed one or an extension Tika does not know.
     */
    public static Optional<String> resolve(String token) {
        String value = token == null ? "" : token.trim().toLowerCase(Locale.ROOT);
        if (value.contains("/")) {
            if (!MIME_TYPE.matcher(value).matches()) {
                return Optional.empty();
            }
            // An alias as the type Tika detects: audio/x-wav is audio/vnd.wave, text/xml is application/xml.
            return Optional.of(value.endsWith("/*") ? value : REGISTRY.normalize(MediaType.parse(value)).toString());
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
     * allows is dropped — the contributor's choice stays in the content, it is only no longer honoured — with a
     * warning naming the field when one is given: the field's view names its node on every render; the parser gives
     * none, the field name it knows coming from the request.
     *
     * @param accept  the field's tokens, as stored
     * @param allowed the administrator's types, already resolved
     * @param field   the field's node path, for the warning; null for no warning
     */
    public static Set<String> forField(Collection<String> accept, Set<String> allowed, String field) {
        Set<String> result = new LinkedHashSet<>();
        if (accept == null || accept.stream().allMatch(token -> token == null || token.isBlank())) {
            result.addAll(allowed);
            return Collections.unmodifiableSet(result);
        }
        accept.stream().filter(token -> token != null && !token.isBlank()).forEach(token -> {
            Set<String> kept = resolve(token).map(type -> kept(type, allowed)).orElse(Set.of());
            if (kept.isEmpty() && field != null) {
                log.warn("[AllowedTypes] Field '{}' accepts '{}', which the uploads configuration does not allow: ignored",
                        field, token);
            }
            result.addAll(kept);
        });
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
