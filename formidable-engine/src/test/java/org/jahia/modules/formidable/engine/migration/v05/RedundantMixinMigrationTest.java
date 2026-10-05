package org.jahia.modules.formidable.engine.migration.v05;

import org.jahia.modules.formidable.engine.api.FmdbMixin;
import org.jahia.modules.formidable.engine.migration.common.ElementsRedeployRetriggeredMigration;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRSessionWrapper;
import org.junit.jupiter.api.Test;

import javax.jcr.nodetype.NodeType;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedundantMixinMigrationTest {

    private static final String MIXIN = "fmdbmix:advancedInputTextSettings";

    /** The node's primary type as the registry knows it: including the mixin as a supertype or not yet. */
    private static NodeType typeIncludingTheMixin(boolean included) {
        NodeType type = mock(NodeType.class);
        when(type.isNodeType(MIXIN)).thenReturn(included);
        return type;
    }

    @Test
    void theRetiredMixinsBelongToTheFourFieldTypesAndTheFieldActionMarker() {
        assertEquals(5, RedundantMixinMigration.RETIRED_MIXINS.size());
        assertEquals("fmdbmix:advancedInputNumberSettings", RedundantMixinMigration.RETIRED_MIXINS.get("fmdb:inputNumber"));
        assertEquals("fmdbmix:advancedInputRangeSettings", RedundantMixinMigration.RETIRED_MIXINS.get("fmdb:inputRange"));
        assertEquals("fmdbmix:advancedInputTextSettings", RedundantMixinMigration.RETIRED_MIXINS.get("fmdb:inputText"));
        assertEquals("fmdbmix:advancedTextareaSettings", RedundantMixinMigration.RETIRED_MIXINS.get("fmdb:textarea"));
        // The feedback settings are reached through the marker every field-action type takes, whatever its module
        assertEquals(FmdbMixin.FIELD_ACTION_FEEDBACK, RedundantMixinMigration.RETIRED_MIXINS.get(FmdbMixin.FIELD_ACTION));
    }

    @Test
    void rerunsOnTheRedeployOfAnyModuleSinceAFieldActionTypeMayComeFromAnyOfThem() {
        RedundantMixinMigration migration = new RedundantMixinMigration();
        assertTrue(migration.retriggeredBy(ElementsRedeployRetriggeredMigration.ELEMENTS_MODULE_ID));
        // The samples module declares fmdbsample:blockedWordsAction; a third-party module may declare another
        assertTrue(migration.retriggeredBy("formidable-test-module-samples-java"));
    }

    @Test
    void aNodeWhoseTypeIncludesTheMixinIsReady() {
        assertTrue(RedundantMixinMigration.includedBySupertype(typeIncludingTheMixin(true), MIXIN));
    }

    @Test
    void aFieldWhoseTypeDoesNotIncludeTheMixinYetWaitsForTheElementsRedeploy() {
        // Engine upgraded first: the mixin is still the only type defining the settings, removing it would drop them.
        assertFalse(RedundantMixinMigration.includedBySupertype(typeIncludingTheMixin(false), MIXIN));
    }

    @Test
    void droppingTheMixinChecksTheNodeOutFirst() throws Exception {
        JCRSessionWrapper session = mock(JCRSessionWrapper.class);
        JCRNodeWrapper field = mock(JCRNodeWrapper.class);

        RedundantMixinMigration.dropMixin(session, field, MIXIN);

        verify(session).checkout(field);
        // The mixin alone goes: the values are defined by the type now and Jahia keeps them.
        verify(field).removeMixin(MIXIN);
    }
}
