package org.jahia.modules.formidable.engine.imports;

import org.jahia.modules.formidable.engine.util.JcrFiles;
import org.jahia.modules.formidable.engine.actions.form.storage.SaveToJcrFormAction;
import org.jahia.modules.formidable.engine.api.FmdbMixin;
import org.jahia.modules.formidable.engine.api.FmdbNodeName;
import org.jahia.modules.formidable.engine.api.FmdbNodeType;
import org.jahia.modules.formidable.engine.api.FmdbProperty;
import org.jahia.modules.formidable.engine.imports.model.ImportedAction;
import org.jahia.modules.formidable.engine.imports.model.ImportedContainer;
import org.jahia.modules.formidable.engine.imports.model.ImportedElement;
import org.jahia.modules.formidable.engine.imports.model.ImportedField;
import org.jahia.modules.formidable.engine.imports.model.ImportedFile;
import org.jahia.modules.formidable.engine.imports.model.ImportedForm;
import org.jahia.modules.formidable.engine.imports.model.ImportedSubmission;
import org.jahia.modules.formidable.engine.permissions.FormResultsAclSyncService;
import org.jahia.services.content.JCRContentUtils;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRSessionWrapper;
import org.jahia.utils.LanguageCodeConverters;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import javax.jcr.query.Query;
import javax.jcr.query.Row;
import javax.jcr.query.RowIterator;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

import static org.jahia.modules.formidable.engine.util.FormidableJcrConstants.ACL_NODE;
import static org.jahia.modules.formidable.engine.util.FormidableJcrConstants.ACL_NODE_TYPE;
import static org.jahia.modules.formidable.engine.util.FormidableJcrConstants.INHERIT_PROPERTY;
import static org.jahia.modules.formidable.engine.util.FormidableJcrConstants.SITES;

/**
 * Writes what an import produces into a site (docs/architecture/forms-import.md, "Target model"): the
 * forms in the {@code imported-forms} content folder, in the edit workspace, and their results in live, as
 * {@code SaveToJcrFormAction} would have written them. Source-agnostic: it reads the {@code model}
 * records and stamps the markers the engine CND declares.
 * <p>
 * The caller saves: the import saves by batches, the dry run never.
 */
// Not final: the run is tested against a mock of it.
public class ImportWriter {

    public static final String FOLDER_NAME = "imported-forms";
    public static final String CONTENTS_NODE = "contents";
    public static final String FIELDS_NODE = "fields";
    private static final String CONTENT_FOLDER_TYPE = "jnt:contentFolder";
    private static final String FOLDER_TYPE = "jnt:folder";
    private static final String TITLE = "jcr:title";
    private static final String OPTIONS_MODE = "optionsMode";
    private static final String OPTIONS = "options";
    private static final String OPTIONS_SOURCE_KEY = "optionsSourceKey";
    private static final String SOURCE_SYSTEM = "sourceSystem";
    private static final String SOURCE_ID = "sourceId";
    private static final String SOURCE_RESULTS_ID = "sourceResultsId";
    private static final String SOURCE_PATH = "sourcePath";
    private static final String SOURCE_NAME = "sourceName";
    private static final String SOURCE_FORM_ID = "sourceFormId";
    private static final String SOURCE_FORM_IDS = "sourceFormIds";
    private static final String CAPTCHA_MIXIN = "fmdbmix:captcha";
    private static final String SYSTEM_AUTHOR = "system";
    private static final String FIELD_LIST_TYPE = "fmdb:fieldList";
    private static final String ACTION_LIST_TYPE = "fmdb:actionList";
    private static final DateTimeFormatter DAY_FOLDERS = DateTimeFormatter.ofPattern("yyyy/MM/dd").withZone(ZoneOffset.UTC);

    /** Opens the binary of an imported file from the export; null when the export does not hold it. */
    @FunctionalInterface
    public interface BinaryOpener {
        InputStream open(ImportedFile file) throws IOException;
    }

    /** A form found or created for an imported one. */
    public record FormHandle(JCRNodeWrapper node, boolean created) {
    }

    /** A field of a form an earlier run created, with the identity of the source field it stands for. */
    public record FoundField(String name, String nodeType, String sourceId, String sourceName) {
    }

    private final JCRSessionWrapper edit;
    private final JCRSessionWrapper live;
    private final String siteKey;
    private final String folderTitle;

