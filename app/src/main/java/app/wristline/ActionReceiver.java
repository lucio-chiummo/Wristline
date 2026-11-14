package app.wristline;

import android.app.Notification;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Forwards taps on Wristline's own notification buttons to the calling app's actions. */
public class ActionReceiver extends BroadcastReceiver {
    static final String ANSWER = "app.wristline.ANSWER";
    static final String DECLINE = "app.wristline.DECLINE";
    static final String ONGOING = "app.wristline.ONGOING";
    static final String EXTRA_INDEX = "index";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (ONGOING.equals(action)) {
            CallActions call = RelayListener.ongoingCall;
            int index = intent.getIntExtra(EXTRA_INDEX, -1);
            if (call != null && index >= 0 && index < call.actions.length) {
                Notification.Action pressed = call.actions[index];
                RelayListener.send(context, pressed.actionIntent);
            }
            return;
        }
        boolean answer = ANSWER.equals(action);
        if (TestCall.notificationRinging) {
            TestCall.notificationTestTapped(context, answer);
            return;
        }
        WatchRing.stop(context);
        if (RelayListener.currentApp == null) {
            // The watch can still show the buttons of a call that is over.
            return;
        }
        // Answered or declined here: not a missed call.
        RelayListener.incomingHandledOnWatch();
        Diagnostics.record(context, answer ? EventLog.ANSWERED_ON_WATCH : EventLog.DECLINED_ON_WATCH,
                RelayListener.currentApp, "notification");
        CallActions call = RelayListener.incomingCall;
        if (call != null) {
            RelayListener.send(context, answer ? call.answer : call.decline);
        }
    }
}
