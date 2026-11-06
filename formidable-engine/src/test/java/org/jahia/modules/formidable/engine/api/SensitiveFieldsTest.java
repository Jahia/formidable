package org.jahia.modules.formidable.engine.api;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The selection a contextual action answers to receive sensitive values: only none and all exist.
 */
class SensitiveFieldsTest {

    @Test
    void noneSelectsNothingAndAllSelectsEveryField() {
        // Verifies the two selections: none selects no sensitive field, all selects every one, and each factory answers
        // the same instance on every call.
        assertFalse(SensitiveFields.none().selectsAll());
        assertTrue(SensitiveFields.all().selectsAll());
        assertSame(SensitiveFields.none(), SensitiveFields.none());
        assertSame(SensitiveFields.all(), SensitiveFields.all());
        assertNotEquals(SensitiveFields.none(), SensitiveFields.all());
    }

    @Test
    void aModuleCannotMakeAnotherSelection() {
        // Verifies the type is closed: the class is final, so no subclass answers selectsAll its own way, and no
        // constructor is accessible, so the factories are the only way to a selection.
        assertTrue(Modifier.isFinal(SensitiveFields.class.getModifiers()));
        for (Constructor<?> constructor : SensitiveFields.class.getDeclaredConstructors()) {
            assertTrue(Modifier.isPrivate(constructor.getModifiers()), constructor.toString());
        }
    }

    @Test
    void itsTextNamesTheSelection() {
        // Verifies a log line that prints a selection says which one it is.
        assertTrue(SensitiveFields.none().toString().contains("none"));
        assertTrue(SensitiveFields.all().toString().contains("all"));
    }
}
