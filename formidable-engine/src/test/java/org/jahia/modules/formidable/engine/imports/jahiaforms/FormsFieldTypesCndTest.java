package org.jahia.modules.formidable.engine.imports.jahiaforms;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link FormsFieldTypes#accepted} copies what the CNDs of formidable-elements and formidable-extended-inputs
 * declare: this test reads those CNDs from the repository checkout, resolves the supertypes and mixins of
 * each target type, and refuses an entry of the table that the type does not declare — so that a property
 * renamed, moved or removed in a {@code definition.cnd} is caught here, not by a writer run.
 */
class FormsFieldTypesCndTest {

    private static final Path ENGINE = Path.of("src/main/resources/META-INF/definitions.cnd");
    private static final List<Path> MODULES = List.of(Path.of("../formidable-elements"), Path.of("../formidable-extended-inputs"));
    private static final Pattern HEADER = Pattern.compile("^\\[([A-Za-z0-9_:]+)\\]\\s*(?:>\\s*([^\\n]*?))?\\s*(mixin)?\\s*$");
    private static final Pattern PROPERTY = Pattern.compile("^\\s*-\\s*([A-Za-z0-9_:]+)\\s*\\(");

    /** A type as its CND declares it: the names it lists after {@code >}, and its own properties. */
    private record Declared(List<String> supertypes, Set<String> properties) {
    }

    @Test
    void everyAcceptedPropertyIsDeclaredOnItsType() throws IOException {
        Map<String, Declared> types = parseAll();
        List<String> targets = List.of(FormsFieldTypes.INPUT_TEXT, FormsFieldTypes.INPUT_EMAIL, FormsFieldTypes.TEXTAREA,
                FormsFieldTypes.INPUT_NUMBER, FormsFieldTypes.INPUT_HIDDEN, FormsFieldTypes.INPUT_DATE, FormsFieldTypes.INPUT_FILE,
                FormsFieldTypes.SELECT, FormsFieldTypes.RADIO, FormsFieldTypes.CHECKBOX,
                FormsFieldTypes.SWITCH, FormsFieldTypes.RATING, FormsFieldTypes.CONSENT);
        for (String type : targets) {
            assertTrue(types.containsKey(type), type + " is declared in no CND of the repository");
            Set<String> declared = propertiesOf(type, types, new HashSet<>());
            Set<String> accepted = FormsFieldTypes.accepted(type);
            assertFalse(accepted.isEmpty(), type + " has no accepted properties");
            List<String> undeclared = accepted.stream().filter(p -> !declared.contains(p)).sorted().toList();
            assertEquals(List.of(), undeclared, type + " accepts settings its CND does not declare");
        }
    }

    @Test
    void theParserReadsATypeItsSupertypesAndItsProperties() throws IOException {
        Map<String, Declared> types = parseAll();
        Declared select = types.get(FormsFieldTypes.SELECT);
        assertTrue(select.supertypes().contains("fmdbmix:choiceField"), select.supertypes().toString());
        assertTrue(select.properties().contains("optionsEmptyLabel"), select.properties().toString());
        // a message slot reaches the type through its validation-messages mixin
        assertTrue(propertiesOf(FormsFieldTypes.INPUT_TEXT, types, new HashSet<>()).contains("msgTooShort"));
        assertTrue(propertiesOf(FormsFieldTypes.INPUT_NUMBER, types, new HashSet<>()).contains("msgRangeOverflow"));
    }

    private static Set<String> propertiesOf(String type, Map<String, Declared> types, Set<String> seen) {
        Set<String> properties = new HashSet<>();
        Declared declared = types.get(type);
        if (declared == null || !seen.add(type)) {
            return properties;
        }
        properties.addAll(declared.properties());
        for (String supertype : declared.supertypes()) {
            properties.addAll(propertiesOf(supertype, types, seen));
        }
        return properties;
    }

    private static Map<String, Declared> parseAll() throws IOException {
        Map<String, Declared> types = new LinkedHashMap<>();
        List<Path> files = new ArrayList<>();
        files.add(ENGINE);
        for (Path module : MODULES) {
            Path settings = module.resolve("settings/definitions.cnd");
            if (Files.exists(settings)) {
                files.add(settings);
            }
            try (Stream<Path> walk = Files.walk(module.resolve("src/components"))) {
                walk.filter(p -> p.getFileName().toString().equals("definition.cnd")).forEach(files::add);
            }
        }
        for (Path file : files) {
            parse(Files.readAllLines(file), types);
        }
        return types;
    }

    private static void parse(List<String> lines, Map<String, Declared> types) {
        String current = null;
        for (String raw : lines) {
            String line = raw.replaceAll("//.*$", "");
            Matcher header = HEADER.matcher(line);
            if (header.matches()) {
                current = header.group(1);
                List<String> supertypes = new ArrayList<>();
                if (header.group(2) != null) {
                    for (String name : header.group(2).split(",")) {
                        if (!name.isBlank()) {
                            supertypes.add(name.trim());
                        }
                    }
                }
                types.computeIfAbsent(current, t -> new Declared(new ArrayList<>(), new HashSet<>())).supertypes().addAll(supertypes);
                continue;
            }
            Matcher property = PROPERTY.matcher(line);
            if (current != null && property.find()) {
                types.get(current).properties().add(property.group(1));
            }
        }
    }
}
