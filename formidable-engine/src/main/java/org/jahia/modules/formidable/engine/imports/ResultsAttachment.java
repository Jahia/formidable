package org.jahia.modules.formidable.engine.imports;

import org.jahia.modules.formidable.engine.api.FmdbMixin;
import org.jahia.modules.formidable.engine.api.FmdbNodeName;
import org.jahia.modules.formidable.engine.api.FmdbNodeType;
import org.jahia.modules.formidable.engine.api.FmdbProperty;
import org.jahia.modules.formidable.engine.permissions.FormResultsAclSyncService;
import org.jahia.services.content.JCRContentUtils;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRSessionWrapper;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.NodeIterator;
import javax.jcr.PropertyIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Value;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.jahia.modules.formidable.engine.util.FormidableJcrConstants.SITES;

/**
 * The <b>Attach to another form</b> action of the Results page (docs/architecture/forms-import.md, "What the
 * attachment writes"): the imported results of one source form, held by an entry, move under a form of the
 * site, their values renamed after the fields of that form through a mapping. Source-agnostic, like the
 * markers it reads: it serves any import that stamped {@code fmdbmix:importedResults} and
 * {@code fmdbmix:importedSubmission}.
 * <p>
 * Where the entry holds this source alone and the form has no entry yet, the entry becomes the entry of the
 * form; otherwise the submissions of the source move into the entry of the form, created when needed, and the
 * entry they left is removed once empty. The values are renamed by batches of 100, in the submissions of this
 * source and nowhere else, and the mapping applied is kept on the entry, per source, composed with the one an
 * earlier run or attachment applied, so that a later import names the values the same way. The readers of the
 * entry are synced from the form.
 */
public final class ResultsAttachment {

    /** Submissions moved or renamed between two saves. */
    static final int BATCH = 100;
    /** The submissions read for the names of the fields of a source: the names are the same across them. */
    static final int NAMES_SAMPLE = 500;
    private static final String SOURCE_SYSTEM = "sourceSystem";
    private static final String SOURCE_FORM_IDS = "sourceFormIds";
    private static final String SOURCE_FORM_ID = "sourceFormId";
    private static final String BUILDING_LANG = "buildingLang";
    private static final String TITLE = "jcr:title";
    /** The keys of the JSON the dialog and the job exchange. */
    static final String ENTRY_KEY = "entry";
    static final String SOURCE_KEY = "source";
    static final String FORM_KEY = "form";
    static final String MAPPING_KEY = "mapping";
    private static final Logger log = LoggerFactory.getLogger(ResultsAttachment.class);

    /**
     * What the administrator asked: the entry, the source form among those it holds, the form of the site to
     * attach the results to, and the field of that form each field of the source lands under, by the name the
     * values hold now; a field left out keeps its name.
     */
    public record Request(String entryId, String sourceFormId, String formId, Map<String, String> mapping) {

        public Request {
            if (entryId == null || entryId.isBlank()) {
                throw new IllegalArgumentException("an attachment names the entry it moves");
            }
            if (sourceFormId == null || sourceFormId.isBlank()) {
                throw new IllegalArgumentException("an attachment names the source form of the entry it moves");
            }
            if (formId == null || formId.isBlank()) {
                throw new IllegalArgumentException("an attachment names the form to attach to");
            }
            mapping = Map.copyOf(mapping == null ? Map.of() : mapping);
        }

        /** {@code {"entry":"<uuid>","source":"<key>","form":"<uuid>","mapping":{"<field>":"<field of the form>"}}}. */
        public static Request fromJson(String json) throws JSONException {
            JSONObject object = new JSONObject(json);
            Map<String, String> mapping = new LinkedHashMap<>();
            JSONObject names = object.optJSONObject(MAPPING_KEY);
            if (names != null) {
                for (String source : names.keySet()) {
                    String target = names.optString(source, "");
                    if (!target.isBlank() && !target.equals(source)) {
                        mapping.put(source, target);
                    }
                }
            }
            return new Request(object.optString(ENTRY_KEY, null), object.optString(SOURCE_KEY, null), object.optString(FORM_KEY, null), mapping);
        }

