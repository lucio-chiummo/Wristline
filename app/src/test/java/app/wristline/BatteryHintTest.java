package app.wristline;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import org.junit.Test;

/** Brands that kill background apps get their own pointer to the extra setting. */
public class BatteryHintTest {
    @Test
    public void aggressiveBrandsGetAHintEachAndOthersNone() {
        assertNotEquals(0, BatteryHint.of(Brand.VIVO));
        assertNotEquals(0, BatteryHint.of(Brand.XIAOMI));
        assertNotEquals(0, BatteryHint.of(Brand.SAMSUNG));
        assertNotEquals(0, BatteryHint.of(Brand.ONEPLUS_OPPO));
        assertEquals(0, BatteryHint.of(Brand.GOOGLE));
        assertEquals(0, BatteryHint.of(Brand.OTHER));
    }

    @Test
    public void eachBrandHasItsOwnText() {
        assertNotEquals(BatteryHint.of(Brand.VIVO), BatteryHint.of(Brand.XIAOMI));
        assertNotEquals(BatteryHint.of(Brand.SAMSUNG), BatteryHint.of(Brand.ONEPLUS_OPPO));
    }
}
