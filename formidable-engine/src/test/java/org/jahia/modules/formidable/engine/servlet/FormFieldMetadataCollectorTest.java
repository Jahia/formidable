package org.jahia.modules.formidable.engine.servlet;

import org.jahia.modules.formidable.engine.actions.FormDataParser;
import org.jahia.services.content.JCRNodeIteratorWrapper;
import org.jahia.services.content.JCRPropertyWrapper;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRValueWrapper;
import org.junit.jupiter.api.Test;

import javax.jcr.Node;
import java.util.Calendar;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FormFieldMetadataCollectorTest {

    @Test
    void collectsChoiceFieldMetadataFromSemanticMixin() throws Exception {
        // Verifies that choice fields declared through semantic mixins expose their node type
        // and allowed option values to the submission parser.
        JCRNodeWrapper choiceField = node(
                "plan",
                "fmdb:radio",
                Set.of("fmdbmix:formElement", "fmdbmix:choiceField"),
                Map.of("options", multiValueProperty(
                        "{\"value\":\"basic\",\"label\":\"Basic\"}",
                        "{\"value\":\"pro\",\"label\":\"Pro\"}"
                )),
                List.of()
        );

        FormFieldMetadataCollector.Result result = FormFieldMetadataCollector.collectFromFormNode(
                formNodeWithFields(choiceField)
        );

        FormDataParser.FieldInfo info = result.fieldInfos().get("plan");
        // Expected outcome: the field is recognized as a choice field with both configured options.
        assertNotNull(info);
        assertEquals("fmdb:radio", info.nodeType());
        assertTrue(info.choiceField());
        assertEquals(Set.of("basic", "pro"), info.allowedChoices());
    }

    @Test
    void collectsFileFieldMetadataAndAcceptTypes() throws Exception {
        // Verifies that file-field metadata includes the configured accept allowlist.
        JCRNodeWrapper fileField = node(
                "resume",
                "fmdb:inputFile",
                Set.of("fmdbmix:formElement", "fmdbmix:fileField"),
                Map.of("accept", multiValueProperty("application/pdf", "image/*")),
                List.of()
        );

        FormFieldMetadataCollector.Result result = FormFieldMetadataCollector.collectFromFormNode(
                formNodeWithFields(fileField)
        );

        FormDataParser.FieldInfo info = result.fieldInfos().get("resume");
        // Expected outcome: the field is flagged as a file input with both accept tokens preserved.
        assertNotNull(info);
        assertTrue(info.fileField());
        assertEquals(Set.of("application/pdf", "image/*"), info.acceptTypes());
    }

    @Test
    void collectsDateAndDatetimeConstraintsFromSemanticMixins() throws Exception {
        // Verifies that date and datetime-local field mixins contribute normalized min/max constraints.
        JCRNodeWrapper dateField = node(
                "birthday",
                "fmdb:inputDate",
                Set.of("fmdbmix:formElement", "fmdbmix:dateField"),
                Map.of(
                        "min", dateProperty(calendar(2026, 6, 1, 0, 0)),
                        "max", dateProperty(calendar(2026, 6, 30, 0, 0))
                ),
                List.of()
        );
        JCRNodeWrapper datetimeField = node(
                "appointment",
                "fmdb:inputDatetimeLocal",
                Set.of("fmdbmix:formElement", "fmdbmix:datetimeLocalField"),
                Map.of(
                        "min", dateProperty(calendar(2026, 6, 1, 9, 15)),
                        "max", dateProperty(calendar(2026, 6, 1, 17, 45))
                ),
                List.of()
        );

        FormFieldMetadataCollector.Result result = FormFieldMetadataCollector.collectFromFormNode(
                formNodeWithFields(dateField, datetimeField)
        );

        FormDataParser.FieldInfo dateInfo = result.fieldInfos().get("birthday");
        // Expected outcome: date constraints are normalized as ISO local-date strings.
        assertNotNull(dateInfo);
        assertTrue(dateInfo.dateField());
        assertNotNull(dateInfo.constraints());
        assertEquals("2026-06-01", dateInfo.constraints().minDate());
        assertEquals("2026-06-30", dateInfo.constraints().maxDate());

        FormDataParser.FieldInfo datetimeInfo = result.fieldInfos().get("appointment");
        // Expected outcome: datetime-local constraints are normalized as ISO local-date-time strings.
        assertNotNull(datetimeInfo);
        assertTrue(datetimeInfo.datetimeLocalField());
        assertNotNull(datetimeInfo.constraints());
        assertEquals("2026-06-01T09:15", datetimeInfo.constraints().minDate());
        assertEquals("2026-06-01T17:45", datetimeInfo.constraints().maxDate());
    }

    @Test
    void todayFlagsResolveRelativeDateBoundsAgainstTheSubmissionDay() throws Exception {
        // Verifies the relative-bounds contract: minToday/maxToday resolve against
        // the collection day — widened by one day to absorb visitor-vs-server
        // timezone drift — and combine with any fixed bound most-restrictively.
        JCRNodeWrapper birthday = node(
                "birthday",
                "fmdb:inputDate",
                Set.of("fmdbmix:formElement", "fmdbmix:dateField"),
                Map.of("maxToday", booleanProperty(true)),
                List.of()
        );
        // A fixed min far in the past: the relative bound is tighter and wins.
        JCRNodeWrapper appointment = node(
                "appointment",
                "fmdb:inputDatetimeLocal",
                Set.of("fmdbmix:formElement", "fmdbmix:datetimeLocalField"),
                Map.of(
                        "min", dateProperty(calendar(2020, 1, 1, 8, 0)),
                        "minToday", booleanProperty(true)
                ),
                List.of()
        );
        // A fixed max already tighter than the relative one: the fixed bound wins.
        JCRNodeWrapper past = node(
                "past",
                "fmdb:inputDate",
                Set.of("fmdbmix:formElement", "fmdbmix:dateField"),
                Map.of(
                        "max", dateProperty(calendar(2020, 6, 30, 0, 0)),
                        "maxToday", booleanProperty(true)
                ),
                List.of()
        );

        FormFieldMetadataCollector.Result result = FormFieldMetadataCollector.collectFromFormNode(
                formNodeWithFields(birthday, appointment, past)
        );

        String tomorrow = java.time.LocalDate.now().plusDays(1).toString();
        String yesterday = java.time.LocalDate.now().minusDays(1).toString();
        assertEquals(tomorrow, result.fieldInfos().get("birthday").constraints().maxDate());
        assertNull(result.fieldInfos().get("birthday").constraints().minDate());
        assertEquals(yesterday + "T00:00", result.fieldInfos().get("appointment").constraints().minDate());
        assertEquals("2020-06-30", result.fieldInfos().get("past").constraints().maxDate());
    }

    @Test
    void skipsNonSubmittableNodes() throws Exception {
        // Verifies that helper nodes marked non-submittable are excluded from parser metadata.
        JCRNodeWrapper nonSubmittable = node(
                "csrfToken",
                "fmdb:hidden",
                Set.of("fmdbmix:formElement", "fmdbmix:nonSubmittable"),
                Map.of(),
                List.of()
        );

        FormFieldMetadataCollector.Result result = FormFieldMetadataCollector.collectFromFormNode(
                formNodeWithFields(nonSubmittable)
        );

        // Expected outcome: the non-submittable helper node is omitted entirely.
        assertFalse(result.fieldInfos().containsKey("csrfToken"));
    }

    private static JCRNodeWrapper formNodeWithFields(JCRNodeWrapper... fields) throws Exception {
        JCRNodeWrapper fieldList = node(
                "fields",
                "fmdb:fieldList",
                Set.of(),
                Map.of(),
                List.of(fields)
        );

        return node(
                "form",
                "fmdb:form",
                Set.of(),
                Map.of(),
                List.of(fieldList)
        );
    }

    @Test
    void collectsSourcedChoiceOptionsThroughTheResolver() throws Exception {
        // Verifies D11 plumbing: a sourced choice field gets its allowed values from the
        // re-resolved source, in the manual-options JSON format.
        JCRNodeWrapper sourced = node(
                "country",
                "fmdb:select",
                Set.of("fmdbmix:formElement", "fmdbmix:choiceField", "fmdbmix:sourcedOptions"),
                Map.of(),
                List.of()
        );

        FormFieldMetadataCollector.Result result = FormFieldMetadataCollector.collectFromFormNode(
                formNodeWithFields(sourced),
                node -> new String[]{
                        "{\"value\":\"FR\",\"label\":\"France\",\"selected\":false}",
                        "{\"value\":\"DE\",\"label\":\"Germany\",\"selected\":false}"
                }
        );

        // Expected outcome: the resolved values are the allowlist, and the field is resolvable.
        assertEquals(Set.of("FR", "DE"), result.toParserMetadata().allowedChoices("country"));
        assertFalse(result.toParserMetadata().choicesUnresolvable("country"));
    }

    @Test
    void collectsCategoryModeChoicesThroughTheResolver() throws Exception {
        // Verifies the gate stays aligned with the service dispatch: a category-mode
        // field also gets its allowlist from the resolver, not from stored options.
        JCRNodeWrapper categoryField = node(
                "tvCategory",
                "fmdb:radio",
                Set.of("fmdbmix:formElement", "fmdbmix:choiceField", "fmdbmix:categoryOptions"),
                Map.of(),
                List.of()
        );

        FormFieldMetadataCollector.Result result = FormFieldMetadataCollector.collectFromFormNode(
                formNodeWithFields(categoryField),
                node -> new String[]{"{\"value\":\"oled\",\"label\":\"OLED\",\"selected\":false}"}
        );

        // Expected outcome: the resolved category values are the allowlist.
        assertEquals(Set.of("oled"), result.toParserMetadata().allowedChoices("tvCategory"));
        assertFalse(result.toParserMetadata().choicesUnresolvable("tvCategory"));
    }

    @Test
    void flagsSourcedChoiceFieldWhenTheSourceCannotDeliver() throws Exception {
        // Verifies the D11 failure path: a failing source flags the field instead of
        // leaving an empty allowlist that would accept anything.
        JCRNodeWrapper sourced = node(
                "country",
                "fmdb:select",
                Set.of("fmdbmix:formElement", "fmdbmix:choiceField", "fmdbmix:sourcedOptions"),
                Map.of(),
                List.of()
        );

        FormFieldMetadataCollector.Result result = FormFieldMetadataCollector.collectFromFormNode(
                formNodeWithFields(sourced),
                node -> {
                    throw new IllegalStateException("source down");
                }
        );

        // Expected outcome: no allowed values, and the unresolvable flag is set.
        assertEquals(Set.of(), result.toParserMetadata().allowedChoices("country"));
        assertTrue(result.toParserMetadata().choicesUnresolvable("country"));
    }

    private static JCRNodeWrapper node(String name,
                                       String primaryType,
                                       Set<String> nodeTypes,
                                       Map<String, JCRPropertyWrapper> properties,
                                       List<JCRNodeWrapper> children) throws Exception {
        JCRNodeWrapper node = mock(JCRNodeWrapper.class);
        when(node.getName()).thenReturn(name);
        when(node.getPath()).thenReturn("/" + name);
        when(node.getPrimaryNodeTypeName()).thenReturn(primaryType);
        when(node.isNodeType(anyString())).thenAnswer(invocation -> {
            String queriedType = invocation.getArgument(0);
            return primaryType.equals(queriedType) || nodeTypes.contains(queriedType);
        });
        when(node.hasProperty(anyString())).thenAnswer(invocation -> properties.containsKey(invocation.getArgument(0)));
        when(node.getProperty(anyString())).thenAnswer(invocation -> properties.get(invocation.getArgument(0)));
        JCRNodeIteratorWrapper childrenIterator = nodeIterator(children);
        when(node.getNodes()).thenReturn(childrenIterator);

        Map<String, JCRNodeWrapper> childMap = children.stream()
                .collect(java.util.stream.Collectors.toMap(
                        child -> {
                            try {
                                return child.getName();
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        },
                        child -> child
                ));
        when(node.hasNode(anyString())).thenAnswer(invocation -> childMap.containsKey(invocation.getArgument(0)));
        when(node.getNode(anyString())).thenAnswer(invocation -> childMap.get(invocation.getArgument(0)));
        return node;
    }

    private static JCRNodeIteratorWrapper nodeIterator(List<? extends Node> nodes) {
        JCRNodeIteratorWrapper iterator = mock(JCRNodeIteratorWrapper.class);
        java.util.Iterator<? extends Node> delegate = nodes.iterator();
        when(iterator.hasNext()).thenAnswer(invocation -> delegate.hasNext());
        when(iterator.nextNode()).thenAnswer(invocation -> delegate.next());
        return iterator;
    }

    private static JCRPropertyWrapper multiValueProperty(String... rawValues) throws Exception {
        JCRPropertyWrapper property = mock(JCRPropertyWrapper.class);
        JCRValueWrapper[] values = new JCRValueWrapper[rawValues.length];
        for (int i = 0; i < rawValues.length; i++) {
            JCRValueWrapper value = mock(JCRValueWrapper.class);
            when(value.getString()).thenReturn(rawValues[i]);
            values[i] = value;
        }
        when(property.getValues()).thenReturn(values);
        return property;
    }

    private static JCRPropertyWrapper dateProperty(Calendar calendar) throws Exception {
        JCRPropertyWrapper property = mock(JCRPropertyWrapper.class);
        when(property.getDate()).thenReturn(calendar);
        return property;
    }

    private static JCRPropertyWrapper booleanProperty(boolean value) throws Exception {
        JCRPropertyWrapper property = mock(JCRPropertyWrapper.class);
        when(property.getBoolean()).thenReturn(value);
        return property;
    }

    private static Calendar calendar(int year, int month, int day, int hour, int minute) {
        Calendar calendar = Calendar.getInstance(TimeZone.getDefault());
        calendar.clear();
        calendar.set(year, month - 1, day, hour, minute, 0);
        return calendar;
    }
}
