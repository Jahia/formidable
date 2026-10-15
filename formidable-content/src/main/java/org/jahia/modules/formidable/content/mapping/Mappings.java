package org.jahia.modules.formidable.content.mapping;

import java.util.ArrayList;
import java.util.List;

/**
 * Reads the multi-valued settings of the action whose lines read {@code left=right}: {@code formField=property}
 * (field and file mappings, file properties) and {@code property=value} (fixed values). Pure and stateless.
 *
 * <p>A line is split at its <em>first</em> {@code =}, so a fixed value may itself contain one. Both sides are trimmed;
 * blank lines are ignored; a value that holds several lines (a text area pasted into a multi-valued property) counts
 * as several. A line without {@code =}, or with an empty side, is a configuration error: it is better to refuse the
 * submission than to run a mapping the author did not mean.</p>
 */
public final class Mappings {

    /**
     * One parsed line.
     *
     * @param left  what stands before the first {@code =}, trimmed, never empty
     * @param right what stands after it, trimmed, never empty
     */
    public record Entry(String left, String right) {
    }

    private Mappings() {
    }

    /**
     * Parses the lines of a setting.
     *
     * @param setting the setting's name, for the error message
     * @param lines   the values of the multi-valued property, may be {@code null}
     * @return the entries in order, empty when there is no line
     * @throws ConfigException on a line that is not {@code left=right}
     */
    public static List<Entry> parse(String setting, List<String> lines) throws ConfigException {
        List<Entry> entries = new ArrayList<>();
        for (String line : flatten(lines)) {
            int equals = line.indexOf('=');
            String left = equals < 0 ? "" : line.substring(0, equals).trim();
            String right = equals < 0 ? "" : line.substring(equals + 1).trim();
            if (left.isEmpty() || right.isEmpty()) {
                throw new ConfigException("Setting '" + setting + "': the line '" + line + "' is not 'left=right'");
            }
            entries.add(new Entry(left, right));
        }
        return entries;
    }

    /**
     * Splits every value on line breaks and drops the blank ones.
     *
     * @param values the raw values, may be {@code null}
     * @return the trimmed, non-blank lines in order
     */
    public static List<String> flatten(List<String> values) {
        List<String> lines = new ArrayList<>();
        if (values == null) {
            return lines;
        }
        for (String value : values) {
            if (value == null) {
                continue;
            }
            for (String line : value.split("\\R")) {
                if (!line.isBlank()) {
                    lines.add(line.trim());
                }
            }
        }
        return lines;
    }
}
