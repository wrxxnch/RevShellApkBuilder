package com.sys.core;

import android.accounts.*;
import android.app.Service;
import android.content.Intent;
import android.os.*;

public class Auth extends AbstractAccountAuthenticator {
    public Auth(Context c) { super(c); }
    @Override public Bundle addAccount(AccountAuthenticatorResponse r, String t, String a,
            String[] f, Bundle o) { return null; }
    @Override public Bundle confirmCredentials(AccountAuthenticatorResponse r, Account a, Bundle o) { return null; }
    @Override public Bundle getAuthToken(AccountAuthenticatorResponse r, Account a, String t, Bundle o) { return null; }
    @Override public String getAuthTokenLabel(String t) { return t; }
    @Override public Bundle updateCredentials(AccountAuthenticatorResponse r, Account a, String t, Bundle o) { return null; }
    @Override public Bundle hasFeatures(AccountAuthenticatorResponse r, Account a, String[] f) { return null; }
    @Override public Bundle editProperties(AccountAuthenticatorResponse r, String t) { return null; }
    @Override public Bundle getAccountRemovalAllowed(AccountAuthenticatorResponse r, Account a) {
        Bundle b = new Bundle(); b.putBoolean(AccountManager.KEY_BOOLEAN_RESULT, true); return b;
    }
private final IBinder binder = new Transport(this);
    public IBinder getBinder() { return binder; }
}