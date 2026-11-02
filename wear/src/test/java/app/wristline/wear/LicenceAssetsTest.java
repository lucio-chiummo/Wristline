package app.wristline.wear;

import static org.junit.Assert.assertArrayEquals;

import android.app.Application;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;

/** Each copy of the watch app carries the terms and the Required Notice too. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 36)
public class LicenceAssetsTest {
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
}
