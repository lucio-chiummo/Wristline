package app.wristline;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** "Build number" lives in a different place on each brand's Settings. */
public class BrandTest {
    @Test
    public void knownBrandsIgnoringCase() {
        assertEquals(Brand.VIVO, Brand.of("vivo"));
        assertEquals(Brand.SAMSUNG, Brand.of("samsung"));
        assertEquals(Brand.GOOGLE, Brand.of("Google"));
        assertEquals(Brand.XIAOMI, Brand.of("Xiaomi"));
        assertEquals(Brand.MOTOROLA, Brand.of("motorola"));
    }

    @Test
    public void subBrandsMapToTheirSettings() {
        assertEquals(Brand.XIAOMI, Brand.of("Redmi"));
        assertEquals(Brand.XIAOMI, Brand.of("POCO"));
        assertEquals(Brand.ONEPLUS_OPPO, Brand.of("OnePlus"));
        assertEquals(Brand.ONEPLUS_OPPO, Brand.of("OPPO"));
        assertEquals(Brand.ONEPLUS_OPPO, Brand.of("realme"));
        assertEquals(Brand.VIVO, Brand.of("iQOO"));
    }

    @Test
    public void unknownOrMissingIsOther() {
        assertEquals(Brand.OTHER, Brand.of("Fairphone"));
        assertEquals(Brand.OTHER, Brand.of(null));
    }
}
