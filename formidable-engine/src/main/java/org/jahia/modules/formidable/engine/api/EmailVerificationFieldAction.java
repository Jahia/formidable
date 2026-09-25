package org.jahia.modules.formidable.engine.api;

import java.io.IOException;

/**
 * A {@link ProviderFieldAction} that verifies an email address with a provider: the value must read as one address
 * ({@link EmailAddress#domainOf}) for the provider to be asked at all — the shape of a value is the field's own
 * validation, at step 9, and an answer is chargeable — and the module writes {@link #verify} alone: the call and
 * the provider's own vocabulary turned into the three verdicts. The samples module ships two, against Experian
 * and against ZeroBounce, the shape to copy for another provider.
 */
public abstract class EmailVerificationFieldAction extends ProviderFieldAction {

    @Override
    protected final boolean concerns(FieldActionRequest request) {
        return EmailAddress.domainOf(request.value()) != null;
    }

    @Override
    protected final FieldActionResult ask(String providerId, FieldActionRequest request) throws IOException {
        return verify(providerId, request.value().trim());
    }

    /**
     * The provider's verdict on one address: accept when the mailbox receives mail, reject when it does not or is a
     * trap or disposable, unavailable when the provider could not conclude or the answer is not its documented one.
     */
    protected abstract FieldActionResult verify(String providerId, String address) throws IOException;
}
