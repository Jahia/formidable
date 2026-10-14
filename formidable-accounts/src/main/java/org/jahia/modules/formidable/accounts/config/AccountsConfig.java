package org.jahia.modules.formidable.accounts.config;

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.AttributeType;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

/**
 * OSGi metatype of {@code org.jahia.modules.formidable.accounts.cfg}: the abuse protection of the two actions and
 * the few operator-owned settings. One file for the platform; every value below is the built-in default.
 */
@ObjectClassDefinition(
        name = "Formidable - Accounts",
        description = "Abuse protection and operator settings of the Create account and Sign in form actions."
)
public @interface AccountsConfig {

    String PID = "org.jahia.modules.formidable.accounts";

    @AttributeDefinition(
            name = "Require a CAPTCHA on the form",
            description = "Both actions refuse to run, answering the visitor with a generic message and logging a configuration "
                    + "error, unless the form carries the CAPTCHA option. Set it to false only on a local development "
                    + "instance: a public sign-up or sign-in form without CAPTCHA is an open door for bots. Default: true.",
            type = AttributeType.BOOLEAN
    )
    boolean requireCaptcha() default true;

    @AttributeDefinition(
            name = "Attempts per remote address",
            description = "How many submissions of these actions one remote address may make in the window below, whatever "
                    + "their outcome. Counted in memory: a restart or another cluster node starts from zero. Default: 20.",
            type = AttributeType.INTEGER
    )
    int maxAttemptsPerAddress() default 20;

    @AttributeDefinition(
            name = "Address window (seconds)",
            description = "The window the per-address limit is counted over. Default: 600.",
            type = AttributeType.LONG
    )
    long addressWindowSeconds() default 600L;

    @AttributeDefinition(
            name = "Attempts per account",
            description = "How many attempts one account (the e-mail or user name that was typed, whether or not it exists) "
                    + "may cost in the window below: every sign-up attempt for it, every failed sign-in for it. A success "
                    + "clears the sign-in count. Over the limit the visitor reads the same message for an account that exists "
                    + "and one that does not. Default: 5.",
            type = AttributeType.INTEGER
    )
    int maxAttemptsPerAccount() default 5;

    @AttributeDefinition(
            name = "Account window (seconds)",
            description = "The window the per-account limit is counted over. Default: 900.",
            type = AttributeType.LONG
    )
    long accountWindowSeconds() default 900L;

    @AttributeDefinition(
            name = "Tracked keys",
            description = "Upper bound on the addresses and accounts remembered at once; the least recently seen is forgotten "
                    + "first. Default: 10000.",
            type = AttributeType.INTEGER
    )
    int maxTrackedKeys() default 10000;

    @AttributeDefinition(
            name = "Groups a form can never add a visitor to",
            description = "Group names, separated by semicolons, that the Create account action refuses, so that a contributor "
                    + "cannot hand out privileges from a public form. Compared in lower case with the group's name and with "
                    + "its key. Default: administrators;privileged;site-administrators;site-privileged;guest;users.",
            type = AttributeType.STRING
    )
    String deniedGroups() default "administrators;privileged;site-administrators;site-privileged;guest;users";

    @AttributeDefinition(
            name = "Public base URL",
            description = "Scheme and host the activation link carries, for example https://www.example.com, without a trailing "
                    + "slash. Empty: the one the sign-up request reached. Set it when Jahia sits behind a proxy that does not "
                    + "forward the original host.",
            type = AttributeType.STRING
    )
    String publicBaseUrl() default "";

    @AttributeDefinition(
            name = "Activation e-mail sender",
            description = "The From address of the activation e-mail. Empty: the sender of Jahia's mail settings.",
            type = AttributeType.STRING
    )
    String mailFrom() default "";
}
