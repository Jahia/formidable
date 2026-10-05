package org.jahia.modules.formidable.jexperience.engine.submission;

import org.jahia.modules.formidable.engine.api.AcceptedSubmission;
import org.jahia.modules.formidable.engine.api.ChoiceOptionsResolver;
import org.jahia.modules.formidable.engine.api.FmdbMixin;
import org.jahia.modules.jexperience.admin.ContextServerService;
import org.jahia.modules.jexperience.admin.ContextServerStatus;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRPropertyWrapper;
import org.jahia.services.content.decorator.JCRSiteNode;
import org.jahia.modules.formidable.jexperience.engine.field.FieldShapes;
import org.jahia.modules.formidable.jexperience.engine.util.JExperienceSite;
import org.junit.jupiter.api.Test;

import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import java.util.Arrays;
import java.util.Iterator;
import java.util.HashMap;
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
 * mappable fields, shaped as the mapping rule expects — and no block on a site whose pages carry no
 * tracker.
 */
class SubmissionEventEnricherTest {

    private static final String FORM_UUID = "8f7e2a10-0000-4000-8000-000000000001";

    /** A field of the form, carrying the given types. */
    private static JCRNodeWrapper field(String name, String... types) throws RepositoryException {
        JCRNodeWrapper node = mock(JCRNodeWrapper.class);
        when(node.getName()).thenReturn(name);
        when(node.getIdentifier()).thenReturn("uuid-of-" + name);
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
        return form(true);
    }

