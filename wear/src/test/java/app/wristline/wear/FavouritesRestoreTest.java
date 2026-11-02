package app.wristline.wear;

import static org.junit.Assert.assertEquals;

import android.app.Application;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.List;
import java.util.Set;

/** A fresh watch app gets the phone's backup; a watch with its own favourites keeps them. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 36)
public class FavouritesRestoreTest {
    private final Application context = RuntimeEnvironment.getApplication();

    @Test
    public void freshWatchGetsTheBackup() {
        Favourites.restoreIfEmpty(context, List.of(3L, 7L));
        assertEquals(Set.of(3L, 7L), Favourites.ids(context));
    }

    @Test
    public void ownFavouritesWin() {
        Favourites.toggle(context, 5);
        Favourites.restoreIfEmpty(context, List.of(3L, 7L));
        assertEquals(Set.of(5L), Favourites.ids(context));
    }

    @Test
    public void emptyBackupChangesNothing() {
        Favourites.restoreIfEmpty(context, List.of());
        assertEquals(Set.of(), Favourites.ids(context));
    }
}