        public String toJson() {
            return new JSONObject().put(ENTRY_KEY, entryId).put(SOURCE_KEY, sourceFormId).put(FORM_KEY, formId)
                    .put(MAPPING_KEY, new JSONObject(mapping)).toString();
        }
    }

    private final JCRSessionWrapper live;
    private final String siteKey;
    private final ImportWriter writer;

    /**
     * @param edit a session on the edit workspace, where the forms stand; localized when the titles are read
     * @param live a system session on the live workspace, where the results are written
     */
    public ResultsAttachment(JCRSessionWrapper edit, JCRSessionWrapper live, String siteKey) {
        this.live = live;
        this.siteKey = siteKey;
        this.writer = new ImportWriter(edit, live, siteKey, ImportJobs.FOLDER_TITLE);
    }

    // --- what the dialog shows ---

    /**
     * The sources an entry holds, for the dialog: each with the title of its imported form when the site still
     * has it, its submissions, the names its values hold now with the title of the field they came from, and the
     * mapping already applied to it.
     */
    public JSONObject describe(String entryId) throws RepositoryException, ImportJobs.RefusedException {
        JCRNodeWrapper entry = importedEntry(entryId);
        JCRNodeWrapper form = writer.formOf(entry);
        JSONArray sources = new JSONArray();
        for (String sourceKey : sourceKeysOf(entry)) {
            List<JCRNodeWrapper> submissions = submissionsOf(entry, sourceKey);
            Map<String, String> mapping = writer.mappingOf(entry, sourceKey);
            JCRNodeWrapper importedForm = writer.findForm(sourceKey, sourceKey);
            Map<String, String> titles = importedForm == null ? Map.of() : titlesOf(importedForm, mapping);
            SampledValues sampled = valuesOf(submissions);
            Map<String, Set<String>> alongside = alongsideOf(submissions);
            JSONArray fields = new JSONArray();
            for (String name : namesOf(submissions)) {
                fields.put(new JSONObject().put("name", name).put("title", titles.getOrDefault(name, ""))
                        .put("values", new JSONArray(sampled.values().getOrDefault(name, Set.of())))
                        .put("moreValues", sampled.more().contains(name))
                        .put("alongside", new JSONArray(alongside.getOrDefault(name, Set.of()))));
            }
            sources.put(new JSONObject()
                    .put("id", sourceKey)
                    .put("title", importedForm == null ? JSONObject.NULL : titleOf(importedForm))
                    .put("submissions", submissions.size())
                    .put("fields", fields)
                    .put(MAPPING_KEY, new JSONObject(mapping)));
        }
        return new JSONObject()
                .put(ENTRY_KEY, entry.getPath())
                .put("name", entry.getName())
                .put(FORM_KEY, form == null ? JSONObject.NULL : form.getPath())
                .put("sources", sources);
    }

    // --- the attachment ---

    /**
     * Refuses what the attachment could not honour, before anything is scheduled: an entry that holds no
     * imported results, a source the entry does not hold, a form that is not a form of the site, and a mapping
     * that sends two fields to one, chains a rename on another, or sends two names a submission holds together
     * to one field. The submissions are read where the run renames them, under the entry and under the entry
     * of the form, so that a run that stopped anywhere runs again.
     */
    public void check(Request request) throws RepositoryException, ImportJobs.RefusedException {
        JCRNodeWrapper entry = importedEntry(request.entryId());
        if (!sourceKeysOf(entry).contains(request.sourceFormId())) {
            throw new ImportJobs.RefusedException("the entry holds no results of the source " + request.sourceFormId());
        }
        JCRNodeWrapper form = writer.findSiteForm(request.formId());
        if (form == null) {
            throw new ImportJobs.RefusedException("the form to attach the results to is not a form of the site");
        }
        List<JCRNodeWrapper> submissions = new ArrayList<>(submissionsOf(entry, request.sourceFormId()));
        JCRNodeWrapper target = writer.findResultsEntry(form);
        if (target != null && !target.isSame(entry)) {
            submissions.addAll(submissionsOf(target, request.sourceFormId()));
        }
        checkMapping(request.mapping(), namesPerSubmission(submissions));
    }

