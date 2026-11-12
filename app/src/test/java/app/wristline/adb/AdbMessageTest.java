package app.wristline.adb;

import static app.wristline.adb.AdbMessage.A_CNXN;
import static app.wristline.adb.AdbMessage.A_WRTE;
import static app.wristline.adb.AdbMessage.MAX_PAYLOAD;
import static app.wristline.adb.AdbMessage.VERSION;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.junit.Test;

public class AdbMessageTest {
    @Test
    public void encodeHeaderLayout() {
        byte[] b = new AdbMessage(A_CNXN, VERSION, MAX_PAYLOAD, "host::\0".getBytes(StandardCharsets.US_ASCII)).encode();
        assertEquals(24 + 7, b.length);
        assertEquals(A_CNXN, le32(b, 0));
        assertEquals(VERSION, le32(b, 4));
        assertEquals(MAX_PAYLOAD, le32(b, 8));
        assertEquals(7, le32(b, 12));
        assertEquals(0, le32(b, 16));
        assertEquals(A_CNXN ^ 0xffffffff, le32(b, 20));
        assertEquals('C', b[0]); // "CNXN" on the wire
    }

    @Test
    public void readRoundTrips() throws IOException {
        byte[] payload = {1, 2, 3, (byte) 0xff};
        AdbMessage m = AdbMessage.read(in(new AdbMessage(A_WRTE, 7, 0x80000001, payload).encode()));
        assertEquals(A_WRTE, m.command);
        assertEquals(7, m.arg0);
        assertEquals(0x80000001, m.arg1);
        assertArrayEquals(payload, m.payload);
    }

    @Test
    public void readEmptyPayload() throws IOException {
        AdbMessage m = AdbMessage.read(in(new AdbMessage(AdbMessage.A_OKAY, 1, 2, new byte[0]).encode()));
        assertEquals(0, m.payload.length);
    }

    @Test
    public void readRejectsBadMagic() {
        byte[] b = new AdbMessage(A_CNXN, VERSION, MAX_PAYLOAD, new byte[0]).encode();
        b[20] ^= 1;
        assertThrows(IOException.class, () -> AdbMessage.read(in(b)));
    }

    @Test
    public void readRejectsOversizedPayload() {
        byte[] b = Arrays.copyOf(new AdbMessage(A_WRTE, 1, 1, new byte[0]).encode(), 24);
        put32(b, 12, MAX_PAYLOAD + 1);
        // No payload bytes follow: rejection must come from the length, before any allocation or read.
        IOException e = assertThrows(IOException.class, () -> AdbMessage.read(in(b)));
        assertEquals(false, e instanceof EOFException);
        put32(b, 12, -1);
        e = assertThrows(IOException.class, () -> AdbMessage.read(in(b)));
        assertEquals(false, e instanceof EOFException);
    }

    @Test
    public void readRejectsTruncated() {
        byte[] b = new AdbMessage(A_WRTE, 1, 1, new byte[] {1, 2, 3}).encode();
        assertThrows(EOFException.class, () -> AdbMessage.read(in(Arrays.copyOf(b, b.length - 1))));
        assertThrows(EOFException.class, () -> AdbMessage.read(in(Arrays.copyOf(b, 10))));
    }

    @Test
    public void constructorRejectsOversizedPayload() {
        assertThrows(IllegalArgumentException.class, () -> new AdbMessage(A_WRTE, 1, 1, new byte[MAX_PAYLOAD + 1]));
    }

    private static DataInputStream in(byte[] b) {
        return new DataInputStream(new ByteArrayInputStream(b));
    }

    private static int le32(byte[] b, int off) {
        return (b[off] & 0xff) | (b[off + 1] & 0xff) << 8 | (b[off + 2] & 0xff) << 16 | (b[off + 3] & 0xff) << 24;
    }

    private static void put32(byte[] b, int off, int v) {
        b[off] = (byte) v;
        b[off + 1] = (byte) (v >>> 8);
        b[off + 2] = (byte) (v >>> 16);
        b[off + 3] = (byte) (v >>> 24);
    }
}
