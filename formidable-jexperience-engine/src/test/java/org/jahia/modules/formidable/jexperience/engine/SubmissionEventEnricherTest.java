package org.jahia.modules.formidable.jexperience.engine;

import org.jahia.modules.formidable.engine.api.AcceptedSubmission;
import org.jahia.modules.formidable.engine.api.ChoiceOptionsResolver;
import org.jahia.modules.jexperience.admin.ContextServerService;
import org.jahia.modules.jexperience.admin.ContextServerStatus;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRPropertyWrapper;
import org.jahia.services.content.decorator.JCRSiteNode;
import org.junit.jupiter.api.Test;

import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The jexperience block of an accepted submission: the form's UUID and the accepted values of the
 * fields the author mapped, shaped as the mapping rule expects — and no block outside a configured site.
 */
class SubmissionEventEnricherTest {

    private static final String FORM_UUID = "8f7e2a10-0000-4000-8000-000000000001";

    /** A field carrying a mapping: the mixin's property named after it, which is what lets its value out. */
    private static JCRNodeWrapper field(String name, String... types) throws RepositoryException {
        return mappedTo(name, "property-of-" + name, types);
    }

    /** A field whose mapping property holds the given value — blank or null for a field nobody mapped. */
    private static JCRNodeWrapper mappedTo(String name, String property, String... types) throws RepositoryException {
        JCRNodeWrapper node = mock(JCRNodeWrapper.class);
        when(node.getName()).thenReturn(name);
        when(node.getPropertyAsString(ProfilePropertiesChoiceListInitializer.PROPERTY)).thenReturn(property);
        for (String type : types) {
            when(node.isNodeType(type)).thenReturn(true);
        }
        return node;
    }

    private static JCRNodeWrapper multiple(JCRNodeWrapper field) throws RepositoryException {
        JCRPropertyWrapper multiple = mock(JCRPropertyWrapper.class);
        when(multiple.getBoolean()).thenReturn(true);
        when(field.hasProperty(FieldShapes.MULTIPLE_PROPERTY)).thenReturn(true);
        when(field.getProperty(FieldShapes.MULTIPLE_PROPERTY)).thenReturn(multiple);
        return field;
    }

    private static JCRNodeWrapper form() throws RepositoryException {
        JCRNodeWrapper form = mock(JCRNodeWrapper.class);
        when(form.getIdentifier()).thenReturn(FORM_UUID);
        JCRSiteNode site = mock(JCRSiteNode.class);
        when(site.getDefaultLanguage()).thenReturn("en");
        when(form.getResolveSite()).thenReturn(site);
        return form;
    }

    private static ContextServerService configured(String siteKey) {
        ContextServerService service = mock(ContextServerService.class);
        when(service.getContextServerStatus(siteKey)).thenReturn(mock(ContextServerStatus.class));
        return service;
    }

    /** The fields the query returns, as an iterator over the given list — empty list included. */
    private static NodeIterator iterator(List<JCRNodeWrapper> fields) {
        Iterator<JCRNodeWrapper> remaining = fields.iterator();
        NodeIterator nodes = mock(NodeIterator.class);
        when(nodes.hasNext()).thenAnswer(call -> remaining.hasNext());
        when(nodes.nextNode()).thenAnswer(call -> remaining.next());
        return nodes;
    }

    /** An enricher over a form whose mixin-carrying fields the query returns; every choice field counts the given choices. */
    private static SubmissionEventEnricher enricher(ContextServerService service, int choices, List<JCRNodeWrapper> fields) throws RepositoryException {
        ChoiceOptionsResolver resolver = mock(ChoiceOptionsResolver.class);
        when(resolver.countChoices(any(), any())).thenReturn(OptionalInt.of(choices));
        NodeIterator nodes = iterator(fields);
        return new SubmissionEventEnricher(resolver, service) {
            @Override
            NodeIterator fieldsCarryingTheMixin(JCRNodeWrapper form) {
                return nodes;
            }
        };
    }

