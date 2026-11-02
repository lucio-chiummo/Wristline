package app.wristline.wear;

import androidx.concurrent.futures.CallbackToFutureAdapter;
import androidx.wear.protolayout.ActionBuilders;
import androidx.wear.protolayout.ColorBuilders;
import androidx.wear.protolayout.DimensionBuilders;
import androidx.wear.protolayout.LayoutElementBuilders;
import androidx.wear.protolayout.ModifiersBuilders;
import androidx.wear.protolayout.ResourceBuilders;
import androidx.wear.protolayout.TimelineBuilders;
import androidx.wear.tiles.RequestBuilders;
import androidx.wear.tiles.TileBuilders;
import androidx.wear.tiles.TileService;

import com.google.common.util.concurrent.ListenableFuture;

import java.util.List;

/** Tile: favourites, then recently called people (5 in all; one tap calls), plus "All" for the full list. */
public class RecentTileService extends TileService {
    private static final String RESOURCES_VERSION = "1";
    private static final int RECENT = 5;
    private static final long REFRESH_MS = 15 * 60 * 1000;

    @Override
    protected ListenableFuture<TileBuilders.Tile> onTileRequest(RequestBuilders.TileRequest request) {
        return CallbackToFutureAdapter.getFuture(completer -> {
            new Thread(() -> {
                try {
                    PhoneLink.Contacts contacts = PhoneLink.contacts(this);
                    completer.set(tile(ContactOrder.quick(ContactOrder.favourites(contacts.all, Favourites.ids(this)),
                            ContactOrder.recent(contacts.all, contacts.recent, RECENT), RECENT), PhoneLink.relayOn(this)));
                } catch (Throwable t) {
                    completer.setException(t);
                }
            }, "recent-tile").start();
            return "recent-tile";
        });
    }

    @Override
    protected ListenableFuture<ResourceBuilders.Resources> onTileResourcesRequest(RequestBuilders.ResourcesRequest request) {
        return CallbackToFutureAdapter.getFuture(completer -> {
            completer.set(new ResourceBuilders.Resources.Builder().setVersion(RESOURCES_VERSION).build());
            return "recent-tile-resources";
        });
    }

    /** @param relayOn whether the phone relays calls; null when it hasn't said (no Pause/Resume chip) */
    private TileBuilders.Tile tile(List<WatchContact> recent, Boolean relayOn) {
        LayoutElementBuilders.Column.Builder column = new LayoutElementBuilders.Column.Builder()
                .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER);
        if (relayOn != null) {
            column.addContent(relayChip(relayOn)).addContent(spacer(6));
        } else {
            column.addContent(text(getString(R.string.tile_label), 13, 0xFFA8AEB8));
        }
        column.addContent(spacer(8));
        // Same initials and colours as the avatars in the app.
        LayoutElementBuilders.Row.Builder row = new LayoutElementBuilders.Row.Builder();
        int inRow = 0;
        for (WatchContact contact : recent) {
            row.addContent(avatar(Avatar.initials(contact.name), contact.name, Ui.avatarColor(this, contact.name),
                    launch("contact-" + contact.id, contact.id)));
            if (++inRow == 3) {
                column.addContent(row.build()).addContent(spacer(6));
                row = new LayoutElementBuilders.Row.Builder();
                inRow = 0;
            }
        }
        row.addContent(avatar("+", getString(R.string.all), getColor(R.color.card_high), launch("all", -1)));
        column.addContent(row.build());

