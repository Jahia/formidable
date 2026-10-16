package org.jahia.modules.formidable.accounts;

import org.jahia.modules.formidable.accounts.account.AccountDirectory;
import org.jahia.modules.formidable.accounts.activation.ActivationToken;
import org.jahia.modules.formidable.accounts.config.AccountsConfig;
import org.jahia.modules.formidable.accounts.config.AccountsConfigService;
import org.jahia.modules.formidable.engine.api.FormActionContext;
import org.jahia.modules.formidable.engine.api.FmdbMixin;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRPropertyWrapper;
import org.jahia.services.content.decorator.JCRSiteNode;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Builders shared by the tests of the actions, the guard and the servlet: nodes, requests, contexts, configuration. */
public final class TestSupport {

    private TestSupport() {
    }

    /**
     * A configuration service holding the given values over the defaults the cfg ships, the groups customers and members
     * allowed, and https://www.example.com as the activation link's origin.
     */
    public static AccountsConfigService config(boolean requireCaptcha, int perAddress, int perAccount) {
        return config(requireCaptcha, perAddress, perAccount, "https://www.example.com");
    }

    /** The same, with the given activation origin (blank: none configured). */
    public static AccountsConfigService config(boolean requireCaptcha, int perAddress, int perAccount, String publicBaseUrl) {
        AccountsConfig config = mock(AccountsConfig.class);
        when(config.requireCaptcha()).thenReturn(requireCaptcha);
        when(config.maxAttemptsPerAddress()).thenReturn(perAddress);
        when(config.addressWindowSeconds()).thenReturn(600L);
        when(config.maxAttemptsPerAccount()).thenReturn(perAccount);
        when(config.accountWindowSeconds()).thenReturn(900L);
        when(config.maxTrackedKeys()).thenReturn(1000);
        when(config.allowedGroups()).thenReturn("customers;members");
        when(config.publicBaseUrl()).thenReturn(publicBaseUrl);
        when(config.mailFrom()).thenReturn("");
        AccountsConfigService service = new AccountsConfigService();
        service.apply(config);
        return service;
    }

    /**
     * A directory for the guard: every user holds the actions permission unless named in {@code withoutPermission}; no
     * account is created. The actions' tests use their own richer fake.
     */
    public static AccountDirectory directory(Set<String> withoutPermission) {
        return new AccountDirectory() {
            @Override
            public boolean isValidUsername(String username) {
                return true;
            }

            @Override
            public boolean isBackOfficeUser(String username) {
                return false;
            }

            @Override
            public boolean holdsPermission(String username, String path, String workspace, String permission) {
                return username != null && !withoutPermission.contains(username);
            }

            @Override
            public Created create(NewAccount account, Function<String, Pending> pending) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void delete(String identifier) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Optional<String> activate(ActivationToken token, Instant now) {
                return Optional.empty();
            }
        };
    }

    /** A string property wrapper, built before it is handed to a stub (Mockito refuses nested stubbing). */
    public static JCRPropertyWrapper string(String value) {
        try {
            JCRPropertyWrapper property = mock(JCRPropertyWrapper.class);
            when(property.getString()).thenReturn(value);
            return property;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** A boolean property wrapper. */
    public static JCRPropertyWrapper bool(boolean value) {
        try {
            JCRPropertyWrapper property = mock(JCRPropertyWrapper.class);
            when(property.getBoolean()).thenReturn(value);
            return property;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** A long property wrapper. */
    public static JCRPropertyWrapper number(long value) {
        try {
            JCRPropertyWrapper property = mock(JCRPropertyWrapper.class);
            when(property.getLong()).thenReturn(value);
            return property;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** An action node carrying the given string properties, last saved by {@code editor}. */
    public static JCRNodeWrapper actionNode(Map<String, String> strings) {
        try {
            JCRNodeWrapper node = mock(JCRNodeWrapper.class);
            when(node.getPath()).thenReturn("/sites/mysite/forms/f/actions/a");
            Map<String, String> all = new HashMap<>(Map.of("jcr:lastModifiedBy", "editor"));
            all.putAll(strings);
            for (Map.Entry<String, String> entry : all.entrySet()) {
                JCRPropertyWrapper property = string(entry.getValue());
                when(node.hasProperty(entry.getKey())).thenReturn(true);
                when(node.getProperty(entry.getKey())).thenReturn(property);
            }
            return node;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** Adds a boolean property to an action node. */
    public static void withBool(JCRNodeWrapper node, String name, boolean value) {
        try {
            JCRPropertyWrapper property = bool(value);
            when(node.hasProperty(name)).thenReturn(true);
            when(node.getProperty(name)).thenReturn(property);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** Adds a long property to an action node. */
    public static void withNumber(JCRNodeWrapper node, String name, long value) {
        try {
            JCRPropertyWrapper property = number(value);
            when(node.hasProperty(name)).thenReturn(true);
            when(node.getProperty(name)).thenReturn(property);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** Gives an action node a weak-reference property naming a page. */
    public static void withPage(JCRNodeWrapper node, String name) {
        try {
            JCRPropertyWrapper reference = mock(JCRPropertyWrapper.class);
            when(reference.getNode()).thenReturn(mock(JCRNodeWrapper.class));
            when(node.hasProperty(name)).thenReturn(true);
            when(node.getProperty(name)).thenReturn(reference);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** A form node, in a site that enabled the module, with or without the CAPTCHA option. */
    public static JCRNodeWrapper form(boolean captcha, boolean moduleEnabled) {
        try {
            JCRNodeWrapper form = mock(JCRNodeWrapper.class);
            when(form.getPath()).thenReturn("/sites/mysite/forms/f");
            when(form.isNodeType(FmdbMixin.CAPTCHA_PROTECTED_FORM)).thenReturn(captcha);
            JCRSiteNode site = mock(JCRSiteNode.class);
            when(site.getInstalledModulesWithAllDependencies()).thenReturn(moduleEnabled ? Set.of("default", "formidable-accounts") : Set.of("default"));
            when(form.getResolveSite()).thenReturn(site);
            return form;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** A request from the given address, with the given Referer, reaching www.example.com. */
    public static HttpServletRequest request(String remoteAddress, String referer) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRemoteAddr()).thenReturn(remoteAddress);
        when(request.getHeader("Referer")).thenReturn(referer);
        when(request.getServerName()).thenReturn("www.example.com");
        when(request.getScheme()).thenReturn("https");
        when(request.getServerPort()).thenReturn(443);
        return request;
    }

    /** A context for an action: the given nodes, values and sensitive values, in French, on site mysite. */
    public static FormActionContext context(JCRNodeWrapper action, JCRNodeWrapper form, HttpServletRequest request,
                                            Map<String, String> values, Map<String, String> sensitive) {
        Map<String, List<String>> open = new HashMap<>();
        values.forEach((name, value) -> open.put(name, List.of(value)));
        Map<String, List<String>> secret = new HashMap<>();
        sensitive.forEach((name, value) -> secret.put(name, List.of(value)));
        return new FormActionContext(action, form, request, mock(HttpServletResponse.class), null, Locale.FRENCH, "mysite", open, secret, List.of());
    }
}
