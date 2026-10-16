package org.jahia.modules.formidable.accounts.choicelist;

import org.jahia.modules.formidable.accounts.TestSupport;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRSessionWrapper;
import org.jahia.services.content.decorator.JCRGroupNode;
import org.jahia.services.content.decorator.JCRSiteNode;
import org.jahia.services.content.nodetypes.initializers.ChoiceListValue;
import org.jahia.services.usermanager.JahiaGroupManagerService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AllowedGroupsInitializerTest {

    private static JCRGroupNode group(String name, String title) {
        JCRGroupNode group = mock(JCRGroupNode.class);
        when(group.getName()).thenReturn(name);
        when(group.getDisplayableName()).thenReturn(title);
        return group;
    }

    @Test
    void theGroupFieldOffersTheAllowedGroupsThatExistGloballyOrOnTheActionsSite() throws Exception {
        // Verifies jahia-private#5909 finding 1 on the editor's side: the contributor picks among the administrator's
        // allowedGroups (customers and members in the test configuration), resolved globally then on the action's site;
        // a name that resolves nowhere is not offered, and no other group ever is.
        JCRSessionWrapper session = mock(JCRSessionWrapper.class);
        JCRNodeWrapper action = mock(JCRNodeWrapper.class);
        JCRSiteNode site = mock(JCRSiteNode.class);
        when(site.getSiteKey()).thenReturn("mysite");
        when(action.getResolveSite()).thenReturn(site);
        when(action.getSession()).thenReturn(session);
        JahiaGroupManagerService groups = mock(JahiaGroupManagerService.class);
        JCRGroupNode customers = group("customers", "Customers");
        JCRGroupNode members = group("members", "members");
        when(groups.lookupGroup(null, "customers", session)).thenReturn(customers);
        when(groups.lookupGroup("mysite", "members", session)).thenReturn(members);
        AllowedGroupsInitializer initializer = new AllowedGroupsInitializer();
        initializer.setConfig(TestSupport.config(true, 20, 5));
        initializer.setGroups(groups);

        List<ChoiceListValue> values = initializer.getChoiceListValues(null, null, List.of(), Locale.ENGLISH, Map.of("contextNode", action));

        assertEquals(List.of("Customers (customers)", "members"), values.stream().map(ChoiceListValue::getDisplayName).toList());
        assertEquals("customers", values.get(0).getValue().getString());
    }

    @Test
    void withoutAContextNodeNothingIsOffered() {
        // Verifies the list is empty rather than global when the editor gives no node to resolve the site from.
        AllowedGroupsInitializer initializer = new AllowedGroupsInitializer();
        initializer.setConfig(TestSupport.config(true, 20, 5));
        JahiaGroupManagerService groups = mock(JahiaGroupManagerService.class);
        JCRGroupNode customers = group("customers", "customers");
        when(groups.lookupGroup(any(), anyString(), any(JCRSessionWrapper.class))).thenReturn(customers);
        initializer.setGroups(groups);

        assertTrue(initializer.getChoiceListValues(null, null, List.of(), Locale.ENGLISH, Map.of()).isEmpty());
    }
}
