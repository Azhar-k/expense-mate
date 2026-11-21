package com.example.expensemate.ui.settings;

import android.accounts.AccountManager;
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
import androidx.activity.result.IntentSenderRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import com.example.expensemate.BuildConfig;
import com.example.expensemate.MainActivity;
import com.example.expensemate.R;
import com.example.expensemate.data.BackupDataLoader;
import com.example.expensemate.databinding.FragmentSettingsBinding;
import com.example.expensemate.databinding.ItemSettingsRowBinding;
import com.example.expensemate.service.BackupScheduler;
import com.example.expensemate.ui.accounts.MoneyFragment;
import com.example.expensemate.service.BackupWorker;
import com.example.expensemate.service.DriveAuth;
import com.example.expensemate.service.GoogleDriveService;
import com.example.expensemate.ui.common.AppTheme;
import com.example.expensemate.ui.common.ReorderDialog;
import com.example.expensemate.ui.common.TabOrder;
import com.example.expensemate.ui.common.Ui;
import com.google.android.gms.auth.api.identity.AuthorizationResult;
import com.google.android.gms.common.api.ApiException;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SettingsFragment extends Fragment {
    private static final String TAG = "SettingsFragment";
    private static final String PREFS = "backup";
    private static final String STATE_PENDING_ACCOUNT = "pending_account";

    private FragmentSettingsBinding binding;
    private ActivityResultLauncher<Intent> accountLauncher;
    private ActivityResultLauncher<IntentSenderRequest> consentLauncher;
    private final ExecutorService driveExecutor = Executors.newSingleThreadExecutor();
    /** The account waiting on Google's approval screen. */
    private String pendingAccount;
    /** Google wants the user to approve Drive access again before anything can reach Drive. */
    private boolean needsConsent;
    /** Back up now was tapped on this screen, so its result is shown when it finishes. */
    private boolean manualBackupRequested;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            pendingAccount = savedInstanceState.getString(STATE_PENDING_ACCOUNT);
        }
        accountLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            String account = result.getData() != null
                    ? result.getData().getStringExtra(AccountManager.KEY_ACCOUNT_NAME) : null;
            if (result.getResultCode() == Activity.RESULT_OK && account != null) {
                requestDriveAccess(account);
            }
        });
        consentLauncher = registerForActivityResult(new ActivityResultContracts.StartIntentSenderForResult(), result -> {
            if (binding == null) {
                return;
            }
            if (result.getResultCode() != Activity.RESULT_OK || pendingAccount == null) {
                showStatus("Drive access wasn't allowed, so nothing can be backed up");
                return;
            }
            try {
                AuthorizationResult access = DriveAuth.resultFromIntent(requireContext(), result.getData());
                if (access.getAccessToken() != null) {
                    onDriveAccess(pendingAccount);
                } else {
                    showStatus("Drive access wasn't allowed, so nothing can be backed up");
                }
            } catch (ApiException e) {
                Log.w(TAG, "Drive approval failed, code " + e.getStatusCode());
                showStatus("Sign-in didn't work. Check your connection and try again.");
            }
        });
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(STATE_PENDING_ACCOUNT, pendingAccount);
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
        configureRow(binding.rowTheme, R.drawable.ic_palette, "Theme", AppTheme.LABELS[AppTheme.selected(requireContext())]);
        binding.rowTheme.getRoot().setOnClickListener(v -> chooseTheme());
        MainActivity main = (MainActivity) requireActivity();
        setUpOrderRow(binding.rowTabOrder, "Tab order", TabOrder.BOTTOM,
                "Drag to move. The top one sits at the left of the bar. The app still opens on Home.",
                main::applyTabOrder);
        setUpOrderRow(binding.rowMoneyOrder, "Money sections", MoneyFragment.SECTIONS,
                "Drag to move. The top one sits at the left, and Money opens on it.", null);

        configureRow(binding.rowAbout, R.drawable.ic_info, "Expense Mate", "Version " + BuildConfig.VERSION_NAME);
        binding.rowAbout.getRoot().setOnClickListener(v -> new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Expense Mate " + BuildConfig.VERSION_NAME)
                .setMessage("Your transactions stay on this phone. Bank messages are read here to record payments, "
                        + "and nothing leaves the device unless you back up to your own Google Drive.")
                .setPositiveButton("OK", null)
                .show());

        binding.btnSignIn.setOnClickListener(v -> {
            String account = DriveAuth.accountName(requireContext());
            if (needsConsent && account != null) {
                requestDriveAccess(account);
            } else {
                accountLauncher.launch(DriveAuth.chooseAccountIntent(requireContext()));
            }
        });
        binding.btnSignOut.setOnClickListener(v -> {
            Context app = requireContext().getApplicationContext();
            DriveAuth.signOut(app);
            BackupScheduler.onSignedOut(app);
            needsConsent = false;
            updateBackupCard();
            showStatus("Signed out");
        });
        binding.btnBackup.setOnClickListener(v -> confirmBackup());
        binding.btnRestore.setOnClickListener(v -> confirmRestore());
        binding.btnDeleteOld.setOnClickListener(v -> confirmDeleteOld());
        WorkManager.getInstance(requireContext()).getWorkInfosForUniqueWorkLiveData(BackupScheduler.MANUAL_WORK)
                .observe(getViewLifecycleOwner(), this::renderManualBackup);
        updateBackupCard();
        String problem = BackupScheduler.problem(requireContext());
        if (problem != null) {
            showStatus(problem);
        }
        refreshFromDrive();
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

    // A row whose subtitle shows the current order, and which opens the dialog to change it
    private void setUpOrderRow(ItemSettingsRowBinding row, String title, TabOrder tabs, String hint,
            @Nullable Runnable onSaved) {
        configureRow(row, R.drawable.ic_reorder, title, tabs.summary(requireContext()));
        row.getRoot().setOnClickListener(v -> {
            List<Integer> ids = tabs.saved(requireContext());
            ReorderDialog.show(requireContext(), title, hint, tabs.labels(requireContext()), order -> {
                List<Integer> ordered = new ArrayList<>();
                for (int index : order) {
                    ordered.add(ids.get(index));
                }
                tabs.save(requireContext(), ordered);
                if (onSaved != null) {
                    onSaved.run();
                }
                if (binding != null) {
                    row.tvSubtitle.setText(tabs.summary(requireContext()));
                }
            });
        });
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

    /** Asks Google for Drive access for the account, showing its approval screen if it needs one. */
    private void requestDriveAccess(String account) {
        showStatus("Connecting to Google Drive…");
        DriveAuth.authorize(requireContext(), account)
                .addOnSuccessListener(result -> {
                    if (binding == null) {
                        return;
                    }
                    if (result.hasResolution() && result.getPendingIntent() != null) {
                        pendingAccount = account;
                        consentLauncher.launch(new IntentSenderRequest.Builder(
                                result.getPendingIntent().getIntentSender()).build());
                    } else {
                        onDriveAccess(account);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.w(TAG, "Drive access failed", e);
                    if (binding != null) {
                        showStatus("Sign-in didn't work. Check your connection and try again.");
                    }
                });
    }

    private void onDriveAccess(String account) {
        Context app = requireContext().getApplicationContext();
        DriveAuth.saveAccount(app, account);
        BackupScheduler.clearProblem(app);
        pendingAccount = null;
        needsConsent = false;
        updateBackupCard();
        showStatus("Signed in");
        refreshFromDrive();
    }

    /** Reads the newest backup's time from Drive, so it shows even after a reinstall. */
    private void refreshFromDrive() {
        if (!DriveAuth.isSignedIn(requireContext())) {
            return;
        }
        Context app = requireContext().getApplicationContext();
        driveExecutor.execute(() -> {
            try {
                long latest = BackupDataLoader.latestBackupTime(app);
                if (latest > 0) {
                    BackupScheduler.recordBackup(app, latest);
                }
                onUi(this::renderLastBackup);
            } catch (DriveAuth.NeedsConsentException e) {
                onUi(() -> {
                    needsConsent = true;
                    updateBackupCard();
                    showStatus("Sign in to Google Drive again to keep backing up");
                });
            } catch (Exception e) {
                Log.w(TAG, "Could not read backups from Drive", e);
            }
        });
    }

    private void updateBackupCard() {
        Context context = requireContext();
        boolean signedIn = DriveAuth.isSignedIn(context);
        boolean ready = signedIn && !needsConsent;
        int readyOnly = ready ? View.VISIBLE : View.GONE;
        binding.btnSignIn.setVisibility(ready ? View.GONE : View.VISIBLE);
        binding.btnSignIn.setText(signedIn ? "Sign in again" : "Sign in with Google");
        binding.btnBackup.setVisibility(readyOnly);
        binding.groupBackupActions.setVisibility(readyOnly);
        binding.btnSignOut.setVisibility(signedIn ? View.VISIBLE : View.GONE);
        binding.tvBackupAccount.setText(signedIn ? DriveAuth.accountName(context) : "Not signed in");

        binding.switchAutoBackup.setVisibility(signedIn ? View.VISIBLE : View.GONE);
        binding.switchAutoBackup.setOnCheckedChangeListener(null);
        binding.switchAutoBackup.setChecked(BackupScheduler.isAutomatic(context));
        binding.switchAutoBackup.setOnCheckedChangeListener((button, on) -> {
            BackupScheduler.setAutomatic(requireContext().getApplicationContext(), on);
            showStatus(on ? "Backs up once a day when the phone is online" : "Automatic backup is off");
        });
        renderLastBackup();
    }

    private void renderLastBackup() {
        if (binding == null) {
            return;
        }
        long last = BackupScheduler.lastBackup(requireContext());
        if (last == 0) {
            binding.tvLastBackup.setText("No backup yet");
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

    /** Back up now runs in WorkManager, so it keeps going if this screen closes; this shows where it is. */
    private void renderManualBackup(List<WorkInfo> infos) {
        if (binding == null || infos == null || infos.isEmpty()) {
            return;
        }
        WorkInfo info = infos.get(infos.size() - 1);
        if (!info.getState().isFinished()) {
            setBackupButtonsEnabled(false);
            showStatus(info.getState() == WorkInfo.State.RUNNING
                    ? "Backing up…" : "Backup waiting for an internet connection…");
            return;
        }
        setBackupButtonsEnabled(true);
        if (!manualBackupRequested) {
            return;
        }
        manualBackupRequested = false;
        String message = info.getOutputData().getString(BackupWorker.KEY_MESSAGE);
        showStatus(message != null ? message : "Backup failed");
        renderLastBackup();
        if (info.getOutputData().getBoolean(BackupWorker.KEY_NEEDS_CONSENT, false)) {
            needsConsent = true;
            updateBackupCard();
        }
    }

    private void confirmBackup() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Back up to Google Drive?")
                .setMessage("This saves a new backup of your data to your Drive. Only your "
                        + GoogleDriveService.KEEP_BACKUPS + " newest backups are kept.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Back up", (dialog, which) -> {
                    manualBackupRequested = true;
                    BackupScheduler.backUpNow(requireContext().getApplicationContext());
                })
                .show();
    }

    private interface DriveTask {
        String run(Context context) throws Exception;
    }

    /** Runs a Drive job off the main thread and shows its result, if this screen is still showing. */
    private void runOnDrive(String progress, String failure, DriveTask task) {
        showStatus(progress);
        setBackupButtonsEnabled(false);
        Context app = requireContext().getApplicationContext();
        driveExecutor.execute(() -> {
            String message;
            boolean consent = false;
            try {
                message = task.run(app);
            } catch (DriveAuth.NeedsConsentException e) {
                message = "Sign in to Google Drive again first";
                consent = true;
            } catch (Exception e) {
                Log.e(TAG, failure, e);
                message = failure + e.getMessage();
            }
            String shown = message;
            boolean lostAccess = consent;
            onUi(() -> {
                setBackupButtonsEnabled(true);
                showStatus(shown);
                if (lostAccess) {
                    needsConsent = true;
                    updateBackupCard();
                }
            });
        });
    }

    private void onUi(Runnable action) {
        Activity activity = getActivity();
        if (activity != null) {
            activity.runOnUiThread(() -> {
                if (binding != null) {
                    action.run();
                }
            });
        }
    }

    private void confirmRestore() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Restore from Google Drive?")
                .setMessage("This deletes everything currently on this phone and replaces it with your latest backup.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete and restore", (dialog, which) -> runOnDrive("Restoring…",
                        "Restore failed, nothing was changed: ", BackupDataLoader::restoreLatest))
                .show();
    }

    private void confirmDeleteOld() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Clear old backups?")
                .setMessage("This permanently deletes older backups from your Drive.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Clear", (dialog, which) -> runOnDrive("Clearing old backups…",
                        "Couldn't clear old backups: ", BackupDataLoader::clearOldBackups))
                .show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // A restore that has started still finishes; nothing new starts
        driveExecutor.shutdown();
    }
}
