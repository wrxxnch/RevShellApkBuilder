package com.sys.core;

import android.app.job.*;
import android.app.*;
import android.content.*;
import android.os.*;

public class Watchdog extends JobService {

    static final int JOB = 7717;

    @Override public boolean onStartJob(JobParameters p) {
        MainActivity.startServiceCompat(this);
        schedule(this);
        return false;
    }
    @Override public boolean onStopJob(JobParameters p) { return true; }

    static void schedule(Context c) {
        try {
            JobScheduler js = (JobScheduler) c.getSystemService(JOB_SCHEDULER_SERVICE);
            JobInfo j = new JobInfo.Builder(JOB, new ComponentName(c, Watchdog.class))
                .setPeriodic(15 * 60 * 1000L)
                .setPersisted(true)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .build();
            js.schedule(j);
        } catch (Exception ignored) {}

        try {
            AlarmManager am = (AlarmManager) c.getSystemService(ALARM_SERVICE);
            Intent i = new Intent(c, BootReceiver.class);
            i.setAction("rs.tick");
            PendingIntent pi = PendingIntent.getBroadcast(c, 0, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            am.setInexactRepeating(AlarmManager.RTC_WAKEUP,
                System.currentTimeMillis() + 15 * 60 * 1000L,
                15 * 60 * 1000L, pi);
        } catch (Exception ignored) {}
    }
}