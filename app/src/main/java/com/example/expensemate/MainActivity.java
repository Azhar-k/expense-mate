package com.example.expensemate;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import com.example.expensemate.service.RcsNotificationListenerService;
import com.example.expensemate.service.SmsMonitorService;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private static final int SMS_PERMISSION_REQUEST_CODE = 123;
    private static final int FOREGROUND_SERVICE_PERMISSION_REQUEST_CODE = 124;
    private static final String PREFS = "main_activity";
    private static final String PREF_NOTIFICATION_ACCESS_ASKED = "notification_access_asked";
    private static final String PREF_SMS_EXPLAINED = "sms_explained";

    private BottomNavigationView bottomNavView;
    private NavController navController;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.nav_host_fragment);
        navController = navHostFragment.getNavController();
        bottomNavView = findViewById(R.id.nav_view);
        NavigationUI.setupWithNavController(bottomNavView, navController);

        AppBarConfiguration appBarConfiguration = new AppBarConfiguration.Builder(
                R.id.navigation_home, R.id.navigation_activity, R.id.navigation_bills, R.id.navigation_accounts)
                .build();
        NavigationUI.setupActionBarWithNavController(this, navController, appBarConfiguration);

        // Tabs are for the four main screens; anything deeper gets a back arrow instead
        navController.addOnDestinationChangedListener((controller, destination, arguments) -> {
            int id = destination.getId();
            boolean topLevel = id == R.id.navigation_home || id == R.id.navigation_activity
                    || id == R.id.navigation_bills || id == R.id.navigation_accounts;
            bottomNavView.setVisibility(topLevel ? View.VISIBLE : View.GONE);
        });

        startCaptureSetup();
    }

    @Override
    public boolean onSupportNavigateUp() {
        return navController.navigateUp() || super.onSupportNavigateUp();
    }

    public void openTab(int tabId) {
        bottomNavView.setSelectedItemId(tabId);
    }

    /** Anchors snackbars above the tabs when they are showing. */
    public View snackbarAnchor() {
        return bottomNavView.getVisibility() == View.VISIBLE ? bottomNavView : null;
    }

    public static boolean hasSmsPermission(Context context) {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS)
                == PackageManager.PERMISSION_GRANTED
                && ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS)
                        == PackageManager.PERMISSION_GRANTED;
    }

    public static boolean isNotificationListenerEnabled(Context context) {
        ComponentName cn = new ComponentName(context, RcsNotificationListenerService.class);
        String flat = Settings.Secure.getString(context.getContentResolver(), "enabled_notification_listeners");
        return flat != null && flat.contains(cn.flattenToString());
    }

    private SharedPreferences prefs() {
        return getSharedPreferences(PREFS, MODE_PRIVATE);
    }

    // On first launch, explain why before the system asks. After that the app does not
    // nag: Settings shows what is off and how to turn it on.
    private void startCaptureSetup() {
        if (hasSmsPermission(this)) {
            checkAndRequestPermissions();
            askForNotificationAccessOnce();
        } else if (!prefs().getBoolean(PREF_SMS_EXPLAINED, false)) {
            prefs().edit().putBoolean(PREF_SMS_EXPLAINED, true).apply();
            new MaterialAlertDialogBuilder(this)
                    .setTitle("Track payments automatically")
                    .setMessage("Expense Mate reads bank messages on your phone to record transactions. "
                            + "Messages stay on this device unless you back them up to your own Google Drive.")
                    .setPositiveButton("Continue", (dialog, which) -> requestSmsPermission())
                    .setNegativeButton("Not now", (dialog, which) -> askForNotificationAccessOnce())
                    .show();
        }
    }

    public void requestSmsPermission() {
        ActivityCompat.requestPermissions(this,
                new String[] { Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS },
                SMS_PERMISSION_REQUEST_CODE);
    }

    private void checkAndRequestPermissions() {
        // Check foreground service data sync permission for Android 14+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
                && ContextCompat.checkSelfPermission(this, Manifest.permission.FOREGROUND_SERVICE_DATA_SYNC)
                        != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[] { Manifest.permission.FOREGROUND_SERVICE_DATA_SYNC },
                    FOREGROUND_SERVICE_PERMISSION_REQUEST_CODE);
            return;
        }
        startSmsMonitorService();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
            @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == SMS_PERMISSION_REQUEST_CODE) {
            if (hasSmsPermission(this)) {
                checkAndRequestPermissions();
            }
            askForNotificationAccessOnce();
        } else if (requestCode == FOREGROUND_SERVICE_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startSmsMonitorService();
            }
        }
    }

    private void startSmsMonitorService() {
        Intent serviceIntent = new Intent(this, SmsMonitorService.class);
        startService(serviceIntent);
        Log.d(TAG, "SMS Monitor Service started");
    }

    private void askForNotificationAccessOnce() {
        if (isNotificationListenerEnabled(this) || prefs().getBoolean(PREF_NOTIFICATION_ACCESS_ASKED, false)) {
            return;
        }
        prefs().edit().putBoolean(PREF_NOTIFICATION_ACCESS_ASKED, true).apply();
        new MaterialAlertDialogBuilder(this)
                .setTitle("Catch RCS bank messages too")
                .setMessage("Some banks send RCS chats instead of SMS. Notification access lets Expense Mate "
                        + "see those. You can change this any time in Settings.")
                .setPositiveButton("Open settings",
                        (dialog, which) -> startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)))
                .setNegativeButton("Not now", null)
                .show();
    }
}
