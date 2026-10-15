package org.jahia.modules.formidable.engine.api;

/**
 * Whether the forms of the instance can carry a captcha: the engine's captcha configuration, read by a module
 * that creates forms (an import) to know whether a captcha field it would add has anything to show and to
 * verify. An OSGi service of the engine.
 */
public interface CaptchaSetup {

    /** True when the widget and its server-side verification are both configured. */
    boolean isComplete();
}
