package com.example.expensemate.example;

import android.content.Context;
import android.util.Log;

import com.example.expensemate.data.AppDatabase;
import com.example.expensemate.data.BackupDataLoader;
import com.example.expensemate.service.GoogleDriveService;
import com.example.expensemate.service.GoogleSignInHelper;

/**
 * Example class demonstrating how to use Google Drive backup functionality
 */
public class GoogleDriveBackupExample {
    private static final String TAG = "GoogleDriveBackupExample";
    
    private final Context context;
    private final GoogleSignInHelper signInHelper;
    private final GoogleDriveService driveService;
    
    public GoogleDriveBackupExample(Context context) {
        this.context = context;
        this.signInHelper = new GoogleSignInHelper(context);
        this.driveService = new GoogleDriveService(context);
    }
    
    /**
     * Example: Backup data to Google Drive
     */
    public void backupToGoogleDrive() {
        // Check if user is signed in
        if (!signInHelper.isSignedIn()) {
            Log.e(TAG, "User not signed in. Please sign in first.");
            return;
        }
        
        // Initialize drive service with access token
        String accessToken = signInHelper.getAccessToken();
        if (accessToken != null) {
            driveService.initializeDriveService(accessToken);
            
            // Export data to Google Drive
            AppDatabase database = AppDatabase.getDatabase(context);
            BackupDataLoader.exportDatabaseDataToGoogleDrive(context, database, new GoogleDriveService.DriveCallback() {
                @Override
                public void onSuccess(String message) {
                    Log.d(TAG, "Backup successful: " + message);
                    // Show success message to user
                }
                
                @Override
                public void onError(String error) {
                    Log.e(TAG, "Backup failed: " + error);
                    // Show error message to user
                }
            });
        } else {
            Log.e(TAG, "Failed to get access token");
        }
    }
    
    /**
     * Example: Restore data from Google Drive
     */
    public void restoreFromGoogleDrive() {
        // Check if user is signed in
        if (!signInHelper.isSignedIn()) {
            Log.e(TAG, "User not signed in. Please sign in first.");
            return;
        }
        
        // Initialize drive service with access token
        String accessToken = signInHelper.getAccessToken();
        if (accessToken != null) {
            driveService.initializeDriveService(accessToken);
            
            // Load backup from Google Drive
            BackupDataLoader.loadBackupDataFromGoogleDrive(context, new GoogleDriveService.DriveCallback() {
                @Override
                public void onSuccess(String message) {
                    Log.d(TAG, "Restore successful: " + message);
                    // Show success message to user
                }
                
                @Override
                public void onError(String error) {
                    Log.e(TAG, "Restore failed: " + error);
                    // Show error message to user
                }
            });
        } else {
            Log.e(TAG, "Failed to get access token");
        }
    }
    
    /**
     * Example: List available backups
     */
    public void listBackups() {
        // Check if user is signed in
        if (!signInHelper.isSignedIn()) {
            Log.e(TAG, "User not signed in. Please sign in first.");
            return;
        }
        
        // Initialize drive service with access token
        String accessToken = signInHelper.getAccessToken();
        if (accessToken != null) {
            driveService.initializeDriveService(accessToken);
            
            // List available backups
            driveService.listBackups(new GoogleDriveService.DriveCallback() {
                @Override
                public void onSuccess(String message) {
                    Log.d(TAG, "Available backups: " + message);
                    // Show backup list to user
                }
                
                @Override
                public void onError(String error) {
                    Log.e(TAG, "Failed to list backups: " + error);
                    // Show error message to user
                }
            });
        } else {
            Log.e(TAG, "Failed to get access token");
        }
    }
    
    /**
     * Example: Sign in to Google
     */
    public void signInToGoogle() {
        // This would typically be called from an Activity
        // The actual sign-in flow requires Activity context and result handling
        Log.d(TAG, "To implement sign-in, use GoogleSignInClient.getSignInIntent() in your Activity");
    }
}
