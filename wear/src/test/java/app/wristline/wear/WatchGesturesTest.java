package app.wristline.wear;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import android.app.Activity;
import android.view.Window;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Function;

/** The Pixel Watch's gestures (double pinch, wrist turn) on Wristline's call screens. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 36)
public class WatchGesturesTest {
    /** Stands in for the wear-sdk's GestureInputManager: same method names and parameters. */
    public static class FakeManager {
        int[] actions;
        Window window;
        Executor executor;
        Consumer<Object> listener;
        Object removed;

        public void addGestureEventListener(int[] actions, Window window, Executor executor, Consumer<Object> listener) {
            this.actions = actions;
            this.window = window;
            this.executor = executor;
            this.listener = listener;
        }

        public void removeGestureEventListener(Consumer<Object> listener) {
            removed = listener;
        }
    }

    /** Stands in for the wear-sdk's GestureEvent, with other values than the real ones. */
    public static class FakeEvent {
        public static final int ACTION_PRIMARY = 11;
        public static final int ACTION_DISMISS = 12;
        private final int action;

        FakeEvent(int action) {
            this.action = action;
        }

        public int getAction() {
            return action;
        }
    }

    private Activity activity;
    private final FakeManager manager = new FakeManager();
    private Function<Activity, Object> realManager;
    private String realEvent;
    private int primary;
    private int dismiss;

    @Before
    public void setUp() {
        activity = Robolectric.buildActivity(Activity.class).setup().get();
        realManager = WatchGestures.manager;
        realEvent = WatchGestures.eventClass;
    }

    @After
    public void tearDown() {
        WatchGestures.manager = realManager;
        WatchGestures.eventClass = realEvent;
    }

    private void useFake() {
        WatchGestures.manager = a -> manager;
        WatchGestures.eventClass = FakeEvent.class.getName();
    }

    @Test
    public void withoutTheWearSdkListenReturnsNullAndDoesNotThrow() {
        assertNull(WatchGestures.listen(activity, () -> primary++, () -> dismiss++));
        WatchGestures.stop(activity, null);
    }

    @Test
    public void aPrimaryGestureRunsTheAction() {
        useFake();

        Object handle = WatchGestures.listen(activity, () -> primary++, null);

        assertNotNull(handle);
        assertArrayEquals("double pinch only", new int[] {FakeEvent.ACTION_PRIMARY}, manager.actions);
        assertSame(activity.getWindow(), manager.window);
        assertNotNull(manager.executor);
        manager.listener.accept(new FakeEvent(FakeEvent.ACTION_PRIMARY));
        assertEquals(1, primary);
    }

    @Test
    public void aDismissGestureRunsTheDismissAction() {
        useFake();

        WatchGestures.listen(activity, () -> primary++, () -> dismiss++);

        assertArrayEquals(new int[] {FakeEvent.ACTION_PRIMARY, FakeEvent.ACTION_DISMISS}, manager.actions);
        manager.listener.accept(new FakeEvent(FakeEvent.ACTION_DISMISS));
        assertEquals(1, dismiss);
        assertEquals(0, primary);
    }

    @Test
    public void stopRemovesTheListener() {
        useFake();
        Object handle = WatchGestures.listen(activity, () -> primary++, null);

        WatchGestures.stop(activity, handle);

        assertSame(manager.listener, manager.removed);
        assertSame(handle, manager.removed);
    }
}
