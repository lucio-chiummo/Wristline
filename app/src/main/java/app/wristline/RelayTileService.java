package app.wristline;

import android.graphics.drawable.Icon;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

/** Quick Settings tile: pause or resume ringing the watch, from the phone's pull-down. */
public class RelayTileService extends TileService {
    @Override
    public void onStartListening() {
        update();
    }

    @Override
    public void onClick() {
        Relaying.set(this, !Prefs.relayOn(this));
        update();
    }

    private void update() {
        Tile tile = getQsTile();
        if (tile == null) {
            return;
        }
        boolean on = Prefs.relayOn(this);
        tile.setState(on ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        tile.setLabel(getString(R.string.app_name));
        tile.setSubtitle(getString(on ? R.string.qs_on : R.string.qs_paused));
        tile.setIcon(Icon.createWithResource(this, R.drawable.ic_watch));
        tile.setStateDescription(getString(on ? R.string.qs_on : R.string.qs_paused));
        tile.updateTile();
    }
}
