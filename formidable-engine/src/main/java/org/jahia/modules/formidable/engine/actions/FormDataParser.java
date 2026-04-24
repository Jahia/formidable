package org.jahia.modules.formidable.engine.actions;

import org.apache.commons.fileupload.FileItemIterator;
import org.apache.commons.fileupload.FileItemStream;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.apache.commons.fileupload.util.Streams;
import org.apache.tika.Tika;
import org.jahia.modules.formidable.engine.config.FormidableConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServletRequest;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Parses multipart/form-data from an HttpServletRequest, applying security controls:
 *
 *   1. Content-Type validation (must be multipart/form-data)
 *   2. Request and per-file size limits
 *   3. Filename sanitization (path traversal, XSS chars stripped)
 *   4. MIME type detection via Apache Tika (magic bytes)
 *   5. MIME type allowlist check — per-field 'accept' takes priority over global cfg fallback
 */
public class FormDataParser {

    private static final Logger log = LoggerFactory.getLogger(FormDataParser.class);

    private static final Tika TIKA = new Tika();

    /**
     * Request attribute key under which the servlet stores pre-parsed files.
     * ForwardFormAction reads this attribute to avoid re-parsing a consumed stream.
     */
    public static final String PARSED_FILES_ATTR = "formidable.parsedFiles";

    /**
     * Parsed representation of a single uploaded file.
     *
     * @param fieldName    the form field name
     * @param originalName sanitized original filename (for display only)
     * @param storageName  UUID-based name to use for storage (never the client name)
     * @param mimeType     detected MIME type (via Tika magic bytes)
     * @param data         file bytes
     */
    public record FormFile(
            String fieldName,
            String originalName,
            String storageName,
            String mimeType,
            byte[] data
    ) {}

    /**
     * Result of a full multipart parse: both text parameters and uploaded files.
     */
    public record ParseResult(
            Map<String, List<String>> parameters,
            List<FormFile> files
    ) {}

    public static class ParseException extends Exception {
        private final int httpStatus;

        public ParseException(String message, int httpStatus) {
            super(message);
            this.httpStatus = httpStatus;
        }

        public int getHttpStatus() { return httpStatus; }
    }


    /**
     * Parses the full multipart request: both text fields and file parts.
     * Use this from a servlet that is the first consumer of the request stream.
     *
     * @param req               the HTTP request (stream not yet consumed)
     * @param config            Formidable global config (size limits, fallback MIME allowlist)
     * @param fieldAcceptTypes  per-field accept values (fieldName → accept string); may be empty
     * @param allowedFieldNames whitelist of field names declared on the form node;
     *                          fields absent from this set are skipped without reading their content.
     *                          Pass an empty set to disable whitelisting (not recommended).
     * @return ParseResult containing all text parameters and validated file entries
     * @throws ParseException if any security check fails
     */
    public static ParseResult parseAll(
            HttpServletRequest req,
            FormidableConfigService config,
            Map<String, String> fieldAcceptTypes,
            Set<String> allowedFieldNames
    ) throws ParseException {

        if (!ServletFileUpload.isMultipartContent(req)) {
            return new ParseResult(new HashMap<>(), List.of());
        }

        Map<String, List<String>> parameters = new HashMap<>();
        List<FormFile> files = new ArrayList<>();

        try {
            ServletFileUpload upload = new ServletFileUpload();
            upload.setFileSizeMax(config.getUploadMaxFileSizeBytes());
            upload.setSizeMax(config.getUploadMaxRequestSizeBytes());
            // setFileCountMax() requires commons-fileupload >= 1.5 (CVE-2023-24998).
            // Enable once the Jahia platform ships 1.5+.
            FileItemIterator iterator = upload.getItemIterator(req);
            while (iterator.hasNext()) {
                FileItemStream item = iterator.next();
                // Whitelist check: fields not declared on the form node are discarded without
                // reading their content. The iterator advances past them automatically.
                if (!allowedFieldNames.isEmpty() && !allowedFieldNames.contains(item.getFieldName())) {
                    log.debug("[FormDataParser] Skipping undeclared field: {}", item.getFieldName());
                    continue;
                }
                if (item.isFormField()) {
                    String value = Streams.asString(item.openStream(), StandardCharsets.UTF_8.name());
                    parameters.computeIfAbsent(item.getFieldName(), k -> new ArrayList<>()).add(value);
                    continue;
                }
                FormFile file = parseFilePart(item, fieldAcceptTypes, config);
                files.add(file);
            }
        } catch (ParseException e) {
            throw e;
        } catch (org.apache.commons.fileupload.FileUploadBase.FileSizeLimitExceededException e) {
            throw new ParseException("File too large. Max allowed: " + config.getUploadMaxFileSizeBytes() + " bytes.", 413);
        } catch (org.apache.commons.fileupload.FileUploadBase.SizeLimitExceededException e) {
            throw new ParseException("Request too large. Max allowed: " + config.getUploadMaxRequestSizeBytes() + " bytes.", 413);
        } catch (Exception e) {
            log.error("[FormDataParser] Failed to parse multipart request", e);
            throw new ParseException("Failed to parse uploaded files: " + e.getMessage(), 500);
        }

        return new ParseResult(parameters, files);
    }


