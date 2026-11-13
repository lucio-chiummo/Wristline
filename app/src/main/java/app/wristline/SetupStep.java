package app.wristline;

/** What the on-phone setup does next while it waits for Wireless debugging. */
final class SetupStep {
    enum Next { FAIL_NO_WIFI, APPLY, ASK_CODE, KEEP_LOOKING, FAIL_NOT_FOUND }

    /** Time kept back for pairing's aftermath (connect, apply, restart) before shortService's limit. */
    static final long FINISH_MARGIN_MS = 20_000;

    private SetupStep() {
    }

    /**
     * @param reachable Wristline is already paired and Wireless debugging is on
     * @param pairingFound the "Pair device with pairing code" service is advertised
     */
    static Next whileLooking(boolean onWifi, boolean reachable, boolean pairingFound, long remainingMs) {
        if (!onWifi) {
            return Next.FAIL_NO_WIFI;
        }
        if (reachable) {
            return Next.APPLY;
        }
        if (pairingFound) {
            return Next.ASK_CODE;
        }
        return remainingMs > FINISH_MARGIN_MS ? Next.KEEP_LOOKING : Next.FAIL_NOT_FOUND;
    }
}
