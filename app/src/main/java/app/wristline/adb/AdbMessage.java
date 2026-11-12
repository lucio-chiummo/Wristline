package app.wristline.adb;

import java.io.DataInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * One adb transport message (AOSP adb/protocol.txt): a 24-byte little-endian header, then the
 * payload. The checksum is left 0, as adb has allowed since protocol version 0x01000001.
 */
final class AdbMessage {
    static final int A_CNXN = 0x4e584e43;
    static final int A_OPEN = 0x4e45504f;
    static final int A_OKAY = 0x59414b4f;
    static final int A_CLSE = 0x45534c43;
    static final int A_WRTE = 0x45545257;
    static final int A_STLS = 0x534c5453;
    static final int VERSION = 0x01000001;
    static final int STLS_VERSION = 0x01000000;
    static final int MAX_PAYLOAD = 1024 * 1024;

    static final int HEADER_BYTES = 24;

    final int command;
    final int arg0;
    final int arg1;
    final byte[] payload;

    AdbMessage(int command, int arg0, int arg1, byte[] payload) {
        if (payload.length > MAX_PAYLOAD) {
            throw new IllegalArgumentException("adb payload too large");
        }
        this.command = command;
        this.arg0 = arg0;
        this.arg1 = arg1;
        this.payload = payload;
    }

    byte[] encode() {
        return ByteBuffer.allocate(HEADER_BYTES + payload.length).order(ByteOrder.LITTLE_ENDIAN)
                .putInt(command).putInt(arg0).putInt(arg1).putInt(payload.length)
                .putInt(0).putInt(command ^ 0xffffffff)
                .put(payload).array();
    }

    /** Reads one message; the length is checked before the payload buffer is allocated. */
    static AdbMessage read(DataInputStream in) throws IOException {
        byte[] header = new byte[HEADER_BYTES];
        in.readFully(header);
        ByteBuffer b = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN);
        int command = b.getInt();
        int arg0 = b.getInt();
        int arg1 = b.getInt();
        int length = b.getInt();
        b.getInt(); // checksum: not checked, adbd sends 0 from VERSION on
        int magic = b.getInt();
        if (magic != (command ^ 0xffffffff)) {
            throw new IOException("bad adb message magic");
        }
        if (length < 0 || length > MAX_PAYLOAD) {
            throw new IOException("adb payload too large: " + Integer.toUnsignedString(length));
        }
        byte[] payload = new byte[length];
        in.readFully(payload);
        return new AdbMessage(command, arg0, arg1, payload);
    }
}
