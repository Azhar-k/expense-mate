package com.example.expensemate;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
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
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.navigation.NavigationView;
import com.example.expensemate.service.SmsMonitorService;

public class MainActivity extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener {
    private static final String TAG = "MainActivity";
    private static final int SMS_PERMISSION_REQUEST_CODE = 123;
    private DrawerLayout drawerLayout;
    private ActionBarDrawerToggle drawerToggle;
    private BottomNavigationView bottomNavView;

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
        NavigationView navigationView = findViewById(R.id.nav_view_drawer);
        navigationView.setNavigationItemSelectedListener(this);

        // Set up drawer toggle
        drawerToggle = new ActionBarDrawerToggle(
                this,
                drawerLayout,
                toolbar,
                R.string.navigation_drawer_open,
                R.string.navigation_drawer_close
        );
        drawerLayout.addDrawerListener(drawerToggle);
        drawerToggle.syncState();

        // Set up navigation
        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.nav_host_fragment);
        if (navHostFragment != null) {
            NavController navController = navHostFragment.getNavController();
            bottomNavView = findViewById(R.id.nav_view);
            NavigationUI.setupWithNavController(bottomNavView, navController);
            
            // Add navigation listener to handle bottom nav visibility
            navController.addOnDestinationChangedListener((controller, destination, arguments) -> {
                if (destination.getId() == R.id.navigation_categories) {
                    bottomNavView.setVisibility(View.GONE);
                } else {
                    bottomNavView.setVisibility(View.VISIBLE);
                }
            });
        }

        // Check and request SMS permission
        checkSmsPermission();

        // Start SMS monitoring service
        startSmsMonitorService();
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        NavController navController = ((NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.nav_host_fragment)).getNavController();
        
        if (id == R.id.nav_home) {
            navController.navigate(R.id.navigation_expense);
        } else if (id == R.id.nav_categories) {
            navController.navigate(R.id.navigation_categories);
        } else if (id == R.id.nav_settings) {
            // TODO: Handle settings
        } else if (id == R.id.nav_about) {
            // TODO: Handle about
        }

        drawerLayout.closeDrawer(GravityCompat.START);
        return true;
    }

    @Override
    public void onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

    private void checkSmsPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.RECEIVE_SMS},
                    SMS_PERMISSION_REQUEST_CODE);
        }
    }

    private void startSmsMonitorService() {
        Intent serviceIntent = new Intent(this, SmsMonitorService.class);
        startService(serviceIntent);
        Log.d(TAG, "SMS Monitor Service started");
    }
}