package org.jahia.modules.formidable.engine.files;

import org.apache.tika.mime.MimeType;
import org.apache.tika.mime.MimeTypeException;
import org.apache.tika.mime.MimeTypes;
import org.jahia.modules.formidable.engine.config.uploads.UploadsConfigService;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

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

    private Supplier<Set<String>> allowedTypes = Set::of;

    public FileTypeService() {
    }

    /** A service over a fixed list of allowed types — the tests' seam. */
    FileTypeService(Supplier<Set<String>> allowedTypes) {
        this.allowedTypes = allowedTypes;
    }

    @Reference
    public void setConfigService(UploadsConfigService config) {
        this.allowedTypes = config::getUploadAllowedMimeTypes;
    }

    /**
     * The extensions shown to a visitor for a token: a MIME type's preferred one ({@code .docx}); for a wildcard, the
     * preferred one of each allowed type it covers ({@code image/*} → {@code .gif, .jpg, .png, .webp}); an extension
     * as it is. Empty for a type Tika does not know.
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
     * subtype, followed by the preferred extension — "PDF (.pdf)", "DOCX (.docx)", "WEBP (.webp)". A module's
     * resource bundle may still word it better, and translate it.
     */
    public String label(String mimeType) {
        String type = normalized(mimeType);
        Optional<MimeType> known = known(type);
        String extension = known.map(MimeType::getExtension).orElse("");
        String name = known.map(MimeType::getAcronym).filter(acronym -> !acronym.isBlank())
                .orElse(extension.isEmpty() ? subtypeOf(type) : extension.substring(1).toUpperCase(Locale.ROOT));
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
                    if (!mime.getExtension().isEmpty()) {
                        extensions.add(mime.getExtension());
                    }
                } else {
                    mime.getExtensions().forEach(extension -> extensions.add(extension.toLowerCase(Locale.ROOT)));
                }
            });
        }
        return extensions.toArray(String[]::new);
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

    private static String subtypeOf(String type) {
        int slash = type.indexOf('/');
        return slash < 0 ? type : type.substring(slash + 1).toUpperCase(Locale.ROOT);
    }
}
