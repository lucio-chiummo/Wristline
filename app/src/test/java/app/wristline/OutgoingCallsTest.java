package app.wristline;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.provider.ContactsContract;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.List;

/** Starting the app's call the watch asked for: which intent, and what gets remembered. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 36)
public class OutgoingCallsTest {
    /** The phone's contacts: only the apps' "Voice call" data rows matter here. */
    public static class FakeContacts extends ContentProvider {
        static final List<Object[]> rows = new ArrayList<>();

        @Override
        public boolean onCreate() {
            return true;
        }

        @Override
        public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
            // ContactsReader asks for CONTACT_ID, DISPLAY_NAME, MIMETYPE, _ID.
            MatrixCursor cursor = new MatrixCursor(projection);
            for (Object[] row : rows) {
                cursor.addRow(row);
            }
            return cursor;
        }

        @Override
        public String getType(Uri uri) {
            return null;
        }

        @Override
        public Uri insert(Uri uri, ContentValues values) {
            return null;
        }

        @Override
        public int delete(Uri uri, String selection, String[] selectionArgs) {
            return 0;
        }

        @Override
        public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
            return 0;
        }
    }

    private Application context;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        FakeContacts.rows.clear();
        Robolectric.buildContentProvider(FakeContacts.class).create(ContactsContract.AUTHORITY);
    }

    @Test
    public void startsTheAppsOwnVoiceCallEntry() {
        FakeContacts.rows.add(new Object[] {7L, "Corvan", SupportedApp.WHATSAPP.callMimeType, 42L});

        OutgoingCalls.start(context, 7, SupportedApp.WHATSAPP, OutgoingCalls.TRIGGER_WATCH);

        Intent started = shadowOf(context).getNextStartedActivity();
        assertNotNull(started);
        assertEquals(Intent.ACTION_VIEW, started.getAction());
        assertEquals(Uri.withAppendedPath(ContactsContract.Data.CONTENT_URI, "42"), started.getData());
        assertEquals(SupportedApp.WHATSAPP.callMimeType, started.getType());
        assertEquals("com.whatsapp", started.getPackage());
        assertArrayEquals(new long[] {7}, Prefs.recentCalls(context));
    }

    @Test
    public void picksTheRowOfTheAppAsked() {
        FakeContacts.rows.add(new Object[] {7L, "Corvan", SupportedApp.WHATSAPP.callMimeType, 42L});
        FakeContacts.rows.add(new Object[] {7L, "Corvan", SupportedApp.TELEGRAM.callMimeType, 43L});

        OutgoingCalls.start(context, 7, SupportedApp.TELEGRAM, OutgoingCalls.TRIGGER_WATCH);

        Intent started = shadowOf(context).getNextStartedActivity();
        assertEquals(Uri.withAppendedPath(ContactsContract.Data.CONTENT_URI, "43"), started.getData());
        assertEquals("org.telegram.messenger", started.getPackage());
    }

    @Test
    public void contactWithoutARowForThatAppStartsNothing() {
        FakeContacts.rows.add(new Object[] {7L, "Corvan", SupportedApp.TELEGRAM.callMimeType, 43L});

        OutgoingCalls.start(context, 7, SupportedApp.WHATSAPP, OutgoingCalls.TRIGGER_WATCH);

        assertNull(shadowOf(context).getNextStartedActivity());
        assertArrayEquals(new long[0], Prefs.recentCalls(context));
    }

    @Test
    public void unknownContactStartsNothing() {
        OutgoingCalls.start(context, 99, SupportedApp.WHATSAPP, OutgoingCalls.TRIGGER_WATCH);

        assertNull(shadowOf(context).getNextStartedActivity());
        assertArrayEquals(new long[0], Prefs.recentCalls(context));
    }

    private EventLog.Entry latest(String code) {
        for (EventLog.Entry e : Prefs.events(context)) {
            if (e.code.equals(code)) {
                return e;
            }
        }
        return null;
    }

    @Test
    public void phoneOnlyStartIsStillRecorded() {
        // No notification listener in tests: the call can't be mirrored, the app starts anyway.
        FakeContacts.rows.add(new Object[] {7L, "Corvan", SupportedApp.WHATSAPP.callMimeType, 42L});

        OutgoingCalls.start(context, 7, SupportedApp.WHATSAPP, OutgoingCalls.TRIGGER_WATCH);

        assertNotNull(latest(EventLog.OUTGOING_UNMIRRORED));
        assertEquals("listener off", latest(EventLog.OUTGOING_UNMIRRORED).detail);
        EventLog.Entry started = latest(EventLog.APP_STARTED);
        assertNotNull("app start not recorded", started);
        assertEquals("com.whatsapp", started.app);
        assertEquals("watch", started.detail);
    }

    @Test
    public void callBackTriggerIsRecorded() {
        FakeContacts.rows.add(new Object[] {7L, "Corvan", SupportedApp.TELEGRAM.callMimeType, 43L});

        OutgoingCalls.start(context, 7, SupportedApp.TELEGRAM, OutgoingCalls.TRIGGER_CALL_BACK);

        assertEquals("call back", latest(EventLog.APP_STARTED).detail);
    }

    @Test
    public void nothingStartedRecordsNoStart() {
        OutgoingCalls.start(context, 99, SupportedApp.WHATSAPP, OutgoingCalls.TRIGGER_WATCH);

        assertNull(latest(EventLog.APP_STARTED));
    }
}
