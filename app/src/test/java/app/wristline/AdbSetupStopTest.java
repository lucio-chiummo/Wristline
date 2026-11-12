package app.wristline;

import static org.junit.Assert.assertEquals;
import static org.robolectric.Shadows.shadowOf;

import android.content.Intent;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;
import org.robolectric.util.ReflectionHelpers.ClassParameter;

import java.util.Collections;
import java.util.List;

/**
 * The typed pairing code arrives as a newer start of the service. stopSelf(id) is ignored unless id
 * is the latest start, so finishing with the first id would leave the service running.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 36)
public class AdbSetupStopTest {
    @Test
    public void finishingAfterACodeStopsTheLatestStart() {
        StatusNotifications.createChannels(RuntimeEnvironment.getApplication());
        AdbSetupService service = Robolectric.buildService(AdbSetupService.class).create().get();
        ReflectionHelpers.setField(service, "running", true);
        ReflectionHelpers.setField(service, "runStartId", 1);

        service.onStartCommand(new Intent(AdbSetupService.ACTION_CODE), 0, 2);
        ReflectionHelpers.callInstanceMethod(service, "finish",
                ClassParameter.from(List.class, Collections.emptyList()),
                ClassParameter.from(List.class, null),
                ClassParameter.from(String.class, "failed"));

        assertEquals(2, shadowOf(service).getStopSelfId());
    }
}
