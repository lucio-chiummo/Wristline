package app.wristline;

/** The 6-digit code Wireless debugging shows for "Pair device with pairing code". */
final class PairingCode {
    private PairingCode() {
    }

    /** The code without spaces or dashes, or null if it isn't 6 digits. */
    static String normalize(CharSequence input) {
        if (input == null) {
            return null;
        }
        String code = input.toString().replaceAll("[\\s-]", "");
        return code.matches("\\d{6}") ? code : null;
    }
}
