package org.jahia.modules.formidable.engine.actions.field;

import org.jahia.modules.formidable.engine.api.FieldAction;
import org.jahia.modules.formidable.engine.api.FieldActionRequest;
import org.jahia.modules.formidable.engine.api.FieldActionResult;
import org.jahia.services.content.JCRNodeWrapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FieldActionTypesServiceTest {

    private static FieldAction action(String type, boolean consumesSensitiveValues) {
        return new FieldAction() {
            @Override
            public String getNodeType() {
                return type;
            }

            @Override
            public boolean consumesSensitiveValues() {
                return consumesSensitiveValues;
            }

            @Override
            public FieldActionResult execute(JCRNodeWrapper actionNode, FieldActionRequest request) {
                return FieldActionResult.accept();
            }
        };
    }

    @Test
    void aTypeRunsOnASensitiveFieldOnlyWhenItsRegisteredActionOptsIn() {
        // Verifies the question the views ask through server.osgi.getService: the answer is the registered Java action's
        // own opt-in — the dispatcher's rule, read from the one runtime — and false for a type nobody registered, or
        // when the runtime is not bound.
        FieldActionRuntime runtime = new FieldActionRuntime();
        runtime.bindFieldAction(action("myco:passwordPolicy", true));
        runtime.bindFieldAction(action("myco:crmLookupAction", false));
        FieldActionTypesService service = new FieldActionTypesService();

        assertFalse(service.consumesSensitiveValues("myco:passwordPolicy"));
        service.setRuntime(runtime);

        assertTrue(service.consumesSensitiveValues("myco:passwordPolicy"));
        assertFalse(service.consumesSensitiveValues("myco:crmLookupAction"));
        assertFalse(service.consumesSensitiveValues("myco:notDeployed"));
        assertFalse(service.consumesSensitiveValues(null));
    }
}
