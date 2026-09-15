package org.jahia.modules.formidable.jexperience.engine;

import org.jahia.modules.jexperience.admin.ContextServerService;
import org.jahia.services.content.JCRCallback;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRSessionWrapper;
import org.jahia.services.content.JCRTemplate;
import org.jahia.services.render.RenderContext;
import org.jahia.services.render.Resource;
import org.jahia.services.render.filter.AbstractFilter;
import org.jahia.services.render.filter.RenderChain;
import org.jahia.services.render.filter.RenderFilter;
import org.osgi.framework.Bundle;
import org.osgi.framework.FrameworkUtil;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.osgi.service.component.annotations.ReferencePolicyOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import javax.jcr.query.Query;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Writes, before every form of a jExperience-configured site in live, what the client script needs
 * to send the form event: a JSON block keyed on the form's UUID — identifier, title, path, mapped
 * fields — and the script itself. The output depends on the published form alone, never on the
 * visitor, so the fragment stays cached and identical for everyone. The script tag comes with each
 * form of a page; the script keeps one instance per page by itself. The prefill push
 * ({@code digitalDataOverrides}) belongs to phase 4 of the integration.
 */
@Component(service = RenderFilter.class, immediate = true)
public class FormJExperienceRenderFilter extends AbstractFilter {

    // named for what it is rather than a path: Sonar S1075 keys on the name, and this is a module's
    // static resource, not a configurable location
    static final String SCRIPT_RESOURCE = "/modules/formidable-jexperience-engine/javascript/formidable-jxp.js";
    /** The attribute of the JSON block, valued with the form's UUID: how the script finds a form's configuration. */
    static final String CONFIG_ATTRIBUTE = "data-formidable-jxp";

    private static final Logger log = LoggerFactory.getLogger(FormJExperienceRenderFilter.class);

    private final AtomicReference<ContextServerService> contextServerService = new AtomicReference<>();

    @Reference(cardinality = ReferenceCardinality.OPTIONAL, policy = ReferencePolicy.DYNAMIC, policyOption = ReferencePolicyOption.GREEDY)
    public void bindContextServerService(ContextServerService service) {
        contextServerService.set(service);
    }

    public void unbindContextServerService(ContextServerService service) {
        contextServerService.compareAndSet(service, null);
    }

    /**
     * The identity of a filter is its instance: the parent compares filters to order a chain, and the
     * service reference this class adds is a binding, not part of what the filter is. Spelt out
     * because a subclass that adds a field and inherits an equality is a smell on its own.
     */
    @Override
    public boolean equals(Object other) {
        return super.equals(other);
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }

    @Activate
    public void activate() {
        setPriority(11);
        setApplyOnNodeTypes(MappingRuleSyncListener.FORM_NODE_TYPE);
        setApplyOnTemplateTypes("html");
        setApplyOnModes("live");
        // the same node is rendered again through a second full chain by a wrapper, an include or an option,
        // and node type, template type and mode all still match: without this the page would carry the block
        // twice, and pay its session and query twice (core's own contribute-once filters say the same)
        setSkipOnConfigurations("include,wrapper,option");
        setDescription("Adds the jExperience configuration block and client script before a form");
    }

    @Override
    public String execute(String previousOut, RenderContext renderContext, Resource resource, RenderChain chain) {
        String siteKey = renderContext.getSite() == null ? null : renderContext.getSite().getSiteKey();
        return prepend(previousOut, siteKey, resource.getNode(), renderContext.getRequest().getContextPath());
    }

    /**
     * The form's markup preceded by the contribution, or the markup alone: outside a
     * jExperience-configured site, and whenever the form cannot be read — a form that renders is
     * worth more than a form that fails over its analytics.
     */
    String prepend(String previousOut, String siteKey, JCRNodeWrapper form, String contextPath) {
        try {
            if (!JExperienceSite.configured(contextServerService.get(), siteKey)) {
                return previousOut;
            }
            return contribution(form, contextPath) + previousOut;
        } catch (RepositoryException | RuntimeException e) {
            // everything, including the site check: an escaped exception is turned into a RenderFilterException
            // and costs the page, not the block, and a form that renders is worth more than its analytics
            log.warn("[FormJExperienceRenderFilter] The jExperience configuration of a form could not be written; the form renders without it", e);
            return previousOut;
        }
    }