    /**
     * @param edit a system session on the edit workspace, where the forms are written
     * @param live a system session on the live workspace, where the results are written
     * @param folderTitle the title of the {@code imported-forms} folder when the import creates it
     */
    public ImportWriter(JCRSessionWrapper edit, JCRSessionWrapper live, String siteKey, String folderTitle) {
        this.edit = edit;
        this.live = live;
        this.siteKey = siteKey;
        this.folderTitle = folderTitle;
    }

    // --- forms ---

    /** The form an earlier run created for this source, by either of its keys, wherever it is in the site. */
    public JCRNodeWrapper findForm(String sourceId, String sourceResultsId) throws RepositoryException {
        List<String> keys = new ArrayList<>();
        if (sourceId != null) {
            keys.add("[" + SOURCE_ID + "] = '" + escape(sourceId) + "'");
        }
        if (sourceResultsId != null) {
            keys.add("[" + SOURCE_RESULTS_ID + "] = '" + escape(sourceResultsId) + "'");
        }
        if (keys.isEmpty()) {
            return null;
        }
        String statement = "SELECT * FROM [" + FmdbMixin.IMPORTED_FORM + "] AS f WHERE ISDESCENDANTNODE(f, '" + SITES
                + escape(siteKey) + "') AND (" + String.join(" OR ", keys) + ")";
        NodeIterator found = edit.getWorkspace().getQueryManager().createQuery(statement, Query.JCR_SQL2).execute().getNodes();
        return found.hasNext() ? (JCRNodeWrapper) found.nextNode() : null;
    }

    /**
     * The fields of a form an earlier run created, as they are now: the contributor may have renamed,
     * moved or deleted some since. Each one still knows the source field it stands for, through its
     * {@code fmdbmix:importedField} marker, which is how the submissions of a later run find their column.
     */
    public List<FoundField> importedFields(JCRNodeWrapper form) throws RepositoryException {
        List<FoundField> fields = new ArrayList<>();
        if (form.hasNode(FIELDS_NODE)) {
            collectImportedFields(form.getNode(FIELDS_NODE), fields);
        }
        return fields;
    }

    private static void collectImportedFields(JCRNodeWrapper parent, List<FoundField> fields) throws RepositoryException {
        NodeIterator children = parent.getNodes();
        while (children.hasNext()) {
            JCRNodeWrapper child = (JCRNodeWrapper) children.nextNode();
            if (child.isNodeType(FmdbMixin.IMPORTED_FIELD)) {
                fields.add(new FoundField(child.getName(), child.getPrimaryNodeTypeName(),
                        child.getPropertyAsString(SOURCE_ID), child.getPropertyAsString(SOURCE_NAME)));
            } else {
                collectImportedFields(child, fields);
            }
        }
    }

    /** The {@code imported-forms} folder of the site, or its path when it does not exist yet. */
    public String importedFormsPath() {
        return SITES + siteKey + "/" + CONTENTS_NODE + "/" + FOLDER_NAME;
    }

    public FormHandle findOrCreateForm(ImportedForm form) throws RepositoryException {
        JCRNodeWrapper existing = findForm(form.sourceId(), form.sourceResultsId());
        if (existing != null) {
            return new FormHandle(existing, false);
        }
        return new FormHandle(createForm(form), true);
    }

    private JCRNodeWrapper createForm(ImportedForm form) throws RepositoryException {
        JCRNodeWrapper folder = importedFormsFolder();
        JCRNodeWrapper node = folder.addNode(JCRContentUtils.findAvailableNodeName(folder, form.name()), "fmdb:form");
        i18n(node, TITLE, form.titles());
        i18n(node, "submissionMessage", form.submissionMessage());
        form.buttonLabels().forEach((property, byLanguage) -> i18nQuietly(node, property, byLanguage));
        if (form.captcha() && edit.getWorkspace().getNodeTypeManager().hasNodeType(CAPTCHA_MIXIN)) {
            node.addMixin(CAPTCHA_MIXIN);
        }
        JCRNodeWrapper fields = childOrCreate(node, FIELDS_NODE, FIELD_LIST_TYPE);
        for (ImportedElement element : form.elements()) {
            writeElement(fields, element);
        }
        JCRNodeWrapper actions = childOrCreate(node, FmdbNodeName.ACTIONS, ACTION_LIST_TYPE);
        for (ImportedAction action : form.actions()) {
            JCRNodeWrapper actionNode = actions.addNode(JCRContentUtils.findAvailableNodeName(actions, action.name()), action.nodeType());
            action.properties().forEach((key, value) -> setQuietly(actionNode, key, value));
            action.i18nProperties().forEach((key, byLanguage) -> i18nQuietly(actionNode, key, byLanguage));
        }
        node.addMixin(FmdbMixin.IMPORTED_FORM);
        node.setProperty(SOURCE_SYSTEM, form.sourceSystem());
        setQuietly(node, SOURCE_ID, form.sourceId());
        setQuietly(node, SOURCE_RESULTS_ID, form.sourceResultsId());
        setQuietly(node, SOURCE_PATH, form.sourcePath());
        return node;
    }

