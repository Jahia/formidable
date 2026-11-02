package org.jahia.modules.formidable.jexperience.engine.render;

import org.jahia.modules.formidable.engine.api.FmdbProperty;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRPropertyWrapper;
import org.jahia.services.content.JCRSessionWrapper;
import org.jahia.services.content.JCRValueWrapper;
import org.junit.jupiter.api.Test;

import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Which profile properties the block tells the page to ask the tracker for, because the form's
 * conditional logic reads them, and what the block depends on. Reading is a query of the elements
 * that can carry rules under the form, replaced here by a fixed list.
 */
class LogicPropertiesTest {

    private static final String COUNTRY_IS_FR = "{\"sourceType\":\"jexperience:profile\",\"property\":\"country\",\"operator\":\"equals\",\"value\":\"FR\"}";

    /** An element under the form, with the rules it stores; none at all is an element that never had one. */
    private static JCRNodeWrapper element(String name, String... rules) throws RepositoryException {
        JCRNodeWrapper node = mock(JCRNodeWrapper.class);
        when(node.getPath()).thenReturn("/sites/mysite/contents/contact/fields/" + name);
        when(node.hasProperty(FmdbProperty.LOGICS)).thenReturn(rules.length > 0);
        if (rules.length > 0) {
            JCRValueWrapper[] values = new JCRValueWrapper[rules.length];
            for (int i = 0; i < rules.length; i++) {
                values[i] = mock(JCRValueWrapper.class);
                when(values[i].getString()).thenReturn(rules[i]);
            }
            JCRPropertyWrapper property = mock(JCRPropertyWrapper.class);
            when(property.getValues()).thenReturn(values);
            when(node.getProperty(FmdbProperty.LOGICS)).thenReturn(property);
        }
        return node;
    }

    private static LogicProperties.Logic read(JCRNodeWrapper... elements) throws RepositoryException {
        Iterator<JCRNodeWrapper> iterator = Arrays.asList(elements).iterator();
        NodeIterator nodes = mock(NodeIterator.class);
        when(nodes.hasNext()).thenAnswer(call -> iterator.hasNext());
        when(nodes.nextNode()).thenAnswer(call -> iterator.next());
        return new LogicProperties() {
            @Override
            NodeIterator logicElements(JCRSessionWrapper session, JCRNodeWrapper form) {
                return nodes;
            }
        }.read(mock(JCRSessionWrapper.class), mock(JCRNodeWrapper.class));
    }

    @Test
    void onlyTheRulesOnTheProfileAreListedEachOnce() throws Exception {
        LogicProperties.Logic logic = read(
                element("companySize", COUNTRY_IS_FR,
                        "{\"sourceType\":\"cookie\",\"cookie\":\"country\",\"operator\":\"exists\"}"),
                element("vatNumber", COUNTRY_IS_FR, "{\"sourceType\":\"jexperience:profile\",\"property\":\" company \",\"operator\":\"exists\"}"),
                element("message"));

        // trimmed as the browser trims the reference, and in the form's order
        assertEquals(List.of("country", "company"), logic.properties());
    }

    @Test
    void everyElementIsADependencyWhateverItHolds() throws Exception {
        // adding the first rule on the profile changes an element that had none: the cached block must go
        LogicProperties.Logic logic = read(element("companySize", COUNTRY_IS_FR), element("message"));

        assertEquals(List.of("/sites/mysite/contents/contact/fields/companySize", "/sites/mysite/contents/contact/fields/message"),
                logic.dependencies());
    }

    @Test
    void aRuleThatNamesNoPropertyOrIsNotJsonIsSkipped() {
        assertNull(LogicProperties.propertyOf("{\"sourceType\":\"jexperience:profile\",\"property\":\"  \",\"operator\":\"exists\"}", "/a"));
        assertNull(LogicProperties.propertyOf("{\"sourceType\":\"jexperience:profile\",\"operator\":\"exists\"}", "/a"));
        assertNull(LogicProperties.propertyOf("not json", "/a"));
    }

    @Test
    void theListIsWrittenAsAJsonArraySafeInAScriptBlock() {
        assertEquals("[]", LogicProperties.json(List.of()));
        assertEquals("[\"country\",\"\\u003c/script\\u003e\"]", LogicProperties.json(List.of("country", "</script>")));
    }
}
