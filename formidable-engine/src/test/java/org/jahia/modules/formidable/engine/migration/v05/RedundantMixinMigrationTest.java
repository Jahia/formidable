package org.jahia.modules.formidable.engine.migration.v05;

import org.jahia.modules.formidable.engine.api.FmdbMixin;
import org.jahia.modules.formidable.engine.migration.common.ElementsRedeployRetriggeredMigration;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRSessionWrapper;
import org.jahia.services.content.JCRWorkspaceWrapper;
import org.junit.jupiter.api.Test;

import javax.jcr.nodetype.NodeType;
import javax.jcr.nodetype.NodeTypeManager;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
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
    void aTypeTheRegistryDoesNotKnowYetIsSkippedNotQueried() throws Exception {
        // The direct 0.3 upgrade path: the engine starts while the formidable-elements of 0.3, which has no number
        // field, still runs. Querying fmdb:inputNumber would throw and stop the whole workspace; no node can be of it.
        JCRSessionWrapper session = mock(JCRSessionWrapper.class);
        JCRWorkspaceWrapper workspace = mock(JCRWorkspaceWrapper.class);
        NodeTypeManager types = mock(NodeTypeManager.class);
        when(session.getWorkspace()).thenReturn(workspace);
        when(workspace.getNodeTypeManager()).thenReturn(types);
        when(types.hasNodeType(anyString())).thenReturn(true);
        when(types.hasNodeType("fmdb:inputNumber")).thenReturn(false);

        Map<String, String> queried = RedundantMixinMigration.retiredMixinsOfRegisteredTypes(session, "default");

        assertEquals(4, queried.size());
        assertFalse(queried.containsKey("fmdb:inputNumber"));
        assertEquals("fmdbmix:advancedInputTextSettings", queried.get("fmdb:inputText"));
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
