package app.wristline;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import app.wristline.adb.AdbKey;
import app.wristline.adb.AdbShell;

import java.util.ArrayList;
import java.util.List;

/** Voice through the watch: where each app's call audio goes, and the commands to change that. */
public class VoiceActivity extends Activity {
    private LinearLayout appsList;
    private View noApps;
    private View setUpSection;
    private Button setUpOnPhone;
    private TextView setUpNote;
    private View setUp;
    private View turnOff;
    private View forgetKey;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_voice);
        ((TextView) findViewById(R.id.screen_title)).setText(R.string.row_voice);
        findViewById(R.id.back).setOnClickListener(v -> finish());
        appsList = findViewById(R.id.voice_apps);
        noApps = findViewById(R.id.voice_no_apps);
        setUpSection = findViewById(R.id.set_up_section);
        setUpOnPhone = findViewById(R.id.set_up_on_phone);
        setUpNote = findViewById(R.id.set_up_note);
        setUp = findViewById(R.id.set_up);
        turnOff = findViewById(R.id.turn_off);
        forgetKey = findViewById(R.id.forget_key);
        setUp.setOnClickListener(v ->
                ComputerSetup.show(this, appsIn(VoiceState.SET_UP, VoiceState.NEEDS_REPAIR), this::refresh));
        setUpOnPhone.setOnClickListener(v -> askConsent());
        turnOff.setOnClickListener(v -> turnOff());
        forgetKey.setOnClickListener(v -> askForgetKey());
    }

    @Override
    protected void onResume() {
        super.onResume();
        SetupProgress.listener = () -> runOnUiThread(this::refresh);
        refresh();
    }

    @Override
    protected void onPause() {
        SetupProgress.listener = null;
        super.onPause();
    }

    /** Installed apps the user switched on. */
    private List<SupportedApp> relayedApps() {
        List<SupportedApp> apps = new ArrayList<>();
        for (SupportedApp app : AppUi.installedApps(this)) {
            if (Prefs.appOn(this, app)) {
                apps.add(app);
            }
        }
        return apps;
    }

    private List<SupportedApp> appsIn(VoiceState... states) {
        List<SupportedApp> apps = new ArrayList<>();
        for (SupportedApp app : relayedApps()) {
            VoiceState state = FullMode.state(this, app);
            for (VoiceState wanted : states) {
                if (state == wanted) {
                    apps.add(app);
                }
            }
        }
        return apps;
    }

    private void refresh() {
        List<SupportedApp> apps = relayedApps();
        appsList.removeAllViews();
        appsList.setVisibility(apps.isEmpty() ? View.GONE : View.VISIBLE);
        noApps.setVisibility(apps.isEmpty() ? View.VISIBLE : View.GONE);
        for (SupportedApp app : apps) {
            if (appsList.getChildCount() > 0) {
                appsList.addView(AppUi.divider(this));
            }
            View row = getLayoutInflater().inflate(R.layout.item_voice_app, appsList, false);
            ((android.widget.ImageView) row.findViewById(R.id.app_icon)).setImageDrawable(AppUi.icon(this, app));
            ((TextView) row.findViewById(R.id.app_name)).setText(app.label);
            bindState(row, FullMode.state(this, app));
            appsList.addView(row);
        }

        List<SupportedApp> needSetUp = appsIn(VoiceState.SET_UP, VoiceState.NEEDS_REPAIR);
        setUpSection.setVisibility(needSetUp.isEmpty() ? View.GONE : View.VISIBLE);
        boolean busy = SetupProgress.running();
        setUpOnPhone.setEnabled(!busy);
        setUpOnPhone.setAlpha(busy ? 0.5f : 1f);
        setUpNote.setVisibility(busy ? View.VISIBLE : View.GONE);
        turnOff.setVisibility(FullMode.appsWithVoiceOn(this).isEmpty() ? View.GONE : View.VISIBLE);
        forgetKey.setVisibility(AdbKey.exists(this) ? View.VISIBLE : View.GONE);
    }

    private void bindState(View row, VoiceState state) {
        TextView sub = row.findViewById(R.id.app_sub);
        TextView pill = row.findViewById(R.id.app_pill);
        int text;
        int pillText;
        int pillBg;
        int pillFg;
        switch (state) {
            case ON:
                text = R.string.voice_state_on;
                pillText = R.string.pill_on;
                pillBg = R.color.green_chip;
                pillFg = R.color.green;
                break;
            case SET_UP:
                text = R.string.voice_state_set_up;
                pillText = R.string.pill_set_up;
                pillBg = R.color.chip;
                pillFg = R.color.chip_text;
                break;
            case NEEDS_REPAIR:
                text = R.string.voice_state_repair;
                pillText = R.string.pill_repair;
                pillBg = R.color.amber_card;
                pillFg = R.color.amber;
                break;
            default:
                text = R.string.voice_state_not_verified;
                pillText = R.string.pill_not_verified;
                pillBg = R.color.neutral_chip;
                pillFg = R.color.muted;
                break;
        }
        sub.setText(text);
        sub.setTextColor(getColor(state == VoiceState.ON ? R.color.green : R.color.muted));
        pill.setText(pillText);
        pill.setBackgroundTintList(ColorStateList.valueOf(getColor(pillBg)));
        pill.setTextColor(getColor(pillFg));
    }

    private void turnOff() {
        // Not only the relayed apps: one with its switch off keeps the setting until it's undone too.
        List<SupportedApp> apps = FullMode.appsWithVoiceOn(this);
        List<SupportedApp> withSetting = new ArrayList<>();
        for (SupportedApp app : apps) {
            if (FullMode.needsAppOp(this, app)) {
                withSetting.add(app);
            }
        }
        if (withSetting.isEmpty()) {
            // Nothing to undo (Instagram, or the app is gone).
            FullMode.markTurnedOff(this, apps);
            refresh();
            return;
        }
        if (!AdbKey.exists(this)) {
            // No key: adbd can't let Wristline in, so go straight to the computer instructions.
            showResetInstructions(apps, withSetting);
            return;
        }
        turnOff.setEnabled(false);
        new Thread(() -> {
            boolean reset = AdbShell.isReachable(this) && FullMode.disable(this, apps);
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) {
                    return; // left the screen while ADB was being looked for: no dialog on a dead window
                }
                turnOff.setEnabled(true);
                refresh();
                if (!reset) {
                    showResetInstructions(apps, withSetting);
                }
            });
        }).start();
    }

    /** The commands that give the apps their setting back, to run on a computer. */
    private void showResetInstructions(List<SupportedApp> apps, List<SupportedApp> withSetting) {
        StringBuilder lines = new StringBuilder();
        for (SupportedApp app : withSetting) {
            lines.append(lines.length() > 0 ? "\n" : "").append("adb shell ").append(AppOpsCommands.reset(app.packageName));
        }
        String commands = lines.toString();
        new AlertDialog.Builder(this)
                .setTitle(R.string.reset_title)
                .setMessage(getString(R.string.reset_message, commands))
                .setNeutralButton(R.string.copy, (d, w) -> ComputerSetup.copy(this, commands))
                .setPositiveButton(R.string.adb_done, (d, w) -> {
                    FullMode.markTurnedOff(this, apps);
                    refresh();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    // On-phone setup, step 1: the user agrees to the exact change for each app.
    private void askConsent() {
        List<SupportedApp> apps = appsIn(VoiceState.SET_UP, VoiceState.NEEDS_REPAIR);
        if (apps.isEmpty()) {
            return;
        }
        StringBuilder message = new StringBuilder();
        for (SupportedApp app : apps) {
            message.append(message.length() > 0 ? "\n\n" : "").append(getString(R.string.consent_app, app.label));
        }
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.consent_title, AppUi.labels(this, apps)))
                .setMessage(message)
                .setPositiveButton(R.string.agree, (d, w) -> {
                    for (SupportedApp app : apps) {
                        Prefs.setConsent(this, app, true);
                    }
                    startActivity(VoiceSetupActivity.intent(this, apps));
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void askForgetKey() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.forget_key_title)
                .setMessage(R.string.forget_key_message)
                .setPositiveButton(R.string.forget_key_confirm, (d, w) -> {
                    AdbShell.forgetKey(this);
                    refresh();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }
}
