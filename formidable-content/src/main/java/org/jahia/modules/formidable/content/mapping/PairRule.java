package org.jahia.modules.formidable.content.mapping;

import org.jahia.modules.formidable.engine.api.SubmittedFile;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * "Every file of this kind needs a companion file": the rule {@code dependentField<-field:mimePattern}, for instance
 * {@code captions<-media:video/*} (every video uploaded in {@code media} needs a file in {@code captions}). The rule
 * counts: when {@code field} holds <em>n</em> files whose detected MIME type matches the pattern, {@code dependentField}
 * must hold at least <em>n</em> files. Pure and stateless.
 *
 * <p>The pattern is {@code type/subtype}, {@code type/*}, or {@code *} / {@code *}{@code /*} for any file. It is matched
 * against the engine's <em>detected</em> type, not the one the browser claimed, case-insensitively and without the
 * parameters ({@code video/mp4; codecs=avc1} is {@code video/mp4}).</p>
 *
 * @param dependentField the field that must hold the companion files
 * @param field          the field whose files need one
 * @param mimePattern    which of its files need one
 */
public record PairRule(String dependentField, String field, String mimePattern) {

    private static final Pattern PATTERN = Pattern.compile("(\\*|\\*/\\*|[a-z0-9!#$&^_.+-]+/(\\*|[a-z0-9!#$&^_.+-]+))");

    /**
     * Parses the lines of {@code requiredPairs}.
     *
     * @param lines the values of the setting, may be {@code null}
     * @return the rules in order
     * @throws ConfigException on a line that is not {@code dependentField<-field:mimePattern}, a pattern that is not a
     *                         MIME type, or a field that is its own companion
     */
    public static List<PairRule> parse(List<String> lines) throws ConfigException {
        List<PairRule> rules = new ArrayList<>();
        for (String line : Mappings.flatten(lines)) {
            int arrow = line.indexOf("<-");
            int colon = arrow < 0 ? -1 : line.indexOf(':', arrow + 2);
            if (arrow <= 0 || colon < 0) {
                throw new ConfigException("Setting 'requiredPairs': the line '" + line + "' is not 'dependentField<-field:mimePattern'");
            }
            String dependent = line.substring(0, arrow).trim();
            String field = line.substring(arrow + 2, colon).trim();
            String pattern = line.substring(colon + 1).trim().toLowerCase(Locale.ROOT);
            if (dependent.isEmpty() || field.isEmpty() || !PATTERN.matcher(pattern).matches()) {
                throw new ConfigException("Setting 'requiredPairs': the line '" + line + "' has an empty field or a pattern that is not a MIME type");
            }
            if (dependent.equals(field)) {
                throw new ConfigException("Setting 'requiredPairs': the line '" + line + "' makes a field its own companion");
            }
            rules.add(new PairRule(dependent, field, pattern));
        }
        return rules;
    }

    /**
     * @param mimeType the detected MIME type of a file, may be {@code null}
     * @return whether a file of that type needs a companion file
     */
    public boolean matches(String mimeType) {
        if (mimeType == null || mimeType.isBlank()) {
            return false;
        }
        if (mimePattern.equals("*") || mimePattern.equals("*/*")) {
            return true;
        }
        String type = mimeType.toLowerCase(Locale.ROOT);
        int parameters = type.indexOf(';');
        type = (parameters < 0 ? type : type.substring(0, parameters)).trim();
        if (mimePattern.endsWith("/*")) {
            return type.startsWith(mimePattern.substring(0, mimePattern.length() - 1));
        }
        return type.equals(mimePattern);
    }

    /**
     * @param files what was uploaded
     * @return whether the files break the rule: more files of {@code field} match the pattern than {@code dependentField}
     *         holds files
     */
    public boolean isBrokenBy(List<SubmittedFile> files) {
        long needing = files.stream().filter(file -> field.equals(file.fieldName()) && matches(file.mimeType())).count();
        if (needing == 0) {
            return false;
        }
        long companions = files.stream().filter(file -> dependentField.equals(file.fieldName())).count();
        return companions < needing;
    }

    /**
     * @param rules the configured rules
     * @param files what was uploaded
     * @return the first rule the files break, empty when every rule holds
     */
    public static Optional<PairRule> firstBroken(List<PairRule> rules, List<SubmittedFile> files) {
        return rules.stream().filter(rule -> rule.isBrokenBy(files)).findFirst();
    }
}
