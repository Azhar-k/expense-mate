package com.example.expensemate;

import android.content.Context;

import com.example.expensemate.data.AppDatabase;
import com.example.expensemate.data.room.RoomStores;
import com.example.expensemate.domain.Services;
import com.example.expensemate.domain.local.LocalServices;

import java.util.concurrent.Executors;

/** Chooses the backend once, so screens and receivers only ever see the service interfaces. */
public final class AppServices {
    private static volatile Services INSTANCE;

    private AppServices() {
    }

    // Built on first use, so the database opens only when something needs it
    public static Services get(Context context) {
        if (INSTANCE == null) {
            synchronized (AppServices.class) {
                if (INSTANCE == null) {
                    INSTANCE = new LocalServices(new RoomStores(AppDatabase.getDatabase(context)),
                            Executors.newSingleThreadExecutor());
                }
            }
        }
        return INSTANCE;
    }
}
