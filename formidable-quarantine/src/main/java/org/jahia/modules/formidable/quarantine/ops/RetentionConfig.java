package org.jahia.modules.formidable.quarantine.ops;

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

/** The settings of the retention, PID {@value QuarantineRetention#PID}. */
@ObjectClassDefinition(name = "Formidable quarantine - retention",
        description = "The retention of the rejected held submissions of the quarantine.")
public @interface RetentionConfig {

    @AttributeDefinition(description = "The number of days a rejected held submission is kept after its last change, "
            + "before the retention purges it.")
    int retentionDays() default QuarantineRetention.DEFAULT_RETENTION_DAYS;
}
