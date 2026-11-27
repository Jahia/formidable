package org.jahia.modules.formidable.quarantine.graphql;

import graphql.ErrorType;
import org.jahia.modules.graphql.provider.dxm.BaseGqlClientException;

import java.util.Map;

/**
 * A request of the API that did not run: nothing changed. The GraphQL error keeps its fixed message, and carries the
 * code of the refusal in its extensions, {@code extensions.code}, which a client reads instead of the message. The
 * error is a client error of the GraphQL provider, built here rather than extended, which the provider answers as it
 * answers any of its own.
 */
final class Refusal {

    /** The key of the code in the extensions of the error. */
    static final String EXTENSION_KEY = "code";

    /** Why a request did not run. */
    enum Code {
        /** The state of the submission, or its decision, does not allow the operation any more. */
        CONFLICT,
        /** The request needs a right that the user does not have, such as a server administrator for the purge. */
        FORBIDDEN,
        /** No held submission of the site has the identifier, or the user does not manage the site. */
        NOT_FOUND
    }

    private Refusal() {
    }

    /** @return the error of a refused request, with its fixed message and its code */
    static BaseGqlClientException of(String message, Code code) {
        return new BaseGqlClientException(message, null, ErrorType.DataFetchingException, Map.of(EXTENSION_KEY, code.name()));
    }
}
