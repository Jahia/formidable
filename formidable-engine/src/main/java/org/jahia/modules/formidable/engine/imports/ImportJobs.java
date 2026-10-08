package org.jahia.modules.formidable.engine.imports;

import org.jahia.modules.formidable.engine.util.FormidableJcrConstants;
import org.jahia.modules.formidable.engine.actions.form.storage.SaveToJcrFormAction;
import org.jahia.modules.formidable.engine.api.FmdbNodeType;
import org.jahia.modules.formidable.engine.config.choiceoptions.ChoiceOptionsConfigService;
import org.jahia.modules.formidable.engine.imports.jahiaforms.FormsExportException;
import org.jahia.modules.formidable.engine.imports.jahiaforms.FormsExportReader;
import org.jahia.modules.formidable.engine.imports.jahiaforms.FormsImportRun;
import org.jahia.registries.ServicesRegistry;
import org.jahia.services.content.JCRContentUtils;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRSessionFactory;
import org.jahia.services.content.JCRSessionWrapper;
import org.jahia.services.content.JCRTemplate;
import org.jahia.services.scheduler.BackgroundJob;
import org.jahia.services.scheduler.SchedulerService;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.SchedulerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.jahia.modules.formidable.engine.util.FormidableJcrConstants.ACL_NODE;
import static org.jahia.modules.formidable.engine.util.FormidableJcrConstants.ACL_NODE_TYPE;
import static org.jahia.modules.formidable.engine.util.FormidableJcrConstants.INHERIT_PROPERTY;

/**
 * The jobs of an import (docs/architecture/forms-import.md, "Running it"): each one a {@code fmdb:importJob}
 * node under {@code formidable-results/import-jobs} of the site, in live, that holds the uploaded export,
 * the state and the report, so that any server of a cluster reads them; each phase, the dry run and the
 * import, a job of Jahia's persistent scheduler, which runs on the processing server.
 */
@Component(service = ImportJobs.class, immediate = true)
public class ImportJobs {

    public static final String JOBS_NODE = "import-jobs";
    public static final String FILE_NODE = "file";
    /** The title of the content folder the import creates for the forms. */
    public static final String FOLDER_TITLE = "Imported from Jahia Forms";
    /** A dry run nobody imported, or an ended job nobody read, goes after this delay. */
    static final Duration STALE_AFTER = Duration.ofHours(1);

