package org.jahia.modules.formidable.quarantine.check;

import org.jahia.modules.formidable.quarantine.api.CheckSettings;
import org.jahia.modules.formidable.quarantine.api.Outcome;
import org.jahia.modules.formidable.quarantine.api.Qualifier;
import org.jahia.modules.formidable.quarantine.api.QuarantinedFile;
import org.jahia.modules.formidable.quarantine.api.Scope;
import org.jahia.modules.formidable.quarantine.api.Submitted;
import org.osgi.service.component.annotations.Component;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * The allow-list check of the module, the service of {@code fmdbq:allowListCheck}. It reads the name and the declared
 * MIME type of a file, never a byte ({@link Scope#METADATA}), and applies to every file. It answers at once:
 * <ul>
 * <li>{@code PASS} when the extension of the name and the essence of the MIME type are both on the lists of the
 * check, case aside;</li>
 * <li>{@code FAIL} otherwise, with a reason code that names which list refused the file. An empty list allows
 * nothing.</li>
 * </ul>
 * A {@code PASS} says only that the type is allowed: alone in the list, the check releases an allowed file with no
 * scan. No answer names the file or its type: the reason codes are fixed.
 */
@Component(service = Qualifier.class, immediate = true)
public class AllowListQualifier implements Qualifier {

    /** The node type the check serves. */
    public static final String NODE_TYPE = "fmdbq:allowListCheck";

    /** The property of the check node that lists the allowed extensions, without the dot. */
    static final String EXTENSIONS = "extensions";
    /** The property of the check node that lists the allowed MIME types. */
    static final String MIME_TYPES = "mimeTypes";

    /** The reason of a {@code PASS}. */
    static final String ALLOWED = "allowList.allowed";
    /** The reason of a {@code FAIL}: the extension of the name is not on the list, or the name has none. */
    static final String EXTENSION_REFUSED = "allowList.extensionRefused";
    /** The reason of a {@code FAIL}: the MIME type is not on the list, or the file declares none. */
    static final String MIME_TYPE_REFUSED = "allowList.mimeTypeRefused";
    /** The reason of the failure of a poll: the check issues no ticket. */
    static final String NO_TICKET = "allowList.noTicket";

    @Override
    public String getNodeType() {
        return NODE_TYPE;
    }

    @Override
    public Scope scope() {
        return Scope.METADATA;
    }

    @Override
    public boolean appliesTo(QuarantinedFile file, CheckSettings settings) {
        return true;
    }

    @Override
    public Submitted submit(QuarantinedFile file, CheckSettings settings) {
        String extension = extensionOf(file.fileName());
        if (extension == null || !listOf(settings, EXTENSIONS).contains(extension)) {
            return Submitted.answered(Outcome.fail(EXTENSION_REFUSED, null));
        }
        String mimeType = essenceOf(file.mimeType());
        if (mimeType == null || !listOf(settings, MIME_TYPES).contains(mimeType)) {
            return Submitted.answered(Outcome.fail(MIME_TYPE_REFUSED, null));
        }
        return Submitted.answered(Outcome.pass(ALLOWED, null));
    }

    @Override
    public Outcome poll(String ticket) {
        return new Outcome.Failed(false, NO_TICKET);
    }

    /** @return the part of the name after its last dot, in lower case; null for a name with no extension */
    static String extensionOf(String fileName) {
        if (fileName == null) {
            return null;
        }
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return null;
        }
        return fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    /** @return the type and subtype of a MIME type, with no parameter, in lower case; null for a blank type */
    static String essenceOf(String mimeType) {
        if (mimeType == null) {
            return null;
        }
        int parameters = mimeType.indexOf(';');
        String essence = (parameters < 0 ? mimeType : mimeType.substring(0, parameters)).trim();
        return essence.isEmpty() ? null : essence.toLowerCase(Locale.ROOT);
    }

    /**
     * @return the entries of a list of the check, trimmed, in lower case, an extension without its leading dot; empty
     *         when the check node holds no such list
     */
    static Set<String> listOf(CheckSettings settings, String property) {
        Set<String> entries = new HashSet<>();
        String joined = settings.properties().get(property);
        if (joined == null) {
            return entries;
        }
        for (String entry : joined.split(CheckSettings.VALUE_SEPARATOR)) {
            String normalized = entry.trim().toLowerCase(Locale.ROOT);
            if (EXTENSIONS.equals(property) && normalized.startsWith(".")) {
                normalized = normalized.substring(1);
            }
            if (!normalized.isEmpty()) {
                entries.add(normalized);
            }
        }
        return entries;
    }
}
