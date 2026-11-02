package app.wristline;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class WatchStatusTest {
    @Test
    public void unreadableMeansSayNothing() {
        assertEquals(WatchStatus.Kind.UNKNOWN, WatchStatus.of(-1, -1));
        assertEquals(WatchStatus.Kind.UNKNOWN, WatchStatus.of(1, -1));
    }

    @Test
    public void noWatchConnected() {
        assertEquals(WatchStatus.Kind.NO_WATCH, WatchStatus.of(0, -1));
        assertEquals(WatchStatus.Kind.NO_WATCH, WatchStatus.of(0, 0));
    }

    @Test
    public void watchWithoutTheApp() {
        assertEquals(WatchStatus.Kind.APP_MISSING, WatchStatus.of(1, 0));
    }

    @Test
    public void ready() {
        assertEquals(WatchStatus.Kind.READY, WatchStatus.of(1, 1));
        assertEquals(WatchStatus.Kind.READY, WatchStatus.of(2, 1));
    }
}
