package org.jahia.modules.formidable.jexperience.engine;

import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRPropertyWrapper;
import org.junit.jupiter.api.Test;

import javax.jcr.nodetype.NodeType;

import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FieldShapesTest {

    private static Optional<FieldShape> shape(Set<String> types, Set<String> flags) throws Exception {
        Predicate<String> isNodeType = types::contains;
        Predicate<String> flag = flags::contains;
        return FieldShapes.infer(isNodeType, flag, () -> OptionalInt.empty());
    }

    private static Set<String> mappable(String... kinds) {
        Set<String> types = new java.util.HashSet<>(Set.of(FieldShapes.MAPPABLE_MARKER, "fmdbmix:element"));
        types.addAll(Set.of(kinds));
        return types;
    }

    @Test
    void aFieldWithoutTheMarkerIsNotMappable() throws Exception {
        // Verifies that only field types declaring fmdbmix:profileMappableField take part.
        assertTrue(shape(Set.of("fmdbmix:element", "fmdbmix:textField"), Set.of()).isEmpty());
    }

    @Test
    void aFileFieldIsNeverMappable() throws Exception {
        // Verifies that the file kind is excluded even when a type claims the marker.
        assertTrue(shape(mappable(FieldShapes.FILE_FIELD), Set.of()).isEmpty());
    }

    @Test
    void textColourAndHiddenHoldOneString() throws Exception {
        // Verifies that text-like kinds, and a field with no value mixin at all, map to a single string.
        assertEquals(Optional.of(new FieldShape(Set.of("string"), false)), shape(mappable(FieldShapes.TEXT_FIELD), Set.of()));
        assertEquals(Optional.of(new FieldShape(Set.of("string"), false)), shape(mappable(FieldShapes.COLOR_FIELD), Set.of()));
        assertEquals(Optional.of(new FieldShape(Set.of("string"), false)), shape(mappable(), Set.of()));
    }

    @Test
    void emailBeatsTextAndFollowsItsMultipleFlag() throws Exception {
        // Verifies that the email input, which also carries fmdbmix:textField, offers email and string,
        // and turns multivalued with its "multiple" property.
        Optional<FieldShape> single = shape(mappable(FieldShapes.EMAIL_FIELD, FieldShapes.TEXT_FIELD), Set.of());
        assertEquals(Optional.of(new FieldShape(Set.of("email", "string"), false)), single);
        Optional<FieldShape> multiple = shape(mappable(FieldShapes.EMAIL_FIELD, FieldShapes.TEXT_FIELD), Set.of(FieldShapes.MULTIPLE_PROPERTY));
        assertTrue(multiple.orElseThrow().multivalued());
    }

    @Test
    void choiceFieldsAreStringsSingleUnlessMultipleSelect() throws Exception {
        // Verifies the cardinality rule of choice fields with a "multiple" property: radio and single select
        // hold one value, a multiple select holds a list.
        assertFalse(shape(mappable(FieldShapes.CHOICE_FIELD), Set.of()).orElseThrow().multivalued());
        assertTrue(shape(mappable(FieldShapes.CHOICE_FIELD), Set.of(FieldShapes.MULTIPLE_PROPERTY)).orElseThrow().multivalued());
    }

    @Test
    void theCheckboxFollowsItsNumberOfChoicesAsTheViewDoes() throws Exception {
        // Verifies the renderer's rule applied to the shape: exactly one choice is one checkbox, one value;
        // two or more, none, or an unknown count is a group — and the "multiple" flag plays no part.
        Set<String> checkbox = mappable(FieldShapes.CHOICE_FIELD);
        checkbox.add(FieldShapes.CHECKBOX_TYPE);
        Predicate<String> isCheckbox = checkbox::contains;
        Predicate<String> noFlag = name -> false;
        assertFalse(FieldShapes.infer(isCheckbox, noFlag, () -> OptionalInt.of(1)).orElseThrow().multivalued());
        assertTrue(FieldShapes.infer(isCheckbox, noFlag, () -> OptionalInt.of(2)).orElseThrow().multivalued());
        assertTrue(FieldShapes.infer(isCheckbox, noFlag, () -> OptionalInt.of(0)).orElseThrow().multivalued());
        FieldShape unknown = FieldShapes.infer(isCheckbox, noFlag, () -> OptionalInt.empty()).orElseThrow();
        assertTrue(unknown.multivalued());
        assertEquals(Set.of("string"), unknown.valueTypeIds());
        assertFalse(FieldShapes.infer(isCheckbox, FieldShapes.MULTIPLE_PROPERTY::equals, () -> OptionalInt.of(1)).orElseThrow().multivalued());
    }

    @Test
    void numberBooleanAndDateKindsOfferTheirOwnTypes() throws Exception {
        // Verifies the value types offered for the numeric, boolean and date kinds.
        assertEquals(Set.of("integer", "long", "float", "double"), shape(mappable(FieldShapes.NUMBER_FIELD), Set.of()).orElseThrow().valueTypeIds());
        assertEquals(Set.of("boolean"), shape(mappable(FieldShapes.BOOLEAN_FIELD), Set.of()).orElseThrow().valueTypeIds());
        assertEquals(Set.of("date"), shape(mappable(FieldShapes.DATE_FIELD), Set.of()).orElseThrow().valueTypeIds());
        assertEquals(Set.of("date"), shape(mappable(FieldShapes.DATETIME_LOCAL_FIELD), Set.of()).orElseThrow().valueTypeIds());
    }

    @Test
    void acceptsMatchesTypeAndCardinality() throws Exception {
        // Verifies that a property must match both the value type and the cardinality of the field.
        FieldShape single = new FieldShape(Set.of("email", "string"), false);
        assertTrue(single.accepts("email", false));
        assertTrue(single.accepts("string", false));
        assertFalse(single.accepts("string", true));
        assertFalse(single.accepts("integer", false));
        assertFalse(single.accepts(null, false));
    }

    private static JCRNodeWrapper node(Boolean multiple, String... types) throws Exception {
        JCRNodeWrapper node = mock(JCRNodeWrapper.class);
        for (String type : types) {
            when(node.isNodeType(type)).thenReturn(true);
        }
        if (multiple != null) {
            JCRPropertyWrapper property = mock(JCRPropertyWrapper.class);
            when(property.getBoolean()).thenReturn(multiple);
            when(node.hasProperty(FieldShapes.MULTIPLE_PROPERTY)).thenReturn(true);
            when(node.getProperty(FieldShapes.MULTIPLE_PROPERTY)).thenReturn(property);
        }
        return node;
    }

    @Test
    void anExistingFieldIsReadFromItsNode() throws Exception {
        // Verifies the runtime entry point on an existing node: the kinds come from isNodeType, the
        // cardinality from the stored "multiple" property, and a node without the marker is not mappable.
        JCRNodeWrapper multipleSelect = node(true, FieldShapes.MAPPABLE_MARKER, FieldShapes.CHOICE_FIELD);
        assertEquals(Optional.of(new FieldShape(Set.of("string"), true)), FieldShapes.infer(multipleSelect, Optional.empty(), () -> OptionalInt.empty()));
        JCRNodeWrapper text = node(null, FieldShapes.MAPPABLE_MARKER, FieldShapes.TEXT_FIELD);
        assertEquals(Optional.of(new FieldShape(Set.of("string"), false)), FieldShapes.infer(text, Optional.empty(), () -> OptionalInt.empty()));
        assertTrue(FieldShapes.infer(node(null, FieldShapes.TEXT_FIELD), Optional.empty(), () -> OptionalInt.empty()).isEmpty());
    }

    @Test
    void theEditorsUnsavedMultipleToggleWinsOverTheStoredOne() throws Exception {
        // Verifies the dependentProperties path: the value the author just switched, not yet saved, decides.
        JCRNodeWrapper singleSelect = node(false, FieldShapes.MAPPABLE_MARKER, FieldShapes.CHOICE_FIELD);
        assertTrue(FieldShapes.infer(singleSelect, Optional.of(true), () -> OptionalInt.empty()).orElseThrow().multivalued());
        JCRNodeWrapper multipleSelect = node(true, FieldShapes.MAPPABLE_MARKER, FieldShapes.CHOICE_FIELD);
        assertFalse(FieldShapes.infer(multipleSelect, Optional.of(false), () -> OptionalInt.empty()).orElseThrow().multivalued());
    }

    @Test
    void aFieldBeingCreatedIsReadFromItsTypeAndTheEditorsToggle() throws Exception {
        // Verifies the runtime entry point on a type (create mode): single until the author switches
        // "multiple" on, and never mappable without the marker.
        NodeType email = mock(NodeType.class);
        when(email.isNodeType(FieldShapes.MAPPABLE_MARKER)).thenReturn(true);
        when(email.isNodeType(FieldShapes.EMAIL_FIELD)).thenReturn(true);
        when(email.isNodeType(FieldShapes.TEXT_FIELD)).thenReturn(true);
        assertEquals(Optional.of(new FieldShape(Set.of("email", "string"), false)), FieldShapes.infer(email, false, () -> OptionalInt.empty()));
        assertEquals(Optional.of(new FieldShape(Set.of("email", "string"), true)), FieldShapes.infer(email, true, () -> OptionalInt.empty()));
        NodeType plain = mock(NodeType.class);
        assertTrue(FieldShapes.infer(plain, false, () -> OptionalInt.empty()).isEmpty());
    }
}
