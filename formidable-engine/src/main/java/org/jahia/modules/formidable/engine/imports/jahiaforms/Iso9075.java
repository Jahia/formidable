package org.jahia.modules.formidable.engine.imports.jahiaforms;

/**
 * Decodes the ISO 9075 escapes of a Jahia document-view export, where {@code _x0020_} is a space and
 * {@code _x0030_6} the node name {@code 06}. Element names carry them, and so do the values of a
 * multi-valued attribute, which separates its values with a space that is never escaped, which is why a
 * value is split before it is decoded; a single-valued attribute is written as it is. The Jackrabbit
 * implementation is not on the engine's compile path.
 */
final class Iso9075 {

    private Iso9075() {
    }

    static String decode(String encoded) {
        if (encoded == null || !encoded.contains("_x")) {
            return encoded;
        }
        StringBuilder out = new StringBuilder(encoded.length());
        int i = 0;
        while (i < encoded.length()) {
            if (encoded.charAt(i) == '_' && isEscape(encoded, i)) {
                out.append((char) Integer.parseInt(encoded, i + 2, i + 6, 16));
                i += 7;
            } else {
                out.append(encoded.charAt(i));
                i++;
            }
        }
        return out.toString();
    }

    private static boolean isEscape(String s, int at) {
        // _xHHHH_ : seven characters
        if (at + 7 > s.length() || s.charAt(at + 1) != 'x' || s.charAt(at + 6) != '_') {
            return false;
        }
        for (int k = at + 2; k < at + 6; k++) {
            if (Character.digit(s.charAt(k), 16) < 0) {
                return false;
            }
        }
        return true;
    }
}
