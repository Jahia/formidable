package org.jahia.modules.formidable.engine.config.formsimport;

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.AttributeType;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

/**
 * The Forms import theme: whether the Results page offers the import, and the largest export it takes
 * (docs/architecture/forms-import.md, "Running it"). Deployed as
 * {@code karaf/etc/org.jahia.modules.formidable.formsImport.cfg}.
 */
@ObjectClassDefinition(
        name = "Formidable — Forms import",
        description = "The import of Jahia Forms forms and results from the Results page: the button, off by default, and the largest export it accepts."
)
public @interface FormsImportConfig {

    @AttributeDefinition(
            name = "Import button",
            description = "Shows the Import button on the Results page of every site, to the administrators of the site. "
                    + "Turn it on for the time of an import, then off again.",
            type = AttributeType.BOOLEAN
    )
    boolean importButtonEnabled() default false;

    @AttributeDefinition(
            name = "Largest export (MB)",
            description = "The largest export file the import dialog accepts, in megabytes.",
            type = AttributeType.LONG
    )
    long maxFileSizeMb() default 200;
}
