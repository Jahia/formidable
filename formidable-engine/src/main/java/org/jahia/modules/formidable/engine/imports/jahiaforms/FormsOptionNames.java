package org.jahia.modules.formidable.engine.imports.jahiaforms;

/**
 * The names of the option nodes Forms writes under its definitions, rules and actions, in one place:
 * what the converters read. Each name is read from the sources of {@code forms-core} 3.x and
 * {@code forms-extended-inputs}: the {@code .wzd} of each definition under
 * {@code src/main/resources/fcnt_*} lists its option keys, and the design views bind them.
 */
final class FormsOptionNames {

    // field definitions
    static final String PLACEHOLDER = "placeholder";
    static final String HELP_TEXT = "helptext";
    static final String ROWS = "rows";
    /** The default value of a hidden field ({@code hiddenDefinition}). */
    static final String VALUE = "value";
    /** The texts of a switch, per language ({@code switchDefinition.wzd}, {@code propertiesI18n}). */
    static final String TEXT_ON = "textOn";
    static final String TEXT_OFF = "textOff";
    /** The highest rating ({@code ratingDefinition.wzd}, {@code max "8"}). */
    static final String MAX = "max";
    static final String MIN = "min";
    /** The texts an accept-terms box submits and shows ({@code acceptTermCheckboxDefinition.wzd}). */
    static final String YES = "yes";
    static final String NO = "no";
    static final String TERMS_LABEL = "termsLabel";
    static final String LINK = "link";
    /** The placeholder of the terms label that the box turns into the link to the terms. */
    static final String LICENSE_PLACEHOLDER = "{LICENSE}";

    // validation rules (the option names are the designView keys of forms-core_en.properties)
    static final String REGEX = "regex";
    /** The file types a {@code fcnt:fileValidation} allows: a JSON list of {@code {key, value, selected}}. */
    static final String FILE_TYPE = "filetype";
    /** The number of files a {@code fcnt:fileNumberValidation} allows. */
    static final String FILE_NUMBER = "filenumber";

    // actions
    static final String TO = "to";
    static final String FROM = "from";
    static final String CC = "cc";
    static final String BCC = "bcc";
    static final String SUBJECT = "subject";
    /** The target of both redirect actions ({@code RedirectToUrlAction}, {@code RedirectToAPageAction}). */
    static final String REDIRECT_TO = "redirectto";

    private FormsOptionNames() {
    }
}
