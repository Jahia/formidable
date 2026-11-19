package org.jahia.test.modules.formidable.quarantine.run;

import java.util.List;

/**
 * What one run did: the held submissions it processed, with the state of each after the run.
 *
 * @param siteKey   the site of the run
 * @param formId    the form the run was restricted to, or {@code null} for every form of the site
 * @param processed the held submissions the run processed, in the order of the run
 */
public record RunReport(String siteKey, String formId, List<Processed> processed) {

    public RunReport {
        processed = List.copyOf(processed);
    }

    /**
     * One held submission the run processed.
     *
     * @param path   the path of the held submission
     * @param formId the identifier of its form
     * @param from   its state before the run
     * @param state  its state after the run, or {@code null} when it could not be read again
     * @param error  why the run of this submission failed, or {@code null}; it names classes and paths, never a value
     */
    public record Processed(String path, String formId, String from, String state, String error) {
    }
}
