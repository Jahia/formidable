package org.jahia.modules.formidable.content.action;

import org.jahia.modules.formidable.content.folders.SubmitterFolder;
import org.jahia.modules.formidable.content.mapping.ConfigException;
import org.jahia.modules.formidable.content.mapping.Mappings;
import org.jahia.modules.formidable.content.mapping.PairRule;
import org.jahia.modules.formidable.content.messages.VisitorText;
import org.jahia.modules.formidable.content.page.RefererPages;
import org.jahia.modules.formidable.content.plan.ContentPlan;
import org.jahia.modules.formidable.content.plan.PlanBuilder;
import org.jahia.modules.formidable.content.plan.Settings;
import org.jahia.modules.formidable.content.plan.ValueException;
import org.jahia.modules.formidable.content.platform.Submitters;
import org.jahia.modules.formidable.content.platform.TypeCatalog;
import org.jahia.modules.formidable.content.store.ContentWriter;
import org.jahia.modules.formidable.engine.api.ContextualFormAction;
import org.jahia.modules.formidable.engine.api.FormAction;
import org.jahia.modules.formidable.engine.api.FormActionContext;
import org.jahia.modules.formidable.engine.api.FormActionException;
import org.jahia.modules.formidable.engine.api.FormActionResult;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRSessionWrapper;
import org.jahia.services.content.decorator.JCRSiteNode;
import org.jahia.services.usermanager.JahiaUser;
import org.jahia.services.usermanager.JahiaUserManagerService;
import org.jahia.utils.LanguageCodeConverters;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Creates a content node from the submission of a signed-in user: the action of {@code fmdbcnt:createContentAction}.
 * The values of the form's fields fill the properties of a node of a chosen type, the uploaded files become
 * {@code jnt:file} nodes the content references, and the result is published, in a folder the user's group is entitled to.
 *
 * <p><b>Who may call it, and what stops a misconfigured form.</b> The form is public, the action is not: a guest is
 * refused before anything else. The writes are made with a system session (the submitter holds no write right in the
 * {@code default} workspace; see {@link ContentWriter}), so everything that keeps one company from posting into
 * another's folder happens <em>before</em> the writer is called, and is repeated at every submission:</p>
 * <ol>
 *   <li>the submitter's groups are searched for the one whose name starts with the configured prefix; none, several or
 *       an unusable name refuse the submission ({@link SubmitterFolder});</li>
 *   <li>the subfolder must already exist (the action never creates one: its ACLs are the project's);</li>
 *   <li>the submitter's <em>own live session</em> must be able to read the content subfolder, and the files subfolder
 *       (an unreadable node is a {@code PathNotFoundException} for a user session). The folder named by a group is
 *       therefore only ever used if the folder's ACLs also say the user belongs there, so a form pointing at the wrong
 *       folder, or a group name that matches the wrong folder, writes nothing;</li>
 *   <li>the type must exist, be concrete and be creatable in that folder, every mapped property must be declared by it
 *       (never a protected one, never a {@code jcr:} or {@code rep:} one but the title), every value must convert.</li>
 * </ol>
 *
 * <p>What the visitor reads is generic ({@link VisitorText}); what is wrong goes to the log. The submitter's name,
 * organization and the page they came from are filled by the action from what the platform knows, after, and over,
 * whatever the form's fields said. A {@code Referer} is the visitor's: it is only used to <em>find</em> a page of the
 * same site that the submitter can read in {@code live} ({@link RefererPages}).</p>
 *
 * <p>The action carries no {@code fmdbmix:readOnlyCompatibleAction}: it writes. It never receives sensitive fields
 * ({@code consumesSensitiveValues} stays {@code false}). Threading: a singleton with no per-request state in fields; the
 * submission is the {@link FormActionContext}.</p>
 */
@Component(service = FormAction.class, immediate = true)
public class CreateContentFormAction implements ContextualFormAction {

    private static final Logger log = LoggerFactory.getLogger(CreateContentFormAction.class);

    static final String NODE_TYPE = "fmdbcnt:createContentAction";
    private static final String LIVE = "live";
    private static final Pattern TYPE_NAME = Pattern.compile("[A-Za-z0-9_.-]+:[A-Za-z0-9_.-]+");
    private static final String PAGE_TYPE = "jnt:page";
    private static final String MAIN_RESOURCE = "jmix:mainResource";

    private Submitters submitters;
    private ContentWriter writer;
    private TypeCatalog catalog;

    /**
     * @param submitters the platform's view of the submitter: groups, organization
     */
    @Reference
    public void setSubmitters(Submitters submitters) {
        this.submitters = submitters;
    }

    /**
     * @param writer the one place that writes
     */
    @Reference
    public void setWriter(ContentWriter writer) {
        this.writer = writer;
    }

    /**
     * @param catalog the node types
     */
    @Reference
    public void setCatalog(TypeCatalog catalog) {
        this.catalog = catalog;
    }

    @Override
    public String getNodeType() {
        return NODE_TYPE;
    }

    @Override
    public FormActionResult execute(FormActionContext context) throws FormActionException {
        Locale locale = context.locale();
        JCRSessionWrapper visitor = context.session();

        // 1. Only signed-in users: a guest is refused before the settings are even read
        JahiaUser user = visitor == null ? null : visitor.getUser();
        if (user == null || JahiaUserManagerService.isGuest(user)) {
            throw FormActionException.refusal(401, "create content refused: the submitter is a guest", VisitorText.SIGN_IN_REQUIRED.in(locale));
        }
        if (!isLive(visitor)) {
            log.error("[formidable-content] The submitter's session is not a live session: the submission is refused.");
            throw unavailable(locale);
        }

        JCRNodeWrapper action = context.actionNode();
        try {
            Settings settings = readSettings(action);
            JCRNodeWrapper parentBase = ActionProps.reference(action, "parentFolder");
            JCRNodeWrapper filesBase = ActionProps.reference(action, "filesFolder");
            if (parentBase == null) {
                throw new ConfigException("The action " + action.getPath() + " has no parentFolder");
            }
            if (filesBase == null && !settings.fileMappings().isEmpty()) {
                throw new ConfigException("The action " + action.getPath() + " maps files but has no filesFolder");
            }

            // 2. The submitter's own subfolder, from their group
            String subfolder = subfolderOf(settings, user, context, action);

            // 3. The folders must exist (never created here) and be readable by the submitter themselves, in live
            String parentPath = pathUnder(parentBase, subfolder);
            String filesPath = filesBase == null ? null : pathUnder(filesBase, subfolder);
            requireExisting(parentBase, subfolder, user, locale);
            requireReadable(visitor, parentPath, user, locale);
            if (filesPath != null) {
                requireExisting(filesBase, subfolder, user, locale);
                requireReadable(visitor, filesPath, user, locale);
            }

            // 4. The type is creatable there, the settings fit the types, the values fit the properties
            requireCreatable(parentBase, subfolder, settings.contentType());
            ContentPlan plan = planOf(context, settings, user, new PlanBuilder.Target(parentPath, filesPath), visitor, action);

            ContentWriter.Created created = write(plan, user, locale);
            log.info("[formidable-content] The content {} was created by {} ({} file(s)){}.", created.path(), user.getUsername(),
                    created.filePaths().size(), plan.publish() ? " and published" : "");
            return FormActionResult.none();
        } catch (ConfigException e) {
            log.error("[formidable-content] The submission is refused: the action is misconfigured. {}", e.getMessage(), e.getCause());
            throw unavailable(locale);
        }
    }

    private Settings readSettings(JCRNodeWrapper action) throws ConfigException {
        String type = ActionProps.string(action, "contentType");
        if (type == null || !TYPE_NAME.matcher(type).matches()) {
            throw new ConfigException("The action " + action.getPath() + " has no valid contentType (prefix:name)");
        }
        return new Settings(type,
                ActionProps.string(action, "groupPrefix"),
                ActionProps.string(action, "titleField"),
                Mappings.parse("fieldMappings", ActionProps.lines(action, "fieldMappings")),
                Mappings.parse("fileMappings", ActionProps.lines(action, "fileMappings")),
                ActionProps.string(action, "fileMixin"),
                Mappings.parse("fileProperties", ActionProps.lines(action, "fileProperties")),
                Mappings.parse("fixedValues", ActionProps.lines(action, "fixedValues")),
                ActionProps.string(action, "submitterProperty"),
                ActionProps.string(action, "organizationProperty"),
                ActionProps.string(action, "pageProperty"),
                PairRule.parse(ActionProps.lines(action, "requiredPairs")),
                ActionProps.bool(action, "publish", true));
    }

    private String subfolderOf(Settings settings, JahiaUser user, FormActionContext context, JCRNodeWrapper action) throws FormActionException {
        if (settings.groupPrefix() == null) {
            return null;
        }
        SubmitterFolder.Result result = SubmitterFolder.resolve(settings.groupPrefix(), submitters.groupPaths(user), context.siteKey());
        if (result.outcome() != SubmitterFolder.Outcome.FOUND) {
            log.warn("[formidable-content] {} has no folder through the groups of {} for the prefix '{}' ({}): the submission is refused.",
                    action.getPath(), user.getUsername(), settings.groupPrefix(), result.outcome());
            throw noFolder(context.locale(), "no usable group (" + result.outcome() + ")");
        }
        return result.subfolder();
    }

    private static String pathUnder(JCRNodeWrapper base, String subfolder) {
        return subfolder == null ? base.getPath() : base.getPath() + "/" + subfolder;
    }

    /** The subfolder is never created: it exists in the form's own (system) view of live, or the submission is refused. */
    private static void requireExisting(JCRNodeWrapper base, String subfolder, JahiaUser user, Locale locale) throws FormActionException {
        if (subfolder == null) {
            return;
        }
        try {
            if (!base.hasNode(subfolder)) {
                log.warn("[formidable-content] The folder {}/{} of {} does not exist: the submission is refused. Create it (with its ACLs) first.",
                        base.getPath(), subfolder, user.getUsername());
                throw noFolder(locale, "the subfolder does not exist");
            }
        } catch (RepositoryException e) {
            throw FormActionException.refusal(500, "the folder could not be inspected", VisitorText.FAILED.in(locale), e);
        }
    }

    /**
     * THE check that keeps one company out of another's folder. The session is the submitter's own live session, which
     * applies their ACLs: a folder they may not read is a {@code PathNotFoundException}. Anything else that goes wrong
     * also refuses: a check that could not be made is not a check that passed.
     */
    private static void requireReadable(JCRSessionWrapper visitor, String path, JahiaUser user, Locale locale) throws FormActionException {
        try {
            visitor.getNode(path);
        } catch (RepositoryException e) {
            log.warn("[formidable-content] {} cannot read {} in live: the submission is refused.", user.getUsername(), path);
            throw noFolder(locale, "the submitter cannot read the folder");
        }
    }

    private void requireCreatable(JCRNodeWrapper base, String subfolder, String type) throws ConfigException {
        try {
            JCRNodeWrapper target = subfolder == null ? base : (JCRNodeWrapper) base.getNode(subfolder);
            if (target.getApplicableChildNodeDefinition("submission", type) == null) {
                throw new ConfigException("A node of type " + type + " cannot be created in " + pathUnder(base, subfolder));
            }
        } catch (RepositoryException e) {
            throw new ConfigException("A node of type " + type + " cannot be created in " + pathUnder(base, subfolder), e);
        }
    }

    private ContentPlan planOf(FormActionContext context, Settings settings, JahiaUser user, PlanBuilder.Target target,
                               JCRSessionWrapper visitor, JCRNodeWrapper action) throws ConfigException, FormActionException {
        Locale locale = context.locale();
        String organization = settings.organizationProperty() == null ? null : submitters.organization(user);
        String pageId = settings.pageProperty() == null ? null : pageOf(visitor, context);
        PlanBuilder.Submission submission = new PlanBuilder.Submission(context.parameters(), context.files());
        try {
            return PlanBuilder.build(settings, submission, new PlanBuilder.Identity(user.getUsername(), organization), pageId, target,
                    languagesOf(context), catalog);
        } catch (ValueException e) {
            log.warn("[formidable-content] A submission to {} was refused: {}", action.getPath(), e.getMessage());
            String message = e.kind() == ValueException.Kind.PAIR ? pairMessage(action, locale) : VisitorText.BAD_VALUE.in(locale);
            throw FormActionException.refusal(422, "create content refused: " + e.getMessage(), message);
        }
    }

    private static String pairMessage(JCRNodeWrapper action, Locale locale) {
        try {
            String own = ActionProps.string(action, "pairRefusalMessage");
            return own != null ? own : VisitorText.PAIR_REFUSAL.in(locale);
        } catch (ConfigException e) {
            return VisitorText.PAIR_REFUSAL.in(locale);
        }
    }

    private ContentWriter.Created write(ContentPlan plan, JahiaUser user, Locale locale) throws FormActionException {
        try {
            return writer.create(plan, user);
        } catch (RepositoryException | RuntimeException e) {
            log.error("[formidable-content] The content of {} could not be created in {}: nothing was kept.", user.getUsername(), plan.parentPath(), e);
            throw FormActionException.refusal(500, "create content failed: " + e.getClass().getSimpleName(), VisitorText.FAILED.in(locale), e);
        }
    }

    /**
     * The page the form was submitted from, as an identifier: the first path {@link RefererPages} proposes that the
     * submitter can read in live and that is a page or a main resource. Empty (the property stays unset) otherwise.
     */
    private static String pageOf(JCRSessionWrapper visitor, FormActionContext context) {
        String referer = context.request().getHeader("Referer");
        for (String path : RefererPages.candidatePaths(referer, context.request().getServerName(), context.siteKey())) {
            try {
                JCRNodeWrapper node = visitor.getNode(path);
                if (node.isNodeType(PAGE_TYPE) || node.isNodeType(MAIN_RESOURCE)) {
                    return node.getIdentifier();
                }
            } catch (RepositoryException e) {
                log.debug("[formidable-content] The path {} proposed by the Referer is not a readable page.", path);
            }
        }
        return null;
    }

    /** The site's languages, the default one first: the title and the translated values are written in each. */
    private static List<Locale> languagesOf(FormActionContext context) {
        List<Locale> languages = new ArrayList<>();
        try {
            JCRSiteNode site = context.formNode() == null ? null : context.formNode().getResolveSite();
            if (site != null) {
                Locale first = site.getDefaultLanguage() == null ? null : LanguageCodeConverters.languageCodeToLocale(site.getDefaultLanguage());
                if (first != null) {
                    languages.add(first);
                }
                for (Locale locale : site.getLanguagesAsLocales()) {
                    if (!languages.contains(locale)) {
                        languages.add(locale);
                    }
                }
            }
        } catch (RepositoryException | RuntimeException e) {
            log.warn("[formidable-content] The languages of the site could not be read: the submission's language is used alone.", e);
        }
        if (languages.isEmpty()) {
            languages.add(context.locale());
        }
        return languages;
    }

    private static boolean isLive(JCRSessionWrapper session) {
        try {
            return LIVE.equals(session.getWorkspace().getName());
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static FormActionException noFolder(Locale locale, String internal) {
        return FormActionException.refusal(403, "create content refused: " + internal, VisitorText.NO_FOLDER.in(locale));
    }

    private static FormActionException unavailable(Locale locale) {
        return FormActionException.refusal(503, "create content refused: the action is misconfigured", VisitorText.UNAVAILABLE.in(locale));
    }
}
