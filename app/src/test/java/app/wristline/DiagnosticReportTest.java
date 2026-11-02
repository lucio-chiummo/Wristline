package app.wristline;

import static org.junit.Assert.assertEquals;
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
        return f;
    }

    @Test
    public void saysWhatMatters() {
        String report = DiagnosticReport.build(facts());

        assertTrue(report.startsWith("Wristline 1.2\n"));
        assertTrue(report.contains("Acme Phone 1, Android SDK 36, patch 2026-01-01"));
        assertTrue(report.contains("Notification listener: NOT connected"));
        assertTrue(report.contains("Missing permissions: BATTERY"));
        assertTrue(report.contains("- com.whatsapp: 1.2.3, switch on, voice ON"));
        assertTrue(report.contains("- org.telegram.messenger: not installed"));
        assertTrue(report.contains("incoming com.whatsapp full mode"));
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
}
