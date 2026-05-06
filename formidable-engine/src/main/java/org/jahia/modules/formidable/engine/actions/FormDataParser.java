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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Parses multipart/form-data from an HttpServletRequest, applying security controls:
 *
 *   1. Content-Type validation (must be multipart/form-data)
 *   2. Request and per-file size limits; file count limit (from config)
 *   3. Field whitelist — undeclared fields skipped before any read
 *   4. Text field strip — HTML tags removed from free-text fields
 *   5. Text field validation — choice match, format (email/date/color), constraints (required/min/max/length/pattern)
 *   6. Filename sanitization (path traversal, control characters and XSS chars stripped)
 *   7. MIME type detection via Apache Tika (magic bytes)
 *   8. MIME type allowlist check — per-field 'accept' takes priority over global cfg fallback
 */
public class FormDataParser {

    private static final Logger log = LoggerFactory.getLogger(FormDataParser.class);

    private static final Tika TIKA = new Tika();

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,}$");

    private static final Pattern COLOR_PATTERN = Pattern.compile("^#[0-9a-fA-F]{6}$");

    // Matches valid HTML tags (opening or closing). Used to strip user-submitted HTML
    // from free-text fields before storing the value.
    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("</?[a-zA-Z][^>]*>");

    private static final Set<String> FREE_TEXT_TYPES = Set.of(
            "fmdb:inputText", "fmdb:textarea", "fmdb:inputHidden");

    private static final DateTimeFormatter DATETIME_LOCAL_FMT =
            new DateTimeFormatterBuilder()
                    .append(DateTimeFormatter.ISO_LOCAL_DATE)
                    .appendLiteral('T')
                    .appendPattern("HH:mm[:ss[.SSS]]")
                    .toFormatter();

    /**
     * Request attribute key under which the servlet stores pre-parsed files.
     * ForwardSubmissionFormAction reads this attribute to avoid re-parsing a consumed stream.
     */
    public static final String PARSED_FILES_ATTR = "formidable.parsedFiles";

    /**
     * Server-side constraints for a single form field, collected from JCR before parsing.
     * Values of -1 (numeric) or null (string) mean "not set — skip this check".
     *
     * @param required   field must be present and non-blank
     * @param minLength  minimum character count (-1 = unconstrained)
     * @param maxLength  maximum character count (-1 = unconstrained)
     * @param pattern    full-match regex (like HTML pattern attribute); null = unconstrained
     * @param minDate    ISO-8601 lower bound for date/datetime-local fields; null = unconstrained
     * @param maxDate    ISO-8601 upper bound for date/datetime-local fields; null = unconstrained
     */
    public record FieldConstraints(
            boolean required,
            long minLength,
            long maxLength,
            String pattern,
            String minDate,
            String maxDate
    ) {}

    /**
     * All per-field JCR-derived metadata needed for parsing and validation.
     *
     * @param allowedNames      field names declared on the form; empty set disables whitelisting
     * @param fieldTypes        fieldName → JCR primary node type (e.g. "fmdb:inputEmail")
     * @param allowedChoices    fieldName → set of allowed submitted values (choice/radio/select fields)
     * @param fieldAcceptTypes  fieldName → pre-resolved MIME type allowlist (file fields only)
     * @param fieldConstraints  fieldName → server-side constraints collected from JCR
     */
    public record FieldMetadata(
            Set<String> allowedNames,
            Map<String, String> fieldTypes,
            Map<String, Set<String>> allowedChoices,
            Map<String, Set<String>> fieldAcceptTypes,
            Map<String, FieldConstraints> fieldConstraints
    ) {}

    /**
     * Parsed representation of a single uploaded file.
     *
     * @param fieldName    the form field name
     * @param originalName sanitized original filename
     * @param mimeType     detected MIME type (via Tika magic bytes)
     * @param data         file bytes
     */
    public record FormFile(
            String fieldName,
            String originalName,
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
        private final boolean validation;

        public ParseException(String message, int httpStatus) {
            this(message, httpStatus, false);
        }

        public ParseException(String message, int httpStatus, boolean validation) {
            super(message);
            this.httpStatus = httpStatus;
            this.validation = validation;
        }

        public int getHttpStatus() { return httpStatus; }

        /** Returns true when the failure is a field-level validation error (FMDB-010). */
        public boolean isValidation() { return validation; }
    }


    /**
     * Parses the full multipart request: both text fields and file parts.
     * Use this from a servlet that is the first consumer of the request stream.
     *
     * @param req           the HTTP request (stream not yet consumed)
     * @param config        Formidable global config (size limits, fallback MIME allowlist)
     * @param fieldMetadata per-field JCR metadata (whitelist, types, choices, accept types, constraints)
     * @return ParseResult containing all text parameters and validated file entries
     * @throws ParseException if any security or validation check fails
     */
    public static ParseResult parseAll(
            HttpServletRequest req,
            FormidableConfigService config,
            FieldMetadata fieldMetadata
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
            upload.setFileCountMax(config.getUploadMaxFileCount());
            FileItemIterator iterator = upload.getItemIterator(req);
            while (iterator.hasNext()) {
                FileItemStream item = iterator.next();
                // Whitelist check: fields not declared on the form node are discarded without
                // reading their content. The iterator advances past them automatically.
                if (!fieldMetadata.allowedNames().isEmpty()
                        && !fieldMetadata.allowedNames().contains(item.getFieldName())) {
                    log.debug("[FormDataParser] Skipping undeclared field: {}", item.getFieldName());
                    continue;
                }
                if (item.isFormField()) {
                    String raw = Streams.asString(item.openStream(), StandardCharsets.UTF_8.name());
                    String value = stripHtmlIfFreeText(raw, fieldMetadata.fieldTypes().get(item.getFieldName()));
                    validateTextField(item.getFieldName(), value, fieldMetadata);
                    parameters.computeIfAbsent(item.getFieldName(), k -> new ArrayList<>()).add(value);
                    continue;
                }
                FormFile file = parseFilePart(item, fieldMetadata.fieldAcceptTypes().get(item.getFieldName()), config);
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


    /**
     * Strips HTML tags from free-text field values to prevent XSS content from being stored
     * or forwarded. Only applied to field types that accept arbitrary user text.
     * Choice, date, email and other typed fields are left untouched: their validators
     * already reject unexpected content.
     */
    private static String stripHtmlIfFreeText(String value, String fieldType) {
        if (value == null) return "";
        if (FREE_TEXT_TYPES.contains(fieldType)) {
            String stripped = HTML_TAG_PATTERN.matcher(value).replaceAll("");
            if (!stripped.equals(value)) {
                log.warn("[FormDataParser] HTML tags stripped from free-text field (type={})", fieldType);
            }
            return stripped;
        }
        return value;
    }

    private static void validateTextField(String fieldName, String value, FieldMetadata meta)
            throws ParseException {
        if (value == null || value.isEmpty()) return;

        // Choice validation: submitted value must be in the declared set
        Set<String> choices = meta.allowedChoices().get(fieldName);
        if (choices != null && !choices.isEmpty() && !choices.contains(value)) {
            log.warn("[FormDataParser] Rejected field '{}': value not in allowed choices", fieldName);
            throw new ParseException(
                    "Field '" + fieldName + "': submitted value is not an allowed choice.", 400, true);
        }

        // Format validation by JCR node type
        String type = meta.fieldTypes().get(fieldName);
        if (type != null) {
            switch (type) {
                case "fmdb:inputEmail"         -> validateEmail(fieldName, value);
                case "fmdb:inputDate"          -> validateDate(fieldName, value);
                case "fmdb:inputDatetimeLocal" -> validateDatetimeLocal(fieldName, value);
                case "fmdb:inputColor"         -> validateColor(fieldName, value);
            }
        }

        // Constraint validation (minLength, maxLength, pattern, min/max date)
        FieldConstraints c = meta.fieldConstraints().get(fieldName);
        if (c != null) {
            validateConstraints(fieldName, value, c, type);
        }
    }

    private static void validateConstraints(String fieldName, String value, FieldConstraints c, String fieldType)
            throws ParseException {
        // required is enforced post-parse at pipeline level (handles absent fields too)

        if (c.minLength() >= 0 && value.length() < c.minLength()) {
            log.warn("[FormDataParser] Rejected field '{}': too short ({} < {})", fieldName, value.length(), c.minLength());
            throw new ParseException(
                    "Field '" + fieldName + "': value too short (min " + c.minLength() + " chars).", 400, true);
        }
        if (c.maxLength() >= 0 && value.length() > c.maxLength()) {
            log.warn("[FormDataParser] Rejected field '{}': too long ({} > {})", fieldName, value.length(), c.maxLength());
            throw new ParseException(
                    "Field '" + fieldName + "': value too long (max " + c.maxLength() + " chars).", 400, true);
        }
        if (c.pattern() != null && !c.pattern().isBlank()) {
            try {
                // String.matches() implicitly anchors (^...$), matching HTML pattern attribute behaviour
                if (!value.matches(c.pattern())) {
                    log.warn("[FormDataParser] Rejected field '{}': value does not match pattern", fieldName);
                    throw new ParseException(
                            "Field '" + fieldName + "': value does not match required format.", 400, true);
                }
            } catch (PatternSyntaxException e) {
                log.warn("[FormDataParser] Invalid pattern on field '{}': {}", fieldName, e.getMessage());
            }
        }
        if (fieldType != null && c.minDate() != null) {
            validateDateBound(fieldName, value, c.minDate(), fieldType, true);
        }
        if (fieldType != null && c.maxDate() != null) {
            validateDateBound(fieldName, value, c.maxDate(), fieldType, false);
        }
    }

    private static void validateDateBound(String fieldName, String value, String bound,
                                           String fieldType, boolean isMin) throws ParseException {
        try {
            if ("fmdb:inputDate".equals(fieldType)) {
                LocalDate submitted = LocalDate.parse(value);
                LocalDate limit     = LocalDate.parse(bound);
                boolean violation   = isMin ? submitted.isBefore(limit) : submitted.isAfter(limit);
                if (violation) {
                    log.warn("[FormDataParser] Rejected field '{}': date {} bound '{}'", fieldName, isMin ? "before min" : "after max", bound);
                    throw new ParseException(
                            "Field '" + fieldName + "': date is " + (isMin ? "before minimum" : "after maximum") + ".", 400, true);
                }
            } else if ("fmdb:inputDatetimeLocal".equals(fieldType)) {
                LocalDateTime submitted = LocalDateTime.parse(value, DATETIME_LOCAL_FMT);
                LocalDateTime limit     = LocalDateTime.parse(bound, DATETIME_LOCAL_FMT);
                boolean violation       = isMin ? submitted.isBefore(limit) : submitted.isAfter(limit);
                if (violation) {
                    log.warn("[FormDataParser] Rejected field '{}': datetime {} bound '{}'", fieldName, isMin ? "before min" : "after max", bound);
                    throw new ParseException(
                            "Field '" + fieldName + "': datetime is " + (isMin ? "before minimum" : "after maximum") + ".", 400, true);
                }
            }
        } catch (DateTimeParseException e) {
            // if the submitted value is unparseable, the type validator already caught it
        }
    }

    private static void validateEmail(String fieldName, String value) throws ParseException {
        // <input type="email" multiple> submits comma-separated addresses
        String[] parts = value.contains(",") ? value.split(",") : new String[]{value};
        for (String part : parts) {
            String email = part.trim();
            if (!email.isEmpty() && !EMAIL_PATTERN.matcher(email).matches()) {
                log.warn("[FormDataParser] Rejected field '{}': invalid email '{}'", fieldName, email);
                throw new ParseException("Field '" + fieldName + "': invalid email format.", 400, true);
            }
        }
    }

    private static void validateDate(String fieldName, String value) throws ParseException {
        try {
            LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            log.warn("[FormDataParser] Rejected field '{}': invalid date '{}'", fieldName, value);
            throw new ParseException(
                    "Field '" + fieldName + "': invalid date format (expected yyyy-MM-dd).", 400, true);
        }
    }

    private static void validateDatetimeLocal(String fieldName, String value) throws ParseException {
        try {
            LocalDateTime.parse(value, DATETIME_LOCAL_FMT);
        } catch (DateTimeParseException e) {
            log.warn("[FormDataParser] Rejected field '{}': invalid datetime-local '{}'", fieldName, value);
            throw new ParseException(
                    "Field '" + fieldName + "': invalid datetime format.", 400, true);
        }
    }

    private static void validateColor(String fieldName, String value) throws ParseException {
        if (!COLOR_PATTERN.matcher(value).matches()) {
            log.warn("[FormDataParser] Rejected field '{}': invalid color '{}'", fieldName, value);
            throw new ParseException(
                    "Field '" + fieldName + "': invalid color format (expected #rrggbb).", 400, true);
        }
    }


    private static FormFile parseFilePart(
            FileItemStream item,
            Set<String> fieldAllowedTypes,
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
        // Field-level allowlist (pre-resolved at collection time); falls back to global config
        Set<String> allowed = (fieldAllowedTypes != null && !fieldAllowedTypes.isEmpty())
                ? fieldAllowedTypes
                : config.getUploadAllowedMimeTypes();

        if (!allowed.isEmpty() && !isMimeAllowed(detectedMime, allowed)) {
            log.warn("[FormDataParser] Rejected file '{}' field='{}': detected MIME '{}' not in allowlist {}",
                    sanitizedName, fieldName, detectedMime, allowed);
            throw new ParseException(
                    "File '" + sanitizedName + "': type '" + detectedMime + "' is not allowed.", 415);
        }

        log.debug("[FormDataParser] Accepted file: field={}, name={}, mime={}, size={}",
                fieldName, sanitizedName, detectedMime, data.length);
        return new FormFile(fieldName, sanitizedName, detectedMime, data);
    }

    /**
     * Strips path traversal sequences, CRLF (header injection), XSS characters,
     * and JCR-invalid node name characters from the filename.
     * All ASCII control characters (0x00–0x1F, 0x7F) are removed to prevent Content-Disposition injection.
     * JCR reserved characters (: [ ] * | ?) are replaced with underscores.
     * Returns "upload" if the result is blank.
     */
    static String sanitizeFilename(String raw) {
        if (raw == null || raw.isBlank()) return "upload";
        // Strip path separators (path traversal)
        String name = raw.replaceAll(".*[/\\\\]", "");
        // Strip all ASCII control chars (includes \r \n \t) and XSS chars
        name = name.replaceAll("[\\x00-\\x1F\\x7F<>\"']", "");
        // Replace JCR-invalid node name characters with underscores
        name = name.replaceAll("[:\\[\\]*|?]", "_");
        name = name.trim();
        return name.isEmpty() ? "upload" : name;
    }


    /**
     * Resolves a single accept token to a MIME type.
     * Extensions like ".pdf" are normalised via Tika; MIME types are returned as-is.
     */
    public static String resolveAcceptToken(String token) {
        if (token.startsWith(".")) {
            String detected = TIKA.detect("file" + token);
            return detected != null ? detected : token;
        }
        return token;
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

}
