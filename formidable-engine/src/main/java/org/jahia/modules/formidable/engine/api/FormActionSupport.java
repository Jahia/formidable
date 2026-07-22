package org.jahia.modules.formidable.engine.api;

import javax.activation.DataHandler;
import java.util.List;
import java.util.Map;

/**
 * Server-side services offered to form actions that are implemented outside this bundle,
 * in particular TypeScript actions registered through the {@code formidable-form-action}
 * JavaScript registry type.
 *
 * Security-sensitive logic is deliberately kept behind this interface instead of being
 * reimplemented by callers:
 * <ul>
 *   <li>forward targets are resolved from operator configuration and SSRF-checked here,
 *       so callers never see or choose the target URL;</li>
 *   <li>email attachments are built with RFC 6266 safe names and size caps enforced
 *       against the global upload configuration.</li>
 * </ul>
 *
 * Obtain it as an OSGi service, e.g. from JavaScript:
 * {@code server.osgi.getService("org.jahia.modules.formidable.engine.api.FormActionSupport")}.
 */
public interface FormActionSupport {

    /** Global per-file upload size cap in bytes, from {@code org.jahia.modules.formidable.cfg}. */
    long getUploadMaxFileSizeBytes();

    /**
     * Builds email attachments from validated uploaded files.
     * Files larger than {@code maxAttachmentSizeBytes} (additionally clamped by
     * {@link #getUploadMaxFileSizeBytes()}) are skipped with a log entry.
     *
     * @param files                  the validated uploaded files of the submission
     * @param maxAttachmentSizeBytes per-attachment size cap requested by the action configuration
     * @return attachment name → data handler, in submission order
     */
    Map<String, DataHandler> buildEmailAttachments(List<SubmittedFile> files, long maxAttachmentSizeBytes);

    /**
     * Forwards the submitted form data to the configured target as {@code multipart/form-data}.
     * The {@code targetId} is resolved against operator configuration ({@code forwardTargets} /
     * {@code devForwardTargets}) and the resolved hostname is rejected when it points to a
     * private or internal address. The target URL is never exposed to the caller.
     *
     * @throws FormActionException 403 when the target is unknown or resolves to a private
     *                             address, 400 on an invalid target, 502 on upstream failures,
     *                             500 on payload construction errors
     */
    void forwardSubmission(String targetId, Map<String, List<String>> parameters, List<SubmittedFile> files)
            throws FormActionException;
}
