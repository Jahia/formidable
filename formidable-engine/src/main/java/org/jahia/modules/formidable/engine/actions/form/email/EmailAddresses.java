package org.jahia.modules.formidable.engine.actions.form.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The addresses of the e-mail actions, {@code to} and {@code from}: a comma-separated list of addresses,
 * each bare ({@code team@example.com}) or with a name ({@code Team <team@example.com>}).
 * <p>
 * The mail service parses them strict when it sends, later, on a queue that never reports to the action:
 * one address with a typo ({@code no-reply@example.com.}, a domain ending with a dot) and the whole message
 * is refused in the background, after the action reported a success and the visitor saw one. The actions
 * parse the same way before they hand the message over: the entries the queue would refuse are left out and
 * logged against the action, the message goes to the others, and an invalid sender gives way to the default
 * one. A typo that the editor let through costs an e-mail, never a submission: an action with no valid
 * recipient left sends nothing and returns, so that the actions after it, Save to JCR among them, run.
 */
public final class EmailAddresses {

    private static final Logger log = LoggerFactory.getLogger(EmailAddresses.class);

    /** The valid entries of a list and the ones the mail service would refuse, each in their order. */
    public record Split(List<String> valid, List<String> invalid) {
    }

    private EmailAddresses() {
    }

    /** The entries of a comma-separated list, trimmed, the blank ones left out. */
    public static List<String> entries(String list) {
        List<String> entries = new ArrayList<>();
        if (list != null) {
            for (String entry : list.split(",")) {
                if (!entry.isBlank()) {
                    entries.add(entry.trim());
                }
            }
        }
        return entries;
    }

    /** Whether the mail service sends to an entry: the strict parse it applies, then the syntax check. */
    public static boolean isValid(String entry) {
        try {
            new InternetAddress(entry.trim(), true).validate();
            return true;
        } catch (AddressException e) {
            return false;
        }
    }

    /** The entries of a list, the valid ones apart from the ones the mail service would refuse. */
    public static Split split(String list) {
        List<String> valid = new ArrayList<>();
        List<String> invalid = new ArrayList<>();
        for (String entry : entries(list)) {
            (isValid(entry) ? valid : invalid).add(entry);
        }
        return new Split(valid, invalid);
    }

    /**
     * The recipients a message of the action goes to: the valid entries of {@code to}, joined back; the
     * entries left out are logged with the action. Empty when no entry is valid, which is logged too: the
     * action then sends nothing and lets the submission go on.
     */
    static Optional<String> recipients(String actionType, String actionPath, String to) {
        Split split = split(to);
        if (!split.invalid().isEmpty()) {
            String leftOut = String.join(", ", split.invalid());
            if (split.valid().isEmpty()) {
                log.warn("[Formidable] {} '{}': no 'to' address the mail service accepts, nothing sent — {}", actionType, actionPath, leftOut);
            } else {
                log.warn("[Formidable] {} '{}': 'to' address(es) the mail service would refuse, left out: {}", actionType, actionPath, leftOut);
            }
        }
        return split.valid().isEmpty() ? Optional.empty() : Optional.of(String.join(", ", split.valid()));
    }

    /**
     * The sender a message of the action carries: {@code from} when it is an address the mail service
     * accepts, else null — the mail service then puts its default sender — logged with the action. Blank
     * is null as before, without a word.
     */
    static String sender(String actionType, String actionPath, String from, String defaultSender) {
        if (from == null || from.isBlank()) {
            return null;
        }
        if (isValid(from)) {
            return from;
        }
        log.warn("[Formidable] {} '{}': 'from' address the mail service would refuse, the default sender {} is used instead: {}", actionType, actionPath, defaultSender, from);
        return null;
    }
}
