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
        this.executorService = Executors.newCachedThreadPool();
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

    public void createDateSpecificBackupFolder(DriveCallback callback) {
        executorService.execute(() -> {
            try {
                String appFolderId = getOrCreateAppFolder();
                if (appFolderId == null) {
                    callback.onError("Failed to create app folder");
                    return;
                }

                String folderName = "Backup_" + new java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US)
                        .format(new java.util.Date());

                File folderMetadata = new File();
                folderMetadata.setName(folderName);
                folderMetadata.setMimeType("application/vnd.google-apps.folder");
                folderMetadata.setParents(Collections.singletonList(appFolderId));

                File createdFolder = driveService.files().create(folderMetadata)
                        .setFields("id")
                        .execute();

                callback.onSuccess(createdFolder.getId());
            } catch (IOException e) {
                Log.e(TAG, "Error creating backup folder", e);
                callback.onError("Failed to create backup folder: " + e.getMessage());
            }
        });
    }

    public void deleteOldBackups(DriveCallback callback) {
        executorService.execute(() -> {
            try {
                String appFolderId = getAppFolderId();
                if (appFolderId == null) {
                    callback.onError("App folder not found");
                    return;
                }

                // List all backup folders
                FileList fileList = driveService.files().list()
                        .setQ("'" + appFolderId
                                + "' in parents and mimeType='application/vnd.google-apps.folder' and name contains 'Backup_'")
                        .setOrderBy("createdTime desc")
                        .setFields("files(id, name, createdTime)")
                        .execute();

                List<File> files = fileList.getFiles();
                if (files == null || files.isEmpty()) {
                    callback.onSuccess("No backups found to delete");
                    return;
                }

                if (files.size() <= 1) {
                    callback.onSuccess("No old backups to delete (only 1 or 0 exists)");
                    return;
                }

                // Keep the first (latest) one, delete the rest
                int deletedCount = 0;
                for (int i = 1; i < files.size(); i++) {
                    File fileToDelete = files.get(i);
                    driveService.files().delete(fileToDelete.getId()).execute();
                    deletedCount++;
                    Log.d(TAG, "Deleted old backup folder: " + fileToDelete.getName());
                }

                callback.onSuccess("Deleted " + deletedCount + " old backup(s)");

            } catch (IOException e) {
                Log.e(TAG, "Error deleting old backups", e);
                callback.onError("Failed to delete old backups: " + e.getMessage());
            }
        });
    }

    public void uploadFileToFolder(String folderId, java.io.File file, String mimeType, DriveCallback callback) {
        executorService.execute(() -> {
            try {
                File fileMetadata = new File();
                fileMetadata.setName(file.getName());
                fileMetadata.setParents(Collections.singletonList(folderId));

                FileContent mediaContent = new FileContent(mimeType, file);
                File uploadedFile = driveService.files().create(fileMetadata, mediaContent)
                        .setFields("id")
                        .execute();

                callback.onSuccess(uploadedFile.getId());
            } catch (IOException e) {
                Log.e(TAG, "Error uploading file " + file.getName(), e);
                callback.onError("Failed to upload " + file.getName() + ": " + e.getMessage());
            }
        });
    }

    public void getLatestBackupFolderId(DriveFolderCallback callback) {
        executorService.execute(() -> {
            try {
                String appFolderId = getAppFolderId();
                if (appFolderId == null) {
                    callback.onError("App folder not found");
                    return;
                }

                FileList fileList = driveService.files().list()
                        .setQ("'" + appFolderId + "' in parents and mimeType='application/vnd.google-apps.folder'")
                        .setOrderBy("createdTime desc")
                        .setPageSize(1)
                        .setFields("files(id, name, createdTime)")
                        .execute();

                List<File> files = fileList.getFiles();
                if (files != null && !files.isEmpty()) {
                    callback.onSuccess(files.get(0).getId());
                } else {
                    callback.onError("No backup folders found");
                }
            } catch (IOException e) {
                Log.e(TAG, "Error finding latest backup folder", e);
                callback.onError("Error finding latest backup: " + e.getMessage());
            }
        });
    }

    public void listFilesInFolder(String folderId, DriveFileListCallback callback) {
        executorService.execute(() -> {
            try {
                FileList fileList = driveService.files().list()
                        .setQ("'" + folderId + "' in parents")
                        .setFields("files(id, name)")
                        .execute();

                callback.onSuccess(fileList.getFiles());
            } catch (IOException e) {
                Log.e(TAG, "Error listing files in folder", e);
                callback.onError("Error listing files: " + e.getMessage());
            }
        });
    }

    public void downloadFile(String fileId, java.io.File destinationFile, DriveCallback callback) {
        executorService.execute(() -> {
            try {
                InputStream inputStream = driveService.files().get(fileId)
                        .executeMediaAsInputStream();

                try (FileOutputStream outputStream = new FileOutputStream(destinationFile)) {
                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    while ((bytesRead = inputStream.read(buffer)) != -1) {
                        outputStream.write(buffer, 0, bytesRead);
                    }
                }
                callback.onSuccess("File downloaded successfully");
            } catch (IOException e) {
                Log.e(TAG, "Error downloading file", e);
                callback.onError("Error downloading file: " + e.getMessage());
            }
        });
    }

    public interface DriveFolderCallback {
        void onSuccess(String folderId);

        void onError(String error);
    }

    public interface DriveFileListCallback {
        void onSuccess(List<File> files);

        void onError(String error);
    }

    public void shutdown() {
        executorService.shutdown();
    }
}
