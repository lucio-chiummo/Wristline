package app.wristline;

import static org.junit.Assert.assertEquals;

import android.content.Context;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowBuild;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 36)
public class BatteryHintTextTest {
    @Test
    public void brandHintIsAppended() {
        Context ctx = RuntimeEnvironment.getApplication();
        ShadowBuild.setManufacturer("Xiaomi");
        assertEquals("x\n" + ctx.getString(R.string.battery_hint_xiaomi), BatteryHint.withBrandHint(ctx, "x"));
        ShadowBuild.setManufacturer("Google");
        assertEquals("x", BatteryHint.withBrandHint(ctx, "x"));
    }
}
