package com.example.expensemate.service;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import com.google.android.gms.auth.api.identity.AuthorizationRequest;
import com.google.android.gms.auth.api.identity.AuthorizationResult;
import com.google.android.gms.auth.api.identity.Identity;
import com.google.android.gms.common.api.Scope;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.api.services.drive.DriveScopes;

import java.io.IOException;
import java.util.Collections;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Access to the backup folder on the user's Google Drive, through Google's AuthorizationClient.
 *
 * The user picks an account with Android's account chooser and grants the drive.file scope once;
 * after that a fresh access token comes back without showing anything, which is what lets a
 * backup run in the background. The chosen account is kept here so signing out can forget it
 * and the next sign-in can pick another.
 */
public final class DriveAuth {
    private static final String PREFS = "backup";
    private static final String PREF_ACCOUNT = "drive_account";
    private static final String GOOGLE = "com.google";

    /** Google wants the user to approve Drive access again; only the app's screen can ask. */
    public static final class NeedsConsentException extends IOException {
        NeedsConsentException() {
            super("Sign in to Google Drive again");
        }
    }

    private DriveAuth() {
    }

    /** Android's chooser for a Google account on this phone. */
    public static Intent chooseAccountIntent(Context context) {
        String current = accountName(context);
        return AccountManager.newChooseAccountIntent(current != null ? new Account(current, GOOGLE) : null,
                null, new String[] { GOOGLE }, null, null, null, null);
    }

    /** Asks Google for Drive access for the account; the result may need the user's approval first. */
    public static Task<AuthorizationResult> authorize(Context context, String accountName) {
        AuthorizationRequest request = AuthorizationRequest.builder()
                .setRequestedScopes(Collections.singletonList(new Scope(DriveScopes.DRIVE_FILE)))
                .setAccount(new Account(accountName, GOOGLE))
                .build();
        return Identity.getAuthorizationClient(context).authorize(request);
    }

    /** The result the approval screen sent back. */
    public static AuthorizationResult resultFromIntent(Context context, Intent data)
            throws com.google.android.gms.common.api.ApiException {
        return Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(data);
    }

    /**
     * A current access token for the saved account, without showing anything. Blocks, so call it
     * off the main thread.
     */
    public static String accessToken(Context context) throws IOException {
        String account = accountName(context);
        if (account == null) {
            throw new NeedsConsentException();
        }
        AuthorizationResult result;
        try {
            result = Tasks.await(authorize(context, account), 30, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            // The account was removed from the phone: retrying cannot help, signing in again can
            if (!isOnPhone(context, account)) {
                throw new NeedsConsentException();
            }
            throw new IOException("Google Drive access failed: " + e.getCause().getMessage(), e.getCause());
        } catch (TimeoutException e) {
            throw new IOException("Google Drive access timed out", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted", e);
        }
        if (result.hasResolution() || result.getAccessToken() == null) {
            throw new NeedsConsentException();
        }
        return result.getAccessToken();
    }

    /** Whether the account is still on this phone; one picked in the chooser stays visible to the app. */
    private static boolean isOnPhone(Context context, String accountName) {
        for (Account account : AccountManager.get(context).getAccountsByType(GOOGLE)) {
            if (account.name.equals(accountName)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isSignedIn(Context context) {
        return accountName(context) != null;
    }

    /** The Google account backups go to, or null when signed out. */
    public static String accountName(Context context) {
        return prefs(context).getString(PREF_ACCOUNT, null);
    }

    public static void saveAccount(Context context, String accountName) {
        prefs(context).edit().putString(PREF_ACCOUNT, accountName).apply();
    }

    /** Forgets the account. The grant stays on the Google account until the user removes it there. */
    public static void signOut(Context context) {
        prefs(context).edit().remove(PREF_ACCOUNT).apply();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
