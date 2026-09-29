package org.jahia.modules.formidable.engine.config.common;

import java.net.URI;

/**
 * The one rule every outbound endpoint an administrator declares obeys — a forward target, a field action
 * provider: HTTPS with a host and no embedded credentials for a standard entry; plain HTTP on localhost or
 * host.docker.internal, and nothing else, for an entry of a development list, which is behind a switch.
 */
public final class EndpointRule {

    private EndpointRule() {
    }

    /** Null when the URI is acceptable under the rule; the reason to log otherwise. */
    public static String unsupportedReason(URI uri, boolean development) {
        if (uri.getUserInfo() != null) {
            return "URI must not include embedded user credentials.";
        }
        String scheme = uri.getScheme();
        if (!development) {
            return "https".equalsIgnoreCase(scheme) ? null : "URI must use HTTPS.";
        }
        if (!"http".equalsIgnoreCase(scheme) || !isAllowedDevelopmentEndpoint(uri)) {
            return "URI must use HTTP on localhost or host.docker.internal.";
        }
        return null;
    }

    /** Whether the host is one of the two a development entry may name. */
    public static boolean isAllowedDevelopmentEndpoint(URI uri) {
        String host = uri.getHost();
        return "localhost".equalsIgnoreCase(host) || "host.docker.internal".equalsIgnoreCase(host);
    }
}
