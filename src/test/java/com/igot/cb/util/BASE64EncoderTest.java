package com.igot.cb.util;

import org.junit.Test;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import static org.junit.Assert.assertEquals;

public class BASE64EncoderTest {
    private final BASE64Encoder encoder = new BASE64Encoder();

    @Test
    public void testBytesPerAtom() {
        assertEquals(3, encoder.bytesPerAtom());
    }

    @Test
    public void testBytesPerLine() {
        assertEquals(57, encoder.bytesPerLine());
    }

    @Test
    public void testEncodeAtomWithOneByte() throws IOException {
        ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        byte[] input = new byte[] { 'A' };
        encoder.encodeAtom(outStream, input, 0, 1);
        assertEquals("QQ==", outStream.toString());
    }

    @Test
    public void testEncodeAtomWithTwoBytes() throws IOException {
        ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        byte[] input = new byte[] { 'A', 'B' };
        encoder.encodeAtom(outStream, input, 0, 2);
        assertEquals("QUI=", outStream.toString());
    }

    @Test
    public void testEncodeAtomWithThreeBytes() throws IOException {
        ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        byte[] input = new byte[] { 'A', 'B', 'C' };
        encoder.encodeAtom(outStream, input, 0, 3);
        assertEquals("QUJD", outStream.toString());
    }

    @Test
    public void testEncodeAtomWithOffset() throws IOException {
        ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        byte[] input = new byte[] { 'X', 'A', 'B', 'C' };
        encoder.encodeAtom(outStream, input, 1, 3);
        assertEquals("QUJD", outStream.toString());
    }

    @Test(expected = NullPointerException.class)
    public void testEncodeAtomWithNullStream() throws IOException {
        byte[] input = new byte[] { 'A', 'B', 'C' };
        encoder.encodeAtom(null, input, 0, 3);
    }

    @Test(expected = ArrayIndexOutOfBoundsException.class)
    public void testEncodeAtomWithInvalidOffset() throws IOException {
        ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        byte[] input = new byte[] { 'A', 'B', 'C' };
        encoder.encodeAtom(outStream, input, 5, 3);
    }
}