    static final String DATA_SITE = "importSiteKey";
    static final String DATA_JOB = "importJobId";
    static final String DATA_PHASE = "importPhase";
    private static final String STATE = "state";
    private static final String REPORT = "report";
    private static final String MESSAGE = "message";
    private static final String UPDATED = "updated";
    private static final DateTimeFormatter JOB_NAMES = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC);
    private static final Logger log = LoggerFactory.getLogger(ImportJobs.class);
    private static final AtomicReference<ImportJobs> INSTANCE = new AtomicReference<>();

    /** The state of a job, as the dialog shows it. */
    public enum State {
        ANALYSING, REVIEW, IMPORTING, DONE, FAILED;

        String stored() {
            return name().toLowerCase(Locale.ROOT);
        }

        static State of(String stored) {
            return stored == null ? FAILED : valueOf(stored.toUpperCase(Locale.ROOT));
        }

        public boolean running() {
            return this == ANALYSING || this == IMPORTING;
        }
    }

    public enum Phase { DRY_RUN, IMPORT }

    /** What the dialog reads of a job. */
    public record JobView(String id, String siteKey, State state, String report, String message, Instant updated) {
    }

    /** A request the job cannot honour in its state: the dialog shows the reason. */
    public static class RefusedException extends Exception {
        public RefusedException(String message) {
            super(message);
        }
    }

    private final AtomicReference<ChoiceOptionsConfigService> optionsConfig = new AtomicReference<>();

    @Reference
    public void setOptionsConfig(ChoiceOptionsConfigService service) {
        optionsConfig.set(service);
    }

    @Activate
    public void activate() {
        INSTANCE.set(this);
    }

    @Deactivate
    public void deactivate() {
        INSTANCE.compareAndSet(this, null);
    }

    /** The active component, for the scheduler's job, which Quartz instantiates on its own. */
    static ImportJobs current() {
        ImportJobs jobs = INSTANCE.get();
        if (jobs == null) {
            throw new IllegalStateException("The import jobs service is not active");
        }
        return jobs;
    }

    // --- the requests of the dialog ---

    /** Stores the upload in a new job node and starts its dry run. */
    public JobView create(String siteKey, String fileName, InputStream file, String mimeType) throws RepositoryException {
        purgeStale(siteKey);
        String jobId = "job-" + JOB_NAMES.format(Instant.now()) + "-" + UUID.randomUUID().toString().substring(0, 3);
        JobView created = JCRTemplate.getInstance().doExecuteWithSystemSession(null, FormidableJcrConstants.WORKSPACE_LIVE, session -> {
            JCRNodeWrapper jobs = jobsNode(session, siteKey);
            JCRNodeWrapper job = jobs.addNode(JCRContentUtils.findAvailableNodeName(jobs, jobId), FmdbNodeType.IMPORT_JOB);
            job.uploadFile(FILE_NODE, file, mimeType == null ? "application/zip" : mimeType);
            job.setProperty(STATE, State.ANALYSING.stored());
            job.setProperty(UPDATED, now());
            session.save();
            log.info("[FormsImport] Job {} created for site {} from the upload '{}'", job.getName(), siteKey, fileName);
            return view(job, siteKey);
        });
        schedule(siteKey, created.id(), Phase.DRY_RUN);
        return created;
    }

    public Optional<JobView> get(String siteKey, String jobId) throws RepositoryException {
        return JCRTemplate.getInstance().doExecuteWithSystemSession(null, FormidableJcrConstants.WORKSPACE_LIVE, session -> {
            JCRNodeWrapper job = jobNode(session, siteKey, jobId);
            return job == null ? Optional.empty() : Optional.of(view(job, siteKey));
        });
    }

    /** The job the dialog should open on: running, or ended and not yet closed; the most recent one. */
    public Optional<JobView> open(String siteKey) throws RepositoryException {
        purgeStale(siteKey);
        return JCRTemplate.getInstance().doExecuteWithSystemSession(null, FormidableJcrConstants.WORKSPACE_LIVE, session -> {
            JobView latest = null;
            for (JCRNodeWrapper job : jobNodes(session, siteKey)) {
                JobView candidate = view(job, siteKey);
                if (latest == null || candidate.updated().isAfter(latest.updated())) {
                    latest = candidate;
                }
            }
            return Optional.ofNullable(latest);
        });
    }

    /** Starts the import of a reviewed dry run, one import at a time per site. */
    public JobView startImport(String siteKey, String jobId) throws RepositoryException, RefusedException {
        if (isImportRunning(siteKey)) {
            throw new RefusedException("another import is running on this site");
        }
        JobView started = JCRTemplate.getInstance().doExecuteWithSystemSession(null, FormidableJcrConstants.WORKSPACE_LIVE, session -> {
            JCRNodeWrapper job = jobNode(session, siteKey, jobId);
            if (job == null) {
                return null;
            }
            if (State.of(job.getPropertyAsString(STATE)) != State.REVIEW) {
                return view(job, siteKey);
            }
            job.setProperty(STATE, State.IMPORTING.stored());
            job.setProperty(UPDATED, now());
            session.save();
            return view(job, siteKey);
        });
        if (started == null) {
            throw new RefusedException("the job does not exist any more");
        }
        if (started.state() != State.IMPORTING) {
            throw new RefusedException("the job is not reviewed (" + started.state().stored() + ")");
        }
        schedule(siteKey, jobId, Phase.IMPORT);
        return started;
    }

    /** Removes the job: a dry run cancelled, or a report read. Refused while the import runs. */
    public void close(String siteKey, String jobId) throws RepositoryException, RefusedException {
        boolean refused = JCRTemplate.getInstance().doExecuteWithSystemSession(null, FormidableJcrConstants.WORKSPACE_LIVE, session -> {
            JCRNodeWrapper job = jobNode(session, siteKey, jobId);
            if (job == null) {
                return false;
            }
            if (State.of(job.getPropertyAsString(STATE)).running()) {
                return true;
            }
            job.remove();
            session.save();
            return false;
        });
        if (refused) {
            throw new RefusedException("the import is running: it cannot be closed before it ends");
        }
    }

    /** Whether an import job of the site is scheduled or executing, on any server. */
    public boolean isImportRunning(String siteKey) {
        try {
            for (JobDetail job : scheduler().getAllActiveJobs(BackgroundJob.getGroupName(FormsImportJob.class))) {
                JobDataMap data = job.getJobDataMap();
                if (siteKey.equals(data.getString(DATA_SITE)) && Phase.IMPORT.name().equals(data.getString(DATA_PHASE))) {
                    return true;
                }
            }
            return false;
        } catch (SchedulerException e) {
            log.warn("[FormsImport] Cannot read the active jobs, assuming an import runs on site {}", siteKey, e);
            return true;
        }
    }

    // --- the work of a job ---

    /** Runs one phase of a job: called by {@link FormsImportJob} on the processing server. */
    void run(String siteKey, String jobId, Phase phase) {
        Path export = null;
        try {
            export = Files.createTempFile("forms-import-", ".zip");
            download(siteKey, jobId, export);
            ImportReport report = runOn(export, siteKey, phase);
            update(siteKey, jobId, phase == Phase.DRY_RUN ? State.REVIEW : State.DONE, report.toJson().toString(), null,
                    phase == Phase.IMPORT);
            log.info("[FormsImport] Job {} of site {}: {} ended, {}", jobId, siteKey, phase, report.toJson().getJSONObject("totals"));
        } catch (Exception e) {
            log.error("[FormsImport] Job {} of site {}: {} failed", jobId, siteKey, phase, e);
            try {
                update(siteKey, jobId, State.FAILED, null, failureMessage(e), phase == Phase.IMPORT);
            } catch (RepositoryException inner) {
                log.error("[FormsImport] Job {} of site {}: the failure could not be recorded", jobId, siteKey, inner);
            }
        } finally {
            if (export != null) {
                try {
                    Files.deleteIfExists(export);
                } catch (IOException e) {
                    log.warn("[FormsImport] Temporary file {} not deleted", export, e);
                }
            }
        }
    }

    private ImportReport runOn(Path export, String siteKey, Phase phase) throws RepositoryException {
        return JCRTemplate.getInstance().doExecuteWithSystemSession(null, FormidableJcrConstants.WORKSPACE_EDIT, edit -> {
            JCRSessionWrapper live = JCRSessionFactory.getInstance().getCurrentSystemSession(FormidableJcrConstants.WORKSPACE_LIVE, null, null);
            try (FormsExportReader reader = FormsExportReader.open(export)) {
                ImportWriter writer = new ImportWriter(edit, live, siteKey, FOLDER_TITLE);
                FormsImportRun run = new FormsImportRun(reader, writer, phase == Phase.DRY_RUN,
                        type -> hasNodeType(edit, type), this::isOptionsSourceDeclared);
                return run.run();
            } catch (IOException | FormsExportException e) {
                throw new RepositoryException(e.getMessage(), e);
            }
        });
    }

    private boolean isOptionsSourceDeclared(String key) {
        ChoiceOptionsConfigService config = optionsConfig.get();
        return config != null && config.resolveOptionsSource(key).isPresent();
    }

    private static boolean hasNodeType(JCRSessionWrapper session, String type) {
        try {
            return session.getWorkspace().getNodeTypeManager().hasNodeType(type);
        } catch (RepositoryException e) {
            return false;
        }
    }

    private void download(String siteKey, String jobId, Path target) throws RepositoryException {
        JCRTemplate.getInstance().doExecuteWithSystemSession(null, FormidableJcrConstants.WORKSPACE_LIVE, session -> {
            JCRNodeWrapper job = jobNode(session, siteKey, jobId);
            if (job == null || !job.hasNode(FILE_NODE)) {
                throw new RepositoryException("The job " + jobId + " holds no export file any more");
            }
            try (InputStream in = job.getNode(FILE_NODE).getFileContent().downloadFile()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                throw new RepositoryException("The export of job " + jobId + " cannot be read", e);
            }
            return null;
        });
    }

    private void update(String siteKey, String jobId, State state, String report, String message, boolean dropFile)
            throws RepositoryException {
        JCRTemplate.getInstance().doExecuteWithSystemSession(null, FormidableJcrConstants.WORKSPACE_LIVE, session -> {
            JCRNodeWrapper job = jobNode(session, siteKey, jobId);
            if (job == null) {
                log.warn("[FormsImport] Job {} of site {} is gone, its {} state is lost", jobId, siteKey, state);
                return null;
            }
            job.setProperty(STATE, state.stored());
            job.setProperty(UPDATED, now());
            if (report != null) {
                job.setProperty(REPORT, report);
            }
            if (message != null) {
                job.setProperty(MESSAGE, message);
            }
            if (dropFile && job.hasNode(FILE_NODE)) {
                job.getNode(FILE_NODE).remove();
            }
            session.save();
            return null;
        });
    }

    private static String failureMessage(Exception e) {
        Throwable cause = e;
        while (cause.getCause() != null && (cause instanceof RepositoryException || cause instanceof RuntimeException)) {
            cause = cause.getCause();
        }
        return cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
    }

    // --- the nodes and the scheduler ---

    private void schedule(String siteKey, String jobId, Phase phase) throws RepositoryException {
        JobDetail detail = BackgroundJob.createJahiaJob("forms-import-" + phase.name().toLowerCase(Locale.ROOT) + "-" + jobId, FormsImportJob.class);
        detail.setRequestsRecovery(true);
        detail.getJobDataMap().put(DATA_SITE, siteKey);
        detail.getJobDataMap().put(DATA_JOB, jobId);
        detail.getJobDataMap().put(DATA_PHASE, phase.name());
        try {
            scheduler().scheduleJobNow(detail);
        } catch (SchedulerException e) {
            update(siteKey, jobId, State.FAILED, null, "the job could not be scheduled: " + e.getMessage(), false);
            throw new RepositoryException("The import job could not be scheduled", e);
        }
    }

    private static SchedulerService scheduler() {
        return ServicesRegistry.getInstance().getSchedulerService();
    }

    private void purgeStale(String siteKey) throws RepositoryException {
        JCRTemplate.getInstance().doExecuteWithSystemSession(null, FormidableJcrConstants.WORKSPACE_LIVE, session -> {
            boolean removed = false;
            for (JCRNodeWrapper job : jobNodes(session, siteKey)) {
                JobView view = view(job, siteKey);
                if (!view.state().running() && view.updated().plus(STALE_AFTER).isBefore(Instant.now())) {
                    log.info("[FormsImport] Job {} of site {} ended {} and was never closed: removed", view.id(), siteKey, view.updated());
                    job.remove();
                    removed = true;
                }
            }
            if (removed) {
                session.save();
            }
            return null;
        });
    }

    private static JCRNodeWrapper jobsNode(JCRSessionWrapper live, String siteKey) throws RepositoryException {
        JCRNodeWrapper root = SaveToJcrFormAction.getOrCreateResultsRoot(live.getNode("/sites/" + siteKey), live);
        if (root.hasNode(JOBS_NODE)) {
            return root.getNode(JOBS_NODE);
        }
        JCRNodeWrapper jobs = root.addNode(JOBS_NODE, FmdbNodeType.IMPORT_JOBS);
        JCRNodeWrapper acl = jobs.addNode(ACL_NODE, ACL_NODE_TYPE);
        acl.setProperty(INHERIT_PROPERTY, false);
        return jobs;
    }

    private static List<JCRNodeWrapper> jobNodes(JCRSessionWrapper live, String siteKey) throws RepositoryException {
        String path = "/sites/" + siteKey + "/" + SaveToJcrFormAction.RESULTS_ROOT_NAME + "/" + JOBS_NODE;
        List<JCRNodeWrapper> jobs = new ArrayList<>();
        if (!live.nodeExists(path)) {
            return jobs;
        }
        NodeIterator children = live.getNode(path).getNodes();
        while (children.hasNext()) {
            JCRNodeWrapper child = (JCRNodeWrapper) children.nextNode();
            if (child.isNodeType(FmdbNodeType.IMPORT_JOB)) {
                jobs.add(child);
            }
        }
        return jobs;
    }

    private static JCRNodeWrapper jobNode(JCRSessionWrapper live, String siteKey, String jobId) throws RepositoryException {
        if (jobId == null || jobId.contains("/")) {
            return null;
        }
        String path = "/sites/" + siteKey + "/" + SaveToJcrFormAction.RESULTS_ROOT_NAME + "/" + JOBS_NODE + "/" + jobId;
        return live.nodeExists(path) ? live.getNode(path) : null;
    }

    private static JobView view(JCRNodeWrapper job, String siteKey) throws RepositoryException {
        Instant updated = job.hasProperty(UPDATED) ? job.getProperty(UPDATED).getDate().toInstant() : Instant.EPOCH;
        return new JobView(job.getName(), siteKey, State.of(job.getPropertyAsString(STATE)),
                job.getPropertyAsString(REPORT), job.getPropertyAsString(MESSAGE), updated);
    }

    private static Calendar now() {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(System.currentTimeMillis());
        return calendar;
    }
}
