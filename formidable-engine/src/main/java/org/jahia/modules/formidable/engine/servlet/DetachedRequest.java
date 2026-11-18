package org.jahia.modules.formidable.engine.servlet;

import org.jahia.modules.formidable.engine.api.DetachedRequestException;
import org.jahia.modules.formidable.engine.api.SubmissionOrigin;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * The request and the response an action receives when it runs after release, with no HTTP request: stand-ins that
 * answer what {@link SubmissionOrigin} kept, and throw {@link DetachedRequestException} for any other call.
 */
public final class DetachedRequest {

    private DetachedRequest() {
    }

    /** A request that answers the {@code lang} parameter, the {@code Referer} header and the time zone header. */
    public static HttpServletRequest request(SubmissionOrigin origin) {
        return standIn(HttpServletRequest.class, (proxy, method, args) -> switch (method.getName()) {
            case "getParameter" -> named(SubmissionOrigin.LANGUAGE_PARAMETER, args) ? origin.language() : refuse(method, args);
            case "getHeader" -> header(origin, method, args);
            default -> ofObject(proxy, method, args, "DetachedRequest");
        });
    }

    /** A response that answers nothing: no browser waits for it. */
    public static HttpServletResponse response() {
        return standIn(HttpServletResponse.class, (proxy, method, args) -> ofObject(proxy, method, args, "DetachedResponse"));
    }

    private static Object header(SubmissionOrigin origin, Method method, Object[] args) {
        if (named(SubmissionOrigin.REFERER_HEADER, args)) {
            return origin.referer();
        }
        return named(SubmissionOrigin.TIME_ZONE_HEADER, args) ? origin.timeZone() : refuse(method, args);
    }

    /** Whether the one argument of the call is the name, whatever its case: a header name has none. */
    private static boolean named(String name, Object[] args) {
        return args != null && args.length == 1 && args[0] instanceof String asked && name.equalsIgnoreCase(asked);
    }

    /** The methods of {@link Object}, which a log line or a collection may call; any other call is refused. */
    private static Object ofObject(Object proxy, Method method, Object[] args, String name) {
        if (method.getDeclaringClass() != Object.class) {
            return refuse(method, args);
        }
        return switch (method.getName()) {
            case "equals" -> proxy == args[0];
            case "hashCode" -> System.identityHashCode(proxy);
            default -> name;
        };
    }

    private static Object refuse(Method method, Object[] args) {
        // the name asked for is a parameter or a header name of the code of an action, never a value of a visitor
        String asked = args != null && args.length == 1 && args[0] instanceof String name ? "(\"" + name + "\")" : "()";
        throw new DetachedRequestException("An action that runs after release has no HTTP request: "
                + method.getDeclaringClass().getSimpleName() + "#" + method.getName() + asked + " is not available. "
                + "It may read the lang parameter, the Referer header and the " + SubmissionOrigin.TIME_ZONE_HEADER
                + " header.");
    }

    private static <T> T standIn(Class<T> type, InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(DetachedRequest.class.getClassLoader(), new Class<?>[] {type}, handler));
    }
}
