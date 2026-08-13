package org.jahia.modules.formidable.engine.options;

import org.jahia.modules.formidable.engine.config.FormidableConfigService;
import org.jahia.modules.formidable.engine.config.FormidableConfigService.OptionsSource;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.nodetypes.initializers.ChoiceListInitializer;
import org.jahia.services.content.nodetypes.initializers.ChoiceListInitializerService;
import org.jahia.services.content.nodetypes.initializers.ChoiceListValue;
import org.json.JSONObject;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Resolves the option list of a sourced choice field at display time.
 *
 * The service bridges the admin-declared options sources (curated Jahia choicelist
 * initializers, see {@code optionsSources} in org.jahia.modules.formidable.cfg) to a
 * primitive-friendly contract usable from the GraalVM JS server views through
 * {@code server.osgi.getService}: a source key and a BCP-47 language tag in, an array
 * of JSON-encoded {@code {"value","label","selected"}} strings out — the exact storage
 * format of manual options, so downstream rendering code cannot tell them apart.
 *
 * Results are cached in memory per (source, language) for the configured TTL. A cache
 * entry also remembers the source definition it was resolved from, so an admin config
 * change takes effect on the next resolution instead of waiting for the TTL. Failures
 * are never cached: a source that errors is retried on the next render.
 */
@Component(service = FormidableOptionsSourceService.class, immediate = true)
public class FormidableOptionsSourceService {

    private static final Logger log = LoggerFactory.getLogger(FormidableOptionsSourceService.class);

    private record CacheEntry(OptionsSource source, String[] options, Instant expiresAt) {}

    private final ConcurrentHashMap<String, CacheEntry> cache = new ConcurrentHashMap<>();

    private FormidableConfigService config;

    // Seams for unit tests: production values bridge to the Jahia platform services.
    private Clock clock = Clock.systemUTC();
    private Function<String, ChoiceListInitializer> initializerLookup =
            key -> ChoiceListInitializerService.getInstance().getInitializers().get(key);

    @Reference
    public void setConfig(FormidableConfigService config) {
        this.config = config;
    }

    /**
     * Resolves the options of a choice field node according to its active options mode.
     * This is the mode dispatch: additional modes (e.g. a category root picked by
     * weakreference) plug in here without touching the callers.
     *
     * @param fieldNode   the choice field node
     * @param languageTag BCP-47 language tag of the rendered or submitted form
     * @return the options as JSON-encoded strings, or null when the field does not use
     *         an options source (manual mode)
     * @throws javax.jcr.RepositoryException    when the field node cannot be read
     * @throws IllegalArgumentException         when the field references an undeclared source
     * @throws IllegalStateException            when the source is declared but cannot deliver
     */
    public String[] resolveForField(JCRNodeWrapper fieldNode, String languageTag) throws javax.jcr.RepositoryException {
        if (fieldNode.isNodeType("fmdbmix:sourcedOptions")) {
            String sourceKey = fieldNode.hasProperty("fmdb:optionsSourceKey")
                    ? fieldNode.getProperty("fmdb:optionsSourceKey").getString()
                    : "";
            return resolve(sourceKey, languageTag);
        }
        if (fieldNode.isNodeType("fmdbmix:categoryOptions")) {
            return resolveCategoryOptions(fieldNode);
        }
        if (fieldNode.isNodeType("fmdbmix:contentOptions")) {
            return resolveContentOptions(fieldNode, config.getOptionsQueryMaxResults());
        }

        return null;
    }

    /**
     * Runs the content-mode JCR-SQL2 query through the given session. A seam so the
     * service stays unit-testable without Jahia's query machinery (same pattern as the
     * initializer lookup).
     */
    @FunctionalInterface
    interface ContentQueryRunner {
        javax.jcr.NodeIterator run(org.jahia.services.content.JCRSessionWrapper session, String sql2)
                throws javax.jcr.RepositoryException;
    }

    private ContentQueryRunner contentQueryRunner = (session, sql2) ->
            session.getWorkspace().getQueryManager()
                    .createQuery(sql2, javax.jcr.query.Query.JCR_SQL2)
                    .execute()
                    .getNodes();

    void setContentQueryRunner(ContentQueryRunner runner) {
        this.contentQueryRunner = runner;
    }

