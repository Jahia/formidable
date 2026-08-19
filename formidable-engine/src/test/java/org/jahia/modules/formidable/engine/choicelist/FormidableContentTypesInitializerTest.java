package org.jahia.modules.formidable.engine.choicelist;

import org.jahia.modules.formidable.engine.options.FormidableOptionsSourceService;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRPropertyWrapper;
import org.jahia.services.content.nodetypes.initializers.ChoiceListValue;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class FormidableContentTypesInitializerTest {

    private static FormidableContentTypesInitializer initializerWith(FormidableOptionsSourceService service) {
        FormidableContentTypesInitializer initializer = new FormidableContentTypesInitializer();
        initializer.setOptionsSourceService(service);
        return initializer;
    }

    @Test
    void rootContextEntryResolvesTheTypesUnderIt() throws Exception {
        // Verifies the dependent-properties contract: the editor passes the new —
        // possibly unsaved — root as a context entry (GraphQL hands it over as
        // List<String>) and the offerable types come back in the manual-options shape.
        FormidableOptionsSourceService service = mock(FormidableOptionsSourceService.class);
        when(service.resolveContentTypes("root-uuid", "en"))
                .thenReturn(new String[]{"{\"value\":\"acme:agency\",\"label\":\"Agency\",\"selected\":false}"});

        List<ChoiceListValue> types = initializerWith(service).getChoiceListValues(null, null, List.of(),
                Locale.ENGLISH, Map.of(FormidableContentTypesInitializer.ROOT_PROPERTY, List.of("root-uuid")));

        assertEquals(1, types.size());
        assertEquals("acme:agency", types.get(0).getValue().getString());
        assertEquals("Agency", types.get(0).getDisplayName());
    }

    @Test
    void formBuildFallsBackToTheStoredRootOfTheEditedField() throws Exception {
        // Verifies the build-time contract: without a context entry the root comes
        // from the stored property of the node being edited (contextNode).
        FormidableOptionsSourceService service = mock(FormidableOptionsSourceService.class);
        when(service.resolveContentTypes("stored-uuid", "en")).thenReturn(new String[0]);
        JCRNodeWrapper fieldNode = mock(JCRNodeWrapper.class);
        when(fieldNode.hasProperty(FormidableContentTypesInitializer.ROOT_PROPERTY)).thenReturn(true);
        JCRPropertyWrapper property = mock(JCRPropertyWrapper.class);
        when(fieldNode.getProperty(FormidableContentTypesInitializer.ROOT_PROPERTY)).thenReturn(property);
        when(property.getString()).thenReturn("stored-uuid");

        initializerWith(service).getChoiceListValues(null, null, List.of(),
                Locale.ENGLISH, Map.of("contextNode", fieldNode));

        verify(service).resolveContentTypes("stored-uuid", "en");
    }

    @Test
    void clearedRootContextEntryEmptiesTheListInsteadOfResurrectingTheStoredRoot() throws Exception {
        // Verifies the cleared-picker contract: a present-but-empty root entry means
        // the contributor just cleared the root — the type list must empty rather
        // than fall back to the stored root of the edited node.
        FormidableOptionsSourceService service = mock(FormidableOptionsSourceService.class);
        JCRNodeWrapper fieldNode = mock(JCRNodeWrapper.class);
        when(fieldNode.hasProperty(FormidableContentTypesInitializer.ROOT_PROPERTY)).thenReturn(true);
        JCRPropertyWrapper property = mock(JCRPropertyWrapper.class);
        when(fieldNode.getProperty(FormidableContentTypesInitializer.ROOT_PROPERTY)).thenReturn(property);
        when(property.getString()).thenReturn("stored-uuid");

        List<ChoiceListValue> types = initializerWith(service).getChoiceListValues(null, null, List.of(),
                Locale.ENGLISH, Map.of(
                        FormidableContentTypesInitializer.ROOT_PROPERTY, List.of(),
                        "contextNode", fieldNode));

        assertTrue(types.isEmpty());
        verifyNoInteractions(service);
    }

    @Test
    void withoutAnyRootTheListIsEmpty() {
        // Verifies the bare-create contract: no context entry and no stored root
        // (new field) means nothing to offer yet, without touching the service.
        FormidableOptionsSourceService service = mock(FormidableOptionsSourceService.class);

        List<ChoiceListValue> types = initializerWith(service).getChoiceListValues(null, null, List.of(),
                Locale.ENGLISH, Map.of());

        assertTrue(types.isEmpty());
    }

    @Test
    void explicitPreviewCallsPassThroughUntouched() {
        // Verifies the chain contract: on preview calls (rootNode + nodeType context)
        // the preview initializer chained after this one replaces the result, so this
        // one steps aside without scanning.
        FormidableOptionsSourceService service = mock(FormidableOptionsSourceService.class);
        List<ChoiceListValue> incoming = List.of();

        List<ChoiceListValue> result = initializerWith(service).getChoiceListValues(null, null, incoming,
                Locale.ENGLISH, Map.of(
                        "rootNode", List.of("root-uuid"),
                        "nodeType", List.of("acme:agency")));

        assertSame(incoming, result);
    }

    @Test
    void unreadableRootSurfacesAsAnExplicitError() throws Exception {
        // Verifies the failure contract: a broken lookup propagates as an explicit
        // error (surfaced by the GraphQL call), never as an empty list.
        FormidableOptionsSourceService service = mock(FormidableOptionsSourceService.class);
        when(service.resolveContentTypes("root-uuid", "en"))
                .thenThrow(new javax.jcr.RepositoryException("gone"));

        assertThrows(IllegalStateException.class,
                () -> initializerWith(service).getChoiceListValues(null, null, List.of(),
                        Locale.ENGLISH, Map.of(FormidableContentTypesInitializer.ROOT_PROPERTY,
                                List.of("root-uuid"))));
    }
}
