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

import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import javax.jcr.query.Query;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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

    /** The fields whose value may be sent: every mappable one the author did not mark sensitive. */
    List<JCRNodeWrapper> sendableFields(JCRNodeWrapper form) throws RepositoryException {
        NodeIterator nodes = mappableFields(form);
        List<JCRNodeWrapper> fields = new ArrayList<>();
        while (nodes.hasNext()) {
            JCRNodeWrapper field = (JCRNodeWrapper) nodes.nextNode();
            if (!SensitiveField.isSensitive(field)) {
                fields.add(field);
            }
        }
        return fields;
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
