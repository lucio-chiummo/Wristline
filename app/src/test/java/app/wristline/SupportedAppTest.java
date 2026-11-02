package app.wristline;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.stream.Collectors;

import org.junit.Test;

public class SupportedAppTest {
    @Test
    public void tableOrderAndPackages() {
        assertEquals(List.of("com.whatsapp", "org.telegram.messenger", "com.instagram.android"),
                SupportedApp.ALL.stream().map(a -> a.packageName).collect(Collectors.toList()));
    }

    @Test
    public void defaultsAndVoiceThroughTheWatch() {
        assertTrue(SupportedApp.forPackage("com.whatsapp").defaultOn);
        assertTrue(SupportedApp.forPackage("com.whatsapp").fullModeVerified);
        assertFalse(SupportedApp.forPackage("org.telegram.messenger").defaultOn);
        assertTrue(SupportedApp.forPackage("org.telegram.messenger").fullModeVerified);
        assertFalse(SupportedApp.forPackage("com.instagram.android").defaultOn);
        assertTrue(SupportedApp.forPackage("com.instagram.android").fullModeVerified);
    }

    @Test
    public void telegramIsNotBetaAndOngoingLacksCategory() {
        SupportedApp telegram = SupportedApp.forPackage("org.telegram.messenger");
        assertFalse(telegram.beta);
        assertTrue(telegram.ongoingLacksCallCategory);
        assertFalse(SupportedApp.forPackage("com.instagram.android").ongoingLacksCallCategory);
        assertFalse(SupportedApp.forPackage("com.whatsapp").beta);
    }

    @Test
    public void unknownPackageIsNull() {
        assertNull(SupportedApp.forPackage("com.example"));
    }

    @Test
    public void onlyWhatsAppKeepsOneNotificationThroughTheCall() {
        assertFalse(SupportedApp.forPackage("com.whatsapp").separateInCallNotification);
        assertTrue(SupportedApp.forPackage("org.telegram.messenger").separateInCallNotification);
        assertTrue(SupportedApp.forPackage("com.instagram.android").separateInCallNotification);
    }
}
