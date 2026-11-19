package com.example.expensemate.service;

import android.content.Context;
import android.util.Log;

import com.google.api.client.http.FileContent;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** The app's backup folders on the user's Google Drive. Every call blocks, so run it off the main thread. */
public class GoogleDriveService {
    private static final String TAG = "GoogleDriveService";
    private static final String APP_FOLDER_NAME = "ExpenseMate_Backups";

    private final Drive driveService;

    /** A Drive client that sends the given access token on every request. */
    public GoogleDriveService(String accessToken) throws IOException {
        if (accessToken == null || accessToken.isEmpty()) {
            throw new IOException("No Google Drive access token");
        }
        driveService = new Drive.Builder(new NetHttpTransport(), GsonFactory.getDefaultInstance(),
                request -> request.getHeaders().setAuthorization("Bearer " + accessToken))
                .setApplicationName("ExpenseMate")
                .build();
    }

    /** Connects with the access the user already granted, without showing anything. */
    public static GoogleDriveService connect(Context context) throws IOException {
        return new GoogleDriveService(DriveAuth.accessToken(context));
    }

    private String getOrCreateAppFolder() throws IOException {
        String appFolderId = getAppFolderId();
        if (appFolderId != null) {
            return appFolderId;
        }
        File folderMetadata = new File();
        folderMetadata.setName(APP_FOLDER_NAME);
        folderMetadata.setMimeType("application/vnd.google-apps.folder");
        File createdFolder = driveService.files().create(folderMetadata).setFields("id").execute();
        Log.d(TAG, "Created app folder: " + createdFolder.getId());
        return createdFolder.getId();
    }

    private String getAppFolderId() throws IOException {
        FileList fileList = driveService.files().list()
                .setQ("name='" + APP_FOLDER_NAME + "' and mimeType='application/vnd.google-apps.folder' "
                        + "and trashed = false")
                .setOrderBy("createdTime")
                .setFields("files(id)")
                .execute();
        List<File> files = fileList.getFiles();
        return files != null && !files.isEmpty() ? files.get(0).getId() : null;
    }

    /** Creates a new, empty backup folder inside the app folder. */
    public String createBackupFolder() throws IOException {
        String appFolderId = getOrCreateAppFolder();
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
        List<BackupFolder> folders = new ArrayList<>();
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
            Map<String, String> files = new HashMap<>();
            if (contents.getFiles() != null) {
                for (File file : contents.getFiles()) {
                    files.put(file.getName(), file.getId());
                }
            }
            long created = folder.getCreatedTime() != null ? folder.getCreatedTime().getValue() : 0;
            folders.add(new BackupFolder(folder.getId(), files, created));
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
     * A backup finished if it has its zip, which uploads whole, or, from before the zip, the
     * marker that was uploaded last. One made before the marker is usable when all four of its
     * files are there, as each file uploads whole.
     */
    public static boolean isComplete(java.util.Set<String> fileNames) {
        return fileNames.contains(BACKUP_ZIP) || fileNames.contains(COMPLETE_MARKER)
                || fileNames.containsAll(LEGACY_FILES);
    }

    /** How many finished backups are kept on Drive after each new one. */
    public static final int KEEP_BACKUPS = 3;
    public static final String BACKUP_ZIP = "backup.zip";
    public static final String COMPLETE_MARKER = "backup_complete.txt";
    private static final List<String> LEGACY_FILES = java.util.Arrays.asList(
            "categories.txt", "recurring_payments.txt", "accounts.txt", "transactions.txt");

    public static final class BackupFolder {
        public final String id;
        public final Map<String, String> files; // Name to Drive file id
        public final long createdTime; // Milliseconds, 0 if Drive did not say

        public BackupFolder(String id, Map<String, String> files) {
            this(id, files, 0);
        }

        public BackupFolder(String id, Map<String, String> files, long createdTime) {
            this.id = id;
            this.files = files;
            this.createdTime = createdTime;
        }
    }

    /**
     * The folders to delete, given newest first, so the newest {@code keep} finished backups stay.
     * An unfinished folder goes once a newer backup has finished; one newer than every finished
     * backup may still be uploading, so it stays.
     */
    public static List<BackupFolder> toPrune(List<BackupFolder> newestFirst, int keep) {
        List<BackupFolder> prune = new ArrayList<>();
        int kept = 0;
        for (BackupFolder folder : newestFirst) {
            if (isComplete(folder.files.keySet())) {
                if (kept < keep) {
                    kept++;
                } else {
                    prune.add(folder);
                }
            } else if (kept > 0) {
                prune.add(folder);
            }
        }
        return prune;
    }

    /** Deletes all but the newest {@code keep} finished backups and returns how many went. */
    public int pruneBackups(int keep) throws IOException {
        List<BackupFolder> prune = toPrune(listBackupFolders(), keep);
        for (BackupFolder folder : prune) {
            delete(folder.id);
        }
        return prune.size();
    }

    /** Keeps the newest backup that can be restored and deletes every other one; returns what to tell the user. */
    public String deleteOldBackups() throws IOException {
        if (findLatestBackup() == null) {
            throw new IOException("No complete backup found, so nothing was deleted");
        }
        int deletedCount = pruneBackups(1);
        return deletedCount == 0 ? "No old backups to delete" : "Deleted " + deletedCount + " old backup(s)";
    }
}
