package org.jahia.modules.formidable.engine.config.formactions;

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.AttributeType;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

/**
 * One service a forward action may send a submission to — a factory configuration, one file per target next to
 * the form actions configuration: {@code karaf/etc/org.jahia.modules.formidable.formActions.target-<id>.cfg}. Its
 * {@code id} setting, required, is what a forward action stores; the URL stays in the file and never reaches a
 * contributor.
 */
@ObjectClassDefinition(
        name = "Formidable — Form actions — Forward target",
        description = "One service a forward action may send a submission to. One configuration per target, named by its Id."
)
public @interface ForwardTargetConfig {

    @AttributeDefinition(
            name = "Id",
            description = "Required. The target's id: what a forward action stores, and the name of the target in the editor's " +
                    "picker when no label is set. Letters, digits, dashes and underscores; keep it once contributors use it.",
            type = AttributeType.STRING
    )
    String id() default "";

    @AttributeDefinition(
            name = "Label",
            description = "Shown to contributors in the target picker of a forward action. Defaults to the id.",
            type = AttributeType.STRING
    )
    String label() default "";

    @AttributeDefinition(
            name = "URL",
            description = "The HTTPS address the submission is posted to (https://api.example.com/forms). A development " +
                    "target (below) may use plain HTTP on localhost or host.docker.internal instead.",
            type = AttributeType.STRING
    )
    String url() default "";

    @AttributeDefinition(
            name = "Development target",
            description = "A service on this machine, over plain HTTP on localhost or host.docker.internal. Honoured only " +
                    "while the form actions configuration's 'Enable development forward targets' is on.",
            type = AttributeType.BOOLEAN
    )
    boolean development() default false;
}
