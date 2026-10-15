package org.jahia.modules.formidable.content.names;

import java.text.Normalizer;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Builds the JCR node names of what the action creates: the content node from its title, a file from its original name.
 * A name is lower case ASCII (letters, digits, hyphens), never longer than {@link #MAX_LENGTH}, and made unique among
 * its siblings with a numeric suffix. Pure and stateless; the "is this name taken" question is a predicate the caller
 * answers from the folder.
 *
 * <p>The text is the visitor's, so what is kept is a whitelist ({@code [a-z0-9]}), not what a blacklist forgot to
 * remove.</p>
 */
public final class NodeNames {

    /** The longest name produced, extension included. */
    public static final int MAX_LENGTH = 80;

    private static final Pattern COMBINING_MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern NOT_ALNUM = Pattern.compile("[^a-z0-9]+");
    private static final int MAX_EXTENSION = 10;
    private static final int SEQUENTIAL_ATTEMPTS = 99;

    private NodeNames() {
    }

    /**
     * @param text     a title or a name, may be {@code null}
     * @param fallback used when nothing of the text survives
     * @return a lower case ASCII slug of at most {@link #MAX_LENGTH} characters
     */
    public static String slug(String text, String fallback) {
        return slug(text, fallback, MAX_LENGTH);
    }

    private static String slug(String text, String fallback, int max) {
        String base = text == null ? "" : text;
        base = COMBINING_MARKS.matcher(Normalizer.normalize(base, Normalizer.Form.NFD)).replaceAll("");
        base = NOT_ALNUM.matcher(base.toLowerCase(Locale.ROOT)).replaceAll("-");
        base = trimHyphens(base);
        if (base.length() > max) {
            base = trimHyphens(base.substring(0, max));
        }
        return base.isEmpty() ? fallback : base;
    }

    /**
     * The sanitised name of an uploaded file: the slug of its base name, then its extension in lower case when it is a
     * plain one ({@code My Video (final).MP4} gives {@code my-video-final.mp4}).
     *
     * @param original the name the visitor's browser sent, may be {@code null}
     * @return a node name of at most {@link #MAX_LENGTH} characters, never empty
     */
    public static String fileName(String original) {
        String name = original == null ? "" : original;
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        name = name.substring(slash + 1);
        int dot = name.lastIndexOf('.');
        String extension = "";
        String stem = name;
        if (dot > 0) {
            // an extension that is not a short plain word is part of the name, not an extension
            String candidate = slug(name.substring(dot + 1), "", MAX_LENGTH).replace("-", "");
            if (!candidate.isEmpty() && candidate.length() <= MAX_EXTENSION) {
                extension = "." + candidate;
                stem = name.substring(0, dot);
            }
        }
        return slug(stem, "file", MAX_LENGTH - extension.length()) + extension;
    }

    /**
     * Makes a name unique among its siblings: the name itself when free, then {@code name-1}, {@code name-2}..., and
     * past {@value #SEQUENTIAL_ATTEMPTS} attempts a random suffix. The suffix goes before the extension of a file name.
     *
     * @param name  a name from {@link #slug} or {@link #fileName}
     * @param taken whether a sibling already has a given name
     * @return a name that was free when asked
     */
    public static String unique(String name, Predicate<String> taken) {
        if (!taken.test(name)) {
            return name;
        }
        int dot = name.lastIndexOf('.');
        String stem = dot > 0 ? name.substring(0, dot) : name;
        String extension = dot > 0 ? name.substring(dot) : "";
        for (int i = 1; i <= SEQUENTIAL_ATTEMPTS; i++) {
            String candidate = stem + "-" + i + extension;
            if (!taken.test(candidate)) {
                return candidate;
            }
        }
        return randomized(name);
    }

    /**
     * The same name with a random suffix, for the rare case where two submissions took the same free name at once.
     *
     * @param name a name from {@link #slug} or {@link #fileName}
     * @return the name with {@code -<8 hex digits>} before its extension
     */
    public static String randomized(String name) {
        int dot = name.lastIndexOf('.');
        String stem = dot > 0 ? name.substring(0, dot) : name;
        String extension = dot > 0 ? name.substring(dot) : "";
        return stem + "-" + String.format("%08x", ThreadLocalRandom.current().nextInt()) + extension;
    }

    private static String trimHyphens(String text) {
        int start = 0;
        int end = text.length();
        while (start < end && text.charAt(start) == '-') {
            start++;
        }
        while (end > start && text.charAt(end - 1) == '-') {
            end--;
        }
        return text.substring(start, end);
    }
}