    @Test
    @SuppressWarnings("unchecked")
    void theBlockCarriesTheFormUuidAndTheMappedValuesShapedLikeTheRule() throws Exception {
        // Verifies the nominal block: a text field gives a string, a checkbox group and a multiple select give
        // lists even with one value, a field the submitter left out is absent, an unknown parameter never appears.
        List<JCRNodeWrapper> fields = List.of(
                field("firstName", FieldShapes.MAPPABLE_MARKER, FieldShapes.TEXT_FIELD),
                field("check-me", FieldShapes.MAPPABLE_MARKER, FieldShapes.CHOICE_FIELD, FieldShapes.CHECKBOX_TYPE),
                multiple(field("topics", FieldShapes.MAPPABLE_MARKER, FieldShapes.CHOICE_FIELD)),
                field("nickname", FieldShapes.MAPPABLE_MARKER, FieldShapes.TEXT_FIELD));
        Map<String, List<String>> parameters = Map.of(
                "firstName", List.of("Ada"),
                "check-me", List.of("one"),
                "topics", List.of("cdp", "forms"),
                "undeclared", List.of("x"));

        Map<String, Object> entries = enricher(configured("mysite"), 3, fields)
                .enrich(new AcceptedSubmission(form(), "mysite", Locale.ENGLISH, parameters));

        Map<String, Object> block = (Map<String, Object>) entries.get(SubmissionEventEnricher.KEY);
        assertEquals(FORM_UUID, block.get("formId"));
        assertEquals(Map.of("firstName", "Ada", "check-me", List.of("one"), "topics", List.of("cdp", "forms")), block.get("fields"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void aFieldOutsideTheMarkerOrWithoutAShapeNeverLeaves() throws Exception {
        // Verifies the boundary: a file field carries the marker by mistake but has no shape, so its value is
        // dropped; a single checkbox is one string.
        List<JCRNodeWrapper> fields = List.of(
                field("upload", FieldShapes.MAPPABLE_MARKER, FieldShapes.FILE_FIELD),
                field("consent", FieldShapes.MAPPABLE_MARKER, FieldShapes.CHOICE_FIELD, FieldShapes.CHECKBOX_TYPE));
        Map<String, Object> entries = enricher(configured("mysite"), 1, fields)
                .enrich(new AcceptedSubmission(form(), "mysite", Locale.ENGLISH, Map.of("upload", List.of("f.txt"), "consent", List.of("yes"))));

        Map<String, Object> block = (Map<String, Object>) entries.get(SubmissionEventEnricher.KEY);
        assertEquals(Map.of("consent", "yes"), block.get("fields"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void aFieldTheAuthorDidNotMapNeverLeavesTheServer() throws Exception {
        // Verifies the boundary of the block: only a non-blank mapping lets a value out, so the message the
        // author left unmapped — section switched on, property empty — stays on the server, as does a field
        // the query never returned.
        List<JCRNodeWrapper> fields = List.of(
                field("email", FieldShapes.MAPPABLE_MARKER, FieldShapes.EMAIL_FIELD),
                mappedTo("message", "  ", FieldShapes.MAPPABLE_MARKER, FieldShapes.TEXT_FIELD));
        Map<String, List<String>> parameters = Map.of(
                "email", List.of("ada@example.com"),
                "message", List.of("a private note"),
                "fullName", List.of("Ada"));

        Map<String, Object> entries = enricher(configured("mysite"), 1, fields)
                .enrich(new AcceptedSubmission(form(), "mysite", Locale.ENGLISH, parameters));

        Map<String, Object> block = (Map<String, Object>) entries.get(SubmissionEventEnricher.KEY);
        assertEquals(Map.of("email", "ada@example.com"), block.get("fields"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void aFormWithNoMappingGetsAnEmptyFieldsBlock() throws Exception {
        // Verifies what a goal-only form sends: the block exists, so the script can send the event a goal
        // counts, and it carries no value at all.
        Map<String, Object> entries = enricher(configured("mysite"), 1, List.of())
                .enrich(new AcceptedSubmission(form(), "mysite", Locale.ENGLISH, Map.of("flavor", List.of("vanilla"))));

        Map<String, Object> block = (Map<String, Object>) entries.get(SubmissionEventEnricher.KEY);
        assertEquals(Map.of(), block.get("fields"));
    }

    @Test
    void theFieldsAreLookedUpByTheMappingMixin() {
        // Verifies which fields the server even considers: the query is the reader's, on the mapping mixin, so
        // the block, the rule and the editor agree. With the marker every mappable field carries, an unmapped
        // field's value would be back in the block.
        String query = SubmissionEventEnricher.queryFor("/sites/mysite/contents/contact");
        assertEquals(FormMappingReader.queryFor("/sites/mysite/contents/contact"), query);
        assertTrue(query.contains(FormMappingReader.MAPPING_MIXIN), query);
        assertFalse(query.contains(FieldShapes.MAPPABLE_MARKER), query);
    }

    @Test
    void nothingIsAddedOutsideAConfiguredSiteOrWhenTheFormCannotBeRead() throws Exception {
        // Verifies the silences: no jExperience settings for the site, and a repository failure while reading
        // the form, each give an empty map — the submission stays accepted, the body has no block.
        List<JCRNodeWrapper> fields = List.of(field("firstName", FieldShapes.MAPPABLE_MARKER, FieldShapes.TEXT_FIELD));
        assertTrue(enricher(mock(ContextServerService.class), 1, fields)
                .enrich(new AcceptedSubmission(form(), "mysite", Locale.ENGLISH, Map.of("firstName", List.of("Ada")))).isEmpty());

        JCRNodeWrapper broken = mock(JCRNodeWrapper.class);
        when(broken.getResolveSite()).thenThrow(new RepositoryException("gone"));
        assertTrue(enricher(configured("mysite"), 1, fields)
                .enrich(new AcceptedSubmission(broken, "mysite", Locale.ENGLISH, Map.of("firstName", List.of("Ada")))).isEmpty());
    }
}
