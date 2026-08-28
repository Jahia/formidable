package org.jahia.modules.formidable.engine.migration;

import org.apache.commons.lang.StringUtils;
import org.jahia.services.content.JCRNodeIteratorWrapper;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRSessionWrapper;
import org.jahia.services.content.JCRTemplate;
import org.jahia.services.content.decorator.JCRSiteNode;
import org.jahia.services.content.nodetypes.ExtendedNodeType;
import org.jahia.services.content.nodetypes.ExtendedPropertyDefinition;
import org.jahia.services.content.nodetypes.NodeTypeRegistry;
import org.jahia.services.observation.JahiaEventListener;
import org.jahia.services.templates.JahiaTemplateManagerService.TemplatePackageRedeployedEvent;
import org.jahia.utils.LanguageCodeConverters;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Value;
import javax.jcr.query.Query;
import java.util.Collection;
import java.util.EventObject;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.function.BiFunction;

/**
 * One-shot content migration for the field and action lists of a form ('fields' and
 * 'actions', autocreated with the form): lists created before they carried a title
 * (mix:title, jcr:title defaulting to the type label) get that default title in every
 * language of their site — the Page Builder box bar and the create-button tooltips show
 * the node's display name, which otherwise falls back to the bare node name.
 *
 * Only a MISSING or blank title is written: a title a contributor already set is never
 * touched, so re-running (every engine restart runs it) is a no-op once every list has
 * a title in every site language. Runs on BOTH workspaces (default and live) so
 * published forms show the title without a republish. Keyed on CONTENT state, NOT on
 * the previously installed module version.
 *
 * The default title is the one the elements module declares on the list types, so the
 * migration can only write once that module has registered its definitions. As the
 * engine usually starts (and is upgraded) before the elements, it runs twice: at its own
 * activation, and again each time the elements module is (re)deployed — the second run
 * is the one that does the work on the engine-first upgrade path.
 *
 * <p>Lifecycle: startup migration introduced in 0.4.x (#231), to be removed in 0.5 — see
 * docs/upgrade-notes.md, "Startup migrations".
 */
@Component(service = {ListTitlesContentMigration.class, JahiaEventListener.class}, immediate = true)
public class ListTitlesContentMigration implements JahiaEventListener<EventObject> {

    @SuppressWarnings("unchecked")
    private static final Class<EventObject>[] ACCEPTED_EVENT_TYPES = new Class[]{TemplatePackageRedeployedEvent.class};
    private static final String ELEMENTS_MODULE_ID = "formidable-elements";

    private static final Logger log = LoggerFactory.getLogger(ListTitlesContentMigration.class);

    private static final String FORM_TYPE = "fmdb:form";
    private static final String TITLE_MIXIN = "mix:title";
    private static final String TITLE_PROPERTY = "jcr:title";
    private static final String[] LIST_NAMES = {"fields", "actions"};

    @Activate
    public void activate() {
        run();
    }

    /** The redeploy event carries the module id as its source. */
    @Override
    public void onEvent(EventObject event) {
        if (event instanceof TemplatePackageRedeployedEvent && ELEMENTS_MODULE_ID.equals(event.getSource())) {
            log.info("[ListTitlesContentMigration] {} (re)deployed, checking the form list titles", ELEMENTS_MODULE_ID);
            run();
        }
    }

    @Override
    public Class<EventObject>[] getEventTypes() {
        return ACCEPTED_EVENT_TYPES;
    }

    void run() {
        for (String workspace : new String[]{"default", "live"}) {
            try {
                JCRTemplate.getInstance().doExecuteWithSystemSessionAsUser(null, workspace, null, session -> {
                    migrateWorkspace(session, workspace);
                    return null;
                });
            } catch (RepositoryException e) {
                log.error("[ListTitlesContentMigration] Migration failed in workspace '{}': {}", workspace, e.getMessage(), e);
            }
        }
    }

