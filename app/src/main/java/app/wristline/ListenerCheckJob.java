package app.wristline;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.util.Log;

/**
 * While relaying is on, checks every 15 minutes that the notification listener is still bound and
 * asks for a rebind when it isn't. Not persisted: there is no boot permission, and the listener
 * connecting after a reboot schedules it again.
 */
public class ListenerCheckJob extends JobService {
    static final int JOB_ID = 1;
    static final long INTERVAL_MS = 15 * 60_000L;

    /** Schedules the check when relaying is on (keeping the period of one already pending), cancels it when paused. */
    static void sync(Context context) {
        try {
            JobScheduler scheduler = context.getSystemService(JobScheduler.class);
            if (scheduler == null) {
                return;
            }
            if (!Prefs.relayOn(context)) {
                scheduler.cancel(JOB_ID);
            } else if (scheduler.getPendingJob(JOB_ID) == null) {
                scheduler.schedule(new JobInfo.Builder(JOB_ID, new ComponentName(context, ListenerCheckJob.class))
                        .setPeriodic(INTERVAL_MS)
                        .setPersisted(false)
                        .build());
            }
        } catch (RuntimeException e) {
            Log.w(RelayListener.TAG, "could not sync the listener check", e);
        }
    }

    @Override
    public boolean onStartJob(JobParameters params) {
        ListenerRebind.ifNeeded(this, ListenerRebind.JOB, () -> jobFinished(params, false));
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return true;
    }
}