    /**
     * Content mode: the options are the descendants of the root node the contributor
     * picked, filtered by the configured content type. Values are the paths relative to
     * the root (unique by construction, readable in results, stable across re-imports),
     * labels the localized displayable names. The query runs through the field's own
     * session, so live only sees published, visitor-readable content, and the result is
     * ordered by path. Above the administrator-configured cap the field fails explicitly
     * like a failing source — never a silent truncation. Not TTL-cached (in-JVM read).
     */
    private String[] resolveContentOptions(JCRNodeWrapper fieldNode, int maxResults)
            throws javax.jcr.RepositoryException {
        if (!fieldNode.hasProperty("fmdb:optionsRootNode")) {
            throw new IllegalStateException("Choice field '" + fieldNode.getPath()
                    + "' is in content mode but no root node is selected");
        }
        if (!fieldNode.hasProperty("fmdb:optionsNodeType")
                || fieldNode.getProperty("fmdb:optionsNodeType").getString().isBlank()) {
            throw new IllegalStateException("Choice field '" + fieldNode.getPath()
                    + "' is in content mode but no content type is configured");
        }

        JCRNodeWrapper root;
        try {
            root = (JCRNodeWrapper) fieldNode.getProperty("fmdb:optionsRootNode").getNode();
        } catch (javax.jcr.RepositoryException e) {
            throw new IllegalStateException("Root node of choice field '" + fieldNode.getPath()
                    + "' cannot be read (deleted, or not published in this workspace)", e);
        }

        return queryContentOptions(root, fieldNode.getProperty("fmdb:optionsNodeType").getString(),
                fieldNode.getPath(), maxResults);
    }

    /**
     * Editor-side preview of a content-mode configuration that may not be saved yet.
     * The {@code default} workspace resolves through the current editor session; the
     * {@code live} workspace resolves through a <b>guest</b> session, so the preview
     * shows exactly what a visitor will get — published, guest-readable content only.
     *
     * @throws IllegalStateException like the field resolution (unreadable root, invalid
     *                               or unknown type, result cap exceeded)
     */
    public String[] resolveContentPreview(String rootIdentifier, String nodeType, String workspace,
            String languageTag) throws javax.jcr.RepositoryException {
        Locale locale = Locale.forLanguageTag(languageTag == null || languageTag.isBlank() ? "en" : languageTag);
        int maxResults = config.getOptionsQueryMaxResults();

        if ("live".equals(workspace)) {
            return org.jahia.services.content.JCRTemplate.getInstance().doExecuteWithUserSession(
                    org.jahia.services.usermanager.JahiaUserManagerService.GUEST_USERNAME, "live", locale,
                    session -> queryContentOptions(readPreviewRoot(session, rootIdentifier),
                            nodeType, "preview:" + rootIdentifier, maxResults));
        }

        org.jahia.services.content.JCRSessionWrapper session = org.jahia.services.content.JCRSessionFactory
                .getInstance().getCurrentUserSession("default", locale);
        return queryContentOptions(readPreviewRoot(session, rootIdentifier), nodeType,
                "preview:" + rootIdentifier, maxResults);
    }

    private static JCRNodeWrapper readPreviewRoot(org.jahia.services.content.JCRSessionWrapper session,
            String rootIdentifier) {
        try {
            return session.getNodeByIdentifier(rootIdentifier);
        } catch (javax.jcr.RepositoryException e) {
            throw new IllegalStateException("Root node '" + rootIdentifier
                    + "' cannot be read (deleted, not published, or not visible to visitors)", e);
        }
    }

    private String[] queryContentOptions(JCRNodeWrapper root, String rawNodeType, String scope, int maxResults)
            throws javax.jcr.RepositoryException {
        String nodeType = rawNodeType == null ? "" : rawNodeType.trim();
        if (!nodeType.matches("[\\w]+:[\\w]+")) {
            throw new IllegalStateException("Choice field '" + scope
                    + "' has an invalid content type '" + nodeType + "'");
        }

        javax.jcr.NodeIterator nodes;
        try {
            nodes = contentQueryRunner.run(root.getSession(),
                    "SELECT * FROM [" + nodeType + "] WHERE ISDESCENDANTNODE('"
                            + root.getPath().replace("'", "''") + "')");
        } catch (javax.jcr.RepositoryException e) {
            // Unknown types surface as InvalidQueryException or NamespaceException
            // depending on which half of the name is wrong.
            throw new IllegalStateException("Choice field '" + scope
                    + "' cannot list contents of type '" + nodeType + "': " + e.getMessage(), e);
        }

        String rootPrefix = root.getPath() + "/";
        java.util.List<String[]> entries = new java.util.ArrayList<>();
        while (nodes.hasNext()) {
            javax.jcr.Node child = nodes.nextNode();
            if (!(child instanceof JCRNodeWrapper content)) {
                continue;
            }
            if (entries.size() >= maxResults) {
                throw new OptionsQueryCapExceededException(scope, maxResults);
            }
            String value = content.getPath().startsWith(rootPrefix)
                    ? content.getPath().substring(rootPrefix.length())
                    : content.getName();
            String label = content.getDisplayableName();
            entries.add(new String[]{value, label != null && !label.isEmpty() ? label : value});
        }

        entries.sort(java.util.Comparator.comparing(entry -> entry[0]));
        return entries.stream()
                .map(entry -> new JSONObject(Map.of(
                        "value", entry[0],
                        "label", entry[1],
                        "selected", false)).toString())
                .toArray(String[]::new);
    }

