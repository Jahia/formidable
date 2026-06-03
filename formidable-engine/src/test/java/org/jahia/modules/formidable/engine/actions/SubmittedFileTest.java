package org.jahia.modules.formidable.engine.actions;

import org.jahia.modules.formidable.engine.api.SubmittedFile;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

class SubmittedFileTest {

    @Test
    void equalsAndHashCodeUseByteArrayContent() {
        // Verifies the SPI file value semantics: two SubmittedFile instances with the same bytes
        // must compare equal even when their backing byte[] instances are different objects.
        SubmittedFile left = new SubmittedFile("upload", "report.pdf", "application/pdf", new byte[]{1, 2, 3});
        SubmittedFile right = new SubmittedFile("upload", "report.pdf", "application/pdf", new byte[]{1, 2, 3});

        // Expected outcome: equality and hashCode are based on byte content, not array identity.
        assertEquals(left, right);
        assertEquals(left.hashCode(), right.hashCode());
    }

    @Test
    void constructorAndAccessorDefensivelyCopyData() {
        // Verifies the SPI immutability boundary: callers must not be able to mutate the stored bytes
        // through either the constructor input array or the accessor return value.
        byte[] original = new byte[]{4, 5, 6};
        SubmittedFile file = new SubmittedFile("upload", "report.pdf", "application/pdf", original);

        original[0] = 9;
        byte[] firstRead = file.data();
        firstRead[1] = 8;

        // Expected outcome: the SubmittedFile keeps the original byte content and returns fresh copies.
        assertArrayEquals(new byte[]{4, 5, 6}, file.data());
        assertNotSame(firstRead, file.data());
    }
}
