package org.jahia.test.modules.formidable.quarantine.stub;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the marker of a text file into what a stub check answers. The first line that is a marker, once trimmed,
 * wins:
 * <ul>
 * <li>{@code VERDICT:PASS}, {@code VERDICT:FAIL}, {@code INCONCLUSIVE}</li>
 * <li>{@code PENDING:<n>}, with {@code n} from 0 to {@value #MAX_PENDING_POLLS}</li>
 * <li>{@code DOWN}</li>
 * </ul>
 * The stubs apply to text files alone ({@code text/plain}), and read at most {@value #MAX_BYTES} bytes.
 */
public final class StubVerdicts {

    /** The one MIME type the stubs apply to. */
    public static final String TEXT_PLAIN = "text/plain";

    /** The count of bytes of a file that the stub reads to find the marker. */
    public static final int MAX_BYTES = 64 * 1024;

    /** The largest count of pending polls a marker may ask. */
    public static final int MAX_PENDING_POLLS = 1000;

    private static final Pattern PENDING = Pattern.compile("PENDING:(\\d{1,4})");
    private static final Pattern LINES = Pattern.compile("\\R");

    private StubVerdicts() {
    }

    /**
     * @param mimeType the MIME type of a file, with or without parameters
     * @return true for {@code text/plain}, in any case and with any parameter such as a charset
     */
    public static boolean appliesTo(String mimeType) {
        if (mimeType == null) {
            return false;
        }
        int parameters = mimeType.indexOf(';');
        String type = parameters < 0 ? mimeType : mimeType.substring(0, parameters);
        return TEXT_PLAIN.equals(type.trim().toLowerCase(Locale.ROOT));
    }

    /**
     * @param in the bytes of the file; read up to {@value #MAX_BYTES} bytes, and not closed
     * @return the verdict of the first marker, or {@link StubVerdict.Unmarked}
     */
    public static StubVerdict read(InputStream in) throws IOException {
        byte[] head = in.readNBytes(MAX_BYTES);
        return parse(new String(head, StandardCharsets.UTF_8));
    }

    /**
     * @param text the text of the file
     * @return the verdict of the first line that is a marker, or {@link StubVerdict.Unmarked}
     */
    public static StubVerdict parse(String text) {
        if (text == null) {
            return new StubVerdict.Unmarked();
        }
        for (String line : LINES.split(text)) {
            StubVerdict verdict = parseLine(line.trim());
            if (verdict != null) {
                return verdict;
            }
        }
        return new StubVerdict.Unmarked();
    }

    private static StubVerdict parseLine(String line) {
        switch (line) {
            case "VERDICT:PASS":
                return new StubVerdict.Pass();
            case "VERDICT:FAIL":
                return new StubVerdict.Fail();
            case "INCONCLUSIVE":
                return new StubVerdict.Inconclusive();
            case "DOWN":
                return new StubVerdict.Down();
            default:
                return parsePending(line);
        }
    }

    private static StubVerdict parsePending(String line) {
        Matcher matcher = PENDING.matcher(line);
        if (!matcher.matches()) {
            return null;
        }
        int polls = Integer.parseInt(matcher.group(1));
        return polls <= MAX_PENDING_POLLS ? new StubVerdict.Pending(polls) : null;
    }
}