    /**
     * The checks of {@link #check} that need no repository. On the mapping itself: two fields cannot be sent to
     * one, and a target that is itself sent elsewhere is refused, because a rename cannot chain on another in
     * one pass (a chain of renames is two attachments). On the submissions, the landing is judged one by one:
     * two names one submission holds cannot land together, since it would lose a value, while a name the earlier
     * submissions hold alone, because a stopped run renamed them already, lets that run go on; a key no
     * submission holds any more renames nothing and passes.
     */
    static void checkMapping(Map<String, String> mapping, List<Set<String>> submissions) throws ImportJobs.RefusedException {
        Map<String, String> sources = new LinkedHashMap<>();
        for (Map.Entry<String, String> rename : mapping.entrySet()) {
            String twice = sources.put(rename.getValue(), rename.getKey());
            if (twice != null) {
                throw new ImportJobs.RefusedException("the fields " + twice + " and " + rename.getKey() + " cannot both land under " + rename.getValue());
            }
            if (mapping.containsKey(rename.getValue())) {
                throw new ImportJobs.RefusedException("the field " + rename.getKey() + " cannot land under " + rename.getValue()
                        + ", which is sent elsewhere itself: attach twice");
            }
        }
        for (Set<String> held : submissions) {
            Map<String, String> landing = new LinkedHashMap<>();
            for (String name : held) {
                String other = landing.put(mapping.getOrDefault(name, name), name);
                if (other != null) {
                    throw new ImportJobs.RefusedException("the fields " + other + " and " + name + " of one submission cannot both land under "
                            + mapping.getOrDefault(name, name));
                }
            }
        }
    }

    /** Where the submissions of the source ended up: the entry of the form, how many moved there, and whether they left theirs. */
    private record Placement(JCRNodeWrapper target, int moved, boolean merged) {
    }

    /** The distinct values met per field in a sample of submissions, capped as the import report caps them. */
    private record SampledValues(Map<String, Set<String>> values, Set<String> more) {
    }

    /** Runs the attachment and reports what it wrote; the sessions are saved by batches and at the end. */
    public JSONObject run(Request request) throws RepositoryException, ImportJobs.RefusedException {
        check(request);
        JCRNodeWrapper entry = importedEntry(request.entryId());
        JCRNodeWrapper form = writer.findSiteForm(request.formId());
        String sourceKey = request.sourceFormId();
        JCRNodeWrapper previousForm = writer.formOf(entry);
        Map<String, String> previousMapping = writer.mappingOf(entry, sourceKey);
        List<JCRNodeWrapper> submissions = submissionsOf(entry, sourceKey);
        Placement placement = place(entry, form, sourceKey, submissions);
        // the submissions of the source as they stand under the target now: a run that stopped renames the moved ones too
        int renamed = rename(submissionsOf(placement.target(), sourceKey), request.mapping());
        writer.recordMapping(placement.target(), sourceKey, compose(previousMapping, request.mapping()));
        FormResultsAclSyncService.syncAclToFormResults(form, placement.target(), live);
        live.save();
        // the entry they left goes last, once everything it described is in place, so that a stopped run starts over
        boolean previousEntryRemoved = placement.merged() && leave(entry, sourceKey);
        boolean formLeftAlone = previousForm != null && !previousForm.isSame(form) && writer.findResultsEntry(previousForm) == null;
        log.info("[FormsImport] Results of the source {} attached to the form {} in site {}: {} submission(s) moved, {} value(s) renamed",
                sourceKey, form.getPath(), siteKey, placement.moved(), renamed);
        return new JSONObject()
                .put(ENTRY_KEY, placement.target().getPath())
                .put("entryName", placement.target().getName())
                .put(FORM_KEY, form.getPath())
                .put("formName", form.getName())
                .put(SOURCE_KEY, sourceKey)
                .put("submissions", submissions.size())
                .put("moved", placement.moved())
                .put("renamed", renamed)
                .put("previousEntryRemoved", previousEntryRemoved)
                .put("formLeftWithoutResults", formLeftAlone ? previousForm.getPath() : JSONObject.NULL);
    }

