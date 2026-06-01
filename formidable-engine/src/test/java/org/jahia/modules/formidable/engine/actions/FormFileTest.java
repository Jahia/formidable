package org.jahia.modules.formidable.engine.actions;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

class FormFileTest {

    @Test
    void equalsAndHashCodeUseByteArrayContent() {
        // Verifies the parsed-file value semantics: two FormFile instances with the same bytes
        // must compare equal even when their backing byte[] instances are different objects.
        FormDataParser.FormFile left = new FormDataParser.FormFile("upload", "report.pdf", "application/pdf", new byte[]{1, 2, 3});
        FormDataParser.FormFile right = new FormDataParser.FormFile("upload", "report.pdf", "application/pdf", new byte[]{1, 2, 3});

        // Expected outcome: equality and hashCode are based on byte content, not array identity.
        assertEquals(left, right);
        assertEquals(left.hashCode(), right.hashCode());
    }

    @Test
    void constructorAndAccessorDefensivelyCopyData() {
        // Verifies the parser immutability boundary: callers must not be able to mutate the stored bytes
        // through either the constructor input array or the accessor return value.
        byte[] original = new byte[]{4, 5, 6};
        FormDataParser.FormFile file = new FormDataParser.FormFile("upload", "report.pdf", "application/pdf", original);

        original[0] = 9;
        byte[] firstRead = file.data();
        firstRead[1] = 8;

        // Expected outcome: the FormFile keeps the original byte content and returns fresh copies.
        assertArrayEquals(new byte[]{4, 5, 6}, file.data());
        assertNotSame(firstRead, file.data());
    }
}
