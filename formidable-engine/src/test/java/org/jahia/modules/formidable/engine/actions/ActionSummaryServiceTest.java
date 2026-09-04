package org.jahia.modules.formidable.engine.actions;

import org.jahia.services.content.nodetypes.SelectorType;
import org.junit.jupiter.api.Test;

import javax.jcr.PropertyType;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ActionSummaryServiceTest {

    private static boolean telling(String name, int requiredType, int selector) {
        return ActionSummaryService.isTelling(name, false, false, false, requiredType, selector);
    }

    @Test
    void aSmallTextOrAChoiceIsTelling() {
        // The e-mail actions' recipient and the forward action's target are what the zone shows.
        assertTrue(telling("to", PropertyType.STRING, SelectorType.SMALLTEXT));
        assertTrue(telling("targetId", PropertyType.STRING, SelectorType.CHOICELIST));
    }

    @Test
    void theTitleAndTechnicalPropertiesAreNot() {
        // jcr:title is declared first by every action type (mix:title) and must be skipped.
        assertFalse(telling("jcr:title", PropertyType.STRING, SelectorType.SMALLTEXT));
        assertFalse(telling("j:view", PropertyType.STRING, SelectorType.SMALLTEXT));
    }

    @Test
    void whatCannotBeReadAtAGlanceIsNot() {
        // Flags, long texts, numbers and lists say nothing at a glance; hidden or protected
        // properties are not the contributor's.
        assertFalse(telling("attachFiles", PropertyType.BOOLEAN, SelectorType.CHECKBOX));
        assertFalse(telling("templateMessage", PropertyType.STRING, SelectorType.TEXTAREA));
        assertFalse(telling("body", PropertyType.STRING, SelectorType.RICHTEXT));
        assertFalse(telling("maxAttachmentSizeMb", PropertyType.LONG, SelectorType.SMALLTEXT));
        assertFalse(ActionSummaryService.isTelling("tags", false, false, true, PropertyType.STRING, SelectorType.SMALLTEXT));
        assertFalse(ActionSummaryService.isTelling("secret", true, false, false, PropertyType.STRING, SelectorType.SMALLTEXT));
        assertFalse(ActionSummaryService.isTelling("fieldKey", false, true, false, PropertyType.STRING, SelectorType.SMALLTEXT));
    }
}
