package org.jahia.modules.formidable.engine.imports.jahiaforms;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FormsLabelsTest {

    private static FormsField field(Map<String, String> titles, Map<String, String> placeholder) {
        FormsOption option = new FormsOption(FormsLabels.PLACEHOLDER, null, placeholder);
        return new FormsField("text-input_0_1", "uuid", "fcnt:inputDefinition", titles, null,
                Map.of(FormsLabels.PLACEHOLDER, option), List.of(), false, false);
    }

    @Test
    void theTitleWinsThenTheLabelNodeThenThePlaceholderThenTheName() {
        FormsLabel label = new FormsLabel("text-input_0_1", "uuid", Map.of("en", "From the label", "fr", ""), Map.of());

        assertEquals(Map.of("en", "Title", "fr", "Titre"),
                FormsLabels.titles(field(Map.of("en", "Title", "fr", "Titre"), Map.of("en", "P", "fr", "P")), label, "text-input_0_1"));
        assertEquals(Map.of("en", "From the label", "fr", "Votre prénom"),
                FormsLabels.titles(field(Map.of("en", "", "fr", ""), Map.of("en", "Your First name*", "fr", "Votre prénom*")), label, "text-input_0_1"));
        assertEquals(Map.of("en", "Your First name", "fr", "Votre courriel"),
                FormsLabels.titles(field(Map.of("en", "", "fr", ""), Map.of("en", "Your First name*", "fr", "Votre courriel *")), null, "text-input_0_1"));
        assertEquals(Map.of("en", "text-input_0_1"),
                FormsLabels.titles(field(Map.of("en", ""), Map.of("en", "")), null, "text-input_0_1"));
    }

    @Test
    void aDeletedFormHasOnlyItsLabelNodes() {
        FormsLabel label = new FormsLabel("email-input_0_2", "uuid", Map.of("en", "Email", "fr", ""), Map.of());
        assertEquals(Map.of("en", "Email", "fr", "email-input_0_2"), FormsLabels.titles(null, label, "email-input_0_2"));
    }

    @Test
    void theRequiredMarkOfAPlaceholderIsStripped() {
        assertEquals("Your First name", FormsLabels.stripRequiredMark("Your First name*"));
        assertEquals("Votre courriel", FormsLabels.stripRequiredMark("Votre courriel *"));
        assertEquals("Plain", FormsLabels.stripRequiredMark("Plain"));
        assertEquals("", FormsLabels.stripRequiredMark("**"));
    }
}
