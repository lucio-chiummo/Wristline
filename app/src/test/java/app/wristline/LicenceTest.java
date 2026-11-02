package app.wristline;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.AlertDialog;
import android.app.Application;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Looper;
import android.widget.TextView;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAlertDialog;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;

/** The app shows its licence, and each copy carries the terms and the Required Notice they ask for. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 36)
public class LicenceTest {
    private void assertBundled(String asset, String repoFile) throws IOException {
        Application context = RuntimeEnvironment.getApplication();
        try (InputStream in = context.getAssets().open("licenses/" + asset)) {
            assertArrayEquals(Files.readAllBytes(new File(repoFile).toPath()), in.readAllBytes());
        }
    }

    @Test
    public void apkCarriesTheRepositorysLicence() throws IOException {
        assertBundled("LICENSE.txt", "../LICENSE");
    }

    @Test
    public void apkCarriesTheRepositorysNotice() throws IOException {
        assertBundled("NOTICE.txt", "../NOTICE");
    }

    /** The licence requires lines starting "Required Notice:" to travel with every copy. */
    @Test
    public void apkCarriesTheRequiredNotice() throws IOException {
        Application context = RuntimeEnvironment.getApplication();
        try (InputStream in = context.getAssets().open("licenses/NOTICE.txt")) {
            String notice = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            assertTrue(notice, notice.contains("Required Notice: Copyright Lucio Chiummo"));
        }
    }

    @Test
    public void apkCarriesThePolyFormTerms() throws IOException {
        Application context = RuntimeEnvironment.getApplication();
        try (InputStream in = context.getAssets().open("licenses/LICENSE.txt")) {
            String terms = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            assertTrue(terms.startsWith("# PolyForm Noncommercial License 1.0.0"));
        }
    }

    private AlertDialog openLicence(MainActivity activity) {
        activity.findViewById(R.id.row_licence).performClick();
        shadowOf(Looper.getMainLooper()).idle();
        AlertDialog dialog = ShadowAlertDialog.getLatestAlertDialog();
        assertNotNull("no licence dialog", dialog);
        return dialog;
    }

    @Test
    public void homeShowsTheLegalNotices() {
        MainActivity activity = Robolectric.buildActivity(MainActivity.class).setup().get();

        AlertDialog dialog = openLicence(activity);

        String text = ((TextView) dialog.findViewById(android.R.id.message)).getText().toString();
        assertTrue(text, text.contains("Lucio Chiummo"));
        assertTrue(text, text.contains("PolyForm Noncommercial"));
        assertTrue(text, text.contains("noncommercial purposes only"));
        assertTrue(text, text.contains("NO WARRANTY"));
    }

    @Test
    public void licenceDialogOpensTheProjectPage() {
        MainActivity activity = Robolectric.buildActivity(MainActivity.class).setup().get();
        AlertDialog dialog = openLicence(activity);

        dialog.getButton(DialogInterface.BUTTON_NEUTRAL).performClick();
        shadowOf(Looper.getMainLooper()).idle();

        Intent opened = shadowOf(activity).getNextStartedActivity();
        assertNotNull(opened);
        assertTrue(opened.getDataString(), opened.getDataString().contains("github.com/lucio-chiummo/Wristline"));
    }
}
