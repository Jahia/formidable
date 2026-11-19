package org.jahia.test.modules.formidable.quarantine.chain;

import java.util.Objects;

import org.jahia.test.modules.formidable.quarantine.check.QuarantinedFile;

/**
 * One call the run makes to a check: hand it a file, or poll a ticket.
 */
public sealed interface Step permits Step.Submit, Step.Poll {

    /** @return the file of the step */
    QuarantinedFile file();

    /** @return the check of the step */
    ChainCheck check();

    /**
     * Hand the file to the check. It is also the step of a retry when the failure came before a ticket.
     */
    record Submit(QuarantinedFile file, ChainCheck check) implements Step {
        public Submit {
            Objects.requireNonNull(file, "file");
            Objects.requireNonNull(check, "check");
        }
    }

    /**
     * Poll the ticket of the check for the file.
     */
    record Poll(QuarantinedFile file, ChainCheck check, String ticket) implements Step {
        public Poll {
            Objects.requireNonNull(file, "file");
            Objects.requireNonNull(check, "check");
            Objects.requireNonNull(ticket, "ticket");
        }
    }
}