    private JCRNodeWrapper importedFormsFolder() throws RepositoryException {
        JCRNodeWrapper contents = edit.getNode(SITES + siteKey + "/" + CONTENTS_NODE);
        if (contents.hasNode(FOLDER_NAME)) {
            return contents.getNode(FOLDER_NAME);
        }
        JCRNodeWrapper folder = contents.addNode(FOLDER_NAME, CONTENT_FOLDER_TYPE);
        for (Locale locale : siteLocales()) {
            folder.getOrCreateI18N(locale).setProperty(TITLE, folderTitle);
        }
        return folder;
    }

    private void writeElement(JCRNodeWrapper parent, ImportedElement element) throws RepositoryException {
        if (element instanceof ImportedContainer container) {
            JCRNodeWrapper node = parent.addNode(JCRContentUtils.findAvailableNodeName(parent, container.name()), container.nodeType());
            i18n(node, TITLE, container.titles());
            for (ImportedElement child : container.children()) {
                writeElement(node, child);
            }
            return;
        }
        writeField(parent, (ImportedField) element);
    }

    private void writeField(JCRNodeWrapper parent, ImportedField field) throws RepositoryException {
        JCRNodeWrapper node = parent.addNode(JCRContentUtils.findAvailableNodeName(parent, field.name()), field.nodeType());
        i18n(node, TITLE, field.titles());
        if (field.required()) {
            node.setProperty(ImportedField.REQUIRED_PROPERTY, "true");
        }
        field.properties().forEach((key, value) -> setQuietly(node, key, value));
        field.i18nProperties().forEach((key, byLanguage) -> i18nQuietly(node, key, byLanguage));
        writeOptions(node, field);
        node.addMixin(FmdbMixin.IMPORTED_FIELD);
        node.setProperty(SOURCE_ID, field.sourceId() != null ? field.sourceId() : field.sourceName());
        node.setProperty(SOURCE_NAME, field.sourceName());
    }

    private void writeOptions(JCRNodeWrapper node, ImportedField field) throws RepositoryException {
        if (field.optionsSourceKey() != null) {
            node.addMixin(FmdbMixin.SOURCED_OPTIONS);
            node.setProperty(OPTIONS_MODE, "sourced");
            node.setProperty(OPTIONS_SOURCE_KEY, field.optionsSourceKey());
        } else if (!field.options().isEmpty()) {
            node.addMixin(FmdbMixin.MANUAL_OPTIONS);
            node.setProperty(OPTIONS_MODE, "manual");
            for (Map.Entry<String, List<String>> byLanguage : field.options().entrySet()) {
                translation(node, byLanguage.getKey()).setProperty(OPTIONS, byLanguage.getValue().toArray(String[]::new));
            }
        }
    }

    // --- results ---

    /**
     * The results entry of the form in live, created as SaveToJcrFormAction creates one when it is missing.
     * The form must be saved first: the auto-split setting saves the live session, entry included, and an
     * entry must never be persisted ahead of the form it points to.
     */
    public JCRNodeWrapper findOrCreateResultsEntry(JCRNodeWrapper formNode, ImportedForm form) throws RepositoryException {
        if (formNode.isNew()) {
            throw new IllegalStateException("The form " + formNode.getName() + " must be saved before its results entry is created");
        }
        JCRNodeWrapper site = live.getNode(SITES + siteKey);
        JCRNodeWrapper root = SaveToJcrFormAction.getOrCreateResultsRoot(site, live);
        JCRNodeWrapper entry = findEntry(root, formNode.getIdentifier());
        if (entry == null) {
            entry = root.addNode(JCRContentUtils.findAvailableNodeName(root, formNode.getName()), FmdbNodeType.FORM_RESULTS);
            entry.setProperty(FmdbProperty.PARENT_FORM, formNode.getIdentifier());
            if (form.buildingLang() != null) {
                entry.setProperty("buildingLang", form.buildingLang());
            }
            JCRNodeWrapper acl = entry.addNode(ACL_NODE, ACL_NODE_TYPE);
            acl.setProperty(INHERIT_PROPERTY, false);
            FormResultsAclSyncService.syncAclToFormResults(formNode, entry, live);
        }
        if (!entry.isNodeType(FmdbMixin.IMPORTED_RESULTS)) {
            entry.addMixin(FmdbMixin.IMPORTED_RESULTS);
            entry.setProperty(SOURCE_SYSTEM, form.sourceSystem());
        }
        addToMultiple(entry, SOURCE_FORM_IDS, form.sourceKey());
        SaveToJcrFormAction.ensureAutoSplit(childOrCreate(entry, FmdbNodeName.SUBMISSIONS, FmdbNodeType.SUBMISSIONS));
        return entry;
    }

