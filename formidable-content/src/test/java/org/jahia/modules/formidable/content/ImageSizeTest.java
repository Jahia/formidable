package org.jahia.modules.formidable.content;

import org.jahia.modules.formidable.content.names.ImageSize;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImageSizeTest {

    private static byte[] image(String format, int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, format, out);
        return out.toByteArray();
    }

    @Test
    void readsTheSizeOfAPngAndAJpeg() throws IOException {
        assertEquals(new ImageSize.Dimensions(30, 20), ImageSize.of(image("png", 30, 20), "image/png").orElseThrow());
        assertEquals(new ImageSize.Dimensions(64, 48), ImageSize.of(image("jpeg", 64, 48), "image/jpeg").orElseThrow());
    }

    @Test
    void aTypeThatIsNotAnImageIsNotRead() throws IOException {
        assertTrue(ImageSize.of(image("png", 3, 3), "video/mp4").isEmpty());
        assertTrue(ImageSize.of(image("png", 3, 3), null).isEmpty());
    }

    @Test
    void aDamagedOrUnknownFileGivesNoSizeAndNoError() {
        assertTrue(ImageSize.of(new byte[]{1, 2, 3, 4}, "image/png").isEmpty());
        assertTrue(ImageSize.of(new byte[0], "image/png").isEmpty());
        assertTrue(ImageSize.of(null, "image/png").isEmpty());
        Optional<ImageSize.Dimensions> truncated = ImageSize.of(new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0d, 0x0a, 0x1a, 0x0a}, "image/png");
        assertTrue(truncated.isEmpty());
    }
}
