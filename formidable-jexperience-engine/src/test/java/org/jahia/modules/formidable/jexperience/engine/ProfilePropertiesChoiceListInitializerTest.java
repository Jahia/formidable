package org.jahia.modules.formidable.jexperience.engine;

import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRPropertyWrapper;
import org.jahia.services.content.decorator.JCRSiteNode;
import org.jahia.services.content.nodetypes.initializers.ChoiceListValue;
import org.junit.jupiter.api.Test;

import javax.jcr.nodetype.NodeType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProfilePropertiesChoiceListInitializerTest {

    private static final List<ProfilePropertyDescriptor> CATALOG = List.of(
            new ProfilePropertyDescriptor("email", "Email (email)", "email", false),
            new ProfilePropertyDescriptor("firstName", "First name (firstName)", "string", false),
            new ProfilePropertyDescriptor("interests", "Interests (interests)", "string", true),
            new ProfilePropertyDescriptor("age", "Age (age)", "integer", false)
    );

    private static ProfilePropertyCatalog catalogOver(List<ProfilePropertyDescriptor> properties) throws Exception {
        ProfilePropertyCatalog catalog = mock(ProfilePropertyCatalog.class);
        when(catalog.profileProperties("site")).thenReturn(properties);
        return catalog;
    }

    private static ProfilePropertiesChoiceListInitializer initializerOver(List<ProfilePropertyDescriptor> properties) throws Exception {
        return new ProfilePropertiesChoiceListInitializer(catalogOver(properties));
    }

    private static List<String> values(List<ChoiceListValue> choices) throws Exception {
        List<String> values = new java.util.ArrayList<>();
        for (ChoiceListValue choice : choices) {
            values.add(choice.getValue().getString());
        }
        return values;
    }

    @Test
    void offersOnlyPropertiesOfTheFieldsTypeAndCardinality() throws Exception {
        // Verifies the compatibility rule: a single email field sees email and string (single-valued only),
        // a checkbox group sees the multivalued strings only, and a number field sees the integer.
        ProfilePropertiesChoiceListInitializer initializer = initializerOver(CATALOG);
        assertEquals(List.of("email", "firstName"), values(initializer.choices(new FieldShape(Set.of("email", "string"), false), "site", Locale.ENGLISH, Optional.empty())));
        assertEquals(List.of("interests"), values(initializer.choices(new FieldShape(Set.of("string"), true), "site", Locale.ENGLISH, Optional.empty())));
        assertEquals(List.of("age"), values(initializer.choices(new FieldShape(Set.of("integer", "long", "float", "double"), false), "site", Locale.ENGLISH, Optional.empty())));
    }

    @Test
    void theCurrentMappingIsKeptWhenTheListNoLongerCarriesIt() throws Exception {
        // Verifies that a property gone from jCustomer's schema stays offered, first and flagged, so the
        // Content Editor does not reset the stored value on the next save.
        ProfilePropertiesChoiceListInitializer initializer = new ProfilePropertiesChoiceListInitializer(catalogOver(CATALOG)) {
            @Override
            String keptMessage(Locale locale) {
                return "(kept)";
            }
        };
        List<ChoiceListValue> choices = initializer.choices(new FieldShape(Set.of("string"), false), "site", Locale.ENGLISH, Optional.of("nickname"));
        assertEquals(List.of("nickname", "firstName"), values(choices));
        assertEquals("nickname (kept)", choices.get(0).getDisplayName());
        // a mapping the list still carries is not duplicated
        assertEquals(List.of("firstName"), values(initializer.choices(new FieldShape(Set.of("string"), false), "site", Locale.ENGLISH, Optional.of("firstName"))));
    }

    @Test
    void theCurrentMappingSurvivesAnUnreachableJCustomer() throws Exception {
        // Verifies the outage case: the stored mapping comes first, the explanatory entry second, so a
        // save during the outage keeps the mapping and the author still reads why the list is short.
        ProfilePropertyCatalog catalog = mock(ProfilePropertyCatalog.class);
        when(catalog.profileProperties("site")).thenThrow(new ProfilePropertiesUnavailableException("down"));
        ProfilePropertiesChoiceListInitializer initializer = new ProfilePropertiesChoiceListInitializer(catalog) {
            @Override
            String unavailableMessage(Locale locale) {
                return "unreachable";
            }

            @Override
            String keptMessage(Locale locale) {
                return "(kept)";
            }
        };
        List<ChoiceListValue> choices = initializer.choices(new FieldShape(Set.of("string"), false), "site", Locale.ENGLISH, Optional.of("firstName"));
        assertEquals(List.of("firstName", ""), values(choices));
        assertEquals("firstName (kept)", choices.get(0).getDisplayName());
        assertEquals("unreachable", choices.get(1).getDisplayName());
    }

    @Test
    void labelsAreTheDescriptorsLabels() throws Exception {
        // Verifies that the author reads the property's name, with its id.
        ProfilePropertiesChoiceListInitializer initializer = initializerOver(CATALOG);
        List<ChoiceListValue> choices = initializer.choices(new FieldShape(Set.of("integer"), false), "site", Locale.ENGLISH, Optional.empty());
        assertEquals("Age (age)", choices.get(0).getDisplayName());
    }

    @Test
    void noCompatiblePropertyYieldsOneMessageEntryWithAnEmptyValue() throws Exception {
        // Verifies that a field whose kind no profile property matches (a boolean on jCustomer's default
        // schema) reads why the dropdown is empty instead of facing a blank list.
        ProfilePropertiesChoiceListInitializer initializer = new ProfilePropertiesChoiceListInitializer(catalogOver(CATALOG)) {
            @Override
            String noneMessage(Locale locale) {
                return "none";
            }
        };
        List<ChoiceListValue> choices = initializer.choices(new FieldShape(Set.of("boolean"), false), "site", Locale.ENGLISH, Optional.empty());
        assertEquals(1, choices.size());
        assertEquals("none", choices.get(0).getDisplayName());
        assertEquals("", choices.get(0).getValue().getString());
        // pre-selected, so the closed select shows the message
        assertEquals("true", choices.get(0).getProperties().get(ProfilePropertiesChoiceListInitializer.DEFAULT_PROPERTY));
    }

    @Test
    void anUnreachableJCustomerYieldsOneMessageEntryWithAnEmptyValue() throws Exception {
        // Verifies that the editor shows a message instead of an empty or broken dropdown, pre-selected,
        // and that saving it stores nothing.
        ProfilePropertyCatalog catalog = mock(ProfilePropertyCatalog.class);
        when(catalog.profileProperties("site")).thenThrow(new ProfilePropertiesUnavailableException("down"));
        ProfilePropertiesChoiceListInitializer initializer = new ProfilePropertiesChoiceListInitializer(catalog) {
            @Override
            String unavailableMessage(Locale locale) {
                return "unreachable";
            }
        };
        List<ChoiceListValue> choices = initializer.choices(new FieldShape(Set.of("string"), false), "site", Locale.ENGLISH, Optional.empty());
        assertEquals(1, choices.size());
        assertEquals("unreachable", choices.get(0).getDisplayName());
        assertEquals("", choices.get(0).getValue().getString());
        assertEquals("true", choices.get(0).getProperties().get(ProfilePropertiesChoiceListInitializer.DEFAULT_PROPERTY));
    }

    // ---- the runtime entry point, with the context jcontent builds ----

    private static JCRSiteNode site() {
        JCRSiteNode site = mock(JCRSiteNode.class);
        when(site.getSiteKey()).thenReturn("site");
        return site;
    }

    /** An existing field node as the editor hands it over: kinds, stored "multiple", stored mapping, site. */
    private static JCRNodeWrapper fieldNode(String stored, Boolean multiple, String... types) throws Exception {
        JCRNodeWrapper node = mock(JCRNodeWrapper.class);
        for (String type : types) {
            when(node.isNodeType(type)).thenReturn(true);
        }
        JCRSiteNode site = site();
        when(node.getResolveSite()).thenReturn(site);
        if (multiple != null) {
            JCRPropertyWrapper property = mock(JCRPropertyWrapper.class);
            when(property.getBoolean()).thenReturn(multiple);
            when(node.hasProperty(FieldShapes.MULTIPLE_PROPERTY)).thenReturn(true);
            when(node.getProperty(FieldShapes.MULTIPLE_PROPERTY)).thenReturn(property);
        }
        if (stored != null) {
            JCRPropertyWrapper property = mock(JCRPropertyWrapper.class);
            when(property.getString()).thenReturn(stored);
            when(node.hasProperty(ProfilePropertiesChoiceListInitializer.PROPERTY)).thenReturn(true);
            when(node.getProperty(ProfilePropertiesChoiceListInitializer.PROPERTY)).thenReturn(property);
        }
        return node;
    }

    private static Map<String, Object> context(Object... keyValues) {
        Map<String, Object> context = new HashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            context.put((String) keyValues[i], keyValues[i + 1]);
        }
        return context;
    }

    private static List<ChoiceListValue> listed(ProfilePropertiesChoiceListInitializer initializer, Map<String, Object> context) {
        return initializer.getChoiceListValues(null, null, List.of(), Locale.ENGLISH, context);
    }

    @Test
    void aMissingContextYieldsNothing() throws Exception {
        // Verifies the guard on a call without the editor's context.
        assertEquals(List.of(), listed(initializerOver(CATALOG), null));
        assertEquals(List.of(), listed(initializerOver(CATALOG), context()));
    }

    @Test
    void anExistingFieldIsShapedAndSitedFromTheContextNode() throws Exception {
        // Verifies the edit path end to end: the field's node gives its kinds, its site key and its stored
        // mapping — kept first when the list lacks it.
        ProfilePropertiesChoiceListInitializer initializer = new ProfilePropertiesChoiceListInitializer(catalogOver(CATALOG)) {
            @Override
            String keptMessage(Locale locale) {
                return "(kept)";
            }
        };
        JCRNodeWrapper text = fieldNode("nickname", null, FieldShapes.MAPPABLE_MARKER, FieldShapes.TEXT_FIELD);
        List<ChoiceListValue> choices = listed(initializer, context(ProfilePropertiesChoiceListInitializer.CONTEXT_NODE, text));
        assertEquals(List.of("nickname", "firstName"), values(choices));
        assertEquals("nickname (kept)", choices.get(0).getDisplayName());
    }

    @Test
    void aNodeThatIsNotMappableYieldsNothing() throws Exception {
        // Verifies the shape guard: a node without the marker (a file field, a non-field) gets no list.
        JCRNodeWrapper notMappable = fieldNode(null, null, FieldShapes.TEXT_FIELD);
        assertEquals(List.of(), listed(initializerOver(CATALOG), context(ProfilePropertiesChoiceListInitializer.CONTEXT_NODE, notMappable)));
    }

    @Test
    void aFieldBeingCreatedIsShapedFromItsTypeAndSitedFromItsParent() throws Exception {
        // Verifies the create path: no node yet, the type gives the kinds and the parent gives the site.
        NodeType number = mock(NodeType.class);
        when(number.isNodeType(FieldShapes.MAPPABLE_MARKER)).thenReturn(true);
        when(number.isNodeType(FieldShapes.NUMBER_FIELD)).thenReturn(true);
        JCRNodeWrapper parent = mock(JCRNodeWrapper.class);
        JCRSiteNode parentSite = site();
        when(parent.getResolveSite()).thenReturn(parentSite);
        List<ChoiceListValue> choices = listed(initializerOver(CATALOG), context(
                ProfilePropertiesChoiceListInitializer.CONTEXT_TYPE, number,
                ProfilePropertiesChoiceListInitializer.CONTEXT_PARENT, parent));
        assertEquals(List.of("age"), values(choices));
        // without a node or a parent there is no site to ask, hence no list
        assertEquals(List.of(), listed(initializerOver(CATALOG), context(ProfilePropertiesChoiceListInitializer.CONTEXT_TYPE, number)));
    }

    @Test
    void theEditorsUnsavedMultipleToggleReshapesTheList() throws Exception {
        // Verifies the dependentProperties re-query: the "multiple" value jcontent puts in the context —
        // a boolean, or a list of strings — wins over the stored one, on an existing node and in create mode.
        JCRNodeWrapper singleSelect = fieldNode(null, false, FieldShapes.MAPPABLE_MARKER, FieldShapes.CHOICE_FIELD);
        assertEquals(List.of("firstName"), values(listed(initializerOver(CATALOG), context(ProfilePropertiesChoiceListInitializer.CONTEXT_NODE, singleSelect))));
        assertEquals(List.of("interests"), values(listed(initializerOver(CATALOG), context(
                ProfilePropertiesChoiceListInitializer.CONTEXT_NODE, singleSelect, FieldShapes.MULTIPLE_PROPERTY, Boolean.TRUE))));
        assertEquals(List.of("interests"), values(listed(initializerOver(CATALOG), context(
                ProfilePropertiesChoiceListInitializer.CONTEXT_NODE, singleSelect, FieldShapes.MULTIPLE_PROPERTY, List.of("true")))));
        // jcontent sends an empty list for a null value: the stored cardinality applies — told apart from
        // "forced single-valued" on a node whose stored multiple is true
        JCRNodeWrapper multipleSelect = fieldNode(null, true, FieldShapes.MAPPABLE_MARKER, FieldShapes.CHOICE_FIELD);
        assertEquals(List.of("interests"), values(listed(initializerOver(CATALOG), context(
                ProfilePropertiesChoiceListInitializer.CONTEXT_NODE, multipleSelect, FieldShapes.MULTIPLE_PROPERTY, List.of()))));
        assertEquals(List.of("firstName"), values(listed(initializerOver(CATALOG), context(
                ProfilePropertiesChoiceListInitializer.CONTEXT_NODE, singleSelect, FieldShapes.MULTIPLE_PROPERTY, List.of()))));

        NodeType select = mock(NodeType.class);
        when(select.isNodeType(FieldShapes.MAPPABLE_MARKER)).thenReturn(true);
        when(select.isNodeType(FieldShapes.CHOICE_FIELD)).thenReturn(true);
        JCRNodeWrapper parent = mock(JCRNodeWrapper.class);
        JCRSiteNode parentSite = site();
        when(parent.getResolveSite()).thenReturn(parentSite);
        assertEquals(List.of("interests"), values(listed(initializerOver(CATALOG), context(
                ProfilePropertiesChoiceListInitializer.CONTEXT_TYPE, select,
                ProfilePropertiesChoiceListInitializer.CONTEXT_PARENT, parent,
                FieldShapes.MULTIPLE_PROPERTY, Boolean.TRUE))));
    }

    @Test
    void theKeyIsFixed() {
        // Verifies that the registry key cannot drift from the one the CND names.
        ProfilePropertiesChoiceListInitializer initializer = new ProfilePropertiesChoiceListInitializer();
        initializer.setKey("somethingElse");
        assertEquals(ProfilePropertiesChoiceListInitializer.KEY, initializer.getKey());
        assertEquals("formidableJExperienceProfileProperties", ProfilePropertiesChoiceListInitializer.KEY);
    }
}
