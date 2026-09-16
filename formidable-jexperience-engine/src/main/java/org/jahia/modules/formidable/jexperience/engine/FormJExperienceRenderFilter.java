package org.jahia.modules.formidable.jexperience.engine;

import org.jahia.modules.jexperience.admin.ContextServerService;
import org.jahia.services.content.JCRCallback;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRSessionWrapper;
import org.jahia.services.content.JCRTemplate;
import org.jahia.services.content.decorator.JCRSiteNode;
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

import javax.jcr.RepositoryException;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Writes, before every form of a tracked site in live, what the client script needs
 * to send the form event: a JSON block keyed on the form's UUID — {@code formId}, {@code name} and
 * {@code path} — and the script itself. What the form maps is not in it: the accepted values reach the script through
 * the submission's answer, so the page says nothing about the mappings (see {@code block}).
 *
 * <p>The output depends on the published form alone, never on the visitor, so the fragment stays
 * cached and identical for everyone. The script tag comes with each
 * form of a page; the script keeps one instance per page by itself. The prefill push
 * ({@code digitalDataOverrides}) belongs to phase 4 of the integration.</p>
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
        return prepend(previousOut, renderContext.getSite(), resource.getNode(), renderContext.getRequest().getContextPath());
    }

    /**
     * The form's markup preceded by the contribution, or the markup alone: outside a site whose
     * pages carry the tracker, and whenever the form cannot be read — a form that renders is worth
     * more than a form that fails over its analytics.
     */
    String prepend(String previousOut, JCRSiteNode site, JCRNodeWrapper form, String contextPath) {
        try {
            if (!JExperienceSite.tracked(site, contextServerService.get())) {
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

    private String block(JCRNodeWrapper form, String uuid, String contextPath) {
        // Built by hand: this module carries no JSON library at runtime, and the page needs three
        // strings. What the form maps is deliberately NOT here — the send decision reads the tracker's
        // own watch list, which a mapped form is in through the rule this integration publishes, so
        // declaring the mappings again would be one more thing to keep in step for nothing. Phase 4
        // will add what prefill needs at page load, which is the first thing the context cannot say.
        String json = "{\"formId\":" + Json.string(uuid)
                + ",\"name\":" + Json.string(form.getDisplayableName())
                + ",\"path\":" + Json.string(form.getPath()) + "}";
        return "<script type=\"application/json\" " + CONFIG_ATTRIBUTE + "=\"" + uuid + "\">" + json + "</script>\n"
                + "<script src=\"" + scriptUrl(contextPath) + "\" defer></script>\n";
    }

    /**
     * The script's URL: the webapp's context path, since {@code /modules/…} is a mapping inside it and a Jahia
     * deployed under one would answer 404 for the bare path, and the module's version, so that a browser holding
     * the previous script fetches the new one after an upgrade instead of running it against a changed block.
     */
    static String scriptUrl(String contextPath) {
        return scriptUrl(contextPath, bundleVersion());
    }

    /** The module's version, or nothing outside a framework — the tests have none. */
    private static String bundleVersion() {
        Bundle bundle = FrameworkUtil.getBundle(FormJExperienceRenderFilter.class);
        return bundle == null ? "" : bundle.getVersion().toString();
    }

    /** Both halves of the URL, apart so that a test can assert the one a framework would give. */
    static String scriptUrl(String contextPath, String version) {
        return (contextPath == null ? "" : contextPath)
                + SCRIPT_RESOURCE
                + (version.isEmpty() ? "" : "?v=" + version);
    }

    /**
     * Runs the reading in a session of its own, which is what names the form where it lives.
     *
     * <p>A form placed through a reference renders as a node contextualised under it, whose path —
     * {@code …/theReference@/theForm} — is no JCR path: written into the block it would name a
     * place the mapping rule never mentions, and the event would carry it. Asking the render
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


}
