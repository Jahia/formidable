package org.jahia.modules.formidable.engine.migration;

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

class AdvancedSettingsMixinMigrationTest {

    private static final String MIXIN = "fmdbmix:advancedInputTextSettings";

    /** The field's primary type as the registry knows it: including the mixin as a supertype or not yet. */
    private static NodeType typeIncludingTheMixin(boolean included) {
        NodeType type = mock(NodeType.class);
        when(type.isNodeType(MIXIN)).thenReturn(included);
        return type;
    }

    @Test
    void theRetiredMixinsBelongToTheTextTextareaNumberAndRangeTypes() {
        assertEquals(4, AdvancedSettingsMixinMigration.RETIRED_MIXINS.size());
        assertEquals("fmdbmix:advancedInputNumberSettings", AdvancedSettingsMixinMigration.RETIRED_MIXINS.get("fmdb:inputNumber"));
        assertEquals("fmdbmix:advancedInputRangeSettings", AdvancedSettingsMixinMigration.RETIRED_MIXINS.get("fmdb:inputRange"));
        assertEquals("fmdbmix:advancedInputTextSettings", AdvancedSettingsMixinMigration.RETIRED_MIXINS.get("fmdb:inputText"));
        assertEquals("fmdbmix:advancedTextareaSettings", AdvancedSettingsMixinMigration.RETIRED_MIXINS.get("fmdb:textarea"));
    }

    @Test
    void aFieldWhoseTypeIncludesTheMixinIsReady() {
        assertTrue(AdvancedSettingsMixinMigration.includedBySupertype(typeIncludingTheMixin(true), MIXIN));
    }

    @Test
    void aFieldWhoseTypeDoesNotIncludeTheMixinYetWaitsForTheElementsRedeploy() {
        // Engine upgraded first: the mixin is still the only type defining the settings, removing it would drop them.
        assertFalse(AdvancedSettingsMixinMigration.includedBySupertype(typeIncludingTheMixin(false), MIXIN));
    }

    @Test
    void droppingTheMixinChecksTheFieldOutFirst() throws Exception {
        JCRSessionWrapper session = mock(JCRSessionWrapper.class);
        JCRNodeWrapper field = mock(JCRNodeWrapper.class);

        AdvancedSettingsMixinMigration.dropMixin(session, field, MIXIN);

        verify(session).checkout(field);
        // The mixin alone goes: the values are defined by the type now and Jahia keeps them.
        verify(field).removeMixin(MIXIN);
    }
}
