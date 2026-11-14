package app.wristline;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

public class DiagnosticReportTest {
    private static DiagnosticReport.Facts facts() {
        DiagnosticReport.Facts f = new DiagnosticReport.Facts();
        f.appVersion = "1.2";
        f.device = "Acme Phone 1";
        f.sdk = 36;
        f.securityPatch = "2026-01-01";
        f.relayOn = true;
        f.listenerConnected = false;
        f.missingPermissions = List.of("BATTERY");
        f.apps = List.of(
                new DiagnosticReport.AppState("com.whatsapp", "1.2.3", true, "ON"),
                new DiagnosticReport.AppState("org.telegram.messenger", null, true, ""));
        f.events = List.of(new EventLog.Entry(0, EventLog.INCOMING, "com.whatsapp", "full mode"));
        f.brand = "Acme";
        f.release = "17";
        f.display = "ACME.1";
        f.locale = "it-IT";
        f.bluetoothCallAudio = true;
        return f;
    }

    @Test
    public void saysWhatMatters() {
        String report = DiagnosticReport.build(facts());

        assertTrue(report.startsWith("Wristline 1.2\n"));
        assertTrue(report.contains("Acme Phone 1 (Acme), Android 17 (ACME.1), SDK 36, patch 2026-01-01"));
        assertTrue(report.contains("Notification listener: NOT connected"));
        assertTrue(report.contains("Missing permissions: BATTERY"));
        assertTrue(report.contains("- com.whatsapp: 1.2.3, switch on, voice ON"));
        assertTrue(report.contains("- org.telegram.messenger: not installed"));
        assertTrue(report.contains("incoming com.whatsapp full mode"));
    }

    @Test
    public void saysWhetherTheAdbKeyIsPresent() {
        DiagnosticReport.Facts f = facts();
        assertTrue(DiagnosticReport.build(f).contains("adb key present: no\n"));
        f.keyPresent = true;
        assertTrue(DiagnosticReport.build(f).contains("adb key present: yes\n"));
    }

    @Test
    public void noEventsSaysSo() {
        DiagnosticReport.Facts f = facts();
        f.events = List.of();
        String report = DiagnosticReport.build(f);
        assertTrue(report.endsWith("Recent events (newest first):\nnone\n"));
    }

    @Test
    public void notificationShapeLeavesTheCallerOut() {
        String shape = NotificationShape.describe("call", true, 1, false, List.of("Decline", "Answer"));
        assertEquals("category=call ongoing=true callType=1 fullScreen=false actions=[Decline, Answer]", shape);
        assertEquals("category=none ongoing=false callType=none fullScreen=true actions=[]",
                NotificationShape.describe(null, false, null, true, List.of()));
    }

    @Test
    public void phoneLineNamesBrandReleaseAndLocale() {
        String report = DiagnosticReport.build(facts());
        assertTrue(report.contains("Device: Acme Phone 1 (Acme), Android 17 (ACME.1), SDK 36, patch 2026-01-01, locale it-IT\n"));
        assertTrue(report.contains("Bluetooth call audio device: yes\n"));
    }

    @Test
    public void unknownBluetoothSaysUnknown() {
        DiagnosticReport.Facts f = facts();
        f.bluetoothCallAudio = null;
        assertTrue(DiagnosticReport.build(f).contains("Bluetooth call audio device: unknown\n"));
    }

    @Test
    public void listsCallBridgesAndCompanions() {
        DiagnosticReport.Facts f = facts();
        f.defaultDialer = "com.android.dialer";
        f.bridges = List.of(new CallBridges.Bridge("com.example.bridge", "2.0", true, false));
        f.companions = List.of("com.heytap.health.international 4.2.0");
        String report = DiagnosticReport.build(f);
        assertTrue(report.contains("Default dialer: com.android.dialer\n"));
        assertTrue(report.contains("Call bridges (InCallService):\n- com.example.bridge 2.0, self-managed yes, ui no\n"));
        assertTrue(report.contains("Watch companion apps:\n- com.heytap.health.international 4.2.0\n"));
        assertTrue("bridges come before the events",
                report.indexOf("Call bridges") < report.indexOf("Recent events"));
    }

    @Test
    public void capsLongBridgeLists() {
        DiagnosticReport.Facts f = facts();
        List<CallBridges.Bridge> many = new java.util.ArrayList<>();
        for (int i = 0; i < 14; i++) {
            many.add(new CallBridges.Bridge("com.example.b" + (char) ('a' + i), "1", false, false));
        }
        f.bridges = many;
        String report = DiagnosticReport.build(f);
        assertTrue(report.contains("- com.example.bl 1, self-managed no, ui no\n- … and 2 more\n"));
        assertFalse(report.contains("com.example.bm"));
    }

    @Test
    public void noBridgesOrCompanionsSaysNone() {
        String report = DiagnosticReport.build(facts());
        assertTrue(report.contains("Call bridges (InCallService): none\n"));
        assertTrue(report.contains("Watch companion apps: none\n"));
    }
}
