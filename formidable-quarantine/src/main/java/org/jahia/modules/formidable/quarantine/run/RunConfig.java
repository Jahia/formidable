package org.jahia.modules.formidable.quarantine.run;

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

/**
 * The settings of the run, PID {@value QuarantineRun#PID}. The defaults are those of {@code Backoff.DEFAULT}: one
 * minute, doubling, capped at one hour, ten attempts. A spec that needs few attempts edits {@code maxAttempts}.
 */
@ObjectClassDefinition(name = "Formidable quarantine - run", description = "The run of the checks of the quarantine.")
public @interface RunConfig {

    @AttributeDefinition(description = "Lift the refusal of a list where an in-process check reads a file before a "
            + "remote check passed it. False by default.")
    boolean allowInProcessFirst() default false;

    @AttributeDefinition(description = "The delay after the first attempt, in seconds. It doubles at each attempt.")
    long backoffInitialSeconds() default 60;

    @AttributeDefinition(description = "The longest delay between two attempts, in seconds.")
    long backoffCapSeconds() default 3600;

    @AttributeDefinition(description = "The count after which the submission goes to review: the retryable failures "
            + "of a check on a file until it answers a result, or the runs in a row that bring no new PASS.")
    int maxAttempts() default 10;
}
