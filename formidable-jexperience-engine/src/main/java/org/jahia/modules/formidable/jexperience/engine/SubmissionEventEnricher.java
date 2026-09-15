package org.jahia.modules.formidable.jexperience.engine;

import org.jahia.modules.formidable.engine.api.AcceptedSubmission;
import org.jahia.modules.formidable.engine.api.ChoiceOptionsResolver;
import org.jahia.modules.formidable.engine.api.SubmissionResponseEnricher;
import org.jahia.modules.jexperience.admin.ContextServerService;
import org.jahia.services.content.JCRNodeWrapper;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.osgi.service.component.annotations.ReferencePolicyOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.jahia.services.content.JCRTemplate;

import javax.jcr.ItemNotFoundException;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import javax.jcr.query.Query;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Adds the {@code jexperience} block to the 200 of an accepted submission — the form's UUID and the
 * accepted values of its fields — for the client script that sends the {@code form} event through
 * the tracker (docs/architecture/jexperience-integration.md, "Submitting").
 *
 * <p>Every value-bearing field is in it, as jExperience's own paths do (its Forms bridge sends the
 * result data whole, its plain-form listener every named input), so that a mapping made in
 * jExperience's Form mappings screen receives a value whatever field it names. The two boundaries:
 * the {@code fmdbmix:profileMappableField} marker, which leaves out files, buttons and containers,
 * and {@link SensitiveField}, the author's per-field "this value never leaves the site". A value is
 * a string, or a list when the field holds several values (a checkbox group, a multiple select), the
 * shape the mapping rule expects: {@link FieldShapes} decides both. Nothing is added for a site
 * without a jExperience configuration.</p>
 */
@Component(service = SubmissionResponseEnricher.class, immediate = true)
public class SubmissionEventEnricher implements SubmissionResponseEnricher {

    /** The top-level key of the block in the response body. */
    static final String KEY = "jexperience";
    /** Where the author's unpublished answer lives; the submission itself is resolved in live. */
    static final String WORKSPACE_DEFAULT = "default";

    private static final Logger log = LoggerFactory.getLogger(SubmissionEventEnricher.class);

    private final AtomicReference<ContextServerService> contextServerService = new AtomicReference<>();

    @Reference
    private ChoiceOptionsResolver optionsResolver;

    public SubmissionEventEnricher() {
    }

    SubmissionEventEnricher(ChoiceOptionsResolver optionsResolver, ContextServerService contextServerService) {
        this.optionsResolver = optionsResolver;
        this.contextServerService.set(contextServerService);
    }

    @Reference(cardinality = ReferenceCardinality.OPTIONAL, policy = ReferencePolicy.DYNAMIC, policyOption = ReferencePolicyOption.GREEDY)
    public void bindContextServerService(ContextServerService service) {
        contextServerService.set(service);
    }

    public void unbindContextServerService(ContextServerService service) {
        contextServerService.compareAndSet(service, null);
    }

    @Override
    public Map<String, Object> enrich(AcceptedSubmission submission) {
        if (!JExperienceSite.configured(contextServerService.get(), submission.siteKey())) {
            return Map.of();
        }
        JCRNodeWrapper form = submission.formNode();
        try {
            // choices are counted in the site's default language, where option values live (see FormMappingReader)
            String language = form.getResolveSite().getDefaultLanguage();
            Map<String, Object> fields = new LinkedHashMap<>();
            for (JCRNodeWrapper field : sendableFields(form)) {
                List<String> values = submission.parameters().get(field.getName());
                if (values == null || values.isEmpty()) {
                    continue;
                }
                Optional<FieldShape> shape = FieldShapes.infer(field, Optional.empty(), () -> optionsResolver.countChoices(field, language));
                shape.ifPresent(s -> fields.put(field.getName(), s.multivalued() ? List.copyOf(values) : values.get(0)));
            }
            Map<String, Object> block = new LinkedHashMap<>();
            block.put("formId", form.getIdentifier());
            block.put("fields", fields);
            return Map.of(KEY, block);
        } catch (RepositoryException e) {
            log.warn("[SubmissionEventEnricher] The fields of the submitted form could not be read: no jexperience block in the response", e);
            return Map.of();
        }
    }

