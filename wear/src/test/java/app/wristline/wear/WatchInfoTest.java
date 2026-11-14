package app.wristline.wear;

import static org.junit.Assert.assertEquals;

import com.google.android.gms.wearable.DataMap;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** What the watch tells the phone about itself, for the phone's diagnostic report. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 36)
public class WatchInfoTest {
    @Test
    public void dataMapCarriesOnlyBuildFacts() {
        DataMap map = WatchInfo.dataMap("OPPO", "OPPO Watch X3", 35, "1.4");

        assertEquals("OPPO", map.getString(Protocol.KEY_MANUFACTURER));
        assertEquals("OPPO Watch X3", map.getString(Protocol.KEY_MODEL));
        assertEquals(35, map.getInt(Protocol.KEY_SDK));
        assertEquals("1.4", map.getString(Protocol.KEY_VERSION));
        assertEquals(4, map.keySet().size());
    }

    @Test
    public void pathMatchesThePhone() {
        assertEquals("/wristline/watchinfo", Protocol.PATH_WATCH_INFO);
    }
}
