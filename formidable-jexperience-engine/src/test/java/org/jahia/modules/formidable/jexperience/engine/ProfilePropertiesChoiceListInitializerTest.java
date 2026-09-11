package org.jahia.modules.formidable.jexperience.engine;

import org.jahia.services.content.nodetypes.initializers.ChoiceListValue;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;
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
        assertEquals(List.of("email", "firstName"), values(initializer.choices(new FieldShape(Set.of("email", "string"), false), "site", Locale.ENGLISH)));
        assertEquals(List.of("interests"), values(initializer.choices(new FieldShape(Set.of("string"), true), "site", Locale.ENGLISH)));
        assertEquals(List.of("age"), values(initializer.choices(new FieldShape(Set.of("integer", "long", "float", "double"), false), "site", Locale.ENGLISH)));
    }

    @Test
    void labelsAreTheDescriptorsLabels() throws Exception {
        // Verifies that the author reads the property's name, with its id.
        ProfilePropertiesChoiceListInitializer initializer = initializerOver(CATALOG);
        List<ChoiceListValue> choices = initializer.choices(new FieldShape(Set.of("integer"), false), "site", Locale.ENGLISH);
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
        List<ChoiceListValue> choices = initializer.choices(new FieldShape(Set.of("boolean"), false), "site", Locale.ENGLISH);
        assertEquals(1, choices.size());
        assertEquals("none", choices.get(0).getDisplayName());
        assertEquals("", choices.get(0).getValue().getString());
    }

    @Test
    void anUnreachableJCustomerYieldsOneMessageEntryWithAnEmptyValue() throws Exception {
        // Verifies that the editor shows a message instead of an empty or broken dropdown, and that
        // picking it stores nothing.
        ProfilePropertyCatalog catalog = mock(ProfilePropertyCatalog.class);
        when(catalog.profileProperties("site")).thenThrow(new ProfilePropertiesUnavailableException("down"));
        ProfilePropertiesChoiceListInitializer initializer = new ProfilePropertiesChoiceListInitializer(catalog) {
            @Override
            String unavailableMessage(Locale locale) {
                return "unreachable";
            }
        };
        List<ChoiceListValue> choices = initializer.choices(new FieldShape(Set.of("string"), false), "site", Locale.ENGLISH);
        assertEquals(1, choices.size());
        assertEquals("unreachable", choices.get(0).getDisplayName());
        assertEquals("", choices.get(0).getValue().getString());
    }
}
