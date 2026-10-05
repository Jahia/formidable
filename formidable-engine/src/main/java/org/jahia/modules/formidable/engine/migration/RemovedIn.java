package org.jahia.modules.formidable.engine.migration;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks what exists only for an upgrade from an earlier version — a startup migration, its plumbing, or a read that
 * keeps content not migrated yet working — and the version that removes it. {@code grep -rn "@RemovedIn"} is the
 * removal checklist of that version; the element's Javadoc says what removing it involves, and
 * docs/administration/upgrade-notes.md, "Startup migrations", why it may go then.
 * <p>
 * Not {@link Deprecated}: nothing here is an API a caller should move away from, and the code-quality rules on
 * deprecation would flag every declaration and every use, tests included, until the version comes. Source retention:
 * the annotation is a marker for the people reading the code, nothing reads it at runtime, and a module compiling
 * against this one imports nothing for it.
 */
@Documented
@Retention(RetentionPolicy.SOURCE)
@Target({ElementType.TYPE, ElementType.METHOD, ElementType.FIELD, ElementType.CONSTRUCTOR})
public @interface RemovedIn {

    /** The version that removes the element, e.g. {@code "0.6"}. */
    String value();
}
