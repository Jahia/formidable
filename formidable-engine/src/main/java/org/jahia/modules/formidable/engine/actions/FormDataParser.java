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
import java.util.ArrayList;
import java.util.Arrays;
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

    public static class ParseException extends Exception {
        private final int httpStatus;

        public ParseException(String message, int httpStatus) {
            super(message);
            this.httpStatus = httpStatus;
        }

        public int getHttpStatus() { return httpStatus; }
    }

    /**
     * Parses the multipart request.
     *
     * @param req               the HTTP request (stream not yet consumed by Jahia for file parts)
     * @param textParameters    text fields already parsed by Jahia (Map&lt;fieldName, values&gt;)
     * @param config            Formidable global config (size limits, fallback MIME allowlist)
     * @param fieldAcceptTypes  per-field accept values from JCR fmdb:inputFile nodes
     *                          (fieldName → accept string, e.g. "image/jpeg,image/png,.pdf")
     * @return list of validated FormFile entries
     * @throws ParseException if any security check fails
     */
    public static List<FormFile> parseFiles(
            HttpServletRequest req,
            Map<String, List<String>> textParameters,
            FormidableConfigService config,
            Map<String, String> fieldAcceptTypes
    ) throws ParseException {

        if (!ServletFileUpload.isMultipartContent(req)) {
            return List.of();
        }

        List<FormFile> files = new ArrayList<>();

        try {
            ServletFileUpload upload = new ServletFileUpload();
            upload.setFileSizeMax(config.getUploadMaxFileSizeBytes());
            upload.setSizeMax(config.getUploadMaxRequestSizeBytes());
            upload.setFileCountMax(config.getUploadMaxFileCount()); // CVE-2023-24998: limit part count

            FileItemIterator iterator = upload.getItemIterator(req);

            while (iterator.hasNext()) {
                FileItemStream item = iterator.next();
                if (item.isFormField()) continue;

                String fieldName    = item.getFieldName();
                String rawFileName  = item.getName();
                String sanitizedName = sanitizeFilename(rawFileName);

                // Read bytes
                ByteArrayOutputStream buf = new ByteArrayOutputStream();
                try (InputStream in = item.openStream()) {
                    in.transferTo(buf);
                }
                byte[] data = buf.toByteArray();

                // Detect real MIME type via Tika magic bytes
                String detectedMime = TIKA.detect(data, sanitizedName);

                // Resolve allowlist: field-level accept → fallback to global cfg
                Set<String> allowed = resolveAllowedTypes(fieldName, fieldAcceptTypes, config);

                if (!allowed.isEmpty() && !isMimeAllowed(detectedMime, sanitizedName, allowed)) {
                    log.warn("[FormDataParser] Rejected file '{}' field='{}': detected MIME '{}' not in allowlist {}",
                            sanitizedName, fieldName, detectedMime, allowed);
                    throw new ParseException(
                            "File '" + sanitizedName + "': type '" + detectedMime + "' is not allowed.", 415);
                }

                String storageName = UUID.randomUUID().toString() + extensionFrom(sanitizedName);

                log.debug("[FormDataParser] Accepted file: field={}, original={}, storage={}, mime={}, size={}",
                        fieldName, sanitizedName, storageName, detectedMime, data.length);

                files.add(new FormFile(fieldName, sanitizedName, storageName, detectedMime, data));
            }
        } catch (ParseException e) {
            throw e;
        } catch (org.apache.commons.fileupload.FileCountLimitExceededException e) {
            throw new ParseException("Too many file parts. Max allowed: " + config.getUploadMaxFileCount() + ".", 413);
        } catch (org.apache.commons.fileupload.FileUploadBase.FileSizeLimitExceededException e) {
            throw new ParseException("File too large. Max allowed: " + config.getUploadMaxFileSizeBytes() + " bytes.", 413);
        } catch (org.apache.commons.fileupload.FileUploadBase.SizeLimitExceededException e) {
            throw new ParseException("Request too large. Max allowed: " + config.getUploadMaxRequestSizeBytes() + " bytes.", 413);
        } catch (Exception e) {
            log.error("[FormDataParser] Failed to parse multipart request", e);
            throw new ParseException("Failed to parse uploaded files: " + e.getMessage(), 500);
        }

        return files;
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
    private static boolean isMimeAllowed(String detectedMime, String filename, Set<String> allowed) {
        if (allowed.contains(detectedMime)) return true;
        // Wildcard check: image/* matches image/jpeg
        String prefix = detectedMime.contains("/") ? detectedMime.substring(0, detectedMime.indexOf('/')) : "";
        if (!prefix.isEmpty() && allowed.contains(prefix + "/*")) return true;
        return false;
    }

    private static String extensionFrom(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(dot) : "";
    }
}

