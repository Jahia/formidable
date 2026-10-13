package org.jahia.modules.formidable.jexperience.engine.profile;

import org.jahia.modules.formidable.engine.api.VisitorProfileSchema.Fit;
import org.junit.jupiter.api.Test;

import javax.jcr.nodetype.NoSuchNodeTypeException;
import javax.jcr.nodetype.NodeType;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CatalogProfileSchemaTest {

    private static final String SITE = "acme";
    private static final String TEXT = "fmdb:inputText";
    private static final String EMAIL = "fmdb:inputEmail";
    private static final String FIRST_NAME = "firstName";

    private static NodeType type(String... mixins) {
        NodeType type = mock(NodeType.class);
        for (String mixin : mixins) {
            when(type.isNodeType(mixin)).thenReturn(true);
        }
        return type;
    }

    private static CatalogProfileSchema schemaOf(ProfilePropertyCatalog catalog) {
        NodeType text = type("fmdbmix:profileMappableField", "fmdbmix:textField");
        NodeType email = type("fmdbmix:profileMappableField", "fmdbmix:textField", "fmdbmix:emailField");
        return new CatalogProfileSchema(catalog, name -> switch (name) {
            case TEXT -> text;
            case EMAIL -> email;
            default -> throw new NoSuchNodeTypeException(name);
        });
    }

    @Test
    void aPropertyFitsWhenJCustomerDeclaresItWithATypeTheFieldHolds() throws Exception {
        ProfilePropertyCatalog catalog = mock(ProfilePropertyCatalog.class);
        when(catalog.profileProperties(SITE)).thenReturn(List.of(
                new ProfilePropertyDescriptor(FIRST_NAME, "First name", "string", false),
                new ProfilePropertyDescriptor("age", "Age", "integer", false),
                new ProfilePropertyDescriptor("interests", "Interests", "string", true)));
        CatalogProfileSchema schema = schemaOf(catalog);

        assertEquals(Fit.FITS, schema.fit(SITE, TEXT, false, FIRST_NAME));
        assertEquals(Fit.FITS, schema.fit(SITE, EMAIL, false, FIRST_NAME));
        // a property jCustomer does not declare is skipped by the mapping rule: the import must not write it
        assertEquals(Fit.UNKNOWN, schema.fit(SITE, TEXT, false, "sfdc__leadComments"));
        // a text field holds a string, one value
        assertEquals(Fit.INCOMPATIBLE, schema.fit(SITE, TEXT, false, "age"));
        assertEquals(Fit.INCOMPATIBLE, schema.fit(SITE, TEXT, false, "interests"));
        assertEquals(Fit.INCOMPATIBLE, schema.fit(SITE, "fmdb:unknown", false, FIRST_NAME));
    }

    @Test
    void anUnreachableProfileServerLeavesTheAnswerOpen() throws Exception {
        ProfilePropertyCatalog catalog = mock(ProfilePropertyCatalog.class);
        when(catalog.profileProperties(SITE)).thenThrow(new ProfilePropertiesUnavailableException("jCustomer down"));

        assertEquals(Fit.UNAVAILABLE, schemaOf(catalog).fit(SITE, TEXT, false, FIRST_NAME));
    }
}
