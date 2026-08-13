package org.jahia.modules.formidable.engine.choicelist;

import org.jahia.modules.formidable.engine.options.FormidableOptionsSourceService;
import org.jahia.modules.formidable.engine.options.OptionsQueryCapExceededException;
import org.jahia.services.content.nodetypes.initializers.ChoiceListValue;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FormidableOptionsPreviewInitializerTest {

    private static FormidableOptionsPreviewInitializer initializerWith(FormidableOptionsSourceService service) {
        FormidableOptionsPreviewInitializer initializer = new FormidableOptionsPreviewInitializer();
        initializer.setOptionsSourceService(service);
        return initializer;
    }

    @Test
    void contentContextResolvesThePreviewForTheRequestedWorkspace() throws Exception {
        // Verifies the content-mode preview contract: rootNode + nodeType + workspace
        // context entries (GraphQL hands them over as List<String>) resolve through the
        // service and come back in the manual-options shape.
        FormidableOptionsSourceService service = mock(FormidableOptionsSourceService.class);
        when(service.resolveContentPreview("root-uuid", "acme:agency", "live", "en"))
                .thenReturn(new String[]{"{\"value\":\"lyon\",\"label\":\"Lyon\",\"selected\":false}"});

        List<ChoiceListValue> preview = initializerWith(service).getChoiceListValues(null, null, List.of(),
                Locale.ENGLISH, Map.of(
                        "rootNode", List.of("root-uuid"),
                        "nodeType", List.of("acme:agency"),
                        "workspace", List.of("live")));

        assertEquals(1, preview.size());
        assertEquals("lyon", preview.get(0).getValue().getString());
        assertEquals("Lyon", preview.get(0).getDisplayName());
        verify(service).resolveContentPreview("root-uuid", "acme:agency", "live", "en");
    }

    @Test
    void capExceededSurfacesAsATypedMarkerInsteadOfAnError() throws Exception {
        // Verifies the cap contract: the contributor-actionable limit travels as a
        // marker entry (value = marker, label = limit), not as an opaque GraphQL error.
        FormidableOptionsSourceService service = mock(FormidableOptionsSourceService.class);
        when(service.resolveContentPreview("root-uuid", "acme:agency", "default", "en"))
                .thenThrow(new OptionsQueryCapExceededException("preview:root-uuid", 100));

        List<ChoiceListValue> preview = initializerWith(service).getChoiceListValues(null, null, List.of(),
                Locale.ENGLISH, Map.of(
                        "rootNode", List.of("root-uuid"),
                        "nodeType", List.of("acme:agency")));

        assertEquals(1, preview.size());
        assertEquals(FormidableOptionsPreviewInitializer.CAP_EXCEEDED_MARKER,
                preview.get(0).getValue().getString());
        assertEquals("100", preview.get(0).getDisplayName());
    }

    @Test
    void sourceKeyContextStillResolvesDeclaredSources() throws Exception {
        FormidableOptionsSourceService service = mock(FormidableOptionsSourceService.class);
        when(service.resolve("countries", "en"))
                .thenReturn(new String[]{"{\"value\":\"FR\",\"label\":\"France\",\"selected\":false}"});

        List<ChoiceListValue> preview = initializerWith(service).getChoiceListValues(null, null, List.of(),
                Locale.ENGLISH, Map.of("sourceKey", List.of("countries")));

        assertEquals(1, preview.size());
        assertEquals("FR", preview.get(0).getValue().getString());
    }

    @Test
    void withoutContextTheIncomingValuesPassThrough() {
        FormidableOptionsSourceService service = mock(FormidableOptionsSourceService.class);
        List<ChoiceListValue> incoming = List.of(new ChoiceListValue("Countries", "countries"));

        List<ChoiceListValue> result = initializerWith(service).getChoiceListValues(null, null, incoming,
                Locale.ENGLISH, Map.of());

        assertSame(incoming, result);
    }
}
