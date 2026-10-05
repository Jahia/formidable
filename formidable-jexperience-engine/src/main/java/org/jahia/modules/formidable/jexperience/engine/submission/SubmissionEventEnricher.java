package org.jahia.modules.formidable.jexperience.engine.submission;

import org.jahia.modules.formidable.engine.api.AcceptedSubmission;
import org.jahia.modules.formidable.engine.api.ChoiceOptionsResolver;
import org.jahia.modules.formidable.engine.api.SubmissionResponseEnricher;
import org.jahia.modules.formidable.engine.api.FmdbMixin;
import org.jahia.modules.jexperience.admin.ContextServerService;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.decorator.JCRSiteNode;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.osgi.service.component.annotations.ReferencePolicyOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.jahia.modules.formidable.jexperience.engine.field.FieldShape;
import org.jahia.modules.formidable.jexperience.engine.field.FieldShapes;
import org.jahia.modules.formidable.jexperience.engine.util.JExperienceSite;
import org.jahia.modules.formidable.jexperience.engine.util.Sql2;

import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import javax.jcr.query.Query;
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
 * <p>Every answered field is in it, as jExperience's own paths do (its Forms bridge sends the
 * result data whole, its plain-form listener every named input), so that a mapping made in
 * jExperience's Form mappings screen receives a value whatever field it names. The boundary is the
 * {@code fmdbmix:profileMappableField} marker, which leaves out files, buttons and containers. A field the
 * visitor left unanswered is absent, not empty ({@link #answered}). A value is a string, or a list
 * when the field holds several values (a checkbox group, a multiple select), the shape the mapping
 * rule expects: {@link FieldShapes} decides both. Nothing is added for a site whose pages carry no
 * tracker ({@link JExperienceSite#tracked}): there would be nobody to read it.</p>
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
        JCRNodeWrapper form = submission.formNode();
        try {
            JCRSiteNode site = form.getResolveSite();
            if (!JExperienceSite.tracked(site, contextServerService.get())) {
                return Map.of();
            }
            // choices are counted in the site's default language, where option values live (see FormMappingReader)
            String language = site.getDefaultLanguage();
            Map<String, Object> fields = new LinkedHashMap<>();
            NodeIterator nodes = mappableFields(form);
            while (nodes.hasNext()) {
                JCRNodeWrapper field = (JCRNodeWrapper) nodes.nextNode();
                String name = field.getName();
                List<String> values = answered(submission.parameters().get(name));
                if (!values.isEmpty()) {
                    Optional<FieldShape> shape = FieldShapes.infer(field, Optional.empty(), () -> optionsResolver.countChoices(field, language));
                    shape.ifPresent(s -> fields.put(name, s.multivalued() ? List.copyOf(values) : values.get(0)));
                }
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
     * The values that say something about the visitor: the non-blank ones, in their order.
     *
     * <p>A field left unanswered must be <strong>absent</strong> from the event, not empty. jCustomer
     * writes whatever the event carries: an empty string is a value, so {@code alwaysSet} would erase
     * the profile's value and {@code setIfMissing} would write the empty string and then keep it for
     * good, every later answer refused as "already set"; an absent field resolves to nothing, and the
     * action writes nothing under either strategy. Forms leaves an empty answer out of its result
     * data, and the tracker's own listener leaves an empty input out of a plain form: this is that
     * rule, applied to the values the pipeline accepted — which is also how the pipeline itself tells
     * an answered field from an unanswered one for its required-field check.</p>
     */
    static List<String> answered(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream().filter(value -> value != null && !value.isBlank()).toList();
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
        return Sql2.descendantsOf(FmdbMixin.PROFILE_MAPPABLE_FIELD, formPath);
    }
}
