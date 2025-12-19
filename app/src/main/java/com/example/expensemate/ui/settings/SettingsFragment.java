package com.example.expensemate.ui.settings;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.appcompat.app.AlertDialog;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.expensemate.R;
import com.example.expensemate.data.AppDatabase;
import com.example.expensemate.data.BackupDataLoader;
import com.example.expensemate.service.GoogleDriveService;
import com.example.expensemate.service.GoogleSignInHelper;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.common.SignInButton;
import com.google.android.gms.tasks.Task;

public class SettingsFragment extends Fragment {
    private static final String TAG = "SettingsFragment";

    private SignInButton btnSignIn;
    private Button btnSignOut;
    private Button btnBackup;
    private Button btnRestore;
    private Button btnDeleteOldBackups;
    private TextView textAccountStatus;
    private TextView textStatus;

    private GoogleSignInHelper signInHelper;
    private ActivityResultLauncher<Intent> signInLauncher;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        signInHelper = new GoogleSignInHelper(requireContext());

        signInLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    Log.d(TAG, "Sign-in result code: " + result.getResultCode());
                    if (result.getResultCode() == Activity.RESULT_OK) {
                        Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(result.getData());
                        handleSignInResult(task);
                    } else {
                        Log.e(TAG, "Sign-in failed or cancelled. Result code: " + result.getResultCode());
                        if (result.getData() != null) {
                            Task<GoogleSignInAccount> task = GoogleSignIn
                                    .getSignedInAccountFromIntent(result.getData());
                            handleSignInResult(task);
                        } else {
                            Toast.makeText(getContext(), "Sign-in cancelled", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_settings, container, false);

        btnSignIn = root.findViewById(R.id.btn_sign_in);
        btnSignOut = root.findViewById(R.id.btn_sign_out);
        btnBackup = root.findViewById(R.id.btn_backup);
        btnRestore = root.findViewById(R.id.btn_restore);
        textAccountStatus = root.findViewById(R.id.text_account_status);
        btnDeleteOldBackups = root.findViewById(R.id.btn_delete_old_backups);
        textStatus = root.findViewById(R.id.text_status);

        setupListeners();
        updateUI(signInHelper.isSignedIn());

        return root;
    }

    private void setupListeners() {
        btnSignIn.setOnClickListener(v -> signIn());
        btnSignOut.setOnClickListener(v -> signOut());

        btnBackup.setOnClickListener(v -> {
            new AlertDialog.Builder(requireContext())
                    .setTitle("Backup Data")
                    .setMessage(
                            "Are you sure you want to backup data to Google Drive? This will overwrite existing backup.")
                    .setPositiveButton("Yes", (dialog, which) -> {
                        textStatus.setText("Backing up data...");
                        btnBackup.setEnabled(false);

                        AppDatabase db = AppDatabase.getDatabase(requireContext());
                        new Thread(() -> {
                            BackupDataLoader.exportDatabaseDataToGoogleDrive(requireContext(), db,
                                    new GoogleDriveService.DriveCallback() {
                                        @Override
                                        public void onSuccess(String message) {
                                            requireActivity().runOnUiThread(() -> {
                                                textStatus.setText("Success: " + message);
                                                btnBackup.setEnabled(true);
                                                Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
                                            });
                                        }

                                        @Override
                                        public void onError(String error) {
                                            requireActivity().runOnUiThread(() -> {
                                                textStatus.setText("Error: " + error);
                                                btnBackup.setEnabled(true);
                                                Log.e(TAG, error);
                                            });
                                        }
                                    });
                        }).start();
                    })
                    .setNegativeButton("No", null)
                    .show();
        });

        btnRestore.setOnClickListener(v -> {
            new AlertDialog.Builder(requireContext())
                    .setTitle("Restore Data")
                    .setMessage(
                            "Are you sure you want to restore data from Google Drive? This will DELETE all current local data.")
                    .setPositiveButton("Yes", (dialog, which) -> {
                        textStatus.setText("Restoring data...");
                        btnRestore.setEnabled(false);

                        new Thread(() -> {
                            BackupDataLoader.loadBackupDataFromGoogleDrive(requireContext(),
                                    new GoogleDriveService.DriveCallback() {

                                        @Override
                                        public void onSuccess(String message) {
                                            requireActivity().runOnUiThread(() -> {
                                                textStatus.setText("Success: " + message);
                                                btnRestore.setEnabled(true);
                                                Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
                                            });
                                        }

                                        @Override
                                        public void onError(String error) {
                                            requireActivity().runOnUiThread(() -> {
                                                textStatus.setText("Error: " + error);
                                                btnRestore.setEnabled(true);
                                                Log.e(TAG, error);
                                            });
                                        }
                                    });
                        }).start();
                    })
                    .setNegativeButton("No", null)
                    .show();
        });

        btnDeleteOldBackups.setOnClickListener(v -> {
            new AlertDialog.Builder(requireContext())
                    .setTitle("Delete Old Backups")
                    .setMessage("Are you sure you want to delete old backups from Google Drive?")
                    .setPositiveButton("Yes", (dialog, which) -> {
                        textStatus.setText("Deleting old backups...");
                        btnDeleteOldBackups.setEnabled(false);

                        new Thread(() -> {
                            GoogleDriveService driveService = new GoogleDriveService(requireContext());
                            GoogleSignInHelper signInHelper = new GoogleSignInHelper(requireContext());

                            if (signInHelper.isSignedIn()) {
                                driveService.initializeDriveService(signInHelper.getAccessToken());
                                driveService.deleteOldBackups(new GoogleDriveService.DriveCallback() {
                                    @Override
                                    public void onSuccess(String message) {
                                        requireActivity().runOnUiThread(() -> {
                                            textStatus.setText("Success: " + message);
                                            btnDeleteOldBackups.setEnabled(true);
                                            Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
                                        });
                                    }

                                    @Override
                                    public void onError(String error) {
                                        requireActivity().runOnUiThread(() -> {
                                            textStatus.setText("Error: " + error);
                                            btnDeleteOldBackups.setEnabled(true);
                                            Log.e(TAG, error);
                                        });
                                    }
                                });
                            }
                        }).start();
                    })
                    .setNegativeButton("No", null)
                    .show();
        });
    }

    private void signIn() {
        Intent signInIntent = signInHelper.getSignInClient().getSignInIntent();
        signInLauncher.launch(signInIntent);
    }

    private void signOut() {
        signInHelper.signOut();
        updateUI(false);
        textStatus.setText("Signed out");
    }

    private void handleSignInResult(Task<GoogleSignInAccount> completedTask) {
        try {
            GoogleSignInAccount account = completedTask.getResult(com.google.android.gms.common.api.ApiException.class);
            updateUI(true);
            textStatus.setText("Signed in successfully");
        } catch (com.google.android.gms.common.api.ApiException e) {
            Log.w(TAG, "signInResult:failed code=" + e.getStatusCode());
            updateUI(false);
            textStatus.setText("Sign in failed");
        }
    }

    private void updateUI(boolean isSignedIn) {
        if (isSignedIn) {
            btnSignIn.setVisibility(View.GONE);
            btnSignOut.setVisibility(View.VISIBLE);
            btnBackup.setEnabled(true);
            btnRestore.setEnabled(true);
            btnDeleteOldBackups.setEnabled(true);

            GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(requireContext());
            if (account != null) {
                textAccountStatus.setText("Signed in as: " + account.getEmail());
            }
        } else {
            btnSignIn.setVisibility(View.VISIBLE);
            btnSignOut.setVisibility(View.GONE);
            btnBackup.setEnabled(false);
            btnRestore.setEnabled(false);
            btnDeleteOldBackups.setEnabled(false);
            textAccountStatus.setText("Not signed in");
        }
    }
}
