package org.jahia.modules.formidable.engine.config.choiceoptions;

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.AttributeType;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

/**
 * One source a choice field may take its options from — a factory configuration, one file per source next to the
 * choice options configuration: {@code karaf/etc/org.jahia.modules.formidable.choiceOptions.source-<id>.cfg}. Its
 * {@code id} setting, required, is what a choice field stores ({@code optionsSourceKey}).
 */
@ObjectClassDefinition(
        name = "Formidable — Choice options — Source",
        description = "One source a choice field may take its options from: a Jahia choicelist initializer. One configuration per source, named by its Id."
)
public @interface OptionsSourceConfig {

    @AttributeDefinition(
            name = "Id",
            description = "Required. The source's id: what a choice field stores. Letters, digits, dashes and underscores; keep it once " +
                    "contributors use it.",
            type = AttributeType.STRING
    )
    String id() default "";

    @AttributeDefinition(
            name = "Label",
            description = "Shown to contributors in the source picker. A label of the form module:resource.key is read from " +
                    "that module's resource bundle, in the editor's language. Defaults to the id.",
            type = AttributeType.STRING
    )
    String label() default "";

    @AttributeDefinition(
            name = "Choicelist initializer",
            description = "The key of the Jahia choicelist initializer that gives the options (country, language, or a " +
                    "module's own).",
            type = AttributeType.STRING
    )
    String initializerKey() default "";

    @AttributeDefinition(
            name = "Initializer parameter",
            description = "The parameter handed to the initializer, when it takes one. Leave empty otherwise.",
            type = AttributeType.STRING
    )
    String param() default "";
}
