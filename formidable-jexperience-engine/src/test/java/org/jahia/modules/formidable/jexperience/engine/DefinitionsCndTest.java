package org.jahia.modules.formidable.jexperience.engine;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.jahia.modules.formidable.engine.api.FmdbMixin;
import org.jahia.modules.formidable.engine.api.FmdbProperty;
import org.jahia.modules.formidable.jexperience.engine.model.JxpMixin;
import org.jahia.modules.formidable.jexperience.engine.model.JxpProperty;
import org.jahia.modules.formidable.jexperience.engine.choicelist.ProfilePropertiesChoiceListInitializer;
import org.jahia.modules.formidable.jexperience.engine.field.FieldShapes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The CND clauses the Java depends on, pinned: no test environment installs this module yet, so
 * the three load-bearing declarations — the mapping mixin attaching to the marker, the
 * dependent-property re-query, the sensitive flag on a switch-less mixin — would otherwise
 * be guarded by nothing. The reader is a line-level parser of the module's own file, not Jahia's
 * (which needs a registry); it knows type headers, {@code extends} lines and property lines.
 */
class DefinitionsCndTest {

    private static List<String> cnd() throws IOException {
        try (InputStream in = DefinitionsCndTest.class.getResourceAsStream("/META-INF/definitions.cnd")) {
            assertNotNull(in, "META-INF/definitions.cnd is on the classpath");
            return List.of(new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n"));
        }
    }

    /** The editor form of the prefill fieldset, where the option carries what the CND cannot. */
    private static String editorForm() throws IOException {
        try (InputStream in = DefinitionsCndTest.class.getResourceAsStream(
                "/META-INF/jahia-content-editor-forms/forms/fmdbmix_jExperiencePrefill.json")) {
            assertNotNull(in, "the prefill editor form is on the classpath");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /** The lines of one type declaration, header included, up to the next header or the end. */
    private static List<String> declarationOf(List<String> lines, String typeName) {
        List<String> block = new ArrayList<>();
        for (String raw : lines) {
            String line = raw.strip();
            if (line.startsWith("[")) {
                if (!block.isEmpty()) {
                    break;
                }
                if (line.startsWith("[" + typeName + "]")) {
                    block.add(line);
                }
            } else if (!block.isEmpty() && !line.isEmpty() && !line.startsWith("//")) {
                block.add(line);
            }
        }
        assertTrue(!block.isEmpty(), "declaration of " + typeName);
        return block;
    }

    private static String lineStartingWith(List<String> block, String prefix) {
        return block.stream().filter(line -> line.startsWith(prefix)).findFirst()
                .orElseThrow(() -> new AssertionError("no line starting with '" + prefix + "' in " + block));
    }

    @Test
    void theMappingMixinAttachesToTheMarkerAndReQueriesOnMultiple() throws Exception {
        // Verifies the two clauses the editor section lives on: `extends` to the engine's marker (what
        // puts the section on every mappable field) and dependentProperties='multiple' (what makes the
        // Content Editor ask the list again when the author switches the cardinality).
        List<String> mixin = declarationOf(cnd(), "fmdbmix:jExperienceProfileMapping");
        assertEquals("[fmdbmix:jExperienceProfileMapping] mixin", mixin.get(0), "no supertype: mappability is the type's claim, not the mixin's");
        assertEquals("extends = " + FmdbMixin.PROFILE_MAPPABLE_FIELD, lineStartingWith(mixin, "extends"));
        String property = lineStartingWith(mixin, "- " + JxpProperty.PROFILE_PROPERTY + " ");
        assertTrue(property.contains("choicelist[" + ProfilePropertiesChoiceListInitializer.KEY + ",dependentProperties='"
                + FieldShapes.MULTIPLE_PROPERTY + "," + FmdbProperty.OPTIONS + ","
                + FmdbProperty.OPTIONS_MODE + "," + JxpProperty.SENSITIVE + "']"), property);
    }

    @Test
    void theSensitiveMixinIsSwitchLessAndItsFlagDrivesTheDropdown() throws Exception {
        // Verifies the three clauses the sensitive flag lives on. It reaches every mappable field through the
        // marker. jcontent renders it without an enable switch, which only a jmix:templateMixin fieldset loses,
        // and the flag has to be answerable before the mapping fieldset is switched on. The mapping's choicelist
        // names it, which is what empties that dropdown the moment the author ticks the box.
        List<String> lines = cnd();
        assertTrue(lines.stream().anyMatch(line -> line.strip().startsWith("<jmix = 'http://www.jahia.org/jahia/mix/1.0'>")), "the jmix namespace is declared");
        List<String> mixin = declarationOf(lines, JxpMixin.SENSITIVE_FIELD);
        assertEquals("[" + JxpMixin.SENSITIVE_FIELD + "] > jmix:templateMixin mixin", mixin.get(0));
        assertEquals("extends = " + FmdbMixin.PROFILE_MAPPABLE_FIELD, lineStartingWith(mixin, "extends"));
        assertEquals("- " + JxpProperty.SENSITIVE + " (boolean) = false autocreated indexed=no", lineStartingWith(mixin, "- " + JxpProperty.SENSITIVE + " "));
        assertTrue(lineStartingWith(declarationOf(lines, JxpMixin.MAPPING), "- " + JxpProperty.PROFILE_PROPERTY + " ")
                .contains("," + JxpProperty.SENSITIVE + "'"), "the choicelist depends on the flag");
    }

    @Test
    void thePrefillIsAMixinOfItsOwnAttachedToTheMarker() throws Exception {
        // Verifies the shape jcontent turns into a switchable fieldset: a mixin that extends the marker, no
        // supertype, holding the one option that shows only when the switch is on — what follows the write,
        // editable by default. What the write itself does (the profile's value replaces a default, a missing
        // value leaves the field alone) is the client script's rule, not a property.
        List<String> mixin = declarationOf(cnd(), JxpMixin.PREFILL);
        assertEquals("[" + JxpMixin.PREFILL + "] mixin", mixin.get(0));
        assertEquals("extends = " + FmdbMixin.PROFILE_MAPPABLE_FIELD, lineStartingWith(mixin, "extends"));
        assertEquals("- " + JxpProperty.PREFILL_THEN + " (string, choicelist[resourceBundle]) = 'editable' autocreated indexed=no < 'editable', 'readOnly', 'hidden'",
                lineStartingWith(mixin, "- " + JxpProperty.PREFILL_THEN + " "));
        assertEquals(1, mixin.stream().map(String::strip).filter(line -> line.startsWith("- ")).count(), "one option beside the switch");
    }

    @Test
    void theOptionIsRequiredByTheEditorOnly() throws Exception {
        // The editor then offers no empty entry beside "leave it editable", which would say the same thing
        // twice — while the CND stays as it was deployed: a mandatory flag ADDED to a registered type is a
        // MAJOR change to Jahia's DefinitionsBundleChecker, which cancels the deployment of the module.
        assertTrue(editorForm().contains("\"name\": \"" + JxpProperty.PREFILL_THEN + "\", \"mandatory\": true"),
                "the editor form makes the option mandatory: " + editorForm());
        assertTrue(declarationOf(cnd(), JxpMixin.PREFILL).stream().noneMatch(line -> line.contains("mandatory")),
                "no mandatory in the CND of " + JxpMixin.PREFILL);
    }

    /**
     * The module names its own model once, and both ways: every mixin and property the CND declares
     * has its constant in {@code JxpMixin} or {@code JxpProperty}, and every constant there is declared.
     * The engine's guard does this for the engine's CND; this test does it for the module's. The expected
     * side is read off the holder classes, so a constant added to one cannot escape the check.
     */
    @Test
    void theModelIsNamedOnceAndBothWays() throws Exception {
        List<String> lines = cnd().stream().map(String::strip).toList();
        Set<String> mixins = lines.stream()
                .filter(line -> line.startsWith("[") && line.matches(".*\\bmixin\\b.*"))
                .map(line -> line.substring(1, line.indexOf(']')))
                .collect(Collectors.toSet());
        assertEquals(declaredIn(JxpMixin.class), mixins);
        Set<String> properties = lines.stream()
                .filter(line -> line.startsWith("- "))
                .map(line -> line.substring(2, line.indexOf(' ', 2)))
                .collect(Collectors.toSet());
        assertEquals(declaredIn(JxpProperty.class), properties);
    }

    /** Every String constant the holder declares, read off the class so that a new one cannot escape the check. */
    private static Set<String> declaredIn(Class<?> holder) throws IllegalAccessException {
        Set<String> values = new HashSet<>();
        for (Field field : holder.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) && field.getType() == String.class) {
                values.add((String) field.get(null));
            }
        }
        return values;
    }
}