    /**
     * The fields whose value may be sent: every mappable one the author did not mark sensitive, in
     * <strong>either</strong> workspace.
     *
     * <p>The submission is resolved in live, so the fields read here are the published ones. Asking
     * live alone would arm this control only at the next publication: an author who ticks the box on
     * a form that is already published and collecting would keep sending that value until someone
     * publishes, which is not what "this value never leaves the site" promises. So a field the author
     * has marked sensitive but not yet published counts as sensitive too. The reverse — unticking the
     * box without publishing — also holds the value back, which is the safe way round.</p>
     */
    List<JCRNodeWrapper> sendableFields(JCRNodeWrapper form) throws RepositoryException {
        NodeIterator nodes = mappableFields(form);
        List<JCRNodeWrapper> published = new ArrayList<>();
        while (nodes.hasNext()) {
            JCRNodeWrapper field = (JCRNodeWrapper) nodes.nextNode();
            if (!SensitiveField.isSensitive(field)) {
                published.add(field);
            }
        }
        Set<String> markedSinceLastPublication = markedSensitiveWhileUnpublished(published);
        return published.stream().filter(field -> !markedSensitiveIn(markedSinceLastPublication, field)).toList();
    }

    private static boolean markedSensitiveIn(Set<String> identifiers, JCRNodeWrapper field) {
        try {
            return identifiers.contains(field.getIdentifier());
        } catch (RepositoryException e) {
            log.warn("[SubmissionEventEnricher] A submitted field could not be identified: its value is not sent", e);
            return true;
        }
    }

    /**
     * Of the fields live says are not sensitive, those the author has marked in the editor and not
     * published yet — read once, in a session of the default workspace, by identifier.
     *
     * <p>When that reading fails the published answer stands, with a warning: a transient repository
     * error must not empty the block for every visitor, and the window this closes is the one between
     * ticking the box and publishing. A seam for the tests, which have no repository.</p>
     */
    Set<String> markedSensitiveWhileUnpublished(List<JCRNodeWrapper> published) {
        if (published.isEmpty()) {
            return Set.of();
        }
        try {
            return JCRTemplate.getInstance().doExecuteWithSystemSessionAsUser(null, WORKSPACE_DEFAULT, null, session -> {
                Set<String> marked = new HashSet<>();
                for (JCRNodeWrapper field : published) {
                    String identifier = field.getIdentifier();
                    try {
                        if (SensitiveField.isSensitive(session.getNodeByIdentifier(identifier))) {
                            marked.add(identifier);
                        }
                    } catch (ItemNotFoundException e) {
                        // published but deleted since: nothing unpublished to honour
                        log.debug("[SubmissionEventEnricher] Field '{}' is gone from the default workspace", identifier);
                    }
                }
                return marked;
            });
        } catch (RepositoryException | RuntimeException e) {
            log.warn("[SubmissionEventEnricher] The editor's sensitive flags could not be read; the published ones stand for this submission", e);
            return Set.of();
        }
    }

    /** The query of the fields carrying the marker — a seam for the tests, which have no query engine. */
    NodeIterator mappableFields(JCRNodeWrapper form) throws RepositoryException {
        return form.getSession().getWorkspace().getQueryManager()
                .createQuery(queryFor(form.getPath()), Query.JCR_SQL2).execute().getNodes();
    }

    /**
     * The fields the server considers: every one carrying the mappable marker, not only the mapped
     * ones — a mapping made in jExperience's own screen names a field Formidable need not know.
     * The path is a SQL2 literal, quotes doubled (the rule of {@code JCRContentUtils.sqlEncode}).
     */
    static String queryFor(String formPath) {
        return "SELECT * FROM [" + FieldShapes.MAPPABLE_MARKER + "] WHERE ISDESCENDANTNODE('" + formPath.replace("'", "''") + "')";
    }
}
