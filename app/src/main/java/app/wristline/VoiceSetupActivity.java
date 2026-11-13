package app.wristline;

import android.app.Activity;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.database.ContentObserver;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import app.wristline.adb.AdbDiscovery;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Guided on-phone setup for voice through the watch: notifications, Wi-Fi, Developer options,
 * Wireless debugging, then pairing with the code. Each step ticks itself off; a failure shows on the step it happened
 * at with Try again, and the steps already done stay done.
 */
public class VoiceSetupActivity extends Activity {
    static final String EXTRA_PACKAGES = "packages";
    /** Settings.Global.ADB_WIFI_ENABLED is hidden API; the key itself is stable since Android 11. */
    private static final String ADB_WIFI_ENABLED = "adb_wifi_enabled";
    private static final long TICK_MS = 1000;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            refresh();
            handler.postDelayed(this, TICK_MS);
        }
    };

    private LinearLayout stepsList;
    private View doneCard;
    private TextView doneText;
    private final View[] steps = new View[4];
    private ArrayList<String> packages;
    private boolean devOptionsConfirmed;

    static Intent intent(Activity from, List<SupportedApp> apps) {
        ArrayList<String> packages = new ArrayList<>();
        for (SupportedApp app : apps) {
            packages.add(app.packageName);
        }
        return new Intent(from, VoiceSetupActivity.class).putStringArrayListExtra(EXTRA_PACKAGES, packages);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        devOptionsConfirmed = savedInstanceState != null && savedInstanceState.getBoolean("dev_confirmed");
        setContentView(R.layout.activity_voice_setup);
        ((TextView) findViewById(R.id.screen_title)).setText(R.string.guide_title);
        findViewById(R.id.back).setOnClickListener(v -> finish());
        stepsList = findViewById(R.id.guide_steps);
        doneCard = findViewById(R.id.guide_done);
        doneText = findViewById(R.id.guide_done_text);
        findViewById(R.id.guide_finish).setOnClickListener(v -> finish());
        findViewById(R.id.guide_turn_off_debugging).setOnClickListener(v ->
                startActivity(new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)));
        findViewById(R.id.guide_use_computer).setOnClickListener(v ->
                ComputerSetup.show(this, apps(), this::finish));

        packages = getIntent().getStringArrayListExtra(EXTRA_PACKAGES);
        if (packages == null || packages.isEmpty()) {
            // Opened from a notification: set up whatever still needs it.
            packages = new ArrayList<>();
            for (SupportedApp app : appsNeedingSetUp()) {
                packages.add(app.packageName);
            }
        }
        // A finished run's result stays (DONE shows the done card, FAILED waits for Try again);
        // only a DONE left from an earlier run for other apps is forgotten, so these get set up.
        if (SetupProgress.phase() == SetupProgress.Phase.DONE && !stillNeedingSetUp().isEmpty()) {
            SetupProgress.set(SetupProgress.Phase.IDLE);
        }
        for (int i = 0; i < steps.length; i++) {
            if (i > 0) {
                stepsList.addView(AppUi.divider(this));
            }
            steps[i] = getLayoutInflater().inflate(R.layout.item_guide_step, stepsList, false);
            ((TextView) steps[i].findViewById(R.id.step_number)).setText(String.valueOf(i + 1));
            stepsList.addView(steps[i]);
        }
        LinearLayout pairExtra = steps[3].findViewById(R.id.step_extra);
        getLayoutInflater().inflate(R.layout.view_code_example, pairExtra, true);
        pairExtra.addView(hint(R.string.guide_pair_arrow, R.color.muted));
        pairExtra.addView(hint(R.string.guide_pair_warning, R.color.amber));
        // Notice Wireless debugging going on while the user is still in Settings, so they can go
        // straight on to the pairing code instead of coming back here first.
        getContentResolver().registerContentObserver(
                Settings.Global.getUriFor(ADB_WIFI_ENABLED), false, debuggingObserver);
    }

    private final ContentObserver debuggingObserver = new ContentObserver(handler) {
        @Override
        public void onChange(boolean selfChange) {
            startIfReady();
        }
    };

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean("dev_confirmed", devOptionsConfirmed);
    }

    @Override
    protected void onDestroy() {
        getContentResolver().unregisterContentObserver(debuggingObserver);
        super.onDestroy();
    }

    private boolean wirelessDebuggingOn() {
        return Settings.Global.getInt(getContentResolver(), ADB_WIFI_ENABLED, 0) != 0;
    }

    private boolean developerOptionsOn() {
        return GuideSteps.developerOptionsOn(
                Settings.Global.getInt(getContentResolver(), Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0),
                Settings.Global.getInt(getContentResolver(), Settings.Global.ADB_ENABLED, 0),
                Settings.Global.getInt(getContentResolver(), ADB_WIFI_ENABLED, 0),
                devOptionsConfirmed);
    }

    /** Everything is on, nothing has run yet and an app still needs it: start waiting for the pairing screen. */
    private void startIfReady() {
        if (SetupProgress.phase() == SetupProgress.Phase.IDLE
                && !stillNeedingSetUp().isEmpty()
                && SetupStart.ready(Permissions.canShowCodePopUp(this), AdbDiscovery.onWifi(this),
                        developerOptionsOn(), wirelessDebuggingOn())) {
            startService();
        }
    }

    private TextView hint(int text, int color) {
        TextView view = new TextView(this, null, 0, R.style.Text_Caption);
        view.setText(text);
        view.setTextColor(getColor(color));
        view.setPadding(0, Math.round(10 * getResources().getDisplayMetrics().density), 0, 0);
        return view;
    }

    @Override
    protected void onResume() {
        super.onResume();
        SetupProgress.listener = () -> runOnUiThread(this::refresh);
        handler.post(tick);
    }

    @Override
    protected void onPause() {
        SetupProgress.listener = null;
        handler.removeCallbacks(tick);
        super.onPause();
    }

    private List<SupportedApp> apps() {
        List<SupportedApp> apps = new ArrayList<>();
        for (String pkg : packages) {
            SupportedApp app = SupportedApp.forPackage(pkg);
            if (app != null) {
                apps.add(app);
            }
        }
        return apps;
    }

    /** This guide's apps that are not on yet. */
    private List<SupportedApp> stillNeedingSetUp() {
        List<SupportedApp> apps = new ArrayList<>();
        for (SupportedApp app : apps()) {
            if (FullMode.state(this, app) != VoiceState.ON) {
                apps.add(app);
            }
        }
        return apps;
    }

    private List<SupportedApp> appsNeedingSetUp() {
        List<SupportedApp> apps = new ArrayList<>();
        for (SupportedApp app : AppUi.installedApps(this)) {
            VoiceState state = FullMode.state(this, app);
            if (Prefs.appOn(this, app) && Prefs.consent(this, app) && (state == VoiceState.SET_UP || state == VoiceState.NEEDS_REPAIR)) {
                apps.add(app);
            }
        }
        return apps;
    }

    /**
     * Opened from a notification with nothing left that may be set up (and no run going): the done
     * card when every app is already on, else a plain "nothing to set up", never a guide that waits.
     */
    private void showNothingLeft() {
        List<SupportedApp> on = appsOn();
        boolean allOn = true;
        for (SupportedApp app : AppUi.installedApps(this)) {
            if (Prefs.appOn(this, app) && !on.contains(app)) {
                allOn = false;
            }
        }
        if (allOn && !on.isEmpty()) {
            showDone(on);
            return;
        }
        stepsList.setVisibility(View.GONE);
        findViewById(R.id.guide_intro).setVisibility(View.GONE);
        findViewById(R.id.guide_use_computer).setVisibility(View.GONE);
        findViewById(R.id.guide_nothing).setVisibility(View.VISIBLE);
        doneCard.setVisibility(View.GONE);
    }

    /** The relayed apps whose voice through the watch is on. */
    private List<SupportedApp> appsOn() {
        List<SupportedApp> on = new ArrayList<>();
        for (SupportedApp app : AppUi.installedApps(this)) {
            if (Prefs.appOn(this, app) && FullMode.state(this, app) == VoiceState.ON) {
                on.add(app);
            }
        }
        return on;
    }

    private void showDone(List<SupportedApp> apps) {
        stepsList.setVisibility(View.GONE);
        findViewById(R.id.guide_intro).setVisibility(View.GONE);
        findViewById(R.id.guide_use_computer).setVisibility(View.GONE);
        findViewById(R.id.guide_nothing).setVisibility(View.GONE);
        doneCard.setVisibility(View.VISIBLE);
        doneText.setText(getString(R.string.guide_done_text, AppUi.labels(this, apps)));
    }

    private void refresh() {
        SetupProgress.Phase current = SetupProgress.phase();
        if (!SetupProgress.running() && current != SetupProgress.Phase.FAILED) {
            if (packages.isEmpty() && current != SetupProgress.Phase.DONE) {
                showNothingLeft();
                return;
            }
            if (packages.isEmpty() || stillNeedingSetUp().isEmpty()) {
                // Every app of this guide is on (just now, or before it opened): done, never a new run.
                showDone(packages.isEmpty() ? appsOn() : apps());
                return;
            }
        }
        startIfReady();
        boolean notifications = Permissions.canShowCodePopUp(this);
        boolean wifi = AdbDiscovery.onWifi(this);
        boolean devOptions = developerOptionsOn();
        SetupProgress.Phase phase = SetupProgress.phase();
        GuideSteps.Status[] status = GuideSteps.of(notifications, wifi, devOptions, phase);
        doneCard.setVisibility(phase == SetupProgress.Phase.DONE ? View.VISIBLE : View.GONE);
        stepsList.setVisibility(phase == SetupProgress.Phase.DONE ? View.GONE : View.VISIBLE);
        if (phase == SetupProgress.Phase.DONE) {
            showDone(apps());
            return;
        }

        bindStep(0, status[0], R.string.guide_step_notifications,
                getString(status[0] == GuideSteps.Status.DONE ? R.string.guide_step_notifications_done : R.string.guide_step_notifications_todo),
                R.string.allow, v -> allowNotifications());
        bindStep(1, status[1], R.string.guide_step_wifi,
                status[1] == GuideSteps.Status.DONE ? getString(R.string.guide_step_wifi_done) : getString(R.string.guide_step_wifi_todo),
                R.string.open_wifi_settings, v -> startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS)));
        bindStep(2, status[2], R.string.guide_step_dev,
                status[2] == GuideSteps.Status.DONE ? getString(R.string.guide_step_dev_done)
                        : getString(R.string.guide_step_dev_todo, getString(developerOptionsPath())),
                R.string.open_about_phone, v -> startActivity(new Intent(Settings.ACTION_DEVICE_INFO_SETTINGS)));
        bindPairStep(status[3], phase);
    }

    private void allowNotifications() {
        if (Permissions.isDone(this, Permissions.Step.POST_NOTIFICATIONS)) {
            // Allowed in general, but the pairing channel or the whole app was switched off in Settings.
            startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName()));
        } else {
            Permissions.open(this, Permissions.Step.POST_NOTIFICATIONS);
        }
    }

    private void bindStep(int index, GuideSteps.Status status, int title, String body, int button,
            View.OnClickListener onClick) {
        View step = steps[index];
        ((TextView) step.findViewById(R.id.step_title)).setText(title);
        TextView bodyView = step.findViewById(R.id.step_body);
        bodyView.setText(body);
        bodyView.setTextColor(getColor(status == GuideSteps.Status.DONE ? R.color.green : R.color.muted));
        paintDot(step, status);
        Button primary = step.findViewById(R.id.step_button);
        primary.setVisibility(status == GuideSteps.Status.CURRENT ? View.VISIBLE : View.GONE);
        primary.setText(button);
        primary.setOnClickListener(onClick);
        Button second = step.findViewById(R.id.step_second_button);
        second.setVisibility(View.GONE);
        if (index == 2 && status == GuideSteps.Status.CURRENT) {
            second.setVisibility(View.VISIBLE);
            second.setText(R.string.guide_dev_confirm);
            second.setOnClickListener(v -> {
                devOptionsConfirmed = true;
                refresh();
            });
        }
        step.setBackgroundColor(status == GuideSteps.Status.CURRENT ? getColor(R.color.highlight_row) : 0);
    }

    private void bindPairStep(GuideSteps.Status status, SetupProgress.Phase phase) {
        View step = steps[3];
        ((TextView) step.findViewById(R.id.step_title)).setText(R.string.guide_step_pair);
        paintDot(step, status);
        boolean active = status == GuideSteps.Status.CURRENT || status == GuideSteps.Status.FAILED;
        TextView body = step.findViewById(R.id.step_body);
        body.setText(R.string.guide_pair_how);
        body.setVisibility(active ? View.VISIBLE : View.GONE);
        step.findViewById(R.id.step_extra).setVisibility(status == GuideSteps.Status.CURRENT ? View.VISIBLE : View.GONE);

        TextView statusView = step.findViewById(R.id.step_status);
        statusView.setVisibility(active ? View.VISIBLE : View.GONE);
        Button primary = step.findViewById(R.id.step_button);
        Button second = step.findViewById(R.id.step_second_button);
        primary.setVisibility(active ? View.VISIBLE : View.GONE);
        second.setVisibility(View.GONE);
        if (status == GuideSteps.Status.FAILED) {
            String reason = SetupProgress.message();
            statusView.setText(reason == null ? getString(R.string.guide_failed_keep)
                    : reason + "\n" + getString(R.string.guide_failed_keep));
            statusView.setTextColor(getColor(R.color.amber));
            step.setBackgroundColor(getColor(R.color.amber_card));
            primary.setText(R.string.try_again);
            primary.setOnClickListener(v -> startService());
            second.setVisibility(View.VISIBLE);
            second.setText(R.string.open_wireless_debugging);
            second.setOnClickListener(v -> startActivity(AppUi.wirelessDebuggingSettings()));
            return;
        }
        step.setBackgroundColor(status == GuideSteps.Status.CURRENT ? getColor(R.color.highlight_row) : 0);
        statusView.setTextColor(getColor(R.color.text));
        statusView.setText(statusText(phase));
        primary.setText(R.string.open_wireless_debugging);
        primary.setOnClickListener(v -> startActivity(AppUi.wirelessDebuggingSettings()));
    }

    private String statusText(SetupProgress.Phase phase) {
        switch (phase) {
            case WAITING_FOR_PAIRING_SCREEN:
                return getString(R.string.guide_status_waiting_screen);
            case WAITING_FOR_CODE:
                long left = Math.max(0, SetupProgress.codeDeadlineWallMs() - System.currentTimeMillis()) / 1000;
                String time = String.format(Locale.ROOT, "%d:%02d", left / 60, left % 60);
                String waiting = getString(R.string.guide_status_waiting_code, time);
                String problem = SetupProgress.message();
                return problem == null ? waiting : problem + "\n" + waiting;
            case PAIRING:
                return getString(R.string.guide_status_pairing);
            case APPLYING:
                return getString(R.string.guide_status_applying);
            default:
                return getString(wirelessDebuggingOn() ? R.string.guide_status_starting
                        : R.string.guide_status_waiting_debugging);
        }
    }

    private void paintDot(View step, GuideSteps.Status status) {
        View dot = step.findViewById(R.id.step_dot);
        TextView number = step.findViewById(R.id.step_number);
        View check = step.findViewById(R.id.step_check);
        boolean done = status == GuideSteps.Status.DONE;
        boolean failed = status == GuideSteps.Status.FAILED;
        check.setVisibility(done ? View.VISIBLE : View.GONE);
        number.setVisibility(done ? View.GONE : View.VISIBLE);
        if (done || failed) {
            dot.setBackgroundResource(R.drawable.bg_circle);
            dot.setBackgroundTintList(ColorStateList.valueOf(getColor(done ? R.color.green : R.color.amber)));
            ((android.widget.ImageView) check).setImageTintList(ColorStateList.valueOf(getColor(R.color.ground)));
            number.setText(failed ? "!" : number.getText());
            number.setTextColor(getColor(R.color.on_amber));
        } else {
            dot.setBackgroundResource(status == GuideSteps.Status.CURRENT ? R.drawable.bg_step_todo : R.drawable.bg_circle);
            dot.setBackgroundTintList(status == GuideSteps.Status.CURRENT ? null
                    : ColorStateList.valueOf(getColor(R.color.neutral_chip)));
            int index = indexOf(step);
            number.setText(String.valueOf(index + 1));
            number.setTextColor(getColor(status == GuideSteps.Status.CURRENT ? R.color.accent : R.color.muted));
        }
        dot.setContentDescription(getString(done ? R.string.step_done : R.string.step_not_done));
    }

    private int indexOf(View step) {
        for (int i = 0; i < steps.length; i++) {
            if (steps[i] == step) {
                return i;
            }
        }
        return 0;
    }

    private void startService() {
        if (SetupProgress.running() || packages.isEmpty()) {
            return;
        }
        SetupProgress.set(SetupProgress.Phase.WAITING_FOR_PAIRING_SCREEN);
        try {
            startForegroundService(AdbSetupService.startIntent(this, packages));
        } catch (RuntimeException e) {
            // From Settings this needs the battery exemption; without it, start when the user is back.
            Log.w(RelayListener.TAG, "could not start setup in the background", e);
            SetupProgress.set(SetupProgress.Phase.IDLE);
        }
    }

    private int developerOptionsPath() {
        switch (Brand.of(Build.MANUFACTURER)) {
            case VIVO:
                return R.string.dev_path_vivo;
            case SAMSUNG:
                return R.string.dev_path_samsung;
            case GOOGLE:
                return R.string.dev_path_google;
            case XIAOMI:
                return R.string.dev_path_xiaomi;
            case ONEPLUS_OPPO:
                return R.string.dev_path_oneplus_oppo;
            case MOTOROLA:
                return R.string.dev_path_motorola;
            default:
                return R.string.dev_path_other;
        }
    }
}