        return new TileBuilders.Tile.Builder()
                .setResourcesVersion(RESOURCES_VERSION)
                .setFreshnessIntervalMillis(REFRESH_MS)
                .setTileTimeline(TimelineBuilders.Timeline.fromLayoutElement(column.build()))
                .build();
    }

    /** "Calls ring · Pause" or "Paused · Resume": one tap switches relaying on the phone. */
    private LayoutElementBuilders.LayoutElement relayChip(boolean on) {
        ActionBuilders.AndroidActivity activity = relayActivity(on);
        String label = getString(on ? R.string.tile_relay_on : R.string.tile_relay_paused);
        return new LayoutElementBuilders.Box.Builder()
                .setHeight(DimensionBuilders.dp(32))
                .setModifiers(new ModifiersBuilders.Modifiers.Builder()
                        .setClickable(new ModifiersBuilders.Clickable.Builder()
                                .setId(on ? "pause" : "resume")
                                .setOnClick(new ActionBuilders.LaunchAction.Builder().setAndroidActivity(activity).build())
                                .build())
                        .setBackground(new ModifiersBuilders.Background.Builder()
                                .setColor(ColorBuilders.argb(on ? getColor(R.color.card_high) : 0xFF5C3A12))
                                .setCorner(new ModifiersBuilders.Corner.Builder().setRadius(DimensionBuilders.dp(16)).build())
                                .build())
                        .setPadding(new ModifiersBuilders.Padding.Builder()
                                .setStart(DimensionBuilders.dp(14)).setEnd(DimensionBuilders.dp(14)).build())
                        .setSemantics(new ModifiersBuilders.Semantics.Builder()
                                .setContentDescription(getString(on ? R.string.tile_pause_desc : R.string.tile_resume_desc))
                                .build())
                        .build())
                .addContent(text(label, 13, on ? 0xFFE8EAED : 0xFFFDD663))
                .build();
    }

    /** @param on whether relaying is on now; the tap switches it the other way */
    private ActionBuilders.AndroidActivity relayActivity(boolean on) {
        return new ActionBuilders.AndroidActivity.Builder()
                .setPackageName(getPackageName())
                .setClassName(ToggleRelayActivity.class.getName())
                .addKeyToExtraMapping(ToggleRelayActivity.EXTRA_ON, ActionBuilders.booleanExtra(!on))
                .addKeyToExtraMapping(LaunchToken.EXTRA, ActionBuilders.stringExtra(LaunchToken.get(this)))
                .build();
    }

    private ModifiersBuilders.Clickable launch(String id, long contactId) {
        ActionBuilders.AndroidActivity.Builder activity = new ActionBuilders.AndroidActivity.Builder()
                .setPackageName(getPackageName())
                .setClassName(MainActivity.class.getName());
        if (contactId >= 0) {
            activity.addKeyToExtraMapping(MainActivity.EXTRA_CONTACT_ID, ActionBuilders.longExtra(contactId))
                    .addKeyToExtraMapping(LaunchToken.EXTRA, ActionBuilders.stringExtra(LaunchToken.get(this)));
        }
        return new ModifiersBuilders.Clickable.Builder()
                .setId(id)
                .setOnClick(new ActionBuilders.LaunchAction.Builder().setAndroidActivity(activity.build()).build())
                .build();
    }

    private static LayoutElementBuilders.LayoutElement avatar(String initial, String name, int color,
            ModifiersBuilders.Clickable clickable) {
        LayoutElementBuilders.Box circle = new LayoutElementBuilders.Box.Builder()
                .setWidth(DimensionBuilders.dp(44))
                .setHeight(DimensionBuilders.dp(44))
                .setModifiers(new ModifiersBuilders.Modifiers.Builder()
                        .setBackground(new ModifiersBuilders.Background.Builder()
                                .setColor(ColorBuilders.argb(color))
                                .setCorner(new ModifiersBuilders.Corner.Builder().setRadius(DimensionBuilders.dp(22)).build())
                                .build())
                        .build())
                .addContent(text(initial, initial.length() > 1 ? 16 : 18, 0xFFE8EAED))
                .build();
        return new LayoutElementBuilders.Column.Builder()
                .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
                .setModifiers(new ModifiersBuilders.Modifiers.Builder()
                        .setClickable(clickable)
                        .setPadding(new ModifiersBuilders.Padding.Builder()
                                .setStart(DimensionBuilders.dp(5)).setEnd(DimensionBuilders.dp(5)).build())
                        .setSemantics(new ModifiersBuilders.Semantics.Builder().setContentDescription(name).build())
                        .build())
                .addContent(circle)
                .addContent(text(Avatar.shortName(name), 11, 0xFFE8EAED))
                .build();
    }

    private static LayoutElementBuilders.Text text(String value, float sizeSp, int color) {
        return new LayoutElementBuilders.Text.Builder()
                .setText(value)
                .setFontStyle(new LayoutElementBuilders.FontStyle.Builder()
                        .setSize(DimensionBuilders.sp(sizeSp))
                        .setColor(ColorBuilders.argb(color))
                        .build())
                .build();
    }

    private static LayoutElementBuilders.Spacer spacer(float heightDp) {
        return new LayoutElementBuilders.Spacer.Builder().setHeight(DimensionBuilders.dp(heightDp)).build();
    }
}