    private static FormFile parseFilePart(
            FileItemStream item,
            Map<String, String> fieldAcceptTypes,
            FormidableConfigService config
    ) throws ParseException {
        String fieldName     = item.getFieldName();
        String sanitizedName = sanitizeFilename(item.getName());

        byte[] data;
        try {
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            try (InputStream in = item.openStream()) {
                in.transferTo(buf);
            }
            data = buf.toByteArray();
        } catch (Exception e) {
            log.error("[FormDataParser] Failed to read file part for field '{}'", fieldName, e);
            throw new ParseException("Failed to read uploaded file: " + e.getMessage(), 500);
        }

        String detectedMime = TIKA.detect(data, sanitizedName);
        Set<String> allowed = resolveAllowedTypes(fieldName, fieldAcceptTypes, config);

        if (!allowed.isEmpty() && !isMimeAllowed(detectedMime, allowed)) {
            log.warn("[FormDataParser] Rejected file '{}' field='{}': detected MIME '{}' not in allowlist {}",
                    sanitizedName, fieldName, detectedMime, allowed);
            throw new ParseException(
                    "File '" + sanitizedName + "': type '" + detectedMime + "' is not allowed.", 415);
        }

        String storageName = UUID.randomUUID() + extensionFrom(sanitizedName);
        log.debug("[FormDataParser] Accepted file: field={}, original={}, storage={}, mime={}, size={}",
                fieldName, sanitizedName, storageName, detectedMime, data.length);
        return new FormFile(fieldName, sanitizedName, storageName, detectedMime, data);
    }

    /**
     * Strips path traversal sequences and XSS characters from the filename.
     * Returns "upload" if the result is blank.
     */
    static String sanitizeFilename(String raw) {
        if (raw == null || raw.isBlank()) return "upload";
        // Strip path separators (path traversal)
        String name = raw.replaceAll(".*[/\\\\]", "");
        // Strip XSS chars
        name = name.replaceAll("[<>\"']", "");
        name = name.trim();
        return name.isEmpty() ? "upload" : name;
    }

    /**
     * Resolves the effective allowlist for a field.
     * Field-level 'accept' takes priority over the global cfg fallback.
     */
    private static Set<String> resolveAllowedTypes(
            String fieldName,
            Map<String, String> fieldAcceptTypes,
            FormidableConfigService config
    ) {
        String accept = fieldAcceptTypes != null ? fieldAcceptTypes.get(fieldName) : null;
        if (accept != null && !accept.isBlank()) {
            return parseAccept(accept);
        }
        return config.getUploadAllowedMimeTypes();
    }

    /**
     * Parses an HTML 'accept' attribute value into a set of MIME types.
     * Extensions like ".pdf" are normalised to their MIME equivalent via Tika.
     */
    private static Set<String> parseAccept(String accept) {
        return Arrays.stream(accept.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(token -> {
                    if (token.startsWith(".")) {
                        // Extension → detect MIME via Tika
                        String detected = TIKA.detect("file" + token);
                        return detected != null ? detected : token;
                    }
                    return token;
                })
                .collect(Collectors.toSet());
    }

    /**
     * Checks whether the detected MIME type is permitted by the allowlist.
     * Supports wildcards (e.g. "image/*").
     */
    private static boolean isMimeAllowed(String detectedMime, Set<String> allowed) {
        if (allowed.contains(detectedMime)) return true;
        String prefix = detectedMime.contains("/") ? detectedMime.substring(0, detectedMime.indexOf('/')) : "";
        return !prefix.isEmpty() && allowed.contains(prefix + "/*");
    }

    private static String extensionFrom(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(dot) : "";
    }
}

