package org.jahia.modules.formidable.jexperience.engine;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The CND clauses the Java depends on, pinned: no test environment installs this module yet, so
 * the three load-bearing declarations — the mapping mixin attaching to the marker, the
 * dependent-property re-query, the form mixin inheriting the switch-less marker — would otherwise
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
        assertEquals("extends = " + FieldShapes.MAPPABLE_MARKER, lineStartingWith(mixin, "extends"));
        String property = lineStartingWith(mixin, "- " + ProfilePropertiesChoiceListInitializer.PROPERTY + " ");
        assertTrue(property.contains("choicelist[" + ProfilePropertiesChoiceListInitializer.KEY + ",dependentProperties='" + FieldShapes.MULTIPLE_PROPERTY + "," + ProfilePropertiesChoiceListInitializer.OPTIONS_PROPERTY + "," + ProfilePropertiesChoiceListInitializer.OPTIONS_MODE_PROPERTY + "']"), property);
    }

    @Test
    void theFormMixinInheritsTheSwitchLessMarkerAndExtendsTheForm() throws Exception {
        // Verifies the clause that removes the fieldset's enable switch, which jcontent drops only for a
        // template mixin, and the clause that attaches the identifier to the form type.
        List<String> lines = cnd();
        assertTrue(lines.stream().anyMatch(line -> line.strip().startsWith("<jmix = 'http://www.jahia.org/jahia/mix/1.0'>")), "the jmix namespace is declared");
        List<String> mixin = declarationOf(lines, FormIdentifier.FORM_MIXIN);
        assertEquals("[" + FormIdentifier.FORM_MIXIN + "] > jmix:templateMixin mixin", mixin.get(0));
        assertEquals("extends = " + FormIdentifierListener.FORM_NODE_TYPE, lineStartingWith(mixin, "extends"));
        assertTrue(lineStartingWith(mixin, "- " + FormIdentifier.PROPERTY + " ").contains("(string)"));
    }
}
