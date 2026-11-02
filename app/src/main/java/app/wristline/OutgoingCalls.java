package app.wristline;

import android.app.ActivityOptions;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.provider.ContactsContract;
import android.util.Log;

/** Starts a WhatsApp/Telegram voice call that the watch asked for. */
final class OutgoingCalls {
    private OutgoingCalls() {
    }

    static void start(Context context, long contactId, SupportedApp app) {
        CallableContacts.Contact contact = ContactsReader.find(context, contactId);
        Long row = contact == null ? null : contact.callRows.get(app.packageName);
        if (row == null) {
            Log.w(RelayListener.TAG, "no " + app.label + " call row for contact " + contactId);
            return;
        }

        // The app's own "Voice call" contact entry: the same thing the Contacts app opens.
        Intent intent = new Intent(Intent.ACTION_VIEW)
                .setDataAndType(Uri.withAppendedPath(ContactsContract.Data.CONTENT_URI, String.valueOf(row)),
                        app.callMimeType)
                .setPackage(app.packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        Context appContext = context.getApplicationContext();
        // Same order as an incoming call: the watch's call (and its audio link) first, the app's
        // call after. An app already in a call hangs up when our call takes the audio mid-call.
        boolean mirrored = RelayListener.startOutgoing(context, app, contact.name, () -> {
            if (!startApp(appContext, intent, app)) {
                RelayConnection.endFromApp();
                RelayListener.outgoingFailed(app);
            }
        });
        if (!mirrored && !startApp(appContext, intent, app)) {
            return;
        }
        Prefs.addRecentCall(context, contactId);
        WatchSync.recordCall(context, true, app, contactId, contact.name, System.currentTimeMillis());
    }

    private static boolean startApp(Context context, Intent intent, SupportedApp app) {
        ActivityOptions options = ActivityOptions.makeBasic();
        if (android.os.Build.VERSION.SDK_INT >= 34) {
            options.setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED);
        }
        try {
            // Starting from the background is allowed: Wristline holds "Display over other apps".
            context.startActivity(intent, options.toBundle());
            Log.i(RelayListener.TAG, "started the " + app.label + " call");
            return true;
        } catch (RuntimeException e) {
            Log.w(RelayListener.TAG, "could not start the " + app.label + " call", e);
            return false;
        }
    }
}