    /**
     * Category mode: the options are the categories directly under the root category the
     * contributor picked. Values are the category names, labels their localized titles.
     * The weakreference is resolved through the field's own session, so the resolution
     * follows the caller's workspace (live render only sees published categories) and
     * language. Not TTL-cached: this is an in-JVM read backed by Jahia's JCR caches, and
     * staying fresh means a category publication shows up on the next render.
     */
    private static String[] resolveCategoryOptions(JCRNodeWrapper fieldNode) throws javax.jcr.RepositoryException {
        if (!fieldNode.hasProperty("fmdb:optionsRootCategory")) {
            throw new IllegalStateException("Choice field '" + fieldNode.getPath()
                    + "' is in category mode but no root category is selected");
        }

        JCRNodeWrapper root;
        try {
            root = (JCRNodeWrapper) fieldNode.getProperty("fmdb:optionsRootCategory").getNode();
        } catch (javax.jcr.RepositoryException e) {
            throw new IllegalStateException("Root category of choice field '" + fieldNode.getPath()
                    + "' cannot be read (deleted, or not published in this workspace)", e);
        }

        java.util.List<String> options = new java.util.ArrayList<>();
        javax.jcr.NodeIterator children = root.getNodes();
        while (children.hasNext()) {
            javax.jcr.Node child = children.nextNode();
            if (child instanceof JCRNodeWrapper category && category.isNodeType("jnt:category")) {
                String value = category.getName();
                String label = category.getDisplayableName();
                options.add(new JSONObject(Map.of(
                        "value", value,
                        "label", label != null && !label.isEmpty() ? label : value,
                        "selected", false)).toString());
            }
        }

        return options.toArray(String[]::new);
    }

    /**
     * Resolves the options of a declared source for one language.
     *
     * @param sourceKey   id of an admin-declared options source ({@code fmdb:optionsSourceKey})
     * @param languageTag BCP-47 language tag of the rendered form (for example {@code en}, {@code fr-FR})
     * @return the options as JSON-encoded {@code {"value","label","selected"}} strings,
     *         possibly empty — never null
     * @throws IllegalArgumentException when the key does not match any declared source
     *                                  (unknown, or removed from the allowlist)
     * @throws IllegalStateException    when the source is declared but cannot deliver
     *                                  (initializer missing or failing)
     */
    public String[] resolve(String sourceKey, String languageTag) {
        OptionsSource source = config.resolveOptionsSource(sourceKey == null ? "" : sourceKey)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown options source '" + sourceKey + "': it is not declared in optionsSources "
                                + "(org.jahia.modules.formidable.cfg)"));

        String cacheKey = source.id() + '|' + languageTag;
        CacheEntry cached = cache.get(cacheKey);
        Instant now = clock.instant();
        if (cached != null && cached.source().equals(source) && now.isBefore(cached.expiresAt())) {
            return cached.options().clone();
        }

        String[] options = resolveFromInitializer(source, languageTag);
        cache.put(cacheKey, new CacheEntry(source, options, now.plus(config.getOptionsSourcesCacheTtl())));
        return options.clone();
    }

    private String[] resolveFromInitializer(OptionsSource source, String languageTag) {
        ChoiceListInitializer initializer = initializerLookup.apply(source.initializerKey());
        if (initializer == null) {
            throw new IllegalStateException("Options source '" + source.id() + "' points to choicelist "
                    + "initializer '" + source.initializerKey() + "', which is not available on this platform");
        }

        List<ChoiceListValue> values;
        try {
            values = initializer.getChoiceListValues(
                    null,
                    source.param().isEmpty() ? null : source.param(),
                    null,
                    Locale.forLanguageTag(languageTag),
                    new HashMap<>());
        } catch (Exception e) {
            throw new IllegalStateException("Options source '" + source.id() + "' failed to resolve for "
                    + "language '" + languageTag + "': " + e.getMessage(), e);
        }

        if (values == null) {
            return new String[0];
        }

        return values.stream()
                .map(value -> toOptionJson(source, value))
                .filter(java.util.Objects::nonNull)
                .toArray(String[]::new);
    }

    private static String toOptionJson(OptionsSource source, ChoiceListValue choice) {
        try {
            String value = choice.getValue() != null ? choice.getValue().getString() : null;
            if (value == null || value.isEmpty()) {
                return null;
            }
            String label = choice.getDisplayName() != null && !choice.getDisplayName().isEmpty()
                    ? choice.getDisplayName()
                    : value;
            return new JSONObject(Map.of("value", value, "label", label, "selected", false)).toString();
        } catch (Exception e) {
            log.debug("[FormidableOptionsSourceService] Skipping unreadable choice value from source '{}'",
                    source.id(), e);
            return null;
        }
    }

    // Test seams

    void setClock(Clock clock) {
        this.clock = clock;
    }

    void setInitializerLookup(Function<String, ChoiceListInitializer> initializerLookup) {
        this.initializerLookup = initializerLookup;
    }
}
