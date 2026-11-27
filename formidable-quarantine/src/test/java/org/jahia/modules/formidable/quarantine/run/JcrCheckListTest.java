package org.jahia.modules.formidable.quarantine.run;

import org.jahia.modules.formidable.engine.api.FmdbMixin;
import org.jahia.services.content.JCRNodeIteratorWrapper;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRPropertyWrapper;
import org.jahia.services.content.JCRValueWrapper;
import org.jahia.modules.formidable.quarantine.api.CheckSettings;
import org.junit.jupiter.api.Test;

import javax.jcr.RepositoryException;
import javax.jcr.nodetype.PropertyDefinition;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** How the run reads the "Checks" list of a form. */
class JcrCheckListTest {

    private static JCRNodeWrapper child(String type, String name, boolean qualifier) throws RepositoryException {
        JCRNodeWrapper child = mock(JCRNodeWrapper.class);
        when(child.getPrimaryNodeTypeName()).thenReturn(type);
        when(child.getName()).thenReturn(name);
        when(child.isNodeType(FmdbMixin.QUALIFIER)).thenReturn(qualifier);
        return child;
    }

    /** The definitions of a type that declares each property given. */
    private static PropertyDefinition[] declaring(String... names) {
        List<PropertyDefinition> definitions = new ArrayList<>();
        for (String name : names) {
            PropertyDefinition definition = mock(PropertyDefinition.class);
            when(definition.getName()).thenReturn(name);
            definitions.add(definition);
        }
        return definitions.toArray(PropertyDefinition[]::new);
    }

    /** A check node that holds each property given: a value, or several. */
    private static JCRNodeWrapper checkHolding(Map<String, List<String>> held) throws RepositoryException {
        JCRNodeWrapper check = child("acme:typedCheck", "typed", true);
        for (Map.Entry<String, List<String>> entry : held.entrySet()) {
            JCRPropertyWrapper property = property(entry.getValue());
            when(check.hasProperty(entry.getKey())).thenReturn(true);
            when(check.getProperty(entry.getKey())).thenReturn(property);
        }
        return check;
    }

    private static List<CheckSettings> settingsOf(JCRNodeWrapper form) throws RepositoryException {
        return JcrCheckList.settingsOf(form, check -> declaring());
    }

    /** A single property for one value, a multiple one for any other count. */
    private static JCRPropertyWrapper property(List<String> values) throws RepositoryException {
        JCRPropertyWrapper property = mock(JCRPropertyWrapper.class);
        boolean multiple = values.size() != 1;
        when(property.isMultiple()).thenReturn(multiple);
        if (!multiple) {
            when(property.getString()).thenReturn(values.get(0));
            return property;
        }
        List<JCRValueWrapper> jcrValues = new ArrayList<>();
        for (String value : values) {
            JCRValueWrapper jcrValue = mock(JCRValueWrapper.class);
            when(jcrValue.getString()).thenReturn(value);
            jcrValues.add(jcrValue);
        }
        when(property.getValues()).thenReturn(jcrValues.toArray(JCRValueWrapper[]::new));
        return property;
    }

    private static JCRNodeWrapper formWithChildren(List<JCRNodeWrapper> children) throws RepositoryException {
        JCRNodeIteratorWrapper iterator = mock(JCRNodeIteratorWrapper.class);
        when(iterator.iterator()).thenReturn(children.iterator());
        JCRNodeWrapper list = mock(JCRNodeWrapper.class);
        when(list.getNodes()).thenReturn(iterator);
        JCRNodeWrapper form = mock(JCRNodeWrapper.class);
        when(form.hasNode(JcrCheckList.QUALIFIERS)).thenReturn(true);
        when(form.getNode(JcrCheckList.QUALIFIERS)).thenReturn(list);
        return form;
    }

    @Test
    void aFormWithNoListHasNoCheck() throws RepositoryException {
        assertEquals(List.of(), settingsOf(mock(JCRNodeWrapper.class)));
    }

    @Test
    void theTranslationChildOfTheListIsNeverACheck() throws RepositoryException {
        JCRNodeWrapper form = formWithChildren(List.of(
                child("jnt:translation", "j:translation_en", false),
                child("fmdbquar:remoteStubCheck", "scan", true)));

        assertEquals(List.of(CheckSettings.withDefaults("fmdbquar:remoteStubCheck", "scan")),
                settingsOf(form));
    }

    @Test
    void aCheckOfAnotherModuleStillCounts() throws RepositoryException {
        JCRNodeWrapper form = formWithChildren(List.of(
                child("acme:sandboxCheck", "sandbox", true),
                child("fmdbquar:inProcessStubCheck", "marker", true)));

        assertEquals(List.of(CheckSettings.withDefaults("acme:sandboxCheck", "sandbox"),
                        CheckSettings.withDefaults("fmdbquar:inProcessStubCheck", "marker")),
                settingsOf(form));
    }

    @Test
    void theOwnPropertiesOfTheTypeAreHandedAsStrings() throws RepositoryException {
        Map<String, List<String>> held = new LinkedHashMap<>();
        held.put("extensions", List.of("pdf", "png"));
        held.put("pattern", List.of("a,b", "c"));
        held.put("mimeTypes", List.of("application/pdf"));
        held.put("onInconclusive", List.of("reject"));
        held.put("jcr:title", List.of("Allowed types"));
        held.put("j:originWS", List.of("default"));
        held.put("mix:something", List.of("x"));
        PropertyDefinition[] definitions = declaring("jcr:title", "j:originWS", "mix:something", "*",
                "onInconclusive", "onUnavailable", "extensions", "mimeTypes", "pattern", "threshold");

        Map<String, List<String>> expected = new LinkedHashMap<>();
        expected.put("extensions", List.of("pdf", "png"));
        expected.put("mimeTypes", List.of("application/pdf"));
        // a value that holds a comma stays one value
        expected.put("pattern", List.of("a,b", "c"));
        assertEquals(expected, JcrCheckList.propertiesOf(checkHolding(held), definitions));
    }

    @Test
    void aMultiplePropertyWithNoValueIsAnEmptyList() throws RepositoryException {
        JCRNodeWrapper check = checkHolding(Map.of("extensions", List.of()));

        assertEquals(Map.of("extensions", List.of()), JcrCheckList.propertiesOf(check, declaring("extensions")));
    }

    @Test
    void theListHandsEachCheckItsProperties() throws RepositoryException {
        JCRNodeWrapper check = checkHolding(Map.of("extensions", List.of("pdf")));
        JCRNodeWrapper form = formWithChildren(List.of(check));

        List<CheckSettings> settings = JcrCheckList.settingsOf(form, node -> declaring("extensions"));

        assertEquals(1, settings.size());
        assertEquals(Map.of("extensions", List.of("pdf")), settings.get(0).properties());
    }

    @Test
    void aSettingOfThePlatformIsNeverAPropertyOfTheCheck() {
        assertFalse(JcrCheckList.isOwnProperty("jcr:title"));
        assertFalse(JcrCheckList.isOwnProperty("j:nodename"));
        assertFalse(JcrCheckList.isOwnProperty("mix:anything"));
        assertFalse(JcrCheckList.isOwnProperty("onUnavailable"));
        assertFalse(JcrCheckList.isOwnProperty("*"));
        assertTrue(JcrCheckList.isOwnProperty("mimeTypes"));
    }
}
