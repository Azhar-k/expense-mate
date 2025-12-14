package com.example.expensemate;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.navigation.NavController;
import androidx.navigation.NavDestination;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.NavigationUI;
import androidx.fragment.app.Fragment;

import com.example.expensemate.ui.accounts.AccountDetailsFragment;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.navigation.NavigationView;
import com.example.expensemate.service.SmsMonitorService;
import android.widget.Toast;

public class MainActivity extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener {
    private static final String TAG = "MainActivity";
    private static final int SMS_PERMISSION_REQUEST_CODE = 123;
    private static final int FOREGROUND_SERVICE_PERMISSION_REQUEST_CODE = 124;
    private DrawerLayout drawerLayout;
    private ActionBarDrawerToggle drawerToggle;
    private BottomNavigationView bottomNavView;
    private NavigationView navigationView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Set up toolbar
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setTitle("Expense Mate");

        // Set up drawer
        drawerLayout = findViewById(R.id.drawer_layout);
        navigationView = findViewById(R.id.nav_view_drawer);
        navigationView.setNavigationItemSelectedListener(this);

        // Set up drawer toggle
        drawerToggle = new ActionBarDrawerToggle(
                this,
                drawerLayout,
                toolbar,
                R.string.navigation_drawer_open,
                R.string.navigation_drawer_close);
        drawerLayout.addDrawerListener(drawerToggle);
        drawerToggle.syncState();

        // Set up navigation
        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.nav_host_fragment);
        if (navHostFragment != null) {
            NavController navController = navHostFragment.getNavController();
            bottomNavView = findViewById(R.id.nav_view);
            NavigationUI.setupWithNavController(bottomNavView, navController);

            // Add navigation listener to handle bottom nav visibility and drawer selection
            navController.addOnDestinationChangedListener((controller, destination, arguments) -> {
                if (destination.getId() == R.id.navigation_categories ||
                        destination.getId() == R.id.navigation_recurring_payments ||
                        destination.getId() == R.id.navigation_sms_scan ||
                        destination.getId() == R.id.navigation_self_transfer ||
                        destination.getId() == R.id.navigation_settings) {
                    bottomNavView.setVisibility(View.GONE);
                    if (destination.getId() == R.id.navigation_categories) {
                        navigationView.setCheckedItem(R.id.nav_categories);
                    } else if (destination.getId() == R.id.navigation_sms_scan) {
                        navigationView.setCheckedItem(R.id.nav_sms_scan);
                    } else if (destination.getId() == R.id.navigation_self_transfer) {
                        navigationView.setCheckedItem(R.id.nav_self_transfer);
                    } else if (destination.getId() == R.id.navigation_settings) {
                        navigationView.setCheckedItem(R.id.nav_settings);
                    } else {
                        navigationView.setCheckedItem(R.id.nav_recurring_payments);
                    }
                } else {
                    bottomNavView.setVisibility(View.VISIBLE);
                    if (destination.getId() == R.id.navigation_summary) {
                        navigationView.setCheckedItem(R.id.nav_home);
                    } else if (destination.getId() == R.id.navigation_transactions) {
                        navigationView.setCheckedItem(R.id.nav_home);
                    } else if (destination.getId() == R.id.navigation_accounts) {
                        navigationView.setCheckedItem(R.id.nav_home);
                    }
                }
            });
        }

        // Check and request permissions
        checkAndRequestPermissions();
    }

    private void checkAndRequestPermissions() {
        // Check SMS permissions
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.RECEIVE_SMS) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this,
                        Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[] {
                            Manifest.permission.RECEIVE_SMS,
                            Manifest.permission.READ_SMS
                    },
                    SMS_PERMISSION_REQUEST_CODE);
            return;
        }

        // Check foreground service data sync permission for Android 14+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.FOREGROUND_SERVICE_DATA_SYNC) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[] { Manifest.permission.FOREGROUND_SERVICE_DATA_SYNC },
                        FOREGROUND_SERVICE_PERMISSION_REQUEST_CODE);
                return;
            }
        }

        // If all permissions are granted, start the service
        startSmsMonitorService();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
            @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == SMS_PERMISSION_REQUEST_CODE) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            if (allGranted) {
                // All SMS permissions granted, check for foreground service permission
                checkAndRequestPermissions();
            } else {
                Toast.makeText(this, "SMS permissions are required for the app to function properly",
                        Toast.LENGTH_LONG).show();
            }
        } else if (requestCode == FOREGROUND_SERVICE_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Foreground service permission granted, start the service
                startSmsMonitorService();
            }
        }
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();

        // Get the current fragment
        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.nav_host_fragment);
        if (navHostFragment != null) {
            NavController navController = navHostFragment.getNavController();
            navigateToDestination(id, navController);
        }

        drawerLayout.closeDrawer(GravityCompat.START);
        return true;
    }

    private void navigateToDestination(int id, NavController navController) {
        if (id == R.id.nav_home) {
            navController.navigate(R.id.navigation_summary);
        } else if (id == R.id.nav_categories) {
            navController.navigate(R.id.navigation_categories);
        } else if (id == R.id.nav_self_transfer) {
            navController.navigate(R.id.navigation_self_transfer);
        } else if (id == R.id.nav_sms_scan) {
            navController.navigate(R.id.navigation_sms_scan);
        } else if (id == R.id.nav_recurring_payments) {
            navController.navigate(R.id.navigation_recurring_payments);
        } else if (id == R.id.nav_settings) {
            navController.navigate(R.id.navigation_settings);
        } else if (id == R.id.nav_about) {
            // TODO: Handle about
        }
    }

    @Override
    public void onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

    private void startSmsMonitorService() {
        Intent serviceIntent = new Intent(this, SmsMonitorService.class);
        startService(serviceIntent);
        Log.d(TAG, "SMS Monitor Service started");
    }
}