    private void migrateWorkspace(JCRSessionWrapper session, String workspace) throws RepositoryException {
        // Scoped to editorial content: module-bundled nodes under /modules belong to
        // their module and must not be rewritten from here.
        Query query = session.getWorkspace().getQueryManager()
                .createQuery("SELECT * FROM [" + FORM_TYPE + "] WHERE ISDESCENDANTNODE('/sites')", Query.JCR_SQL2);
        JCRNodeIteratorWrapper forms = (JCRNodeIteratorWrapper) query.execute().getNodes();

        int migrated = 0;
        while (forms.hasNext()) {
            JCRNodeWrapper form = (JCRNodeWrapper) forms.nextNode();
            try {
                boolean touched = false;
                for (String listName : LIST_NAMES) {
                    if (form.hasNode(listName)) {
                        touched |= migrateList(session, form.getNode(listName), siteLanguages(form),
                                ListTitlesContentMigration::defaultTitle);
                    }
                }
                if (touched) {
                    // One save per form: a failure must never discard the forms already
                    // migrated before it, nor poison the later saves.
                    session.save();
                    migrated++;
                }
            } catch (RepositoryException e) {
                log.error("[ListTitlesContentMigration] Could not migrate form '{}' in workspace '{}': {}",
                        form.getPath(), workspace, e.getMessage(), e);
                // Drop the half-applied changes, or every later save would re-throw them.
                session.refresh(false);
            }
        }

        if (migrated > 0) {
            log.info("[ListTitlesContentMigration] Gave a title to the lists of {} form(s) in workspace '{}'", migrated, workspace);
        } else {
            log.debug("[ListTitlesContentMigration] Every form list already has its titles in workspace '{}'", workspace);
        }
    }

    /**
     * The languages a list must carry a title in: every language its site declares.
     * A form outside a site (none is expected under /sites) gets nothing.
     */
    private static Collection<String> siteLanguages(JCRNodeWrapper form) throws RepositoryException {
        JCRSiteNode site = form.getResolveSite();
        Set<String> languages = new LinkedHashSet<>();
        if (site != null && site.getLanguages() != null) {
            languages.addAll(site.getLanguages());
        }
        return languages;
    }

    /**
     * The type's own default for jcr:title, resolved in the given language (the CND
     * declares it as a resourceBundle default), or null when the type has none.
     */
    static String defaultTitle(String nodeType, Locale locale) {
        try {
            ExtendedNodeType type = NodeTypeRegistry.getInstance().getNodeType(nodeType);
            ExtendedPropertyDefinition definition = type.getPropertyDefinition(TITLE_PROPERTY);
            Value[] defaults = definition != null ? definition.getDefaultValues(locale) : null;
            return defaults != null && defaults.length > 0 ? defaults[0].getString() : null;
        } catch (RepositoryException | RuntimeException e) {
            log.warn("[ListTitlesContentMigration] No default title for type '{}' in '{}': {}", nodeType, locale, e.getMessage());
            return null;
        }
    }

    /**
     * Writes the default title on every language where the list has none (or a blank one).
     *
     * @param defaultTitle resolves the default title of a node type in a language; null
     *                     means no default, and that language is left alone
     * @return true when at least one language was written (the session then carries
     *         unsaved changes)
     */
    boolean migrateList(JCRSessionWrapper session, JCRNodeWrapper list, Collection<String> languages,
            BiFunction<String, Locale, String> defaultTitle) throws RepositoryException {
        boolean touched = false;
        for (String language : languages) {
            Locale locale = LanguageCodeConverters.languageCodeToLocale(language);
            if (hasTitle(list, locale)) {
                continue;
            }
            String title = defaultTitle.apply(list.getPrimaryNodeTypeName(), locale);
            if (StringUtils.isBlank(title)) {
                continue;
            }
            if (!touched) {
                session.checkout(list);
                touched = true;
            }
            if (!list.isNodeType(TITLE_MIXIN)) {
                // Lists stored before the type carried mix:title: the registered type now
                // brings it, but stamping it keeps the write legal whatever registered first.
                list.addMixin(TITLE_MIXIN);
            }
            list.getOrCreateI18N(locale).setProperty(TITLE_PROPERTY, title);
            log.info("[ListTitlesContentMigration] Titled '{}' in '{}': {}", list.getPath(), language, title);
        }
        return touched;
    }

    private static boolean hasTitle(JCRNodeWrapper list, Locale locale) throws RepositoryException {
        if (!list.hasI18N(locale)) {
            return false;
        }
        Node translation = list.getI18N(locale);
        return translation.hasProperty(TITLE_PROPERTY)
                && StringUtils.isNotBlank(translation.getProperty(TITLE_PROPERTY).getString());
    }
}
