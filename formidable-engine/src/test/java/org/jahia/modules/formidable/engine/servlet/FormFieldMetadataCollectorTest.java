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

    private static Calendar calendar(int year, int month, int day, int hour, int minute) {
        Calendar calendar = Calendar.getInstance(TimeZone.getDefault());
        calendar.clear();
        calendar.set(year, month - 1, day, hour, minute, 0);
        return calendar;
    }
}
