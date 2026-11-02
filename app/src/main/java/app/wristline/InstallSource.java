package app.wristline;

/** Where Wristline was installed from, for setup hints. */
final class InstallSource {
    private static final String PLAY_STORE = "com.android.vending";
    private static final int ANDROID_13 = 33;

    private InstallSource() {
    }

    /**
     * Android 13+ blocks notification access ("restricted setting") for apps installed from a file
     * until the user allows it in App info › ⋮ › Allow restricted settings.
     */
    static boolean restrictedSettingsHint(String installerPackage, int sdk) {
        return sdk >= ANDROID_13 && !PLAY_STORE.equals(installerPackage);
    }
}
