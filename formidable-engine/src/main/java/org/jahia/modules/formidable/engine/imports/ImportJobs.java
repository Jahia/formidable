package org.jahia.modules.formidable.engine.imports;

import org.jahia.modules.formidable.engine.util.FormidableJcrConstants;
import org.jahia.modules.formidable.engine.actions.form.storage.SaveToJcrFormAction;
import org.jahia.modules.formidable.engine.api.FmdbNodeType;
import org.jahia.modules.formidable.engine.config.captcha.CaptchaConfigService;
import org.jahia.modules.formidable.engine.config.choiceoptions.ChoiceOptionsConfigService;
import org.jahia.modules.formidable.engine.imports.jahiaforms.FormsExportException;
import org.jahia.modules.formidable.engine.imports.jahiaforms.FormsExportReader;
import org.jahia.modules.formidable.engine.imports.jahiaforms.FormsImportRun;
import org.jahia.modules.formidable.engine.util.JcrFiles;
import org.jahia.registries.ServicesRegistry;
import org.jahia.services.content.JCRCallback;
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
import org.quartz.JobDetail;
import org.quartz.ObjectAlreadyExistsException;
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
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.jahia.modules.formidable.engine.util.FormidableJcrConstants.ACL_NODE;
import static org.jahia.modules.formidable.engine.util.FormidableJcrConstants.ACL_NODE_TYPE;
import static org.jahia.modules.formidable.engine.util.FormidableJcrConstants.INHERIT_PROPERTY;
import static org.jahia.modules.formidable.engine.util.FormidableJcrConstants.SITES;

/**
 * The jobs of an import (docs/architecture/forms-import.md, "Running it"): each one a {@code fmdb:importJob}
 * node under {@code formidable-results/import-jobs} of the site, in live, that holds the uploaded export,
 * the state and the report, so that any server of a cluster reads them; each phase, the dry run and the
 * import, a job of Jahia's persistent scheduler, which runs on the processing server.
 * <p>
 * The import job of a site is scheduled under one name, {@code forms-import-<siteKey>}: the scheduler
 * holds one job of a name at a time, across the cluster, which is what keeps one import at a time per
 * site. A dry run writes nothing and runs under the name of its job node.
 */
@Component(service = ImportJobs.class, immediate = true)
public class ImportJobs {

    public static final String JOBS_NODE = "import-jobs";
    public static final String FILE_NODE = "file";
    /** The title of the content folder the import creates for the forms. */
    public static final String FOLDER_TITLE = "Imported from Jahia Forms";
    /** A dry run nobody imported, or an ended job nobody read, goes after this delay. */
    static final Duration STALE_AFTER = Duration.ofHours(1);
    /**
     * A job node that reads as running while the scheduler holds no active job for it is taken for stopped
     * after this delay, which covers the moment between the node's save and its schedule.
     */
    static final Duration STOPPED_AFTER = Duration.ofMinutes(1);
    static final String ANOTHER_IMPORT_RUNS = "another import is running on this site";
    static final String STOPPED = "the job stopped before it ended: the server restarted, or the module was "
            + "redeployed meanwhile. Try again.";

    static final String DATA_SITE = "importSiteKey";
    static final String DATA_JOB = "importJobId";
    static final String DATA_PHASE = "importPhase";
    private static final String STATE = "state";
    private static final String REPORT = "report";
    private static final String MESSAGE = "message";
    private static final String UPDATED = "updated";
    private static final String JOB_NAME_PREFIX = "forms-import-";
    /** The temporary copy of the export a phase reads, on the disk of the processing server. */
    private static final String EXPORT_COPY_PREFIX = "forms-import-export-";
    private static final String ZIP = "application/zip";
    private static final Set<String> ACTIVE_STATUSES = Set.of(BackgroundJob.STATUS_ADDED, BackgroundJob.STATUS_SCHEDULED,
            BackgroundJob.STATUS_EXECUTING);
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

        public RefusedException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private final AtomicReference<ChoiceOptionsConfigService> optionsConfig = new AtomicReference<>();
    private final AtomicReference<CaptchaConfigService> captchaConfig = new AtomicReference<>();

    @Reference
    public void setOptionsConfig(ChoiceOptionsConfigService service) {
        optionsConfig.set(service);
    }

