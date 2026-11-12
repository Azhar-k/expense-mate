package com.example.expensemate.ui.settings;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;

import com.example.expensemate.BuildConfig;
import com.example.expensemate.MainActivity;
import com.example.expensemate.R;
import com.example.expensemate.data.AppDatabase;
import com.example.expensemate.data.BackupDataLoader;
import com.example.expensemate.data.Category;
import com.example.expensemate.databinding.FragmentSettingsBinding;
import com.example.expensemate.databinding.ItemSettingsRowBinding;
import com.example.expensemate.service.GoogleDriveService;
import com.example.expensemate.service.GoogleSignInHelper;
import com.example.expensemate.ui.common.AppTheme;
import com.example.expensemate.ui.common.Ui;
import com.example.expensemate.data.MessagePattern;
import com.example.expensemate.viewmodel.CategoryViewModel;
import com.example.expensemate.viewmodel.PatternViewModel;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class SettingsFragment extends Fragment {
    private static final String TAG = "SettingsFragment";
    private static final String PREFS = "backup";
    private static final String PREF_LAST_BACKUP = "last_backup";

    private FragmentSettingsBinding binding;
    private GoogleSignInHelper signInHelper;
    private ActivityResultLauncher<Intent> signInLauncher;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        signInHelper = new GoogleSignInHelper(requireContext());
        signInLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK || result.getData() != null) {
                        handleSignInResult(GoogleSignIn.getSignedInAccountFromIntent(result.getData()));
                    }
                });
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        binding = FragmentSettingsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        configureRow(binding.rowScan, R.drawable.ic_search, "Scan messages", "Find past transactions");
        binding.rowScan.getRoot().setOnClickListener(
                v -> NavHostFragment.findNavController(this).navigate(R.id.navigation_sms_scan));

        configureRow(binding.rowPatterns, R.drawable.ic_sms, "Message patterns", "");
        binding.rowPatterns.getRoot().setOnClickListener(
                v -> NavHostFragment.findNavController(this).navigate(R.id.navigation_patterns));
        new ViewModelProvider(requireActivity()).get(PatternViewModel.class).getPatterns()
                .observe(getViewLifecycleOwner(), patterns -> {
                    int on = 0;
                    for (MessagePattern pattern : patterns) {
                        if (pattern.isActive()) {
                            on++;
                        }
                    }
                    binding.rowPatterns.tvSubtitle.setText(on + " on · add your bank");
                });

        configureRow(binding.rowCategories, R.drawable.ic_tag, "Categories", "");
        binding.rowCategories.getRoot().setOnClickListener(
                v -> NavHostFragment.findNavController(this).navigate(R.id.navigation_categories));
        new ViewModelProvider(requireActivity()).get(CategoryViewModel.class).getAllCategories()
                .observe(getViewLifecycleOwner(), categories -> {
                    int expense = 0;
                    int income = 0;
                    for (Category category : categories) {
                        if ("INCOME".equals(category.getType())) {
                            income++;
                        } else {
                            expense++;
                        }
                    }
                    binding.rowCategories.tvSubtitle.setText(expense + " expense · " + income + " income");
                });

        configureRow(binding.rowTheme, R.drawable.ic_palette, "Theme", AppTheme.LABELS[AppTheme.selected(requireContext())]);
        binding.rowTheme.getRoot().setOnClickListener(v -> chooseTheme());

        configureRow(binding.rowAbout, R.drawable.ic_info, "Expense Mate", "Version " + BuildConfig.VERSION_NAME);
        binding.rowAbout.getRoot().setOnClickListener(v -> new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Expense Mate " + BuildConfig.VERSION_NAME)
                .setMessage("Your transactions stay on this phone. Bank messages are read here to record payments, "
                        + "and nothing leaves the device unless you back up to your own Google Drive.")
                .setPositiveButton("OK", null)
                .show());

        binding.btnSignIn.setOnClickListener(v -> signInLauncher.launch(signInHelper.getSignInClient().getSignInIntent()));
        binding.btnSignOut.setOnClickListener(v -> {
            signInHelper.signOut();
            updateBackupCard(false);
            showStatus("Signed out");
        });
        binding.btnBackup.setOnClickListener(v -> confirmBackup());
        binding.btnRestore.setOnClickListener(v -> confirmRestore());
        binding.btnDeleteOld.setOnClickListener(v -> confirmDeleteOld());
        updateBackupCard(signInHelper.isSignedIn());
    }

    @Override
    public void onResume() {
        super.onResume();
        renderCaptureStatus();
    }

    private void chooseTheme() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Theme")
                .setSingleChoiceItems(AppTheme.LABELS, AppTheme.selected(requireContext()), (dialog, which) -> {
                    dialog.dismiss();
                    AppTheme.select(requireContext().getApplicationContext(), which);
                    if (binding != null) {
                        binding.rowTheme.tvSubtitle.setText(AppTheme.LABELS[which]);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void configureRow(ItemSettingsRowBinding row, int icon, String title, String subtitle) {
        Ui.styleIconTile(row.ivIcon, icon, R.color.surface_variant, R.color.on_surface);
        row.tvTitle.setText(title);
        row.tvSubtitle.setText(subtitle);
    }

    private void renderCaptureStatus() {
        Context context = requireContext();
        boolean sms = MainActivity.hasSmsPermission(context);
        configureRow(binding.rowSms, R.drawable.ic_sms, "SMS reading",
                sms ? "New bank messages are recorded automatically" : "New bank messages aren't being recorded");
        styleCaptureRow(binding.rowSms, sms);
        binding.rowSms.getRoot().setOnClickListener(v -> {
            if (MainActivity.hasSmsPermission(context)) {
                openAppSettings();
            } else if (ActivityCompat.shouldShowRequestPermissionRationale(requireActivity(),
                    android.Manifest.permission.READ_SMS) || !wasAskedForSms()) {
                markAskedForSms();
                ((MainActivity) requireActivity()).requestSmsPermission();
            } else {
                // Android stops showing the prompt after a refusal, so only system settings can turn it on
                openAppSettings();
            }
        });

        boolean notifications = MainActivity.isNotificationListenerEnabled(context);
        configureRow(binding.rowNotifications, R.drawable.ic_bolt, "RCS notifications",
                notifications ? "RCS bank messages are recorded automatically" : "Needs notification access");
        styleCaptureRow(binding.rowNotifications, notifications);
        binding.rowNotifications.getRoot().setOnClickListener(
                v -> startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));
    }

    private boolean wasAskedForSms() {
        return prefs().getBoolean("sms_asked", false);
    }

    private void markAskedForSms() {
        prefs().edit().putBoolean("sms_asked", true).apply();
    }

    private void styleCaptureRow(ItemSettingsRowBinding row, boolean active) {
        if (active) {
            Ui.styleIconTile(row.ivIcon, sameIcon(row), R.color.primary_container, R.color.primary);
            Ui.styleTag(row.tvTag, "On", R.color.primary_container, R.color.on_primary_container);
        } else {
            Ui.styleIconTile(row.ivIcon, sameIcon(row), R.color.warning_container, R.color.warning);
            Ui.styleTag(row.tvTag, "Turn on", R.color.warning_container, R.color.warning);
        }
    }

    private int sameIcon(ItemSettingsRowBinding row) {
        return row == binding.rowSms ? R.drawable.ic_sms : R.drawable.ic_bolt;
    }

    private void openAppSettings() {
        startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", requireContext().getPackageName(), null)));
    }

    private SharedPreferences prefs() {
        return requireContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private void handleSignInResult(Task<GoogleSignInAccount> task) {
        try {
            task.getResult(ApiException.class);
            updateBackupCard(true);
            showStatus("Signed in");
        } catch (ApiException e) {
            Log.w(TAG, "signInResult:failed code=" + e.getStatusCode());
            updateBackupCard(false);
            showStatus("Sign-in didn't work. Check your connection and try again.");
        }
    }

    private void updateBackupCard(boolean signedIn) {
        int signedInOnly = signedIn ? View.VISIBLE : View.GONE;
        binding.btnSignIn.setVisibility(signedIn ? View.GONE : View.VISIBLE);
        binding.btnBackup.setVisibility(signedInOnly);
        binding.groupBackupActions.setVisibility(signedInOnly);
        binding.btnSignOut.setVisibility(signedInOnly);
        if (signedIn) {
            GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(requireContext());
            binding.tvBackupAccount.setText(account != null ? account.getEmail() : "Signed in");
        } else {
            binding.tvBackupAccount.setText("Not signed in");
        }
        renderLastBackup();
    }

    private void renderLastBackup() {
        long last = prefs().getLong(PREF_LAST_BACKUP, 0);
        if (last == 0) {
            binding.tvLastBackup.setText("No backup recorded since this update");
            return;
        }
        Calendar then = Calendar.getInstance();
        then.setTimeInMillis(last);
        Calendar now = Calendar.getInstance();
        boolean today = then.get(Calendar.YEAR) == now.get(Calendar.YEAR)
                && then.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR);
        SimpleDateFormat format = new SimpleDateFormat(today ? "'today,' HH:mm" : "d MMM yyyy, HH:mm",
                Locale.getDefault());
        binding.tvLastBackup.setText("Last backup: " + format.format(new Date(last)));
    }

    private void showStatus(String message) {
        binding.tvBackupStatus.setText(message);
        binding.tvBackupStatus.setVisibility(View.VISIBLE);
    }

    private void setBackupButtonsEnabled(boolean enabled) {
        binding.btnBackup.setEnabled(enabled);
        binding.btnRestore.setEnabled(enabled);
        binding.btnDeleteOld.setEnabled(enabled);
    }

    /** Reports a Drive result on the main thread, if this screen is still showing. */
    private GoogleDriveService.DriveCallback driveCallback(Runnable onSuccess) {
        return new GoogleDriveService.DriveCallback() {
            @Override
            public void onSuccess(String message) {
                finish(message, onSuccess);
            }

            @Override
            public void onError(String error) {
                Log.e(TAG, error);
                finish(error, null);
            }

            private void finish(String message, @Nullable Runnable action) {
                Activity activity = getActivity();
                if (activity == null) {
                    return;
                }
                activity.runOnUiThread(() -> {
                    if (binding == null) {
                        return;
                    }
                    setBackupButtonsEnabled(true);
                    showStatus(message);
                    if (action != null) {
                        action.run();
                    }
                });
            }
        };
    }

    private void confirmBackup() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Back up to Google Drive?")
                .setMessage("This saves a new backup of your data to your Drive.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Back up", (dialog, which) -> {
                    showStatus("Backing up…");
                    setBackupButtonsEnabled(false);
                    Context appContext = requireContext().getApplicationContext();
                    BackupDataLoader.exportDatabaseDataToGoogleDrive(appContext, AppDatabase.getDatabase(appContext),
                            driveCallback(() -> {
                                prefs().edit().putLong(PREF_LAST_BACKUP, System.currentTimeMillis()).apply();
                                renderLastBackup();
                            }));
                })
                .show();
    }

    private void confirmRestore() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Restore from Google Drive?")
                .setMessage("This deletes everything currently on this phone and replaces it with your latest backup.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete and restore", (dialog, which) -> {
                    showStatus("Restoring…");
                    setBackupButtonsEnabled(false);
                    BackupDataLoader.loadBackupDataFromGoogleDrive(requireContext().getApplicationContext(),
                            driveCallback(null));
                })
                .show();
    }

    private void confirmDeleteOld() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Clear old backups?")
                .setMessage("This permanently deletes older backups from your Drive.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Clear", (dialog, which) -> {
                    showStatus("Clearing old backups…");
                    setBackupButtonsEnabled(false);
                    Context appContext = requireContext().getApplicationContext();
                    GoogleDriveService.DriveCallback callback = driveCallback(null);
                    new Thread(() -> {
                        GoogleSignInHelper helper = new GoogleSignInHelper(appContext);
                        if (helper.isSignedIn()) {
                            GoogleDriveService driveService = new GoogleDriveService(appContext);
                            driveService.initializeDriveService(helper.getAccessToken());
                            driveService.deleteOldBackups(callback);
                        } else {
                            callback.onError("Sign in to Google Drive first");
                        }
                    }).start();
                })
                .show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
