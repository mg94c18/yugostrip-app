package org.mg94c18.alanford.sync;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.mg94c18.alanford.BuildConfig;

import java.util.UUID;

import static org.mg94c18.alanford.Logger.LOG_V;
import static org.mg94c18.alanford.Logger.TAG;

public class DummyAccount {
    // Constants
    // An account type, in the form of a domain name
    private static final String ACCOUNT_TYPE = "org.mg94c18.alanford";
    private static final String PREF_ACCOUNT_NAME = "accountName";

    /**
     * Create a new dummy account for the sync adapter
     *
     * @param context The application context
     */
    public static Account getSyncAccount(Context context) {
        SharedPreferences preferences = context.getSharedPreferences(SyncAdapter.SHARED_PREFS_NAME, Context.MODE_PRIVATE);
        String accountName = preferences.getString(PREF_ACCOUNT_NAME, null);
        if (accountName != null) {
            return new Account(accountName, ACCOUNT_TYPE);
        }

        accountName = UUID.randomUUID().toString();
        // Create the account type and default account
        Account newAccount = new Account(accountName, ACCOUNT_TYPE);
        // Get an instance of the Android account manager
        AccountManager accountManager =
                (AccountManager) context.getSystemService(
                        Context.ACCOUNT_SERVICE);
        if (accountManager == null) {
            return null;
        }

        /*
         * Add the account and account type, no password or user data
         * If successful, return the Account object, otherwise report an error.
         */
        if (accountManager.addAccountExplicitly(newAccount, null, null)) {
            /*
             * If you don't set android:syncable="true" in
             * in your <provider> element in the manifest,
             * then call context.setIsSyncable(account, AUTHORITY, 1)
             * here.
             */
            if (BuildConfig.DEBUG) { LOG_V("Added the sync account " + newAccount.name); }
            preferences.edit().putString(PREF_ACCOUNT_NAME, accountName).apply();
            return newAccount;
        } else {
            /*
             * The account exists or some other error occurred. Log this, report it,
             * or handle it internally.
             */
            Log.wtf(TAG,"Cannot add the sync account " + newAccount.name + ", assuming already added");
            return newAccount;
        }
    }
}
