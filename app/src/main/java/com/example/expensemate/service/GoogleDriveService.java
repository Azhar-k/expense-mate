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

    /** Creates a new, empty backup folder inside the app folder. */
    public String createBackupFolder() throws IOException {
        String appFolderId = getOrCreateAppFolder();
        if (appFolderId == null) {
            throw new IOException("Failed to create app folder");
        }
        String folderName = "Backup_" + new java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US)
                .format(new java.util.Date());

        File folderMetadata = new File();
        folderMetadata.setName(folderName);
        folderMetadata.setMimeType("application/vnd.google-apps.folder");
        folderMetadata.setParents(Collections.singletonList(appFolderId));
        return driveService.files().create(folderMetadata).setFields("id").execute().getId();
    }

    public void uploadFile(String folderId, java.io.File file, String mimeType) throws IOException {
        File fileMetadata = new File();
        fileMetadata.setName(file.getName());
        fileMetadata.setParents(Collections.singletonList(folderId));
        driveService.files().create(fileMetadata, new FileContent(mimeType, file)).setFields("id").execute();
    }

    public void delete(String fileId) throws IOException {
        driveService.files().delete(fileId).execute();
    }

    public void downloadFile(String fileId, java.io.File destinationFile) throws IOException {
        try (InputStream inputStream = driveService.files().get(fileId).executeMediaAsInputStream();
                FileOutputStream outputStream = new FileOutputStream(destinationFile)) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
        }
    }

    /** Backup folders, newest first, each with the files it holds by name. */
    private List<BackupFolder> listBackupFolders() throws IOException {
        List<BackupFolder> folders = new java.util.ArrayList<>();
        String appFolderId = getAppFolderId();
        if (appFolderId == null) {
            return folders;
        }
        FileList fileList = driveService.files().list()
                .setQ("'" + appFolderId + "' in parents and mimeType='application/vnd.google-apps.folder' "
                        + "and trashed = false")
                .setOrderBy("createdTime desc")
                .setPageSize(100)
                .setFields("files(id, name, createdTime)")
                .execute();
        if (fileList.getFiles() == null) {
            return folders;
        }
        for (File folder : fileList.getFiles()) {
            FileList contents = driveService.files().list()
                    .setQ("'" + folder.getId() + "' in parents and trashed = false")
                    .setFields("files(id, name)")
                    .execute();
            java.util.Map<String, String> files = new java.util.HashMap<>();
            if (contents.getFiles() != null) {
                for (File file : contents.getFiles()) {
                    files.put(file.getName(), file.getId());
                }
            }
            folders.add(new BackupFolder(folder.getId(), files));
        }
        return folders;
    }

    /** The newest backup that can be restored, or null if there is none. */
    public BackupFolder findLatestBackup() throws IOException {
        for (BackupFolder folder : listBackupFolders()) {
            if (isComplete(folder.files.keySet())) {
                return folder;
            }
        }
        return null;
    }

    /**
     * A backup finished if it has the marker that is uploaded last. One made before the marker
     * existed is usable when all four of its files are there, as each file uploads whole.
     */
    public static boolean isComplete(java.util.Set<String> fileNames) {
        return fileNames.contains(COMPLETE_MARKER) || fileNames.containsAll(LEGACY_FILES);
    }

    public static final String COMPLETE_MARKER = "backup_complete.txt";
    private static final List<String> LEGACY_FILES = java.util.Arrays.asList(
            "categories.txt", "recurring_payments.txt", "accounts.txt", "transactions.txt");

    public static final class BackupFolder {
        public final String id;
        public final java.util.Map<String, String> files; // Name to Drive file id

        BackupFolder(String id, java.util.Map<String, String> files) {
            this.id = id;
            this.files = files;
        }
    }

    /** Keeps the newest backup that can be restored and deletes every other one. */
    public void deleteOldBackups(DriveCallback callback) {
        executorService.execute(() -> {
            try {
                List<BackupFolder> folders = listBackupFolders();
                BackupFolder keep = null;
                for (BackupFolder folder : folders) {
                    if (isComplete(folder.files.keySet())) {
                        keep = folder;
                        break;
                    }
                }
                if (keep == null) {
                    callback.onError("No complete backup found, so nothing was deleted");
                    return;
                }
                int deletedCount = 0;
                for (BackupFolder folder : folders) {
                    if (folder != keep) {
                        delete(folder.id);
                        deletedCount++;
                    }
                }
                callback.onSuccess(deletedCount == 0 ? "No old backups to delete" : "Deleted " + deletedCount + " old backup(s)");
            } catch (IOException e) {
                Log.e(TAG, "Error deleting old backups", e);
                callback.onError("Failed to delete old backups: " + e.getMessage());
            }
        });
    }

    public void shutdown() {
        executorService.shutdown();
    }
}
