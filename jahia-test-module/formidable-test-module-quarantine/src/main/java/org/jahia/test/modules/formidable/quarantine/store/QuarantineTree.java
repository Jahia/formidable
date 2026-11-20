package org.jahia.test.modules.formidable.quarantine.store;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;

/**
 * The names of the quarantine tree, as the CND of the module declares them, and its layout:
 * {@code /sites/<site>/formidable-quarantine/<form>/YYYY/MM/DD/<held>}, in live.
 */
public final class QuarantineTree {

    /** The name of the root of the tree under a site node. */
    public static final String ROOT_NAME = "formidable-quarantine";

    public static final String ROOT_TYPE = "fmdbquar:quarantineRoot";
    public static final String FORM_TYPE = "fmdbquar:formQuarantine";
    public static final String DATE_FOLDER_TYPE = "fmdbquar:dateFolder";
    public static final String HELD_TYPE = "fmdbquar:heldSubmission";
    public static final String VALUES_TYPE = "fmdbquar:values";
    public static final String SENSITIVE_VALUES_TYPE = "fmdbquar:sensitiveValues";
    public static final String ITEM_TYPE = "fmdbquar:item";
    /** The type of the two containers {@link #CHECKS} and {@link #AUDIT}, which accept any {@code jnt:content}. */
    public static final String RECORDS_TYPE = "fmdbquar:records";

    /** Under a held submission, the public values. */
    public static final String VALUES = "values";
    /** Under a held submission, the sensitive values, apart from the public ones. */
    public static final String SENSITIVE = "sensitive";
    /** Under a held submission, the outcomes of the checks and the decision of the run. */
    public static final String CHECKS = "checks";
    /** Under a held submission, the audit records of the operations and of the run. */
    public static final String AUDIT = "audit";
    /** The prefix of the name of an item: {@code item0}, {@code item1}, in the order of the files. */
    public static final String ITEM_PREFIX = "item";

    // the properties of a held submission
    public static final String STATE = "state";
    public static final String CREATED_AT = "createdAt";
    public static final String ATTEMPTS = "attempts";
    public static final String NEXT_ATTEMPT = "nextAttempt";
    public static final String COMPLETED_ACTIONS = "completedActions";
    /** The last change of the state: the retention reads it. */
    public static final String CHANGED_AT = "changedAt";
    public static final String FORM_ID = "formId";
    public static final String SITE_KEY = "siteKey";
    public static final String LOCALE = "locale";
    public static final String ORIGIN_LANGUAGE = "originLanguage";
    public static final String ORIGIN_REFERER = "originReferer";
    public static final String ORIGIN_TIME_ZONE = "originTimeZone";
    public static final String VALUE_FIELDS = "valueFields";
    public static final String SENSITIVE_FIELDS = "sensitiveFields";

    // the properties of an item
    public static final String DATA = "data";
    public static final String SHA256 = "sha256";
    public static final String SIZE = "size";
    public static final String FILE_NAME = "fileName";
    public static final String FIELD_NAME = "fieldName";
    public static final String MIME_TYPE = "mimeType";

    private QuarantineTree() {
    }

    /** The path of the root of the tree of a site. */
    public static String rootPath(String siteKey) {
        return sitePath(siteKey) + "/" + ROOT_NAME;
    }

    /** The path of the folder of a form, below the root of the tree of a site. */
    public static String formPath(String siteKey, String formId) {
        return rootPath(siteKey) + "/" + formId;
    }

    /** The path of the site node. */
    public static String sitePath(String siteKey) {
        return "/sites/" + siteKey;
    }

    /** The year, the month and the day of an instant, in UTC, as the names of the three date folders. */
    public static List<String> dateFolders(Instant at) {
        ZonedDateTime utc = at.atZone(ZoneOffset.UTC);
        return List.of(
                String.format("%04d", utc.getYear()),
                String.format("%02d", utc.getMonthValue()),
                String.format("%02d", utc.getDayOfMonth()));
    }

    /** The name of the item of the file at this index of the submission. */
    public static String itemName(int index) {
        return ITEM_PREFIX + index;
    }
}
