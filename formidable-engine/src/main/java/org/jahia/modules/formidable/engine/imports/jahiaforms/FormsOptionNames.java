package org.jahia.modules.formidable.engine.imports.jahiaforms;

/**
 * The names of the option nodes Forms writes under its definitions, rules and actions, in one place:
 * what the converters read. A name here is a fact of {@code forms-core} 3.x.
 */
final class FormsOptionNames {

    // field definitions: placeholder, helptext and rows are in the sample export; the next ones are the
    // names Forms gives the settings of its switch, hidden and rating definitions, to confirm against an
    // export that holds such fields (the sample holds none) before the import of those fields is relied on
    static final String PLACEHOLDER = "placeholder";
    static final String HELP_TEXT = "helptext";
    static final String ROWS = "rows";
    static final String VALUE = "value";
    static final String ON_LABEL = "onLabel";
    static final String OFF_LABEL = "offLabel";
    static final String MAX = "max";
    static final String MIN = "min";

    // validation rules (the option names are the designView keys of forms-core_en.properties)
    static final String REGEX = "regex";
    /** The file types a {@code fcnt:fileValidation} allows. */
    static final String FILE_TYPE = "type";
    /** The number of files a {@code fcnt:fileNumberValidation} allows. */
    static final String FILE_NUMBER = "fileNumber";

    // actions
    static final String TO = "to";
    static final String FROM = "from";
    static final String CC = "cc";
    static final String BCC = "bcc";
    static final String SUBJECT = "subject";
    static final String REDIRECT_TO = "redirectto";
    static final String URL = "url";

    private FormsOptionNames() {
    }
}
