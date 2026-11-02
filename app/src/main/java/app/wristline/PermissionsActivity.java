package app.wristline;

import android.app.Activity;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.util.List;

/** Checklist of the four permissions, with one button for each that's missing. */
public class PermissionsActivity extends Activity {
    private LinearLayout steps;
    private ProgressBar progress;
    private TextView progressText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_permissions);
        ((TextView) findViewById(R.id.screen_title)).setText(R.string.row_permissions);
        findViewById(R.id.back).setOnClickListener(v -> finish());
        findViewById(R.id.open_bluetooth).setOnClickListener(v ->
                startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS)));
        steps = findViewById(R.id.steps);
        progress = findViewById(R.id.progress);
        progressText = findViewById(R.id.progress_text);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        refresh();
    }

    private void refresh() {
        Permissions.Step[] all = Permissions.Step.values();
        List<Permissions.Step> missing = Permissions.missing(this);
        int done = all.length - missing.size();
        progress.setMax(all.length);
        progress.setProgress(done);
        progressText.setText(getString(R.string.permissions_progress, done, all.length));

        steps.removeAllViews();
        for (Permissions.Step step : all) {
            if (steps.getChildCount() > 0) {
                steps.addView(AppUi.divider(this));
            }
            boolean isDone = !missing.contains(step);
            View row = getLayoutInflater().inflate(R.layout.item_step, steps, false);
            ((TextView) row.findViewById(R.id.step_title)).setText(step.title);
            ((TextView) row.findViewById(R.id.step_desc)).setText(BatteryHint.description(this, step));
            View dot = row.findViewById(R.id.step_dot);
            if (isDone) {
                dot.setBackgroundResource(R.drawable.bg_circle);
                dot.setBackgroundTintList(ColorStateList.valueOf(getColor(R.color.green)));
            } else {
                dot.setBackgroundResource(R.drawable.bg_step_todo);
                row.setBackgroundColor(getColor(R.color.highlight_row));
            }
            dot.setContentDescription(getString(isDone ? R.string.step_done : R.string.step_not_done));
            row.findViewById(R.id.step_check).setVisibility(isDone ? View.VISIBLE : View.GONE);
            boolean hint = Permissions.showRestrictedHint(this, step);
            row.findViewById(R.id.step_hint).setVisibility(hint ? View.VISIBLE : View.GONE);
            View hintButton = row.findViewById(R.id.step_hint_button);
            hintButton.setVisibility(hint ? View.VISIBLE : View.GONE);
            hintButton.setOnClickListener(v -> startActivity(Permissions.appInfo(this)));
            View button = row.findViewById(R.id.step_button);
            button.setVisibility(isDone ? View.GONE : View.VISIBLE);
            button.setOnClickListener(v -> Permissions.open(this, step));
            steps.addView(row);
        }
    }
}