    /**
     * Puts the submissions of the source under the form (docs/architecture/forms-import.md, "What the attachment
     * writes"): the entry becomes the entry of the form when the form has none and the entry holds the submissions
     * of this source and nothing else, no native submission and no other source; the submissions stay where they
     * are when the entry is already the entry of the form; otherwise they move into the entry of the form,
     * created when needed, and the caller makes them leave theirs once they are in place.
     */
    private Placement place(JCRNodeWrapper entry, JCRNodeWrapper form, String sourceKey, List<JCRNodeWrapper> submissions)
            throws RepositoryException {
        JCRNodeWrapper target = writer.findResultsEntry(form);
        if (target == null && sourceKeysOf(entry).size() == 1 && countSubmissions(entry) == submissions.size()) {
            relink(entry, form);
            return new Placement(entry, 0, false);
        }
        if (target != null && target.isSame(entry)) {
            return new Placement(entry, 0, false);
        }
        if (target == null) {
            target = writer.createResultsEntry(form, entry.getPropertyAsString(BUILDING_LANG));
        }
        writer.markImported(target, entry.getPropertyAsString(SOURCE_SYSTEM), sourceKey);
        live.save();
        return new Placement(target, move(submissions, target), true);
    }

    /** The entry becomes the entry of the form, under its name when that name is free. */
    private static void relink(JCRNodeWrapper entry, JCRNodeWrapper form) throws RepositoryException {
        entry.setProperty(FmdbProperty.PARENT_FORM, form.getIdentifier());
        if (!form.getName().equals(entry.getName()) && !entry.getParent().hasNode(form.getName())) {
            entry.rename(form.getName());
        }
    }

    /** Moves the submissions under the day folders of the target, saving every batch; the count moved. */
    private int move(List<JCRNodeWrapper> submissions, JCRNodeWrapper target) throws RepositoryException {
        JCRNodeWrapper container = ImportWriter.childOrCreate(target, FmdbNodeName.SUBMISSIONS, FmdbNodeType.SUBMISSIONS);
        int moved = 0;
        for (JCRNodeWrapper submission : submissions) {
            Instant created = submission.getProperty("jcr:created").getDate().toInstant();
            JCRNodeWrapper day = ImportWriter.dayFolder(container, created);
            live.move(submission.getPath(), day.getPath() + "/" + JCRContentUtils.findAvailableNodeName(day, submission.getName()));
            if (++moved % BATCH == 0) {
                live.save();
            }
        }
        live.save();
        return moved;
    }

    /**
     * What the submissions of a source leave behind: the entry, removed once it holds no submission at all; else
     * the source, struck from its keys and its mappings, and the marker of imported results with it once no
     * source is left, since the entry then holds native submissions alone. True when the entry was removed.
     */
    private boolean leave(JCRNodeWrapper entry, String sourceKey) throws RepositoryException {
        if (!holdsSubmissions(entry)) {
            entry.remove();
            live.save();
            return true;
        }
        List<String> keys = new ArrayList<>(sourceKeysOf(entry));
        keys.remove(sourceKey);
        if (keys.isEmpty()) {
            entry.removeMixin(FmdbMixin.IMPORTED_RESULTS);
        } else {
            entry.setProperty(SOURCE_FORM_IDS, keys.toArray(String[]::new));
            writer.forgetMapping(entry, sourceKey);
        }
        live.save();
        return false;
    }

