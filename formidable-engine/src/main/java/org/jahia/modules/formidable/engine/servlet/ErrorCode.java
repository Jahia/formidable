package org.jahia.modules.formidable.engine.servlet;

/**
 * Error codes returned in JSON responses when a form submission fails.
 * See docs/error-codes.md for the full glossary.
 *
 * Clients receive only the code (e.g. "FMDB-006"); detailed reasons are
 * written to server logs only, never exposed to the caller.
 */
enum ErrorCode {

    FMDB_001(415),  // Content-Type is not multipart/form-data
    FMDB_002(400),  // Missing required 'fid' URL parameter
    FMDB_003(413),  // Content-Length exceeds configured maximum
    FMDB_004(400),  // Form node not found in live workspace
    FMDB_005(500),  // CAPTCHA required by form but not configured server-side
    FMDB_006(400),  // CAPTCHA token absent or rejected by provider
    FMDB_007(400),  // Multipart parsing failed (size/count/type violation)
    FMDB_008(422),  // An action in the pipeline failed
    FMDB_009(401),  // Authentication required — form has fmdbmix:requireAuthentication and user is Guest
    FMDB_500(500);  // Unexpected internal error

    /** HTTP status code associated with this error. */
    final int httpStatus;

    ErrorCode(int httpStatus) {
        this.httpStatus = httpStatus;
    }

    /** Returns the code string as it appears in API responses, e.g. {@code "FMDB-006"}. */
    public String code() {
        return name().replace('_', '-');
    }
}

