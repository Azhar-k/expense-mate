package com.example.expensemate.service;

import android.content.Context;
import android.util.Log;

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.FileContent;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class GoogleDriveService {
    private static final String TAG = "GoogleDriveService";
    private static final String APP_FOLDER_NAME = "ExpenseMate_Backups";
    private static final String BACKUP_FILE_NAME = "expense_mate_backup.txt";
    
    private final Context context;
    private final ExecutorService executorService;
    private Drive driveService;
    
    public interface DriveCallback {
        void onSuccess(String message);
        void onError(String error);
    }
    
    public GoogleDriveService(Context context) {
        this.context = context;
        this.executorService = Executors.newSingleThreadExecutor();
    }
    
    public void initializeDriveService(String accessToken) {
        try {
            GoogleCredential credential = new GoogleCredential()
                    .setAccessToken(accessToken);
            
            driveService = new Drive.Builder(
                    GoogleNetHttpTransport.newTrustedTransport(),
                    GsonFactory.getDefaultInstance(),
                    credential)
                    .setApplicationName("ExpenseMate")
                    .build();
                    
            Log.d(TAG, "Google Drive service initialized successfully");
        } catch (GeneralSecurityException | IOException e) {
            Log.e(TAG, "Error initializing Google Drive service", e);
        }
    }
    
    public void uploadBackupToDrive(java.io.File backupFile, DriveCallback callback) {
        if (driveService == null) {
            callback.onError("Google Drive service not initialized");
            return;
        }
        
        executorService.execute(() -> {
            try {
                // Create app folder if it doesn't exist
                String appFolderId = getOrCreateAppFolder();
                if (appFolderId == null) {
                    callback.onError("Failed to create app folder");
                    return;
                }
                
                // Check if backup file already exists and delete it
                deleteExistingBackup(appFolderId);
                
                // Create file metadata
                File fileMetadata = new File();
                fileMetadata.setName(BACKUP_FILE_NAME);
                fileMetadata.setParents(Collections.singletonList(appFolderId));
                
                // Upload the file
                FileContent mediaContent = new FileContent("text/plain", backupFile);
                File uploadedFile = driveService.files().create(fileMetadata, mediaContent)
                        .setFields("id")
                        .execute();
                
                Log.d(TAG, "Backup uploaded successfully. File ID: " + uploadedFile.getId());
                callback.onSuccess("Backup uploaded to Google Drive successfully");
                
            } catch (IOException e) {
                Log.e(TAG, "Error uploading backup to Google Drive", e);
                callback.onError("Failed to upload backup: " + e.getMessage());
            }
        });
    }
    
    public void downloadBackupFromDrive(java.io.File destinationFile, DriveCallback callback) {
        if (driveService == null) {
            callback.onError("Google Drive service not initialized");
            return;
        }
        
        executorService.execute(() -> {
            try {
                // Get app folder
                String appFolderId = getAppFolderId();
                if (appFolderId == null) {
                    callback.onError("App folder not found");
                    return;
                }
                
                // Find the backup file
                String backupFileId = findBackupFile(appFolderId);
                if (backupFileId == null) {
                    callback.onError("No backup file found in Google Drive");
                    return;
                }
                
                // Download the file
                InputStream inputStream = driveService.files().get(backupFileId)
                        .executeMediaAsInputStream();
                
                // Write to destination file
                try (FileOutputStream outputStream = new FileOutputStream(destinationFile)) {
                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    while ((bytesRead = inputStream.read(buffer)) != -1) {
                        outputStream.write(buffer, 0, bytesRead);
                    }
                }
                
                Log.d(TAG, "Backup downloaded successfully");
                callback.onSuccess("Backup downloaded from Google Drive successfully");
                
            } catch (IOException e) {
                Log.e(TAG, "Error downloading backup from Google Drive", e);
                callback.onError("Failed to download backup: " + e.getMessage());
            }
        });
    }
    
    public void listBackups(DriveCallback callback) {
        if (driveService == null) {
            callback.onError("Google Drive service not initialized");
            return;
        }
        
        executorService.execute(() -> {
            try {
                String appFolderId = getAppFolderId();
                if (appFolderId == null) {
                    callback.onError("App folder not found");
                    return;
                }
                
                FileList fileList = driveService.files().list()
                        .setQ("'" + appFolderId + "' in parents and name contains 'backup'")
                        .setFields("files(id,name,createdTime)")
                        .execute();
                
                List<File> files = fileList.getFiles();
                if (files == null || files.isEmpty()) {
                    callback.onSuccess("No backups found");
                } else {
                    StringBuilder result = new StringBuilder("Available backups:\n");
                    for (File file : files) {
                        result.append("- ").append(file.getName())
                              .append(" (Created: ").append(file.getCreatedTime())
                              .append(")\n");
                    }
                    callback.onSuccess(result.toString());
                }
                
            } catch (IOException e) {
                Log.e(TAG, "Error listing backups", e);
                callback.onError("Failed to list backups: " + e.getMessage());
            }
        });
    }
    
    private String getOrCreateAppFolder() {
        try {
            // First, try to find existing app folder
            String appFolderId = getAppFolderId();
            if (appFolderId != null) {
                return appFolderId;
            }
            
            // Create new app folder
            File folderMetadata = new File();
            folderMetadata.setName(APP_FOLDER_NAME);
            folderMetadata.setMimeType("application/vnd.google-apps.folder");
            
            File createdFolder = driveService.files().create(folderMetadata)
                    .setFields("id")
                    .execute();
            
            Log.d(TAG, "Created app folder: " + createdFolder.getId());
            return createdFolder.getId();
            
        } catch (IOException e) {
            Log.e(TAG, "Error creating app folder", e);
            return null;
        }
    }
    
    private String getAppFolderId() {
        try {
            FileList fileList = driveService.files().list()
                    .setQ("name='" + APP_FOLDER_NAME + "' and mimeType='application/vnd.google-apps.folder'")
                    .setFields("files(id)")
                    .execute();
            
            List<File> files = fileList.getFiles();
            if (files != null && !files.isEmpty()) {
                return files.get(0).getId();
            }
            return null;
            
        } catch (IOException e) {
            Log.e(TAG, "Error getting app folder ID", e);
            return null;
        }
    }
    
    private String findBackupFile(String appFolderId) {
        try {
            FileList fileList = driveService.files().list()
                    .setQ("'" + appFolderId + "' in parents and name='" + BACKUP_FILE_NAME + "'")
                    .setFields("files(id)")
                    .execute();
            
            List<File> files = fileList.getFiles();
            if (files != null && !files.isEmpty()) {
                return files.get(0).getId();
            }
            return null;
            
        } catch (IOException e) {
            Log.e(TAG, "Error finding backup file", e);
            return null;
        }
    }
    
    private void deleteExistingBackup(String appFolderId) {
        try {
            String backupFileId = findBackupFile(appFolderId);
            if (backupFileId != null) {
                driveService.files().delete(backupFileId).execute();
                Log.d(TAG, "Deleted existing backup file");
            }
        } catch (IOException e) {
            Log.e(TAG, "Error deleting existing backup", e);
        }
    }
    
    public void shutdown() {
        executorService.shutdown();
    }
}
