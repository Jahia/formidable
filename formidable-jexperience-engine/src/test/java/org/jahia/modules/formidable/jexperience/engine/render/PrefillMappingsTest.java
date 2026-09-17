package org.jahia.modules.formidable.jexperience.engine.render;

import org.jahia.modules.formidable.jexperience.engine.model.JxpMixin;
import org.jahia.modules.formidable.jexperience.engine.model.JxpProperty;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRPropertyWrapper;
import org.jahia.services.content.JCRSessionWrapper;
import org.junit.jupiter.api.Test;

import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Which fields the block tells the page to prefill, what the block depends on, and how the pairs are
 * written. Reading is a query of the mappable fields under the form, replaced here by a fixed list; the
 * rule of inclusion is the point: mapped, the prefill mixin on, not sensitive — and every mappable
 * field, included or not, is a dependency.
 */
class PrefillMappingsTest {

    private static JCRNodeWrapper field(String name, String property, boolean mapped, boolean prefill, boolean overrides, boolean sensitive) throws RepositoryException {
        JCRNodeWrapper node = mock(JCRNodeWrapper.class);
        when(node.getName()).thenReturn(name);
        when(node.getPath()).thenReturn("/sites/mysite/contents/contact/fields/" + name);
        when(node.isNodeType(JxpMixin.MAPPING)).thenReturn(mapped);
        when(node.isNodeType(JxpMixin.PREFILL)).thenReturn(prefill);
        when(node.getPropertyAsString(JxpProperty.PROFILE_PROPERTY)).thenReturn(mapped ? property : null);
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
            NodeIterator mappableFields(JCRSessionWrapper session, JCRNodeWrapper form) {
                return nodes;
            }
        };
    }

    @Test
    void onlyAMappedFieldWithThePrefillMixinAndNotSensitiveIsListed() throws Exception {
        PrefillMappings.Prefill prefill = over(List.of(
                field("firstName", "firstName", true, true, false, false),
                field("email", "email", true, true, true, false),
                field("phoneNumber", "phoneNumber", true, false, false, false), // mapped, prefill off
                field("secret", "nationality", true, true, true, true),         // prefill on, but sensitive
                field("switchedOn", "", true, true, false, false),              // mapping switched on, property left empty
                field("message", null, false, false, false, false)              // mappable, never mapped
        )).read(mock(JCRSessionWrapper.class), mock(JCRNodeWrapper.class));

        assertEquals(Map.of(
                "firstName", new PrefillMappings.Entry("firstName", false),
                "email", new PrefillMappings.Entry("email", true)), prefill.entries());
        // in the form's order, which is the query's
        assertEquals(List.of("firstName", "email"), List.copyOf(prefill.entries().keySet()));
    }

    @Test
    void everyMappableFieldIsADependencyWhateverItMaps() throws Exception {
        // Verifies what the block's cache entry must be flushed for: mapping a field later, switching its
        // prefill on, ticking its override — all changes to a field the block did not mention yet.
        PrefillMappings.Prefill prefill = over(List.of(
                field("firstName", "firstName", true, true, false, false),
                field("message", null, false, false, false, false)
        )).read(mock(JCRSessionWrapper.class), mock(JCRNodeWrapper.class));

        assertEquals(List.of("/sites/mysite/contents/contact/fields/firstName", "/sites/mysite/contents/contact/fields/message"), prefill.dependencies());
    }

    @Test
    void theBlockCarriesNamesAndTheOverrideFlagAndNothingElse() {
        Map<String, PrefillMappings.Entry> entries = Map.of("first\"Name", new PrefillMappings.Entry("firstName", true));
        assertEquals("{\"first\\\"Name\":{\"property\":\"firstName\",\"overridesDefault\":true}}", PrefillMappings.json(entries));
        assertEquals("{}", PrefillMappings.json(Map.of()));
    }
}
