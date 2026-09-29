package org.jahia.modules.formidable.engine.files;

import org.apache.tika.mime.MimeType;
import org.apache.tika.mime.MimeTypeException;
import org.apache.tika.mime.MimeTypes;
import org.jahia.modules.formidable.engine.config.uploads.UploadsConfigService;
import org.jahia.services.content.JCRNodeWrapper;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import javax.jcr.RepositoryException;
import javax.jcr.Value;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

/**
 * What a file type is, from the one table that knows: Apache Tika's registry of MIME types, the same Tika the
 * engine detects an uploaded file's real type with. For a token of a file field's {@code accept} — a MIME type,
 * a wildcard such as {@code image/*}, or an extension — it gives the extensions a visitor sees and the ones a
 * file is recognised by, and for a MIME type a readable default label. Nothing is kept in a table of the module:
 * a type the administrator adds to the allowed ones needs no code and no bundle entry.
 * <p>
 * Reached by the file field's view in formidable-elements through OSGi, by this class name.
 */
@Component(service = FileTypeService.class)
public class FileTypeService {

    private static final MimeTypes REGISTRY = MimeTypes.getDefaultMimeTypes();
    private static final String ACCEPT = "accept";

    private Supplier<Set<String>> allowedTypes = Set::of;

    public FileTypeService() {
    }

    /** A service over a fixed list of allowed types — the tests' seam. */
    FileTypeService(Supplier<Set<String>> allowedTypes) {
        this.allowedTypes = allowedTypes;
    }

    @Reference
    public void setConfigService(UploadsConfigService config) {
        this.allowedTypes = config::getUploadAllowedTypes;
    }

    /**
     * What a file field accepts, as MIME types and wildcards: its own {@code accept} values restricted to the allowed
     * types, or all of them when it declares none ({@link AllowedTypes#forField}). Empty: the field accepts no file.
     * The field's view hands its node over: no array crosses from JavaScript.
     */
    public String[] allowedFor(JCRNodeWrapper field) throws RepositoryException {
        List<String> accept = new ArrayList<>();
        if (field.hasProperty(ACCEPT)) {
            for (Value value : field.getProperty(ACCEPT).getValues()) {
                accept.add(value.getString());
            }
        }
        return allowedFor(accept, field.getPath());
    }

    /** What a field accepts from its accept values; the field is named in the warning for a value no longer allowed. */
    String[] allowedFor(List<String> accept, String field) {
        return AllowedTypes.forField(accept, allowedTypes.get(), field).toArray(String[]::new);
    }

    /**
     * The extensions shown to a visitor for a token: a MIME type's own one ({@link #shownExtension}, {@code .docx});
     * for a wildcard, the one of each allowed type it covers ({@code image/*} → {@code .gif, .jpg, .png, .webp}); an
     * extension as it is. Empty for a type Tika does not know.
     */
    public String[] shownExtensions(String token) {
        return extensions(token, true);
    }

    /**
     * The extensions a file is recognised by for a token, when the browser gives no type: every extension of a MIME
     * type ({@code .jpg, .jpeg, .jpe…}), of every allowed type a wildcard covers, or the extension itself.
     */
    public String[] recognisedExtensions(String token) {
        return extensions(token, false);
    }

    /**
     * The default label of a MIME type in the editor: its acronym, else its preferred extension in capitals, else its
     * type itself, followed by the extension shown — "PDF (.pdf)", "DOCX (.docx)", "MP3 (.mp3)", "image/*". A module's
     * resource bundle may still word it better, and translate it.
     */
    public String label(String mimeType) {
        String type = normalized(mimeType);
        Optional<MimeType> known = known(type);
        String extension = known.map(FileTypeService::shownExtension).orElse("");
        String name = known.map(MimeType::getAcronym).filter(acronym -> !acronym.isBlank())
                .orElse(extension.isEmpty() ? type : extension.substring(1).toUpperCase(Locale.ROOT));
        return extension.isEmpty() ? name : name + " (" + extension + ")";
    }

    private String[] extensions(String token, boolean preferredOnly) {
        String type = normalized(token);
        if (type.isEmpty()) {
            return new String[0];
        }
        if (type.startsWith(".")) {
            return new String[] {type};
        }
        Set<String> extensions = new LinkedHashSet<>();
        List<String> types = type.endsWith("/*")
                ? allowedTypes.get().stream().map(FileTypeService::normalized)
                        .filter(allowed -> allowed.startsWith(type.substring(0, type.length() - 1))).sorted().toList()
                : List.of(type);
        for (String each : types) {
            known(each).ifPresent(mime -> {
                if (preferredOnly) {
                    if (!shownExtension(mime).isEmpty()) {
                        extensions.add(shownExtension(mime));
                    }
                } else {
                    mime.getExtensions().forEach(extension -> extensions.add(extension.toLowerCase(Locale.ROOT)));
                }
            });
        }
        return extensions.toArray(String[]::new);
    }

    /**
     * The extension a type is shown with: Tika's preferred one, unless it is no abbreviation of the type's acronym and
     * the acronym is an extension of the type too — {@code audio/mpeg} prefers {@code .mpga}, known as MP3, which is
     * {@code .mp3}; {@code image/jpeg} keeps {@code .jpg}, an abbreviation of JPEG. Seven types of Tika 3.3.2's registry
     * have an acronym among their extensions other than the preferred one.
     */
    static String shownExtension(MimeType mime) {
        String preferred = mime.getExtension();
        String acronym = mime.getAcronym() == null ? "" : mime.getAcronym().toLowerCase(Locale.ROOT);
        if (acronym.isEmpty() || !mime.getExtensions().contains("." + acronym) || abbreviates(preferred, acronym)) {
            return preferred;
        }
        return "." + acronym;
    }

    /** Whether an extension's letters all appear, in order, in the acronym: {@code .jpg} in JPEG, {@code .mid} in MIDI. */
    private static boolean abbreviates(String extension, String acronym) {
        int next = 0;
        for (char letter : extension.substring(Math.min(1, extension.length())).toCharArray()) {
            next = acronym.indexOf(letter, next) + 1;
            if (next == 0) {
                return false;
            }
        }
        return !extension.isEmpty();
    }

    private static Optional<MimeType> known(String type) {
        try {
            MimeType mime = REGISTRY.getRegisteredMimeType(type);
            return Optional.ofNullable(mime);
        } catch (MimeTypeException e) {
            return Optional.empty();
        }
    }

    private static String normalized(String token) {
        return token == null ? "" : token.trim().toLowerCase(Locale.ROOT);
    }
}