    /** The configuration block of the form, then the script tag. */
    String contribution(JCRNodeWrapper rendered, String contextPath) throws RepositoryException {
        String uuid = rendered.getIdentifier();
        JCRSessionWrapper renderSession = rendered.getSession();
        return inOwnSession(renderSession.getWorkspace().getName(), renderSession.getLocale(),
                session -> block(session.getNodeByIdentifier(uuid), uuid, contextPath));
    }

    private String block(JCRNodeWrapper form, String uuid, String contextPath) throws RepositoryException {
        StringBuilder json = new StringBuilder("{\"formId\":").append(Json.string(uuid))
                .append(",\"name\":").append(Json.string(form.getDisplayableName()))
                .append(",\"path\":").append(Json.string(form.getPath()))
                .append(",\"mappings\":[");
        String separator = "";
        for (Map.Entry<String, String> mapping : mappedFields(form).entrySet()) {
            json.append(separator).append("{\"field\":").append(Json.string(mapping.getKey()))
                    .append(",\"property\":").append(Json.string(mapping.getValue())).append('}');
            separator = ",";
        }
        json.append("]}");
        return "<script type=\"application/json\" " + CONFIG_ATTRIBUTE + "=\"" + uuid + "\">" + json + "</script>\n"
                + "<script src=\"" + scriptUrl(contextPath) + "\" defer></script>\n";
    }

    /**
     * The script's URL: the webapp's context path, since {@code /modules/…} is a mapping inside it and a Jahia
     * deployed under one would answer 404 for the bare path, and the module's version, so that a browser holding
     * the previous script fetches the new one after an upgrade instead of running it against a changed block.
     */
    static String scriptUrl(String contextPath) {
        Bundle bundle = FrameworkUtil.getBundle(FormJExperienceRenderFilter.class);
        String version = bundle == null ? "" : "?v=" + bundle.getVersion();
        return (contextPath == null ? "" : contextPath) + SCRIPT_RESOURCE + version;
    }

    /**
     * Runs the reading in a session of its own, which is what names the form where it lives.
     *
     * <p>A form placed through a reference renders as a node contextualised under it, whose path —
     * {@code …/theReference@/theForm} — is no JCR path: the query of the mapped fields matches
     * nothing and the event would name a place the mapping rule never mentions. Asking the render
     * session for the identifier gives that same contextualised node back, since the session holds
     * it under the form's identifier (observed in live on a referenced form); only a session that
     * never saw the reference resolves the identifier to the form itself. The identifier is the
     * form's own either way, so it is read from the rendered node.</p>
     *
     * <p>A seam for the tests, and the one place a session is opened: the filter runs on a cache
     * miss, and its output is the same for every visitor.</p>
     */
    <T> T inOwnSession(String workspace, Locale locale, JCRCallback<T> callback) throws RepositoryException {
        return JCRTemplate.getInstance().doExecuteWithSystemSessionAsUser(null, workspace, locale, callback);
    }

    /**
     * Field name to profile property, for the form's fields that carry a mapping — as stored, before
     * the schema check the rule applies: the script only asks whether the author mapped anything.
     * A field marked sensitive is left out, so the page never names it either.
     */
    Map<String, String> mappedFields(JCRNodeWrapper form) throws RepositoryException {
        NodeIterator nodes = fieldsCarryingAMapping(form);
        Map<String, String> mappings = new LinkedHashMap<>();
        while (nodes.hasNext()) {
            JCRNodeWrapper field = (JCRNodeWrapper) nodes.nextNode();
            if (SensitiveField.isMapped(field) && !SensitiveField.isSensitive(field)) {
                mappings.put(field.getName(), field.getPropertyAsString(ProfilePropertiesChoiceListInitializer.PROPERTY));
            }
        }
        return mappings;
    }

    /** The query of the fields carrying the mapping mixin — a seam for the tests, which have no query engine. */
    NodeIterator fieldsCarryingAMapping(JCRNodeWrapper form) throws RepositoryException {
        return form.getSession().getWorkspace().getQueryManager()
                .createQuery(FormMappingReader.queryFor(form.getPath()), Query.JCR_SQL2).execute().getNodes();
    }
}
