package org.jahia.modules.formidable.formsimport.config;

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.AttributeType;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

/**
 * The Forms import settings: the largest export the Results page takes (docs/architecture/forms-import.md,
 * "Running it"). Deployed as {@code karaf/etc/org.jahia.modules.formidable.formsImport.cfg}. The Import
 * button itself follows the module being enabled on the site, not a setting.
 */
@ObjectClassDefinition(
        name = "Formidable — Forms import",
        description = "The import of Jahia Forms forms and results from the Results page: the largest export it accepts. "
                + "The Import button shows on the sites the module is enabled on."
)
public @interface FormsImportConfig {

    @AttributeDefinition(
            name = "Largest export (MB)",
            description = "The largest export file the import dialog accepts, in megabytes.",
            type = AttributeType.LONG
    )
    long maxFileSizeMb() default 200;
}
