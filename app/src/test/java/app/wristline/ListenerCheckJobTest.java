package app.wristline;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.Context;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.List;

/** While relaying is on, a periodic job checks the notification listener is still bound. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 36)
public class ListenerCheckJobTest {
    private Context context;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
    }

    @After
    public void tearDown() {
        context.getSystemService(JobScheduler.class).cancelAll();
    }

    private List<JobInfo> jobs() {
        return context.getSystemService(JobScheduler.class).getAllPendingJobs();
    }

    @Test
    public void relayingOnSchedulesTheCheck() {
        Prefs.setRelayOn(context, true);

        ListenerCheckJob.sync(context);

        assertEquals(1, jobs().size());
        JobInfo job = jobs().get(0);
        assertEquals(ListenerCheckJob.JOB_ID, job.getId());
        assertTrue(job.isPeriodic());
        assertEquals(15 * 60_000L, job.getIntervalMillis());
        assertFalse(job.isPersisted());
    }

    @Test
    public void pausingCancelsTheCheck() {
        Prefs.setRelayOn(context, true);
        ListenerCheckJob.sync(context);

        Relaying.set(context, false);

        assertEquals(0, jobs().size());
    }

    @Test
    public void resumingSchedulesItAgain() {
        Relaying.set(context, false);

        Relaying.set(context, true);

        assertEquals(1, jobs().size());
    }

    @Test
    public void connectingSchedulesIt() {
        Prefs.setRelayOn(context, true);
        assertEquals(0, jobs().size());
        RelayListener listener = Robolectric.buildService(RelayListener.class).create().get();

        listener.onListenerConnected();

        assertEquals(1, jobs().size());
        listener.onListenerDisconnected();
    }
}