    /**
     * Renames the values of the submissions as the mapping says, by batches: a name already renamed, or absent,
     * is left alone, so that an attachment that stopped half-way runs again. The files of a field follow its name.
     */
    private int rename(List<JCRNodeWrapper> submissions, Map<String, String> mapping) throws RepositoryException {
        if (mapping.isEmpty()) {
            return 0;
        }
        int renamed = 0;
        int touched = 0;
        for (JCRNodeWrapper submission : submissions) {
            int values = renameValues(submission, mapping);
            boolean moved = renameFiles(submission, mapping);
            renamed += values;
            if ((values > 0 || moved) && ++touched % BATCH == 0) {
                live.save();
            }
        }
        live.save();
        return renamed;
    }

    /** Renames the values of one submission as the mapping says; the count renamed. */
    private static int renameValues(JCRNodeWrapper submission, Map<String, String> mapping) throws RepositoryException {
        if (!submission.hasNode(FmdbNodeName.DATA)) {
            return 0;
        }
        JCRNodeWrapper data = submission.getNode(FmdbNodeName.DATA);
        int renamed = 0;
        for (Map.Entry<String, String> name : mapping.entrySet()) {
            if (renameValue(data, name.getKey(), name.getValue())) {
                renamed++;
            }
        }
        return renamed;
    }

    /** Moves the files folders of one submission under the new names of their fields; whether any moved. */
    private boolean renameFiles(JCRNodeWrapper submission, Map<String, String> mapping) throws RepositoryException {
        if (!submission.hasNode(FmdbNodeName.FILES)) {
            return false;
        }
        JCRNodeWrapper files = submission.getNode(FmdbNodeName.FILES);
        boolean moved = false;
        for (Map.Entry<String, String> name : mapping.entrySet()) {
            if (files.hasNode(name.getKey()) && !files.hasNode(name.getValue())) {
                live.move(files.getNode(name.getKey()).getPath(), files.getPath() + "/" + name.getValue());
                moved = true;
            }
        }
        return moved;
    }

    private static boolean renameValue(JCRNodeWrapper data, String from, String to) throws RepositoryException {
        if (!data.hasProperty(from) || data.hasProperty(to)) {
            return false;
        }
        javax.jcr.Property property = data.getProperty(from);
        if (property.isMultiple()) {
            Value[] values = property.getValues();
            String[] texts = new String[values.length];
            for (int i = 0; i < values.length; i++) {
                texts[i] = values[i].getString();
            }
            data.setProperty(to, texts);
        } else {
            data.setProperty(to, property.getString());
        }
        property.remove();
        return true;
    }

    /**
     * The mapping to keep on the entry: from the names the import generated to the names the values hold after
     * this attachment, so that a later run of the import names its values the same way. The names the earlier
     * mapping sent somewhere go on from there; the names it left alone start from themselves.
     */
    static Map<String, String> compose(Map<String, String> earlier, Map<String, String> now) {
        Map<String, String> composed = new LinkedHashMap<>();
        for (Map.Entry<String, String> name : earlier.entrySet()) {
            String landing = now.getOrDefault(name.getValue(), name.getValue());
            if (!landing.equals(name.getKey())) {
                composed.put(name.getKey(), landing);
            }
        }
        Set<String> current = new LinkedHashSet<>(earlier.values());
        for (Map.Entry<String, String> name : now.entrySet()) {
            if (!current.contains(name.getKey()) && !composed.containsKey(name.getKey()) && !name.getValue().equals(name.getKey())) {
                composed.put(name.getKey(), name.getValue());
            }
        }
        return composed;
    }

    // --- the nodes ---

