package org.jahia.modules.formidable.jexperience.engine.render;

import org.jahia.modules.formidable.jexperience.engine.model.JxpProperty;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRSessionWrapper;
import org.junit.jupiter.api.Test;

import javax.jcr.NodeIterator;
import org.jahia.services.content.JCRPropertyWrapper;
import javax.jcr.RepositoryException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Which fields the block tells the page to prefill, and how the pairs are written. Reading is the
 * rule's own query under the form, replaced here by a fixed list; the rule of inclusion is the point:
 * mapped, prefill switched on, not sensitive.
 */
class PrefillMappingsTest {

    private static JCRNodeWrapper field(String name, String property, boolean prefill, boolean overrides, boolean sensitive) throws RepositoryException {
        JCRNodeWrapper node = mock(JCRNodeWrapper.class);
        when(node.getName()).thenReturn(name);
        when(node.getPath()).thenReturn("/sites/mysite/contents/contact/fields/" + name);
        when(node.getPropertyAsString(JxpProperty.PROFILE_PROPERTY)).thenReturn(property);
        flag(node, JxpProperty.PREFILL, prefill);
        flag(node, JxpProperty.PREFILL_OVERRIDES_DEFAULT, overrides);
        flag(node, JxpProperty.SENSITIVE, sensitive);
        return node;
    }

    private static void flag(JCRNodeWrapper node, String name, boolean value) throws RepositoryException {
        JCRPropertyWrapper property = mock(JCRPropertyWrapper.class);
        when(property.getBoolean()).thenReturn(value);
        when(node.hasProperty(name)).thenReturn(true);
        when(node.getProperty(name)).thenReturn(property);
    }

    private static PrefillMappings over(List<JCRNodeWrapper> fields) {
        Iterator<JCRNodeWrapper> iterator = fields.iterator();
        NodeIterator nodes = mock(NodeIterator.class);
        when(nodes.hasNext()).thenAnswer(call -> iterator.hasNext());
        when(nodes.nextNode()).thenAnswer(call -> iterator.next());
        return new PrefillMappings() {
            @Override
            NodeIterator mappedFields(JCRSessionWrapper session, JCRNodeWrapper form) {
                return nodes;
            }
        };
    }

    @Test
    void onlyAMappedFieldWithPrefillOnAndNotSensitiveIsListed() throws Exception {
        Map<String, PrefillMappings.Entry> entries = over(List.of(
                field("firstName", "firstName", true, false, false),
                field("email", "email", true, true, false),
                field("phoneNumber", "phoneNumber", false, false, false),   // mapped, prefill off
                field("secret", "nationality", true, true, true),           // prefill on, but sensitive
                field("message", "", true, false, false)                    // switched on, property left empty
        )).read(mock(JCRSessionWrapper.class), mock(JCRNodeWrapper.class));

        assertEquals(Map.of(
                "firstName", new PrefillMappings.Entry("firstName", false),
                "email", new PrefillMappings.Entry("email", true)), entries);
        // in the form's order, which is the query's
        assertEquals(List.of("firstName", "email"), List.copyOf(entries.keySet()));
    }

    @Test
    void theBlockCarriesNamesAndTheOverrideFlagAndNothingElse() {
        Map<String, PrefillMappings.Entry> entries = Map.of("first\"Name", new PrefillMappings.Entry("firstName", true));
        assertEquals("{\"first\\\"Name\":{\"property\":\"firstName\",\"overridesDefault\":true}}", PrefillMappings.json(entries));
        assertEquals("{}", PrefillMappings.json(Map.of()));
    }
}
