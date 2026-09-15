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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The jexperience block of an accepted submission: the form's UUID and the accepted values of its
 * fields, shaped as the mapping rule expects, minus the ones the author marked sensitive — and no
 * block outside a configured site.
 */
class SubmissionEventEnricherTest {

    private static final String FORM_UUID = "8f7e2a10-0000-4000-8000-000000000001";

    /** A field of the form, mappable and not sensitive. */
    private static JCRNodeWrapper field(String name, String... types) throws RepositoryException {
        JCRNodeWrapper node = mock(JCRNodeWrapper.class);
        when(node.getName()).thenReturn(name);
        for (String type : types) {
            when(node.isNodeType(type)).thenReturn(true);
        }
        return node;
    }

    /** The same field, with the author's "this value never leaves the site" ticked. */
    private static JCRNodeWrapper sensitive(JCRNodeWrapper field) throws RepositoryException {
        JCRPropertyWrapper flag = mock(JCRPropertyWrapper.class);
        when(flag.getBoolean()).thenReturn(true);
        when(field.hasProperty(SensitiveField.PROPERTY)).thenReturn(true);
        when(field.getProperty(SensitiveField.PROPERTY)).thenReturn(flag);
        return field;
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

    /** An enricher over a form whose mappable fields the query returns; every choice field counts the given choices. */
    private static SubmissionEventEnricher enricher(ContextServerService service, int choices, List<JCRNodeWrapper> fields) throws RepositoryException {
        ChoiceOptionsResolver resolver = mock(ChoiceOptionsResolver.class);
        when(resolver.countChoices(any(), any())).thenReturn(OptionalInt.of(choices));
        NodeIterator nodes = iterator(fields);
        return new SubmissionEventEnricher(resolver, service) {
            @Override
            NodeIterator mappableFields(JCRNodeWrapper form) {
                return nodes;
            }
        };
    }

    @Test
    @SuppressWarnings("unchecked")
    void theBlockCarriesTheFormUuidAndTheValuesShapedLikeTheRule() throws Exception {
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
    void aFieldTheAuthorMarkedSensitiveNeverLeavesTheServer() throws Exception {
        // Verifies the one boundary the author draws: a sensitive field's value is left out even though the
        // field is mappable and the submitter sent it, while the other values are all there — an unmapped one
        // included, since a mapping made in jExperience's screen may name it.
        List<JCRNodeWrapper> fields = List.of(
                field("email", FieldShapes.MAPPABLE_MARKER, FieldShapes.EMAIL_FIELD),
                field("message", FieldShapes.MAPPABLE_MARKER, FieldShapes.TEXT_FIELD),
                sensitive(field("nationalId", FieldShapes.MAPPABLE_MARKER, FieldShapes.TEXT_FIELD)));
        Map<String, List<String>> parameters = Map.of(
                "email", List.of("ada@example.com"),
                "message", List.of("a note"),
                "nationalId", List.of("1234567890"));

        Map<String, Object> entries = enricher(configured("mysite"), 1, fields)
                .enrich(new AcceptedSubmission(form(), "mysite", Locale.ENGLISH, parameters));

        Map<String, Object> block = (Map<String, Object>) entries.get(SubmissionEventEnricher.KEY);
        assertEquals(Map.of("email", "ada@example.com", "message", "a note"), block.get("fields"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void aFormWhoseFieldsAreAllSensitiveGetsAnEmptyFieldsBlock() throws Exception {
        // Verifies the far end: the block still exists, so the script can send the event a goal counts, and it
        // carries no value at all.
        Map<String, Object> entries = enricher(configured("mysite"), 1, List.of())
                .enrich(new AcceptedSubmission(form(), "mysite", Locale.ENGLISH, Map.of("flavor", List.of("vanilla"))));

        Map<String, Object> block = (Map<String, Object>) entries.get(SubmissionEventEnricher.KEY);
        assertEquals(Map.of(), block.get("fields"));
    }

    @Test
    void theFieldsAreLookedUpByTheMappableMarker() {
        // Verifies which fields the server considers: every one carrying the marker, not only the mapped ones,
        // since a mapping made in jExperience's own screen names a field Formidable need not know. A quote in
        // the path is doubled, as SQL2 wants.
        assertEquals("SELECT * FROM [" + FieldShapes.MAPPABLE_MARKER + "] WHERE ISDESCENDANTNODE('/sites/mysite/contents/contact')",
                SubmissionEventEnricher.queryFor("/sites/mysite/contents/contact"));
        assertEquals("SELECT * FROM [" + FieldShapes.MAPPABLE_MARKER + "] WHERE ISDESCENDANTNODE('/sites/mysite/contents/l''enquete')",
                SubmissionEventEnricher.queryFor("/sites/mysite/contents/l'enquete"));
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
