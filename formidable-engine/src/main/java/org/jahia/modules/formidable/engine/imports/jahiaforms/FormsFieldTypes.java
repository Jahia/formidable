package org.jahia.modules.formidable.engine.imports.jahiaforms;

import java.util.Map;
import java.util.function.Predicate;

/**
 * The Formidable type each Forms field definition becomes (docs/architecture/forms-import.md, "The
 * fields"). The {@code fmdbext:*} types of formidable-extended-inputs are used when the repository
 * registers them, else the formidable-elements type of the row, with a word in the report. This is the
 * one place of the engine that names concrete field types: the import creates them, it does not read them.
 */
final class FormsFieldTypes {

    static final String INPUT_TEXT = "fmdb:inputText";
    static final String INPUT_EMAIL = "fmdb:inputEmail";
    static final String TEXTAREA = "fmdb:textarea";
    static final String INPUT_NUMBER = "fmdb:inputNumber";
    static final String INPUT_HIDDEN = "fmdb:inputHidden";
    static final String INPUT_DATE = "fmdb:inputDate";
    static final String INPUT_FILE = "fmdb:inputFile";
    static final String SELECT = "fmdb:select";
    static final String RADIO = "fmdb:radio";
    static final String CHECKBOX = "fmdb:checkbox";
    static final String SWITCH = "fmdbext:switch";
    static final String RATING = "fmdbext:rating";
    static final String CONSENT = "fmdbext:consent";
    private static final String CREATED_AS = "created as ";

    /**
     * @param nodeType the type to create
     * @param note what the report says of the choice, or null when the type is the natural equivalent
     */
    record Mapping(String nodeType, String note) {
        boolean is(String type) {
            return type.equals(nodeType);
        }
    }

    private static final Map<String, String> PLAIN = Map.ofEntries(
            Map.entry("input", INPUT_TEXT),
            Map.entry("email", INPUT_EMAIL),
            Map.entry("textArea", TEXTAREA),
            Map.entry("number", INPUT_NUMBER),
            Map.entry("hidden", INPUT_HIDDEN),
            Map.entry("selectBasic", SELECT),
            Map.entry("selectMultiple", SELECT),
            Map.entry("multipleRadios", RADIO),
            Map.entry("multipleRadiosInline", RADIO),
            Map.entry("multipleCheckBoxes", CHECKBOX),
            Map.entry("multipleCheckBoxesInline", CHECKBOX),
            Map.entry("datePicker", INPUT_DATE),
            Map.entry("simpleDate", INPUT_DATE),
            Map.entry("countryList", SELECT),
            Map.entry("fileUpload", INPUT_FILE),
            Map.entry("imageCheckbox", CHECKBOX));

    /** Forms kind to the extended type and the elements fallback. */
    private record Extended(String preferred, String fallback) {
    }

    private static final Map<String, Extended> EXTENDED = Map.of(
            "switch", new Extended(SWITCH, RADIO),
            "rating", new Extended(RATING, INPUT_NUMBER),
            "acceptTermCheckbox", new Extended(CONSENT, CHECKBOX));

    private static final Map<String, String> NO_EQUIVALENT = Map.of(
            "phone", INPUT_TEXT,
            "matrixRadios", TEXTAREA,
            "matrixCheckBoxes", TEXTAREA);

    private FormsFieldTypes() {
    }

    /** Null when the field is not recreated at all: a password, a content display, the buttons and fieldset marks. */
    static Mapping of(FormsField field, Predicate<String> registered) {
        String kind = field.kind();
        if (!isRecreated(kind)) {
            return null;
        }
        if (PLAIN.containsKey(kind)) {
            return new Mapping(PLAIN.get(kind), null);
        }
        Extended extended = EXTENDED.get(kind);
        if (extended != null) {
            return registered.test(extended.preferred())
                    ? new Mapping(extended.preferred(), null)
                    : new Mapping(extended.fallback(), CREATED_AS + extended.fallback()
                    + " because " + extended.preferred() + " is not deployed on this instance");
        }
        String nearest = NO_EQUIVALENT.get(kind);
        if (nearest != null) {
            return new Mapping(nearest, CREATED_AS + nearest + " because Formidable has no " + kind + " field");
        }
        return new Mapping(INPUT_TEXT, CREATED_AS + INPUT_TEXT + " because Formidable has no equivalent of "
                + field.type());
    }

    /** The kinds the import turns into something else than a field, or into nothing. */
    static boolean isRecreated(String kind) {
        return !isLayout(kind) && !"password".equals(kind) && !"contentDisplay".equals(kind);
    }

    static boolean isLayout(String kind) {
        return isButton(kind) || isFieldsetStart(kind) || isFieldsetEnd(kind);
    }

    static boolean isButton(String kind) {
        return "button".equals(kind) || "buttonTriple".equals(kind);
    }

    static boolean isFieldsetStart(String kind) {
        return "fieldsetStart".equals(kind);
    }

    static boolean isFieldsetEnd(String kind) {
        return "fieldsetEnd".equals(kind);
    }

    static boolean isMultipleChoice(String kind) {
        return "selectMultiple".equals(kind) || kind.startsWith("multipleCheckBoxes") || "imageCheckbox".equals(kind);
    }
}
