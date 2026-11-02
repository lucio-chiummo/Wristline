package app.wristline.wear;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;

import androidx.wear.watchface.complications.data.ComplicationData;
import androidx.wear.watchface.complications.data.ComplicationType;
import androidx.wear.watchface.complications.data.MonochromaticImageComplicationData;
import androidx.wear.watchface.complications.data.ShortTextComplicationData;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 36)
public class QuickCallComplicationTest {
    private final QuickCallComplicationService service =
            Robolectric.buildService(QuickCallComplicationService.class).create().get();
    private final Application context = RuntimeEnvironment.getApplication();

    @Test
    public void shortTextShowsTheFirstNameWithACallIconAndTapsToCall() {
        ComplicationData data = service.getPreviewData(ComplicationType.SHORT_TEXT);

        assertTrue(data instanceof ShortTextComplicationData);
        ShortTextComplicationData text = (ShortTextComplicationData) data;
        assertEquals("Annaly", text.getText().getTextAt(context.getResources(), java.time.Instant.now()).toString());
        assertNotNull(text.getMonochromaticImage());
        assertNotNull(text.getTapAction());
        assertEquals("Call Annaly", text.getContentDescription()
                .getTextAt(context.getResources(), java.time.Instant.now()).toString());
    }

    @Test
    public void iconOnlyForSmallSlots() {
        ComplicationData data = service.getPreviewData(ComplicationType.MONOCHROMATIC_IMAGE);

        assertTrue(data instanceof MonochromaticImageComplicationData);
        assertNotNull(data.getTapAction());
    }
}
