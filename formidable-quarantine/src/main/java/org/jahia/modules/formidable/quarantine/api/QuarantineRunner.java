package org.jahia.modules.formidable.quarantine.api;

import org.osgi.annotation.versioning.ProviderType;

/**
 * Starts one pass of the run of the checks from outside the module, such as the "run now" endpoint of the test
 * module. The module registers its run as this service.
 */
@ProviderType
public interface QuarantineRunner {

    /**
     * Makes one pass over the due held submissions of the site, or of one form of it.
     *
     * @param siteKey        the key of the site
     * @param formId         the identifier of the one form to run, or {@code null} for every form of the site
     * @param ignoreSchedule true to take every submission in state {@code held} or {@code checking}, whatever its next
     *                       attempt says
     * @return the submissions processed, with the state of each after the run
     * @throws QuarantineRunException when the quarantine tree of the site cannot be read, possibly after the pass
     *                                processed the submissions of earlier forms
     */
    RunReport runNow(String siteKey, String formId, boolean ignoreSchedule) throws QuarantineRunException;

    /** @return the settings the next run applies */
    RunnerSettings runnerSettings();
}
