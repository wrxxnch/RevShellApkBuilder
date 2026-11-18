package com.sys.core;

import android.content.*;

public class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent i) {
        MainActivity.startServiceCompat(c);
        Watchdog.schedule(c);
    }
}