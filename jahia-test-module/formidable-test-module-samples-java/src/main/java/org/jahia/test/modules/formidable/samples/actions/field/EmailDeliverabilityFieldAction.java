package org.jahia.test.modules.formidable.samples.actions.field;

import org.jahia.modules.formidable.engine.api.FieldAction;
import org.jahia.modules.formidable.engine.api.FieldActionRequest;
import org.jahia.modules.formidable.engine.api.FieldActionResult;
import org.jahia.services.content.JCRNodeWrapper;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.naming.Context;
import javax.naming.NameNotFoundException;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.InitialDirContext;
import java.util.Hashtable;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * The built-in field action: <strong>does the address's domain exist at all?</strong> It asks the domain name
 * system for the domain's mail exchangers, then for its addresses — a domain without an MX record but with an A
 * record still receives mail, which is what RFC 5321 calls the implicit exchanger.
 *
 * <p><strong>Only a domain the resolver says does not exist is refused.</strong> An answer that comes back empty is
 * not proof of anything: a resolver may decline a record type rather than deny the name — Docker's embedded
 * resolver answers nothing to a mail-exchanger question, and most Jahia installations run behind one. So an empty
 * answer is an unavailable check, which the contributor's {@code whenUnavailable} decides, and never a refusal.
 * What the action catches is the case that matters and is provable: the mistyped domain.</p>
 *
 * <p>It is the built-in because it needs nothing: no provider, no credential, no account, no configuration. It is
 * also honest about its limit, and the contributor's help text says so: <strong>it does not prove the mailbox
 * exists</strong>. Only a paid provider or an actual delivery can, which is what {@code FieldActionGateway} and the
 * {@code fieldActionProviders} configuration are there for.</p>
 *
 * <p>What leaves the server is the <em>domain</em>, never the address: the local part is dropped before the query,
 * so the visitor's identity is not handed to a resolver. A value that is not an address at all is accepted without
 * a query — the shape of a value is the field's own validation, at step 9, not this action's business. A resolver
 * that does not answer is an unavailable check, which the contributor's {@code whenUnavailable} setting decides,
 * and the queries carry their own short timeout: this runs on the request thread, at submission.</p>
 */
@Component(service = FieldAction.class)
public class EmailDeliverabilityFieldAction implements FieldAction {

    public static final String NODE_TYPE = "fmdbsample:emailDeliverabilityAction";

    /** What the longest legal domain name measures, so an absurd value is refused before any query. */
    static final int MAX_DOMAIN_LENGTH = 253;
    /** A domain worth asking about: labels of letters, digits and hyphens, at least two of them. */
    private static final Pattern DOMAIN = Pattern.compile("[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?(\\.[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?)+");
    /** Asked one at a time, in this order: a single query for several types answers "DNS error" on resolvers that answer each separately. */
    private static final String[] MAIL_RECORDS = {"MX", "A", "AAAA"};

    private static final Logger log = LoggerFactory.getLogger(EmailDeliverabilityFieldAction.class);

    /**
     * The question this action asks the domain name system, and the one seam its tests replace: the records that
     * say a domain can receive mail, empty when it announces none. A domain that does not exist throws
     * {@link NameNotFoundException}; a resolver that cannot answer throws another {@link NamingException}, and the
     * two mean opposite things — one is the visitor's typo, the other is an outage.
     */
    @FunctionalInterface
    public interface MailRecords {
        List<String> of(String domain) throws NamingException;
    }

    private final transient MailRecords records;

    public EmailDeliverabilityFieldAction() {
        this(EmailDeliverabilityFieldAction::lookup);
    }

    EmailDeliverabilityFieldAction(MailRecords records) {
        this.records = records;
    }

    @Override
    public String getNodeType() {
        return NODE_TYPE;
    }

    @Override
    public FieldActionResult execute(JCRNodeWrapper actionNode, FieldActionRequest request) {
        String domain = domainOf(request.value());
        if (domain == null) {
            // Not an address, or not one this action can read: the field's own validation judges the shape.
            return FieldActionResult.accept();
        }
        try {
            return records.of(domain).isEmpty()
                    ? FieldActionResult.unavailable("the resolver returned no record for the domain")
                    : FieldActionResult.accept();
        } catch (NameNotFoundException e) {
            return FieldActionResult.reject("no such domain");
        } catch (NamingException e) {
            log.info("Formidable could not ask the domain name system about a domain of field '{}': {}",
                    request.fieldName(), e.getClass().getSimpleName());
            return FieldActionResult.unavailable("the resolver did not answer: " + e.getClass().getSimpleName());
        }
    }

    /** The domain of an address, lower-cased and without its trailing dot; null when the value is not one address. */
    static String domainOf(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        int at = trimmed.lastIndexOf('@');
        if (at < 1 || at == trimmed.length() - 1 || trimmed.indexOf('@') != at) {
            return null;
        }
        String domain = trimmed.substring(at + 1).toLowerCase(Locale.ROOT);
        if (domain.endsWith(".")) {
            domain = domain.substring(0, domain.length() - 1);
        }
        return domain.length() <= MAX_DOMAIN_LENGTH && DOMAIN.matcher(domain).matches() ? domain : null;
    }

    /**
     * The domain name system, asked through the platform's own runtime: the mail exchangers of the domain, and
     * failing those its addresses. The timeouts are short and the retries few on purpose — the submission waits
     * for this.
     */
    private static List<String> lookup(String domain) throws NamingException {
        Hashtable<String, String> environment = new Hashtable<>();
        environment.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.dns.DnsContextFactory");
        environment.put("com.sun.jndi.dns.timeout.initial", "2000");
        environment.put("com.sun.jndi.dns.timeout.retries", "1");
        InitialDirContext context = new InitialDirContext(environment);
        try {
            // One question per query. Asking for MX, A and AAAA together answers "DNS error" against resolvers
            // that answer each of the three separately — measured before this line existed.
            for (String record : MAIL_RECORDS) {
                Attribute attribute = context.getAttributes(domain, new String[]{record}).get(record);
                if (attribute != null && attribute.size() > 0) {
                    return List.of(record);
                }
            }
            return List.of();
        } finally {
            context.close();
        }
    }
}
