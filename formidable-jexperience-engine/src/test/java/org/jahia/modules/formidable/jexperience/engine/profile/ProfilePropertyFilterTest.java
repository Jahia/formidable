package org.jahia.modules.formidable.jexperience.engine.profile;

import org.apache.unomi.api.Metadata;
import org.apache.unomi.api.PropertyType;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProfilePropertyFilterTest {

    private static PropertyType type(String id, String name, String valueType, Set<String> systemTags) {
        Metadata metadata = new Metadata(id);
        metadata.setName(name);
        metadata.setSystemTags(systemTags);
        PropertyType type = new PropertyType(metadata);
        type.setValueTypeId(valueType);
        return type;
    }

    @Test
    void describesAVisibleProperty() {
        // Verifies the descriptor of a plain profile property: id, "label (id)", lower-case type, single-valued.
        Optional<ProfilePropertyDescriptor> descriptor = ProfilePropertyFilter.describe(type("firstName", "First name", "String", Set.of("profileProperties")));
        assertEquals(Optional.of(new ProfilePropertyDescriptor("firstName", "First name (firstName)", "string", false)), descriptor);
    }

    @Test
    void labelIsTheIdWhenTheyAreEqual() {
        // Verifies that an unnamed property is not shown as "id (id)".
        PropertyType type = type("nickname", null, "string", Set.of());
        assertEquals("nickname", ProfilePropertyFilter.describe(type).orElseThrow().label());
    }

    @Test
    void multivaluedFollowsTheType() {
        // Verifies that jCustomer's multivalued flag reaches the descriptor.
        PropertyType type = type("interests", "Interests", "string", Set.of());
        type.setMultivalued(true);
        assertTrue(ProfilePropertyFilter.describe(type).orElseThrow().multivalued());
    }

    @Test
    void excludesHiddenReadOnlyAndProtectedProperties() {
        // Verifies the three metadata flags an author must never see in the dropdown.
        PropertyType hidden = type("h", "Hidden", "string", Set.of());
        hidden.getMetadata().setHidden(true);
        PropertyType readOnly = type("r", "Read only", "string", Set.of());
        readOnly.getMetadata().setReadOnly(true);
        PropertyType protekted = type("p", "Protected", "string", Set.of());
        protekted.setProtected(true);
        assertTrue(ProfilePropertyFilter.describe(hidden).isEmpty());
        assertTrue(ProfilePropertyFilter.describe(readOnly).isEmpty());
        assertTrue(ProfilePropertyFilter.describe(protekted).isEmpty());
    }

    @Test
    void excludesSystemAndFormMappingHiddenTags() {
        // Verifies the two system tags jCustomer and jExperience use to keep properties out of mappings.
        assertTrue(ProfilePropertyFilter.describe(type("s", "System", "string", Set.of("profileProperties", "systemProfileProperties"))).isEmpty());
        assertTrue(ProfilePropertyFilter.describe(type("m", "No mapping", "string", Set.of("hiddenFromFormMappingProperties"))).isEmpty());
    }

    @Test
    void excludesPropertiesWithoutIdOrType() {
        // Verifies that a descriptor needs both a name and a value type.
        assertTrue(ProfilePropertyFilter.describe(type("", "", "string", Set.of())).isEmpty());
        assertTrue(ProfilePropertyFilter.describe(type("x", "X", " ", Set.of())).isEmpty());
        assertTrue(ProfilePropertyFilter.describe(null).isEmpty());
    }

    @Test
    void aRuleMayReadWhatNoFormWrites() {
        // Verifies that the read-side filter keeps the read-only, protected and system properties a mapping
        // refuses: a visit count or a score is what a rule tests and no field could hold.
        PropertyType readOnly = type("nbOfVisits", "Visits", "integer", Set.of("systemProfileProperties"));
        readOnly.getMetadata().setReadOnly(true);
        PropertyType protekted = type("score", "Score", "integer", Set.of());
        protekted.setProtected(true);
        assertEquals("Visits (nbOfVisits)", ProfilePropertyFilter.describeReadable(readOnly).orElseThrow().label());
        assertTrue(ProfilePropertyFilter.describeReadable(protekted).isPresent());
        assertTrue(ProfilePropertyFilter.describeReadable(type("m", "No mapping", "string", Set.of("hiddenFromFormMappingProperties"))).isPresent());
    }

    @Test
    void aRuleMayNotReadAHiddenPropertyAListOrASet() {
        // Verifies what the read-side filter leaves out: what jCustomer hides, and what the page reads as absent.
        PropertyType hidden = type("h", "Hidden", "string", Set.of());
        hidden.getMetadata().setHidden(true);
        PropertyType list = type("interests", "Interests", "string", Set.of());
        list.setMultivalued(true);
        assertTrue(ProfilePropertyFilter.describeReadable(hidden).isEmpty());
        assertTrue(ProfilePropertyFilter.describeReadable(list).isEmpty());
        assertTrue(ProfilePropertyFilter.describeReadable(type("address", "Address", "set", Set.of())).isEmpty());
        assertTrue(ProfilePropertyFilter.describeReadable(type("", "", "string", Set.of())).isEmpty());
        assertTrue(ProfilePropertyFilter.describeReadable(null).isEmpty());
    }
}
