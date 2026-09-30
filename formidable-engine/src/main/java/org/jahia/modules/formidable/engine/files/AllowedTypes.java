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
import java.util.TreeSet;
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
    /** The one token that lets any file through: the administrator's "any file", which holds whatever Tika adds. */
    public static final String ANY_FILE = "*/*";
    /** The roots of Tika's hierarchy: every text type descends from the first, every type from the second. */
    private static final Set<String> ROOTS = Set.of("text/plain", UNKNOWN);
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
        if (ANY_FILE.equals(value)) {
            return Optional.of(ANY_FILE);
        }
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
            Optional<String> type = resolve(token);
            Set<String> kept = type.map(resolved -> kept(resolved, allowed)).orElse(Set.of());
            if (kept.isEmpty() && field != null) {
                log.warn("[AllowedTypes] Field '{}' accepts '{}', {}: ignored", field, token,
                        type.isEmpty() ? "which is not a file type" : "which the uploads configuration does not allow");
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
     * Whether the allowed types let a MIME type through: listed as is, covered by a wildcard of its top-level type, or
     * by {@value #ANY_FILE}. No allowed type lets nothing through. A wildcard is let through only by itself or
     * {@value #ANY_FILE}.
     */
    public static boolean permits(String type, Set<String> allowed) {
        if (type == null) {
            return false;
        }
        int slash = type.indexOf('/');
        return allowed.contains(ANY_FILE) || allowed.contains(type)
                || slash > 0 && allowed.contains(type.substring(0, slash) + "/*");
    }

    /**
     * What a contributor may pick for a file field: the allowed types, and — where they hold {@value #ANY_FILE},
     * which is never offered, a field without types already accepting anything — a wildcard for each top-level type
     * of Tika's registry (application, audio, image, text, video… ten in Tika 3.3.2), so that a field can still be
     * narrowed. Sorted.
     */
    public static Set<String> choices(Set<String> allowed) {
        Set<String> choices = new TreeSet<>(allowed);
        if (choices.remove(ANY_FILE)) {
            REGISTRY.getTypes().forEach(type -> choices.add(type.getType() + "/*"));
        }
        return Collections.unmodifiableSet(choices);
    }

    /**
     * Whether the allowed types let an uploaded file through: its detected type is permitted ({@link #permits}), or it
     * is what Tika's registry calls a specialization of the type the file's name stands for, when that type is listed
     * as is. The content names the codec where the name names the container: an Ogg video, {@code .ogv}, is detected
     * {@code video/theora}, which Tika declares a kind of {@code video/ogg}; an Opus track in {@code .oga} is
     * {@code audio/opus}, a kind of {@code audio/ogg}. Three limits keep the hierarchy from widening the list, since
     * "a kind of" there means "readable as": the name's type must be listed itself, not through a wildcard; the two
     * types must share their top-level type — an SVG named {@code .xml} is {@code image/svg+xml}, a kind of
     * {@code application/xml}, and stays refused; and the name's type must not be a root of the hierarchy, so that
     * allowing {@code txt} never lets an HTML page named {@code .txt} through.
     *
     * @param detected the type Tika detected from the file's content and name
     * @param fileName the file's name, whose extension says what the visitor meant to send
     */
    public static boolean permitsFile(String detected, String fileName, Set<String> allowed) {
        if (permits(detected, allowed)) {
            return true;
        }
        if (detected == null || fileName == null) {
            return false;
        }
        String named = TIKA.detect(fileName);
        if (named == null || ROOTS.contains(named) || !allowed.contains(named) || !sameTopLevel(detected, named)) {
            return false;
        }
        MediaType type = MediaType.parse(detected);
        return type != null && REGISTRY.isSpecializationOf(type, MediaType.parse(named));
    }

    private static boolean sameTopLevel(String one, String other) {
        int slash = one.indexOf('/');
        return slash > 0 && other.startsWith(one.substring(0, slash + 1));
    }
}
