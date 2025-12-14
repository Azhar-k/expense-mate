package com.example.expensemate.service;

import android.content.Context;
import android.util.Log;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.Scope;
import com.google.api.services.drive.DriveScopes;

public class GoogleSignInHelper {
    private static final String TAG = "GoogleSignInHelper";
    
    private final Context context;
    private GoogleSignInClient googleSignInClient;
    
    public interface SignInCallback {
        void onSuccess(String accessToken);
        void onError(String error);
    }
    
    public GoogleSignInHelper(Context context) {
        this.context = context;
        initializeSignInClient();
    }
    
    private void initializeSignInClient() {
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .requestScopes(new Scope(DriveScopes.DRIVE_FILE))
                .build();
        
        googleSignInClient = GoogleSignIn.getClient(context, gso);
    }
    
    public GoogleSignInClient getSignInClient() {
        return googleSignInClient;
    }
    
    public void signIn(SignInCallback callback) {
        try {
            GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(context);
            if (account != null && !account.isExpired()) {
                // User is already signed in
                Log.d(TAG, "User already signed in");
                callback.onSuccess(account.getIdToken());
            } else {
                // User needs to sign in
                Log.d(TAG, "User needs to sign in");
                callback.onError("User needs to sign in");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error checking sign-in status", e);
            callback.onError("Error checking sign-in status: " + e.getMessage());
        }
    }
    
    public void signOut() {
        googleSignInClient.signOut().addOnCompleteListener(task -> {
            Log.d(TAG, "User signed out");
        });
    }
    
    public boolean isSignedIn() {
        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(context);
        return account != null && !account.isExpired();
    }
    
    public String getAccessToken() {
        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(context);
        if (account != null && !account.isExpired()) {
            return account.getIdToken();
        }
        return null;
    }
}