    private static JCRNodeWrapper findEntry(JCRNodeWrapper root, String formIdentifier) throws RepositoryException {
        NodeIterator children = root.getNodes();
        while (children.hasNext()) {
            Node child = children.nextNode();
            if (child instanceof JCRNodeWrapper candidate && candidate.isNodeType(FmdbNodeType.FORM_RESULTS)
                    && candidate.hasProperty(FmdbProperty.PARENT_FORM)
                    && formIdentifier.equals(candidate.getProperty(FmdbProperty.PARENT_FORM).getString())) {
                return candidate;
            }
        }
        return null;
    }

    /** The source identities of every submission already imported into the site, whatever its entry. */
    public Set<String> importedSubmissionIds() throws RepositoryException {
        String statement = "SELECT [" + SOURCE_ID + "] FROM [" + FmdbMixin.IMPORTED_SUBMISSION + "] AS s WHERE ISDESCENDANTNODE(s, '" + SITES
                + escape(siteKey) + "/" + SaveToJcrFormAction.RESULTS_ROOT_NAME + "')";
        RowIterator rows = live.getWorkspace().getQueryManager().createQuery(statement, Query.JCR_SQL2).execute().getRows();
        Set<String> ids = new HashSet<>();
        while (rows.hasNext()) {
            Row row = rows.nextRow();
            javax.jcr.Value value = row.getValue(SOURCE_ID);
            if (value != null) {
                ids.add(value.getString());
            }
        }
        return ids;
    }

    /**
     * Writes one submission under the entry: the {@code yyyy/MM/dd} folders of its date, created by the
     * import itself since the auto-split listener places only the direct children of {@code submissions},
     * and the node with its original date through the {@code addNode} overload the Jahia import uses.
     */
    public JCRNodeWrapper writeSubmission(JCRNodeWrapper entry, ImportedSubmission submission, BinaryOpener binaries)
            throws RepositoryException, IOException {
        JCRNodeWrapper day = dayFolder(childOrCreate(entry, FmdbNodeName.SUBMISSIONS, FmdbNodeType.SUBMISSIONS), submission.created());
        String name = JCRContentUtils.findAvailableNodeName(day, SaveToJcrFormAction.submissionNodeName(submission.created()));
        Calendar created = calendar(submission.created());
        String author = author();
        JCRNodeWrapper node = day.addNode(name, FmdbNodeType.FORM_SUBMISSION, null, created, author, created, author);
        // the discriminator Save to JCR documents for an import: the source system the entry records
        node.setProperty("origin", entry.getPropertyAsString(SOURCE_SYSTEM));
        setQuietly(node, "locale", submission.locale());
        setQuietly(node, "referer", submission.referer());
        JCRNodeWrapper data = childOrCreate(node, FmdbNodeName.DATA, FmdbNodeType.SUBMISSION_DATA);
        for (Map.Entry<String, List<String>> value : submission.values().entrySet()) {
            if (value.getValue().size() == 1) {
                data.setProperty(value.getKey(), value.getValue().get(0));
            } else {
                data.setProperty(value.getKey(), value.getValue().toArray(String[]::new));
            }
        }
        writeFiles(node, submission.files(), binaries);
        node.addMixin(FmdbMixin.IMPORTED_SUBMISSION);
        node.setProperty(SOURCE_ID, submission.sourceId());
        node.setProperty(SOURCE_FORM_ID, submission.sourceFormId());
        return node;
    }