    @Reference
    public void setCaptchaConfig(CaptchaConfigService service) {
        captchaConfig.set(service);
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

    /**
     * Stores the upload in a new job node and starts its dry run. The stream is read into the node's
     * binary by the repository: a stream that fails to read fails the request, with the cause.
     */
    public JobView create(String siteKey, String fileName, InputStream file, String mimeType) throws RepositoryException {
        reconcile(siteKey);
        String jobId = "job-" + JOB_NAMES.format(Instant.now()) + "-" + UUID.randomUUID().toString().substring(0, 3);
        JobView created = inLive(session -> {
            JCRNodeWrapper jobs = jobsNode(session, siteKey, true);
            JCRNodeWrapper job = jobs.addNode(JCRContentUtils.findAvailableNodeName(jobs, jobId), FmdbNodeType.IMPORT_JOB);
            JcrFiles.addFile(job, FILE_NODE, file, mimeType == null ? ZIP : mimeType);
            job.setProperty(STATE, State.ANALYSING.stored());
            job.setProperty(UPDATED, now());
            session.save();
            log.info("[FormsImport] Job {} created for site {} from the upload '{}'", job.getName(), siteKey, fileName);
            return view(job, siteKey);
        });
        try {
            schedule(siteKey, created.id(), Phase.DRY_RUN);
        } catch (SchedulerException e) {
            throw notScheduled(siteKey, created.id(), e);
        }
        return created;
    }

    public Optional<JobView> get(String siteKey, String jobId) throws RepositoryException {
        return inLive(session -> {
            JCRNodeWrapper job = jobNode(session, siteKey, jobId);
            return job == null ? Optional.empty() : Optional.of(view(job, siteKey));
        });
    }

    /** The job the dialog should open on: running, or ended and not yet closed; the most recent one. */
    public Optional<JobView> open(String siteKey) throws RepositoryException {
        reconcile(siteKey);
        return inLive(session -> {
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
        refuseWhileImportRuns(siteKey);
        JobView started = inLive(session -> {
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
        try {
            schedule(siteKey, jobId, Phase.IMPORT);
        } catch (ObjectAlreadyExistsException e) {
            // two Import clicks at the same moment: the scheduler took the first, this one goes back to its review
            update(siteKey, jobId, State.REVIEW, null, null, false);
            throw new RefusedException(ANOTHER_IMPORT_RUNS);
        } catch (SchedulerException e) {
            throw notScheduled(siteKey, jobId, e);
        }
        return started;
    }

    /**
     * Removes the job: a dry run cancelled, even while it runs since it writes nothing, or a report read.
     * Refused while the import runs.
     */
    public void close(String siteKey, String jobId) throws RepositoryException, RefusedException {
        boolean refused = inLive(session -> {
            JCRNodeWrapper job = jobNode(session, siteKey, jobId);
            if (job == null) {
                return false;
            }
            if (State.of(job.getPropertyAsString(STATE)) == State.IMPORTING) {
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

    // --- the work of a job ---

    /** Runs one phase of a job: called by {@link FormsImportJob} on the processing server. */
    void run(String siteKey, String jobId, Phase phase) {
        Path export = null;
        try {
            export = Files.createTempFile(EXPORT_COPY_PREFIX, ".zip");
            if (!download(siteKey, jobId, export)) {
                log.info("[FormsImport] Job {} of site {} was closed before its {} ran", jobId, siteKey, phase);
                return;
            }
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
        return JCRTemplate.getInstance().doExecuteWithSystemSessionAsUser(null, FormidableJcrConstants.WORKSPACE_EDIT, null, edit -> {
            JCRSessionWrapper live = JCRSessionFactory.getInstance().getCurrentSystemSession(FormidableJcrConstants.WORKSPACE_LIVE, null, null);
            try (FormsExportReader reader = FormsExportReader.open(export)) {
                ImportWriter writer = new ImportWriter(edit, live, siteKey, FOLDER_TITLE);
                FormsImportRun run = new FormsImportRun(reader, writer, phase == Phase.DRY_RUN,
                        type -> hasNodeType(edit, type), this::isOptionsSourceDeclared, isCaptchaConfigured());
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

    /** A captcha the forms can use: its widget and its verification are both configured. */
    private boolean isCaptchaConfigured() {
        CaptchaConfigService config = captchaConfig.get();
        return config != null && config.isCaptchaWidgetConfigured() && config.isCaptchaVerificationConfigured();
    }

    private static boolean hasNodeType(JCRSessionWrapper session, String type) {
        try {
            return session.getWorkspace().getNodeTypeManager().hasNodeType(type);
        } catch (RepositoryException e) {
            return false;
        }
    }

    /** Copies the export of the job to the file; false when the job was closed meanwhile. */
    private static boolean download(String siteKey, String jobId, Path target) throws RepositoryException {
        return inLive(session -> {
            JCRNodeWrapper job = jobNode(session, siteKey, jobId);
            if (job == null || !job.hasNode(FILE_NODE)) {
                return false;
            }
            try (InputStream in = job.getNode(FILE_NODE).getFileContent().downloadFile()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                throw new RepositoryException("The export of job " + jobId + " cannot be read", e);
            }
            return true;
        });
    }

    private static void update(String siteKey, String jobId, State state, String report, String message, boolean dropFile)
            throws RepositoryException {
        inLive(session -> {
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

    // --- the scheduler ---

    /** The name of the scheduler job of a phase: one per site for the import, one per job node for a dry run. */
    static String jobName(String siteKey, String jobId, Phase phase) {
        return phase == Phase.IMPORT ? JOB_NAME_PREFIX + siteKey : JOB_NAME_PREFIX + "dry-run-" + jobId;
    }

    private static void schedule(String siteKey, String jobId, Phase phase) throws SchedulerException {
        // createJahiaJob takes a description and generates the name: the name is what makes the import one per site
        JobDetail detail = BackgroundJob.createJahiaJob(JOB_NAME_PREFIX + phase.name().toLowerCase(Locale.ROOT) + " " + jobId,
                FormsImportJob.class);
        detail.setName(jobName(siteKey, jobId, phase));
        detail.setRequestsRecovery(true);
        detail.getJobDataMap().put(DATA_SITE, siteKey);
        detail.getJobDataMap().put(DATA_JOB, jobId);
        detail.getJobDataMap().put(DATA_PHASE, phase.name());
        scheduler().scheduleJobNow(detail);
    }

    private static RepositoryException notScheduled(String siteKey, String jobId, SchedulerException e) throws RepositoryException {
        update(siteKey, jobId, State.FAILED, null, "the job could not be scheduled: " + e.getMessage(), false);
        return new RepositoryException("The import job could not be scheduled", e);
    }

    /**
     * Refuses while the scheduler holds an active import job of the site, on any server. The job is
     * durable, so an ended one still holds the name: it is dropped to free it.
     */
    private static void refuseWhileImportRuns(String siteKey) throws RefusedException {
        String name = jobName(siteKey, null, Phase.IMPORT);
        try {
            JobDetail existing = scheduler().getScheduler().getJobDetail(name, group());
            if (existing == null) {
                return;
            }
            if (isActive(existing)) {
                throw new RefusedException(ANOTHER_IMPORT_RUNS);
            }
            scheduler().getScheduler().deleteJob(name, group());
        } catch (SchedulerException e) {
            throw new RefusedException("the import job of site " + siteKey + " cannot be read, the import is refused: " + e.getMessage(), e);
        }
    }

    /** Whether the scheduler still holds, active, the job of the phase a node says is running. */
    private static boolean isSchedulerJobActive(JobView job) {
        Phase phase = job.state() == State.ANALYSING ? Phase.DRY_RUN : Phase.IMPORT;
        try {
            JobDetail detail = scheduler().getScheduler().getJobDetail(jobName(job.siteKey(), job.id(), phase), group());
            return detail != null && isActive(detail) && job.id().equals(detail.getJobDataMap().getString(DATA_JOB));
        } catch (SchedulerException e) {
            log.warn("[FormsImport] Cannot read the scheduler job of {} on site {}, assuming it runs", job.id(), job.siteKey(), e);
            return true;
        }
    }

    /** A job added, scheduled or executing; a job that ended keeps its status in its data. */
    private static boolean isActive(JobDetail detail) {
        String status = detail.getJobDataMap().getString(BackgroundJob.JOB_STATUS);
        return status == null || ACTIVE_STATUSES.contains(status);
    }

    private static String group() {
        return BackgroundJob.getGroupName(FormsImportJob.class);
    }

    private static SchedulerService scheduler() {
        return ServicesRegistry.getInstance().getSchedulerService();
    }

    // --- the nodes ---

    /**
     * Brings the job nodes of the site in line with what happened: an ended job nobody closed in time is
     * removed, and a job that reads as running while the scheduler holds no active job for it, because
     * the module stopped between the schedule and the run, is marked failed so that it stops reopening.
     */
    private static void reconcile(String siteKey) throws RepositoryException {
        inLive(session -> {
            boolean changed = false;
            Instant now = Instant.now();
            for (JCRNodeWrapper job : jobNodes(session, siteKey)) {
                JobView view = view(job, siteKey);
                if (view.state().running()) {
                    if (view.updated().plus(STOPPED_AFTER).isBefore(now) && !isSchedulerJobActive(view)) {
                        log.warn("[FormsImport] Job {} of site {} reads as {} since {} and the scheduler holds no job for it: failed",
                                view.id(), siteKey, view.state().stored(), view.updated());
                        job.setProperty(STATE, State.FAILED.stored());
                        job.setProperty(MESSAGE, STOPPED);
                        job.setProperty(UPDATED, now());
                        changed = true;
                    }
                } else if (view.updated().plus(STALE_AFTER).isBefore(now)) {
                    log.info("[FormsImport] Job {} of site {} ended {} and was never closed: removed", view.id(), siteKey, view.updated());
                    job.remove();
                    changed = true;
                }
            }
            if (changed) {
                session.save();
            }
            return null;
        });
    }

    /** The jobs node of the site, the child of the results root of its type, whatever its name; null when absent and not created. */
    private static JCRNodeWrapper jobsNode(JCRSessionWrapper live, String siteKey, boolean create) throws RepositoryException {
        JCRNodeWrapper site = live.getNode(SITES + siteKey);
        if (!create && !site.hasNode(SaveToJcrFormAction.RESULTS_ROOT_NAME)) {
            return null;
        }
        JCRNodeWrapper root = SaveToJcrFormAction.getOrCreateResultsRoot(site, live);
        NodeIterator children = root.getNodes();
        while (children.hasNext()) {
            JCRNodeWrapper child = (JCRNodeWrapper) children.nextNode();
            if (child.isNodeType(FmdbNodeType.IMPORT_JOBS)) {
                return child;
            }
        }
        if (!create) {
            return null;
        }
        // a form named import-jobs owns that entry: the jobs node then takes the next free name
        JCRNodeWrapper jobs = root.addNode(JCRContentUtils.findAvailableNodeName(root, JOBS_NODE), FmdbNodeType.IMPORT_JOBS);
        JCRNodeWrapper acl = jobs.addNode(ACL_NODE, ACL_NODE_TYPE);
        acl.setProperty(INHERIT_PROPERTY, false);
        return jobs;
    }

    private static List<JCRNodeWrapper> jobNodes(JCRSessionWrapper live, String siteKey) throws RepositoryException {
        List<JCRNodeWrapper> jobs = new ArrayList<>();
        JCRNodeWrapper jobsNode = jobsNode(live, siteKey, false);
        if (jobsNode == null) {
            return jobs;
        }
        NodeIterator children = jobsNode.getNodes();
        while (children.hasNext()) {
            JCRNodeWrapper child = (JCRNodeWrapper) children.nextNode();
            if (child.isNodeType(FmdbNodeType.IMPORT_JOB)) {
                jobs.add(child);
            }
        }
        return jobs;
    }

    /** The job node of that id, and only a job node: nothing else under the jobs node is ever handed out. */
    private static JCRNodeWrapper jobNode(JCRSessionWrapper live, String siteKey, String jobId) throws RepositoryException {
        if (jobId == null || jobId.contains("/")) {
            return null;
        }
        JCRNodeWrapper jobsNode = jobsNode(live, siteKey, false);
        if (jobsNode == null || !jobsNode.hasNode(jobId)) {
            return null;
        }
        JCRNodeWrapper job = jobsNode.getNode(jobId);
        return job.isNodeType(FmdbNodeType.IMPORT_JOB) ? job : null;
    }

    private static JobView view(JCRNodeWrapper job, String siteKey) throws RepositoryException {
        Instant updated = job.hasProperty(UPDATED) ? job.getProperty(UPDATED).getDate().toInstant() : Instant.EPOCH;
        return new JobView(job.getName(), siteKey, State.of(job.getPropertyAsString(STATE)),
                job.getPropertyAsString(REPORT), job.getPropertyAsString(MESSAGE), updated);
    }

    private static <T> T inLive(JCRCallback<T> callback) throws RepositoryException {
        return JCRTemplate.getInstance().doExecuteWithSystemSessionAsUser(null, FormidableJcrConstants.WORKSPACE_LIVE, null, callback);
    }

    private static Calendar now() {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(System.currentTimeMillis());
        return calendar;
    }
}
