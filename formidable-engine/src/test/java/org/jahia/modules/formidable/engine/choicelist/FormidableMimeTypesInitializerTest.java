package org.jahia.modules.formidable.engine.choicelist;

import org.jahia.modules.formidable.engine.config.uploads.UploadsConfigService;
import org.jahia.modules.formidable.engine.files.FileTypeService;
import org.jahia.services.content.nodetypes.initializers.ChoiceListValue;
import org.junit.jupiter.api.Test;

import javax.jcr.RepositoryException;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.ListResourceBundle;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FormidableMimeTypesInitializerTest {

    private static final String KEY = "fmdb_inputFile.accept";

    private static ResourceBundle bundle() {
        return new ListResourceBundle() {
            @Override
            protected Object[][] getContents() {
                return new Object[][] {{KEY + ".application/pdf", "Document PDF"}};
            }
        };
    }

    private static String valueOf(ChoiceListValue value) {
        try {
            return value.getValue().getString();
        } catch (RepositoryException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void theBundlesWordingWinsWhereItHasTheKeyElseTheFallback() {
        // Verifies the lookup Jahia's own resourceBundle initializer broke once, keying on the label instead of the
        // type: the bundle's wording under <key>.<mime>, else Tika's label — also when there is no bundle at all.
        assertEquals("Document PDF", FormidableMimeTypesInitializer.label(bundle(), KEY + ".application/pdf", "PDF (.pdf)"));
        assertEquals("ZIP (.zip)", FormidableMimeTypesInitializer.label(bundle(), KEY + ".application/zip", "ZIP (.zip)"));
        assertEquals("PDF (.pdf)", FormidableMimeTypesInitializer.label(null, KEY + ".application/pdf", "PDF (.pdf)"));
    }

    @Test
    void everyAllowedTypeIsOfferedSortedAndLabelledByTikaWithoutAModule() {
        // Verifies the values the editor gets: every allowed type as its MIME type, sorted, and a property declared
        // by no module (no bundle to read) still labelled — by Tika.
        UploadsConfigService config = mock(UploadsConfigService.class);
        when(config.getUploadAllowedTypes()).thenReturn(new LinkedHashSet<>(List.of("image/png", "application/pdf")));
        FileTypeService fileTypes = new FileTypeService();
        fileTypes.setConfigService(config);
        FormidableMimeTypesInitializer initializer = new FormidableMimeTypesInitializer();
        initializer.setConfigService(config);
        initializer.setFileTypes(fileTypes);

        // No definition — Jahia's definition classes cannot be mocked here — reads as no module to take a bundle from.
        List<ChoiceListValue> values = initializer.getChoiceListValues(null, null, List.of(), Locale.ENGLISH, Map.of());

        assertEquals(List.of("application/pdf", "image/png"), values.stream().map(FormidableMimeTypesInitializerTest::valueOf).toList());
        assertEquals(List.of("PDF (.pdf)", "PNG (.png)"), values.stream().map(ChoiceListValue::getDisplayName).toList());
    }
}
