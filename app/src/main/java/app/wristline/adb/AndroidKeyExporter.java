package app.wristline.adb;

import android.net.ssl.SSLSockets;

import java.io.IOException;

import javax.net.ssl.SSLSocket;

/**
 * RFC 8446 exported keying material from Android's TLS stack. adb binds the pairing password to
 * the TLS session this way, so a relayed connection cannot reuse the code. Only runs on a device:
 * the JVM has no {@link SSLSockets}, which is why {@link PairingClient.KeyExporter} exists.
 */
final class AndroidKeyExporter implements PairingClient.KeyExporter {
    @Override
    public byte[] export(SSLSocket socket, String label, int length) throws IOException {
        if (!SSLSockets.isSupportedSocket(socket)) {
            throw new IOException("TLS socket cannot export keying material");
        }
        return SSLSockets.exportKeyingMaterial(socket, label, null, length);
    }
}