    /** The entry, in the results root of the site, that carries imported results; refused otherwise. */
    private JCRNodeWrapper importedEntry(String entryId) throws RepositoryException, ImportJobs.RefusedException {
        JCRNodeWrapper entry;
        try {
            entry = live.getNodeByIdentifier(entryId);
        } catch (javax.jcr.ItemNotFoundException e) {
            throw new ImportJobs.RefusedException("the entry does not exist any more");
        }
        boolean ofSite = entry.getPath().startsWith(writer.resultsRootPath() + "/");
        if (!ofSite || !entry.isNodeType(FmdbNodeType.FORM_RESULTS) || !entry.isNodeType(FmdbMixin.IMPORTED_RESULTS)) {
            throw new ImportJobs.RefusedException("the entry holds no imported results");
        }
        return entry;
    }

    private static List<String> sourceKeysOf(JCRNodeWrapper entry) throws RepositoryException {
        List<String> keys = new ArrayList<>();
        if (entry.hasProperty(SOURCE_FORM_IDS)) {
            for (Value value : entry.getProperty(SOURCE_FORM_IDS).getValues()) {
                keys.add(value.getString());
            }
        }
        return keys;
    }

    /** The imported submissions of the source under the entry, wherever its day folders put them. */
    static List<JCRNodeWrapper> submissionsOf(JCRNodeWrapper entry, String sourceKey) throws RepositoryException {
        List<JCRNodeWrapper> submissions = new ArrayList<>();
        if (entry.hasNode(FmdbNodeName.SUBMISSIONS)) {
            collect(entry.getNode(FmdbNodeName.SUBMISSIONS), sourceKey, submissions);
        }
        return submissions;
    }

    private static void collect(JCRNodeWrapper parent, String sourceKey, List<JCRNodeWrapper> submissions) throws RepositoryException {
        NodeIterator children = parent.getNodes();
        while (children.hasNext()) {
            JCRNodeWrapper child = (JCRNodeWrapper) children.nextNode();
            if (child.isNodeType(FmdbNodeType.FORM_SUBMISSION)) {
                if (child.isNodeType(FmdbMixin.IMPORTED_SUBMISSION) && sourceKey.equals(child.getPropertyAsString(SOURCE_FORM_ID))) {
                    submissions.add(child);
                }
            } else {
                collect(child, sourceKey, submissions);
            }
        }
    }

    private static boolean holdsSubmissions(JCRNodeWrapper entry) throws RepositoryException {
        return countSubmissions(entry) > 0;
    }

    /** Every submission under the entry, native or imported, whatever its source. */
    static int countSubmissions(JCRNodeWrapper entry) throws RepositoryException {
        return entry.hasNode(FmdbNodeName.SUBMISSIONS) ? countSubmissionsUnder(entry.getNode(FmdbNodeName.SUBMISSIONS)) : 0;
    }

    private static int countSubmissionsUnder(JCRNodeWrapper parent) throws RepositoryException {
        int count = 0;
        NodeIterator children = parent.getNodes();
        while (children.hasNext()) {
            JCRNodeWrapper child = (JCRNodeWrapper) children.nextNode();
            count += child.isNodeType(FmdbNodeType.FORM_SUBMISSION) ? 1 : countSubmissionsUnder(child);
        }
        return count;
    }

    /** The distinct values per field in the sample of the submissions, capped as the import report caps them. */
    private static SampledValues valuesOf(List<JCRNodeWrapper> submissions) throws RepositoryException {
        Map<String, Set<String>> values = new LinkedHashMap<>();
        Set<String> more = new LinkedHashSet<>();
        for (JCRNodeWrapper submission : submissions.subList(0, Math.min(submissions.size(), NAMES_SAMPLE))) {
            if (!submission.hasNode(FmdbNodeName.DATA)) {
                continue;
            }
            PropertyIterator properties = submission.getNode(FmdbNodeName.DATA).getProperties();
            while (properties.hasNext()) {
                javax.jcr.Property property = properties.nextProperty();
                if (!property.getName().contains(":")) {
                    sample(values, more, property);
                }
            }
        }
        return new SampledValues(values, more);
    }

