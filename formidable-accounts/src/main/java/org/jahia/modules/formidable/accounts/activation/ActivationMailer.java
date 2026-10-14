package org.jahia.modules.formidable.accounts.activation;

/**
 * Sends the activation e-mail. A seam over Jahia's mail service.
 *
 * <p>Implementations are singletons and keep no per-request state.</p>
 */
public interface ActivationMailer {

    /** The e-mail could not be sent: the mail service is absent or disabled, or the address is not acceptable. */
    final class MailUnavailableException extends Exception {
        private static final long serialVersionUID = 1L;

        public MailUnavailableException(String message) {
            super(message);
        }

        public MailUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /** @return whether an e-mail can be sent now (Jahia's mail service is bound and its SMTP settings enabled) */
    boolean isAvailable();

    /**
     * @param from    the sender, blank for the sender of Jahia's mail settings
     * @param to      the recipient, which comes from the visitor: validated here
     * @param subject the subject, one line
     * @param text    the plain-text body
     * @param html    the HTML body, already escaped
     * @throws MailUnavailableException when nothing was sent
     */
    void send(String from, String to, String subject, String text, String html) throws MailUnavailableException;
}