    /** A file the export does not hold is left behind: the run reports it when it counts the files. */
    private static void writeFiles(JCRNodeWrapper submission, List<ImportedFile> files, BinaryOpener binaries)
            throws RepositoryException, IOException {
        for (ImportedFile file : files) {
            try (InputStream binary = binaries.open(file)) {
                if (binary == null) {
                    continue;
                }
                JCRNodeWrapper folder = childOrCreate(submission, FmdbNodeName.FILES, FOLDER_TYPE);
                JCRNodeWrapper fieldFolder = childOrCreate(folder, file.fieldName(), FOLDER_TYPE);
                JcrFiles.addFile(fieldFolder, JCRContentUtils.findAvailableNodeName(fieldFolder, file.fileName()), binary,
                        file.mimeType() == null ? "application/octet-stream" : file.mimeType());
            }
        }
    }

    private static JCRNodeWrapper dayFolder(JCRNodeWrapper submissions, Instant created) throws RepositoryException {
        JCRNodeWrapper folder = submissions;
        for (String segment : DAY_FOLDERS.format(created).split("/")) {
            folder = folder.hasNode(segment) ? folder.getNode(segment) : folder.addNode(segment, FmdbNodeType.SPLITTED_SUBMISSION);
        }
        return folder;
    }

    /** Saves the forms, before their results entries are created. */
    public void saveForms() throws RepositoryException {
        edit.save();
    }

    public void save() throws RepositoryException {
        edit.save();
        live.save();
    }

    // --- helpers ---

    /** The name the submissions are created by: the system user of the sessions, as SaveToJcrFormAction leaves it. */
    private String author() {
        for (JCRSessionWrapper session : List.of(edit, live)) {
            if (session.getUser() != null) {
                return session.getUser().getName();
            }
        }
        return SYSTEM_AUTHOR;
    }

    /** An autocreated child, which Jahia does not create on addNode: taken when there, added otherwise. */
    private static JCRNodeWrapper childOrCreate(JCRNodeWrapper node, String name, String type) throws RepositoryException {
        return node.hasNode(name) ? node.getNode(name) : node.addNode(name, type);
    }

    private List<Locale> siteLocales() throws RepositoryException {
        List<Locale> locales = new ArrayList<>();
        JCRNodeWrapper site = edit.getNode(SITES + siteKey);
        if (site.hasProperty("j:languages")) {
            for (javax.jcr.Value language : site.getProperty("j:languages").getValues()) {
                locales.add(LanguageCodeConverters.languageCodeToLocale(language.getString()));
            }
        }
        if (locales.isEmpty()) {
            locales.add(Locale.ENGLISH);
        }
        return locales;
    }

    private static void i18n(JCRNodeWrapper node, String property, Map<String, String> byLanguage) throws RepositoryException {
        for (Map.Entry<String, String> value : byLanguage.entrySet()) {
            if (value.getValue() != null && !value.getValue().isBlank()) {
                translation(node, value.getKey()).setProperty(property, value.getValue());
            }
        }
    }

    private static void i18nQuietly(JCRNodeWrapper node, String property, Map<String, String> byLanguage) {
        try {
            i18n(node, property, byLanguage);
        } catch (RepositoryException e) {
            throw new IllegalStateException("Cannot write " + property + " on " + node.getPath(), e);
        }
    }

    /** The translation node of a language; the plain language ({@code ""}) means the default language of the site. */
    private static Node translation(JCRNodeWrapper node, String language) throws RepositoryException {
        String code = language == null || language.isBlank() ? siteDefaultLanguage(node) : language;
        return node.getOrCreateI18N(LanguageCodeConverters.languageCodeToLocale(code));
    }

    private static String siteDefaultLanguage(JCRNodeWrapper node) throws RepositoryException {
        JCRNodeWrapper site = node.getResolveSite();
        return site != null && site.hasProperty("j:defaultLanguage") ? site.getProperty("j:defaultLanguage").getString() : "en";
    }

    private static void setQuietly(JCRNodeWrapper node, String property, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        try {
            node.setProperty(property, value);
        } catch (RepositoryException e) {
            throw new IllegalStateException("Cannot write " + property + " on " + node.getPath(), e);
        }
    }

    private static void addToMultiple(JCRNodeWrapper node, String property, String value) throws RepositoryException {
        if (value == null) {
            return;
        }
        List<String> values = new ArrayList<>();
        if (node.hasProperty(property)) {
            for (javax.jcr.Value existing : node.getProperty(property).getValues()) {
                values.add(existing.getString());
            }
        }
        if (!values.contains(value)) {
            values.add(value);
            node.setProperty(property, values.toArray(String[]::new));
        }
    }

    private static Calendar calendar(Instant instant) {
        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone(ZoneOffset.UTC));
        calendar.setTimeInMillis(instant.toEpochMilli());
        return calendar;
    }

    private static String escape(String value) {
        return value.replace("'", "''");
    }
}
