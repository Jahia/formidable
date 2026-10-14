package org.jahia.modules.formidable.accounts.activation;

import org.jahia.services.mail.MailService;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.osgi.service.component.annotations.ReferencePolicyOption;

import java.util.concurrent.atomic.AtomicReference;

/**
 * {@link ActivationMailer} on Jahia's {@link MailService}. The service queues the message through a Camel route:
 * a delivery failure on the asynchronous path is logged by the platform and does not reach this class, so the check
 * that can be made is the one made here, that the service is bound and enabled.
 *
 * <p>Threading: a singleton; the only field is an atomic reference to the (optional, dynamic) service.</p>
 */
@Component(service = ActivationMailer.class, immediate = true)
public class JahiaActivationMailer implements ActivationMailer {

    private final AtomicReference<MailService> mail = new AtomicReference<>();

    @Reference(cardinality = ReferenceCardinality.OPTIONAL, policy = ReferencePolicy.DYNAMIC, policyOption = ReferencePolicyOption.GREEDY, unbind = "unbindMailService")
    public void bindMailService(MailService service) {
        mail.set(service);
    }

    public void unbindMailService(MailService service) {
        mail.compareAndSet(service, null);
    }

    @Override
    public boolean isAvailable() {
        MailService service = mail.get();
        return service != null && service.isEnabled();
    }

    @Override
    public void send(String from, String to, String subject, String text, String html) throws MailUnavailableException {
        MailService service = mail.get();
        if (service == null || !service.isEnabled()) {
            throw new MailUnavailableException("Jahia's mail service is unavailable or disabled");
        }
        if (to == null || containsLineBreak(to) || !MailService.isValidEmailAddress(to, false)) {
            throw new MailUnavailableException("the recipient is not an acceptable address");
        }
        String sender = from == null || from.isBlank() ? service.defaultSender() : from;
        try {
            service.sendMessage(sender, to, null, null, subject, text, html);
        } catch (RuntimeException e) {
            throw new MailUnavailableException("the mail service refused the message", e);
        }
    }

    private static boolean containsLineBreak(String value) {
        return value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0;
    }
}
