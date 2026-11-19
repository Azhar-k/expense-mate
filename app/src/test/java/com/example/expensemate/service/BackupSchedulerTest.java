package com.example.expensemate.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.work.Configuration;
import androidx.work.ListenableWorker;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;
import androidx.work.testing.SynchronousExecutor;
import androidx.work.testing.TestWorkerBuilder;
import androidx.work.testing.WorkManagerTestInitHelper;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class BackupSchedulerTest {
    private Context context;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        WorkManagerTestInitHelper.initializeTestWorkManager(context,
                new Configuration.Builder().setExecutor(new SynchronousExecutor()).build());
        context.getSharedPreferences("backup", Context.MODE_PRIVATE).edit().clear().commit();
    }

    private List<WorkInfo> work(String name) throws Exception {
        return WorkManager.getInstance(context).getWorkInfosForUniqueWork(name).get();
    }

    @Test
    public void theDailyBackupWaitsForAConnectionAndBattery() {
        PeriodicWorkRequest request = BackupScheduler.dailyRequest();
        assertEquals(TimeUnit.DAYS.toMillis(1), request.getWorkSpec().intervalDuration);
        assertEquals(NetworkType.CONNECTED, request.getWorkSpec().constraints.getRequiredNetworkType());
        assertTrue(request.getWorkSpec().constraints.requiresBatteryNotLow());
    }

    @Test
    public void turningAutomaticBackupOnAndOffSchedulesAndCancelsIt() throws Exception {
        assertFalse(BackupScheduler.isAutomatic(context));

        BackupScheduler.setAutomatic(context, true);
        assertTrue(BackupScheduler.isAutomatic(context));
        assertEquals(1, work(BackupScheduler.AUTO_WORK).size());
        assertEquals(WorkInfo.State.ENQUEUED, work(BackupScheduler.AUTO_WORK).get(0).getState());

        BackupScheduler.setAutomatic(context, true);
        assertEquals("Turning it on again keeps the one schedule", 1, work(BackupScheduler.AUTO_WORK).size());

        BackupScheduler.onSignedOut(context);
        assertFalse(BackupScheduler.isAutomatic(context));
        assertEquals(WorkInfo.State.CANCELLED, work(BackupScheduler.AUTO_WORK).get(0).getState());
    }

    @Test
    public void aBackupWithoutAnAccountAsksToSignInAgain() {
        BackupWorker worker = TestWorkerBuilder.from(context, BackupWorker.class, Executors.newSingleThreadExecutor())
                .build();
        ListenableWorker.Result result = worker.doWork();

        assertTrue(result instanceof ListenableWorker.Result.Failure);
        assertTrue("Retrying cannot help", result.getOutputData().getBoolean(BackupWorker.KEY_NEEDS_CONSENT, false));
        assertNotNull(BackupScheduler.problem(context));
    }

    @Test
    public void theLastBackupOnlyMovesForwardAndClearsTheProblem() {
        BackupScheduler.recordProblem(context, "failed");
        BackupScheduler.recordBackup(context, 2000);
        BackupScheduler.recordBackup(context, 1000); // An older one found on Drive
        assertEquals(2000, BackupScheduler.lastBackup(context));
        assertNull(BackupScheduler.problem(context));
    }

    @Test
    public void signingOutForgetsTheAccount() {
        DriveAuth.saveAccount(context, "someone@example.com");
        assertTrue(DriveAuth.isSignedIn(context));
        DriveAuth.signOut(context);
        assertFalse(DriveAuth.isSignedIn(context));
        assertNull(DriveAuth.accountName(context));
    }
}
