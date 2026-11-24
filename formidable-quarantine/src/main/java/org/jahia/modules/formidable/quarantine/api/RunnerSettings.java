package org.jahia.modules.formidable.quarantine.api;

/**
 * The settings the next run applies, as the configuration of the run gives them.
 *
 * @param allowInProcessFirst   whether a list may let an in-process check read a file before a remote check passed it
 * @param maxAttempts           the count after which a submission goes to review
 * @param backoffInitialSeconds the delay after the first attempt
 * @param backoffCapSeconds     the longest delay
 */
public record RunnerSettings(boolean allowInProcessFirst, int maxAttempts, long backoffInitialSeconds,
                             long backoffCapSeconds) {
}
