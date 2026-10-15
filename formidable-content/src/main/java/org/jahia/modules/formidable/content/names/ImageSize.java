package org.jahia.modules.formidable.content.names;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.ByteArrayInputStream;
import java.util.Iterator;
import java.util.Optional;

/**
 * The width and height of an image, read from its header and never decoded: a small file that declares a huge canvas
 * costs nothing. Only the JDK's own readers (PNG, JPEG, GIF, BMP) are used, so a reader registered by another bundle
 * that has since been refreshed is never called. Stateless and thread-safe.
 */
public final class ImageSize {

    private static final String JDK_READERS = "com.sun.imageio.plugins.";

    /**
     * @param width  pixels
     * @param height pixels
     */
    public record Dimensions(int width, int height) {
    }

    private ImageSize() {
    }

    /**
     * @param data     the file's bytes
     * @param mimeType the detected MIME type; anything but {@code image/*} answers empty without reading
     * @return the size, or empty when the type is not an image, the JDK cannot read it, or its header is damaged
     */
    public static Optional<Dimensions> of(byte[] data, String mimeType) {
        if (data == null || data.length == 0 || mimeType == null || !mimeType.toLowerCase(java.util.Locale.ROOT).startsWith("image/")) {
            return Optional.empty();
        }
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(data))) {
            if (input == null) {
                return Optional.empty();
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            while (readers.hasNext()) {
                ImageReader reader = readers.next();
                if (!reader.getClass().getName().startsWith(JDK_READERS)) {
                    reader.dispose();
                    continue;
                }
                try {
                    reader.setInput(input, true, true);
                    int width = reader.getWidth(0);
                    int height = reader.getHeight(0);
                    return width > 0 && height > 0 ? Optional.of(new Dimensions(width, height)) : Optional.empty();
                } finally {
                    reader.dispose();
                }
            }
            return Optional.empty();
        } catch (Exception | LinkageError e) {
            // a damaged header, or a reader that cannot be loaded: the file is stored without its size
            return Optional.empty();
        }
    }
}
