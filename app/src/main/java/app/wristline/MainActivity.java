package app.wristline;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/** Home: is relaying working, which apps it relays, and the one thing to fix if not. */
public class MainActivity extends Activity {
    private LinearLayout statusCard;
    private View statusIconBg;
    private ImageView statusIcon;
    private TextView statusTitle;
    private TextView statusSub;
    private View attentionBox;
    private TextView attentionTitle;
    private TextView attentionDesc;
    private Button attentionButton;
    private Switch masterSwitch;
    private TextView appsCount;
    private LinearLayout appsList;
    private TextView noApps;
    private View rowVoice;
    private View rowPermissions;
    private View rowWatchCalling;
    private View rowHistory;
    private View rowTestCall;
    private View rowDnd;
    private static final int REQUEST_CONTACTS = 2;
    /** The Fix button has been tapped and the card still says nothing is received. */
    private boolean fixTried;

    private final CompoundButton.OnCheckedChangeListener masterListener = (button, checked) -> {
        Relaying.set(this, checked);
        refresh();
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Prefs.migrate(this);
        LegacyAdbKey.delete(this);
        StatusNotifications.createChannels(this);
        setContentView(R.layout.activity_home);

        statusCard = findViewById(R.id.status_card);
        statusIconBg = findViewById(R.id.status_icon_bg);
        statusIcon = findViewById(R.id.status_icon);
        statusTitle = findViewById(R.id.status_title);
        statusSub = findViewById(R.id.status_sub);
        attentionBox = findViewById(R.id.attention_box);
        attentionTitle = findViewById(R.id.attention_title);
        attentionDesc = findViewById(R.id.attention_desc);
        attentionButton = findViewById(R.id.attention_button);
        masterSwitch = findViewById(R.id.master_switch);
        appsCount = findViewById(R.id.apps_count);
        appsList = findViewById(R.id.apps_list);
        noApps = findViewById(R.id.no_apps);
        rowVoice = findViewById(R.id.row_voice);
        rowPermissions = findViewById(R.id.row_permissions);
        rowWatchCalling = findViewById(R.id.row_watch_calling);
        bindRow(rowWatchCalling, R.drawable.ic_watch, R.string.row_watch_calling);
        rowWatchCalling.setOnClickListener(v -> watchCallingDialog());
        rowHistory = findViewById(R.id.row_history);
        bindRow(rowHistory, R.drawable.ic_info, R.string.row_history);
        rowHistory.setOnClickListener(v -> historyDialog());
        rowDnd = findViewById(R.id.row_dnd);
        bindRow(rowDnd, R.drawable.ic_dnd, R.string.row_dnd);
        rowDnd.setOnClickListener(v -> dndDialog());
        rowTestCall = findViewById(R.id.row_test_call);
        bindRow(rowTestCall, R.drawable.ic_watch, R.string.row_test_call);
        ((TextView) rowTestCall.findViewById(R.id.row_sub)).setText(R.string.test_call_sub);
        rowTestCall.setOnClickListener(v -> ringWatch());
        View rowReport = findViewById(R.id.row_report);
        bindRow(rowReport, R.drawable.ic_help, R.string.row_report);
        ((TextView) rowReport.findViewById(R.id.row_sub)).setText(R.string.report_sub);
        rowReport.setOnClickListener(v -> startActivity(Diagnostics.shareIntent(this)));
        View rowLicence = findViewById(R.id.row_licence);
        bindRow(rowLicence, R.drawable.ic_info, R.string.row_licence);
        ((TextView) rowLicence.findViewById(R.id.row_sub)).setText(R.string.licence_sub);
        rowLicence.setOnClickListener(v -> licenceDialog());

        findViewById(R.id.help).setOnClickListener(v ->
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.how_to_url)))));
        bindRow(rowVoice, R.drawable.ic_mic, R.string.row_voice);
        bindRow(rowPermissions, R.drawable.ic_checklist, R.string.row_permissions);
        rowVoice.setOnClickListener(v -> startActivity(new Intent(this, VoiceActivity.class)));
        rowPermissions.setOnClickListener(v -> startActivity(new Intent(this, PermissionsActivity.class)));
    }

    @Override
    protected void onResume() {
        super.onResume();
        ListenerRebind.ifNeeded(this, ListenerRebind.HOME, null);
        TestCall.onResult = this::refresh;
        ListenerRebind.onChange = this::refresh;
        Relaying.onChange = this::refresh;
        refresh();
    }

    @Override
    protected void onPause() {
        TestCall.onResult = null;
        ListenerRebind.onChange = null;
        Relaying.onChange = null;
        super.onPause();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        if (requestCode == REQUEST_CONTACTS && watchCallingPermitted()) {
            setWatchCalling(true);
        }
        refresh();
    }

    /** Contacts for the list; "make phone calls" because the apps' call entries require it. */
    private static final String[] WATCH_CALLING_PERMISSIONS = {
            Manifest.permission.READ_CONTACTS, Manifest.permission.CALL_PHONE};

    private boolean watchCallingPermitted() {
        for (String permission : WATCH_CALLING_PERMISSIONS) {
            if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    private void watchCallingDialog() {
        boolean on = Prefs.watchCalling(this);
        new AlertDialog.Builder(this)
                .setTitle(R.string.watch_calling_title)
                .setMessage(R.string.watch_calling_message)
                .setPositiveButton(on ? R.string.turn_off : R.string.turn_on, (d, w) -> {
                    if (on) {
                        setWatchCalling(false);
                    } else if (watchCallingPermitted()) {
                        setWatchCalling(true);
                    } else {
                        requestPermissions(WATCH_CALLING_PERMISSIONS, REQUEST_CONTACTS);
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void licenceDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.row_licence)
                .setMessage(R.string.licence_notice)
                .setNeutralButton(R.string.licence_open, (d, w) ->
                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.help_url)))))
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    private void setWatchCalling(boolean on) {
        Prefs.setWatchCalling(this, on);
        if (on) {
            RelayListener.watchCallingTurnedOn();
        }
        WatchSync.publish(this);
        refresh();
    }

    private void refresh() {
        List<Permissions.Step> missing = Permissions.missing(this);
        List<SupportedApp> installed = AppUi.installedApps(this);
        List<SupportedApp> broken = new ArrayList<>();
        List<SupportedApp> voiceOn = new ArrayList<>();
        for (SupportedApp app : installed) {
            if (!Prefs.appOn(this, app)) {
                continue;
            }
            VoiceState state = FullMode.state(this, app);
            if (state == VoiceState.ON) {
                voiceOn.add(app);
            } else if (state == VoiceState.NEEDS_REPAIR) {
                broken.add(app);
            }
        }
        boolean relayOn = Prefs.relayOn(this);

        // A relayed call in the history also proves calls reach the watch: no test needed then.
        boolean reachedWatch = Prefs.testCallResult(this) == TestCall.Result.REACHED
                || !Prefs.callHistory(this).isEmpty();
        SetupStatus.Kind kind = SetupStatus.decide(
                relayOn, missing.size(), ListenerRebind.unbound(this), broken.size(), reachedWatch);
        if (kind != SetupStatus.Kind.NOT_RECEIVING) {
            fixTried = false;
        }
        bindStatus(kind, missing, broken);

        masterSwitch.setOnCheckedChangeListener(null);
        masterSwitch.setChecked(relayOn);
        masterSwitch.setOnCheckedChangeListener(masterListener);

        bindApps(installed, relayOn);

        ((TextView) rowVoice.findViewById(R.id.row_sub)).setText(voiceOn.isEmpty()
                ? getString(R.string.voice_off)
                : getString(R.string.voice_on_for, AppUi.labels(this, voiceOn)));
        bindWatchCallingRow();
        bindHistoryRow();
        String rules = getString(Prefs.followDnd(this) ? R.string.dnd_on : R.string.dnd_off);
        if (Prefs.notWhileInUse(this)) {
            rules += "\n" + getString(R.string.in_use_on);
        }
        ((TextView) rowDnd.findViewById(R.id.row_sub)).setText(rules);
        int total = Permissions.Step.values().length;
        ((TextView) rowPermissions.findViewById(R.id.row_sub)).setText(missing.isEmpty()
                ? getString(R.string.permissions_all_done, total)
                : getString(R.string.permissions_progress, total - missing.size(), total));
    }

    private void bindStatus(SetupStatus.Kind kind, List<Permissions.Step> missing, List<SupportedApp> broken) {
        attentionBox.setVisibility(View.GONE);
        switch (kind) {
            case PAUSED:
                paintStatus(R.color.paused_card, R.color.divider, R.color.text, R.drawable.ic_pause);
                statusTitle.setText(R.string.status_paused_title);
                statusSub.setText(R.string.status_paused_sub);
                break;
            case STEPS_LEFT:
                paintStatus(R.color.amber_card, R.color.amber, R.color.on_amber, R.drawable.ic_warning);
                statusTitle.setText(getResources().getQuantityString(
                        R.plurals.status_steps_title, missing.size(), missing.size()));
                statusSub.setText(R.string.status_steps_sub);
                Permissions.Step step = missing.get(0);
                String description = BatteryHint.description(this, step);
                if (Permissions.showRestrictedHint(this, step)) {
                    description += "\n\n" + getString(R.string.restricted_hint);
                }
                showAttention(getString(step.title), description, getString(R.string.allow),
                        v -> Permissions.open(this, step));
                break;
            case NOT_RECEIVING:
                paintStatus(R.color.amber_card, R.color.amber, R.color.on_amber, R.drawable.ic_warning);
                statusTitle.setText(R.string.status_unbound_title);
                statusSub.setText(R.string.status_unbound_sub);
                String unboundDesc = BatteryHint.withBrandHint(this, getString(R.string.unbound_desc));
                if (fixTried) {
                    showAttention(getString(R.string.unbound_title), unboundDesc,
                            getString(R.string.open_wristline_settings),
                            v -> startActivity(Permissions.appInfo(this)));
                } else {
                    showAttention(getString(R.string.unbound_title), unboundDesc, getString(R.string.fix),
                            v -> fix());
                }
                break;
            case NEEDS_REPAIR:
                paintStatus(R.color.amber_card, R.color.amber, R.color.on_amber, R.drawable.ic_warning);
                statusTitle.setText(R.string.status_repair_title);
                statusSub.setText(getString(R.string.status_repair_sub, AppUi.labels(this, broken)));
                showAttention(null, null, getString(R.string.repair),
                        v -> startActivity(new Intent(this, VoiceActivity.class)));
                break;
            case TRY_IT:
                paintStatus(R.color.ready_card, R.color.green, R.color.ground, R.drawable.ic_watch);
                statusTitle.setText(R.string.status_try_title);
                statusSub.setText(R.string.status_try_sub);
                boolean missed = Prefs.testCallResult(this) == TestCall.Result.MISSED;
                showAttention(getString(missed ? R.string.try_missed_title : R.string.try_title),
                        getString(missed ? R.string.try_missed_desc : R.string.try_desc),
                        getString(R.string.row_test_call), v -> ringWatch());
                break;
            default:
                paintStatus(R.color.ready_card, R.color.green, R.color.ground, R.drawable.ic_watch);
                statusTitle.setText(R.string.status_ready_title);
                statusSub.setText(R.string.status_ready_sub);
                break;
        }
    }

    private void fix() {
        if (!ListenerRebind.unbound(this)) {
            refresh();
            return;
        }
        ListenerRebind.toggle(this, ListenerRebind.FIX);
        fixTried = true;
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (!isDestroyed()) {
                refresh();
            }
        }, ListenerRebind.CHECK_AFTER_MS);
    }

    private void paintStatus(int cardColor, int iconBgColor, int iconColor, int icon) {
        statusCard.setBackgroundTintList(ColorStateList.valueOf(getColor(cardColor)));
        statusIconBg.setBackgroundTintList(ColorStateList.valueOf(getColor(iconBgColor)));
        statusIcon.setImageResource(icon);
        statusIcon.setImageTintList(ColorStateList.valueOf(getColor(iconColor)));
    }

    private void showAttention(String title, String description, String button, View.OnClickListener onClick) {
        attentionBox.setVisibility(View.VISIBLE);
        attentionTitle.setVisibility(title == null ? View.GONE : View.VISIBLE);
        attentionDesc.setVisibility(description == null ? View.GONE : View.VISIBLE);
        attentionTitle.setText(title);
        attentionDesc.setText(description);
        attentionButton.setText(button);
        attentionButton.setOnClickListener(onClick);
    }

    private void bindApps(List<SupportedApp> installed, boolean relayOn) {
        appsList.removeAllViews();
        appsList.setVisibility(installed.isEmpty() ? View.GONE : View.VISIBLE);
        noApps.setVisibility(installed.isEmpty() ? View.VISIBLE : View.GONE);
        appsList.setAlpha(relayOn ? 1f : 0.45f);
        int on = 0;
        LayoutInflater inflater = getLayoutInflater();
        for (SupportedApp app : installed) {
            if (appsList.getChildCount() > 0) {
                appsList.addView(AppUi.divider(this));
            }
            View row = inflater.inflate(R.layout.item_app, appsList, false);
            boolean appOn = Prefs.appOn(this, app);
            if (appOn) {
                on++;
            }
            ((ImageView) row.findViewById(R.id.app_icon)).setImageDrawable(AppUi.icon(this, app));
            ((TextView) row.findViewById(R.id.app_name)).setText(app.label);
            row.findViewById(R.id.app_beta).setVisibility(app.beta ? View.VISIBLE : View.GONE);
            ((TextView) row.findViewById(R.id.app_sub)).setText(!appOn ? getString(R.string.app_sub_off)
                    : FullMode.shouldUse(this, app) ? getString(R.string.app_sub_voice_watch)
                    : getString(R.string.app_sub_voice_phone));
            Switch toggle = row.findViewById(R.id.app_switch);
            toggle.setChecked(appOn);
            toggle.setContentDescription(app.label);
            toggle.setOnCheckedChangeListener((button, checked) -> {
                Prefs.setAppOn(this, app, checked);
                refresh();
            });
            row.setOnClickListener(v -> toggle.toggle());
            appsList.addView(row);
        }
        appsCount.setText(getString(R.string.apps_count, on, installed.size()));
    }

    private void bindWatchCallingRow() {
        TextView sub = rowWatchCalling.findViewById(R.id.row_sub);
        if (!Prefs.watchCalling(this)) {
            sub.setText(R.string.watch_calling_off);
            return;
        }
        if (!watchCallingPermitted()) {
            // On from before this permission existed (or revoked): one tap asks again.
            sub.setText(R.string.watch_calling_needs_permission);
            rowWatchCalling.setOnClickListener(v -> requestPermissions(WATCH_CALLING_PERMISSIONS, REQUEST_CONTACTS));
            return;
        }
        rowWatchCalling.setOnClickListener(v -> watchCallingDialog());
        new Thread(() -> {
            int count = WatchSync.count(this);
            WatchStatus.Kind watch = WatchSync.status(this);
            runOnUiThread(() -> {
                String text = count == 0 ? getString(R.string.watch_calling_none)
                        : getString(R.string.watch_calling_on, count);
                if (watch == WatchStatus.Kind.NO_WATCH) {
                    text += "\n" + getString(R.string.watch_not_connected);
                } else if (watch == WatchStatus.Kind.APP_MISSING) {
                    text += "\n" + getString(R.string.watch_app_missing);
                }
                sub.setText(text);
            });
        }).start();
    }

    private void bindHistoryRow() {
        List<CallHistory.Entry> history = Prefs.callHistory(this);
        ((TextView) rowHistory.findViewById(R.id.row_sub)).setText(history.isEmpty()
                ? getString(R.string.history_empty)
                : getString(R.string.history_summary, history.size(), CallHistory.fromWatch(history)));
    }

    /** The last relayed calls, newest first, with Clear (also clears the watch's Recent list). */
    private void historyDialog() {
        List<CallHistory.Entry> history = Prefs.callHistory(this);
        if (history.isEmpty()) {
            new AlertDialog.Builder(this).setTitle(R.string.row_history).setMessage(R.string.history_empty)
                    .setPositiveButton(R.string.done, null).show();
            return;
        }
        CharSequence[] lines = new CharSequence[history.size()];
        for (int i = 0; i < lines.length; i++) {
            CallHistory.Entry e = history.get(i);
            SupportedApp app = SupportedApp.forPackage(e.app);
            String when = android.text.format.DateUtils.formatSameDayTime(e.time, System.currentTimeMillis(),
                    java.text.DateFormat.SHORT, java.text.DateFormat.SHORT).toString();
            lines[i] = getString(e.outgoing ? R.string.history_outgoing
                            : e.missed ? R.string.history_missed : R.string.history_incoming,
                    e.name, app == null ? e.app : app.label, when);
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.row_history)
                .setItems(lines, null)
                .setPositiveButton(R.string.done, null)
                .setNegativeButton(R.string.history_clear, (d, w) -> {
                    Prefs.clearCallHistory(this);
                    WatchSync.publish(this);
                    bindHistoryRow();
                })
                .show();
    }

    /** "When the watch rings": follow Do Not Disturb, and not while the phone is in use. */
    private void dndDialog() {
        boolean[] checked = {Prefs.followDnd(this), Prefs.notWhileInUse(this)};
        new AlertDialog.Builder(this)
                .setTitle(R.string.row_dnd)
                .setMultiChoiceItems(new CharSequence[] {getString(R.string.dnd_follow), getString(R.string.in_use_rule)},
                        checked, (d, which, isChecked) -> checked[which] = isChecked)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    Prefs.setFollowDnd(this, checked[0]);
                    Prefs.setNotWhileInUse(this, checked[1]);
                    refresh();
                })
                .setNeutralButton(R.string.rules_explain, (d, w) -> new AlertDialog.Builder(this)
                        .setTitle(R.string.row_dnd)
                        .setMessage(R.string.dnd_message)
                        .setPositiveButton(android.R.string.ok, null)
                        .show())
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void ringWatch() {
        int result;
        if (RelayConnection.current != null || RelayListener.currentApp != null) {
            result = R.string.test_call_busy;
        } else {
            result = RelayListener.startTestCall(this) ? R.string.test_call_started : R.string.test_call_failed;
        }
        Toast.makeText(this, result, Toast.LENGTH_LONG).show();
    }

    private static void bindRow(View row, int icon, int title) {
        ((ImageView) row.findViewById(R.id.row_icon)).setImageResource(icon);
        ((TextView) row.findViewById(R.id.row_title)).setText(title);
    }
}