    /** The same form on a site that does, or does not, run jExperience. */
    private static JCRNodeWrapper form(boolean jExperienceEnabled) throws RepositoryException {
        JCRNodeWrapper form = mock(JCRNodeWrapper.class);
        when(form.getIdentifier()).thenReturn(FORM_UUID);
        JCRSiteNode site = mock(JCRSiteNode.class);
        when(site.getSiteKey()).thenReturn("mysite");
        when(site.getDefaultLanguage()).thenReturn("en");
        when(site.getInstalledModules()).thenReturn(jExperienceEnabled
                ? List.of("formidable-elements", JExperienceSite.MODULE)
                : List.of("formidable-elements"));
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
                field("firstName", FmdbMixin.PROFILE_MAPPABLE_FIELD, FmdbMixin.TEXT_FIELD),
                field("check-me", FmdbMixin.PROFILE_MAPPABLE_FIELD, FmdbMixin.CHOICE_FIELD, FmdbMixin.CARDINALITY_FROM_CHOICES),
                multiple(field("topics", FmdbMixin.PROFILE_MAPPABLE_FIELD, FmdbMixin.CHOICE_FIELD)),
                field("nickname", FmdbMixin.PROFILE_MAPPABLE_FIELD, FmdbMixin.TEXT_FIELD));
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
                field("upload", FmdbMixin.PROFILE_MAPPABLE_FIELD, FmdbMixin.FILE_FIELD),
                field("consent", FmdbMixin.PROFILE_MAPPABLE_FIELD, FmdbMixin.CHOICE_FIELD, FmdbMixin.CARDINALITY_FROM_CHOICES));
        Map<String, Object> entries = enricher(configured("mysite"), 1, fields)
                .enrich(new AcceptedSubmission(form(), "mysite", Locale.ENGLISH, Map.of("upload", List.of("f.txt"), "consent", List.of("yes"))));

        Map<String, Object> block = (Map<String, Object>) entries.get(SubmissionEventEnricher.KEY);
        assertEquals(Map.of("consent", "yes"), block.get("fields"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void aFormWithNoAnsweredMappableFieldGetsAnEmptyFieldsBlock() throws Exception {
        // Verifies the far end: the block still exists, so the script can send the event a goal counts, and it
        // carries no value at all (review of #379: kept from the flag's tests, which never needed the flag for it).
        Map<String, Object> entries = enricher(configured("mysite"), 1, List.of())
                .enrich(new AcceptedSubmission(form(), "mysite", Locale.ENGLISH, Map.of("flavor", List.of("vanilla"))));

        Map<String, Object> block = (Map<String, Object>) entries.get(SubmissionEventEnricher.KEY);
        assertEquals(FORM_UUID, block.get("formId"));
        assertEquals(Map.of(), block.get("fields"));
    }

    @Test
    void theFieldsAreLookedUpByTheMappableMarker() {
        // Verifies which fields the server considers: every one carrying the marker, not only the mapped ones,
        // since a mapping made in jExperience's own screen names a field Formidable need not know. A quote in
        // the path is doubled, as SQL2 wants.
        assertEquals("SELECT * FROM [" + FmdbMixin.PROFILE_MAPPABLE_FIELD + "] WHERE ISDESCENDANTNODE('/sites/mysite/contents/contact')",
                SubmissionEventEnricher.queryFor("/sites/mysite/contents/contact"));
        assertEquals("SELECT * FROM [" + FmdbMixin.PROFILE_MAPPABLE_FIELD + "] WHERE ISDESCENDANTNODE('/sites/mysite/contents/l''enquete')",
                SubmissionEventEnricher.queryFor("/sites/mysite/contents/l'enquete"));
    }

    @Test
    void nothingIsAddedOnAnUntrackedSiteOrWhenTheFormCannotBeRead() throws Exception {
        // Verifies the silences: no jExperience settings for the site, a site holding the settings but not
        // running jExperience (the settings are not a per-site answer — jExperience falls back to the
        // platform's, so this site would claim to be configured), and a repository failure while reading the
        // form, each give an empty map — the submission stays accepted, the body has no block.
        List<JCRNodeWrapper> fields = List.of(field("firstName", FmdbMixin.PROFILE_MAPPABLE_FIELD, FmdbMixin.TEXT_FIELD));
        assertTrue(enricher(mock(ContextServerService.class), 1, fields)
                .enrich(new AcceptedSubmission(form(), "mysite", Locale.ENGLISH, Map.of("firstName", List.of("Ada")))).isEmpty());
        assertTrue(enricher(configured("mysite"), 1, fields)
                .enrich(new AcceptedSubmission(form(false), "mysite", Locale.ENGLISH, Map.of("firstName", List.of("Ada")))).isEmpty());

        JCRNodeWrapper broken = mock(JCRNodeWrapper.class);
        when(broken.getResolveSite()).thenThrow(new RepositoryException("gone"));
        assertTrue(enricher(configured("mysite"), 1, fields)
                .enrich(new AcceptedSubmission(broken, "mysite", Locale.ENGLISH, Map.of("firstName", List.of("Ada")))).isEmpty());
    }

    @Test
    @SuppressWarnings("unchecked")
    void aFieldPresentWithNoValueIsSkippedRatherThanRead() throws Exception {
        // Verifies the empty case, which is not the absent one: a checkbox group with nothing ticked can reach
        // the pipeline as a name mapped to an empty list, and reading its first value would throw — dropping
        // the whole block through the servlet's per-enricher guard, not just that field.
        Map<String, List<String>> parameters = new HashMap<>();
        parameters.put("topics", List.of());
        parameters.put("fullName", List.of("Ada"));

        Map<String, Object> entries = enricher(configured("mysite"), 1, List.of(
                field("topics", FmdbMixin.PROFILE_MAPPABLE_FIELD, FmdbMixin.CHOICE_FIELD),
                field("fullName", FmdbMixin.PROFILE_MAPPABLE_FIELD, FmdbMixin.TEXT_FIELD)))
                .enrich(new AcceptedSubmission(form(), "mysite", Locale.ENGLISH, parameters));

        Map<String, Object> block = (Map<String, Object>) entries.get(SubmissionEventEnricher.KEY);
        assertEquals(Map.of("fullName", "Ada"), block.get("fields"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void aFieldLeftUnansweredIsAbsentFromTheBlockRatherThanEmpty() throws Exception {
        // Verifies the blank case (issue #339): a text left blank and a select left on its empty option reach the
        // pipeline as a list of one empty string, not as an empty list. Sent as "", jCustomer writes the empty
        // string — alwaysSet erases the profile's value, setIfMissing writes "" and then keeps it for good. The
        // field must be absent, as a group with nothing ticked is; blanks are not answers either.
        Map<String, List<String>> parameters = new HashMap<>();
        parameters.put("country", List.of(""));
        parameters.put("nickname", List.of("   "));
        parameters.put("fullName", List.of("Ada"));

        Map<String, Object> entries = enricher(configured("mysite"), 3, List.of(
                field("country", FmdbMixin.PROFILE_MAPPABLE_FIELD, FmdbMixin.CHOICE_FIELD),
                field("nickname", FmdbMixin.PROFILE_MAPPABLE_FIELD, FmdbMixin.TEXT_FIELD),
                field("fullName", FmdbMixin.PROFILE_MAPPABLE_FIELD, FmdbMixin.TEXT_FIELD)))
                .enrich(new AcceptedSubmission(form(), "mysite", Locale.ENGLISH, parameters));

        Map<String, Object> block = (Map<String, Object>) entries.get(SubmissionEventEnricher.KEY);
        assertEquals(Map.of("fullName", "Ada"), block.get("fields"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void aMultiValuedFieldKeepsItsAnswersAndDropsItsBlanks() throws Exception {
        // Verifies the list case: a multiple select can post an empty option beside real ones; the blanks go, the
        // answers stay in their order, and a list of blanks alone leaves the field absent like a scalar would.
        Map<String, List<String>> parameters = new HashMap<>();
        parameters.put("topics", List.of("", "cdp", " ", "forms"));
        parameters.put("services", List.of("", ""));

        Map<String, Object> entries = enricher(configured("mysite"), 3, List.of(
                multiple(field("topics", FmdbMixin.PROFILE_MAPPABLE_FIELD, FmdbMixin.CHOICE_FIELD)),
                multiple(field("services", FmdbMixin.PROFILE_MAPPABLE_FIELD, FmdbMixin.CHOICE_FIELD))))
                .enrich(new AcceptedSubmission(form(), "mysite", Locale.ENGLISH, parameters));

        Map<String, Object> block = (Map<String, Object>) entries.get(SubmissionEventEnricher.KEY);
        assertEquals(Map.of("topics", List.of("cdp", "forms")), block.get("fields"));
    }

    @Test
    void answeredKeepsTheNonBlankValuesInOrderAndReadsAMissingNameAsNothing() {
        // Verifies the rule alone, the three outcomes: nothing for a missing name, nothing for blanks only, the
        // non-blank values in their order otherwise — a null entry counted as a blank, never dereferenced.
        assertEquals(List.of(), SubmissionEventEnricher.answered(null));
        assertEquals(List.of(), SubmissionEventEnricher.answered(List.of("", "  ")));
        assertEquals(List.of("b", "a"), SubmissionEventEnricher.answered(Arrays.asList(null, "b", "", "a")));
    }

}
