package org.jahia.test.modules.formidable.quarantine.ops;

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

/** The settings of the retention, PID {@value QuarantineRetention#PID}. */
@ObjectClassDefinition(name = "Formidable quarantine test module - retention",
        description = "The retention of the rejected held submissions of the quarantine test module. Never deploy this "
                + "module in production.")
public @interface RetentionConfig {

    @AttributeDefinition(description = "The number of days a rejected held submission is kept after its last change, "
            + "before the retention purges it.")
    int retentionDays() default QuarantineRetention.DEFAULT_RETENTION_DAYS;
}
