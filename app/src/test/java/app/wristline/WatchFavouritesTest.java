package app.wristline;

import static org.junit.Assert.assertArrayEquals;

import org.junit.Test;

import java.nio.charset.StandardCharsets;

public class WatchFavouritesTest {
    private static long[] parse(String text) {
        return WatchProtocol.parseIds(text.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    public void idsFromTheWatch() {
        assertArrayEquals(new long[] {3, 7, 12}, parse("3,7,12"));
        assertArrayEquals(new long[] {5}, parse(" 5 "));
    }

    @Test
    public void emptyOrDamaged() {
        assertArrayEquals(new long[0], parse(""));
        assertArrayEquals(new long[0], WatchProtocol.parseIds(null));
        assertArrayEquals(new long[] {4}, parse("x,4,"));
    }
}
