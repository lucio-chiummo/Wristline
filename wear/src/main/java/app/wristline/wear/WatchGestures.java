package app.wristline.wear;

import android.app.Activity;
import android.content.Context;
import android.util.Log;
import android.view.Window;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * The Pixel Watch's hand gestures on a Wristline screen: the double pinch (the "primary" action)
 * and the wrist turn ("dismiss"). Without a subscriber for its window the watch turns them into
 * Back. They come from the watch's wear-sdk library, reached by reflection: the library only
 * exists on the watches that have it, so there's no compile-time dependency and elsewhere
 * nothing happens.
 */
final class WatchGestures {
    private static final String SDK = "com.google.wear.Sdk";
    private static final String MANAGER = "com.google.wear.input.GestureInputManager";

    /** Finds the gesture manager for an activity; tests swap in a fake. */
    static Function<Activity, Object> manager = WatchGestures::gestureManager;
    /** The class holding the ACTION_ constants; tests swap in a fake. */
    static String eventClass = "com.google.wear.input.GestureEvent";

    /** The missing library is logged once, not on every screen. */
    private static boolean logged;

    private WatchGestures() {
    }

    /**
     * Runs {@code onPrimary} for a double pinch on {@code activity}'s window, and {@code onDismiss}
     * (when not null) for a wrist turn, on the main thread. Returns the handle for {@link #stop},
     * or null when the watch has no such gestures.
     */
    static Object listen(Activity activity, Runnable onPrimary, Runnable onDismiss) {
        try {
            Object gestures = manager.apply(activity);
            Class<?> events = Class.forName(eventClass);
            int primary = events.getField("ACTION_PRIMARY").getInt(null);
            int dismiss = events.getField("ACTION_DISMISS").getInt(null);
            int[] actions = onDismiss == null ? new int[] {primary} : new int[] {primary, dismiss};
            Consumer<Object> listener = event -> {
                try {
                    int action = (int) event.getClass().getMethod("getAction").invoke(event);
                    if (action == primary) {
                        onPrimary.run();
                    } else if (action == dismiss && onDismiss != null) {
                        onDismiss.run();
                    }
                } catch (ReflectiveOperationException e) {
                    Log.w(PhoneLink.TAG, "could not read a watch gesture", e);
                }
            };
            gestures.getClass()
                    .getMethod("addGestureEventListener", int[].class, Window.class, Executor.class, Consumer.class)
                    .invoke(gestures, actions, activity.getWindow(), activity.getMainExecutor(), listener);
            return listener;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            if (!logged) {
                logged = true;
                // A refused subscription comes wrapped: its cause is what the watch said.
                Throwable cause = e instanceof InvocationTargetException && e.getCause() != null ? e.getCause() : e;
                Log.i(PhoneLink.TAG, "no watch gestures: " + cause, cause);
            }
            return null;
        }
    }

    /** Ends {@link #listen}; a null handle does nothing. */
    static void stop(Activity activity, Object handle) {
        if (handle == null) {
            return;
        }
        try {
            Object gestures = manager.apply(activity);
            gestures.getClass().getMethod("removeGestureEventListener", Consumer.class).invoke(gestures, handle);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            Log.w(PhoneLink.TAG, "could not stop the watch gestures", e);
        }
    }

    private static Object gestureManager(Context context) {
        try {
            Method get = Class.forName(SDK).getMethod("getWearManager", Context.class, Class.class);
            return get.invoke(null, context, Class.forName(MANAGER));
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