    private static void sample(Map<String, Set<String>> values, Set<String> more, javax.jcr.Property property) throws RepositoryException {
        Set<String> distinct = values.computeIfAbsent(property.getName(), name -> new LinkedHashSet<>());
        Value[] stored = property.isMultiple() ? property.getValues() : new Value[] {property.getValue()};
        for (Value value : stored) {
            String text = value.getString();
            if (text.isBlank()) {
                continue;
            }
            if (distinct.size() >= ImportReport.VALUES_LISTED && !distinct.contains(text)) {
                more.add(property.getName());
            } else {
                distinct.add(text);
            }
        }
    }

    /** The names the values hold, in the order they are met, from a sample of the submissions. */
    static Set<String> namesOf(List<JCRNodeWrapper> submissions) throws RepositoryException {
        Set<String> names = new LinkedHashSet<>();
        namesPerSubmission(submissions.subList(0, Math.min(submissions.size(), NAMES_SAMPLE))).forEach(names::addAll);
        return names;
    }

    /** The names each submission holds values for, one set per submission, every submission read. */
    static List<Set<String>> namesPerSubmission(List<JCRNodeWrapper> submissions) throws RepositoryException {
        List<Set<String>> held = new ArrayList<>();
        for (JCRNodeWrapper submission : submissions) {
            Set<String> names = new LinkedHashSet<>();
            if (submission.hasNode(FmdbNodeName.DATA)) {
                PropertyIterator properties = submission.getNode(FmdbNodeName.DATA).getProperties();
                while (properties.hasNext()) {
                    String name = properties.nextProperty().getName();
                    if (!name.contains(":")) {
                        names.add(name);
                    }
                }
            }
            held.add(names);
        }
        return held;
    }

    /**
     * The names each name stands alongside in a submission of the sample: what the dialog keeps apart, where a
     * stopped run left the earlier submissions under a renamed name and the later ones under the old one.
     */
    static Map<String, Set<String>> alongsideOf(List<JCRNodeWrapper> submissions) throws RepositoryException {
        Map<String, Set<String>> alongside = new LinkedHashMap<>();
        for (Set<String> held : namesPerSubmission(submissions.subList(0, Math.min(submissions.size(), NAMES_SAMPLE)))) {
            for (String name : held) {
                Set<String> others = alongside.computeIfAbsent(name, key -> new LinkedHashSet<>());
                held.stream().filter(other -> !other.equals(name)).forEach(others::add);
            }
        }
        return alongside;
    }

    /**
     * The title of the field each current name came from, from the imported form the site still holds: a name
     * the earlier mapping changed is looked up by the name the form knows.
     */
    private static Map<String, String> titlesOf(JCRNodeWrapper importedForm, Map<String, String> mapping) throws RepositoryException {
        Map<String, String> byOriginal = new LinkedHashMap<>();
        if (importedForm.hasNode(ImportWriter.FIELDS_NODE)) {
            collectTitles(importedForm.getNode(ImportWriter.FIELDS_NODE), byOriginal);
        }
        Map<String, String> byCurrent = new LinkedHashMap<>();
        byOriginal.forEach((original, title) -> byCurrent.put(mapping.getOrDefault(original, original), title));
        return byCurrent;
    }

    private static void collectTitles(JCRNodeWrapper parent, Map<String, String> titles) throws RepositoryException {
        NodeIterator children = parent.getNodes();
        while (children.hasNext()) {
            JCRNodeWrapper child = (JCRNodeWrapper) children.nextNode();
            if (child.isNodeType(FmdbMixin.IMPORTED_FIELD)) {
                titles.put(child.getName(), titleOf(child));
            } else {
                collectTitles(child, titles);
            }
        }
    }

    /** The title of a node in the language of the session, else its name. */
    private static String titleOf(JCRNodeWrapper node) throws RepositoryException {
        String title = node.hasProperty(TITLE) ? node.getPropertyAsString(TITLE) : null;
        return title == null || title.isBlank() ? node.getName() : title;
    }
}
