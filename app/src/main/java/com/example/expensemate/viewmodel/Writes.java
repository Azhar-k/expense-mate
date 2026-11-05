package com.example.expensemate.viewmodel;

import android.util.Log;

import java.util.concurrent.CompletableFuture;

/** Screens do not wait on writes, so a failed one is at least logged. */
final class Writes {
    private Writes() {
    }

    static void log(String tag, String what, CompletableFuture<?> write) {
        write.whenComplete((result, error) -> {
            if (error != null) {
                Log.e(tag, "Error " + what, error);
            }
        });
    }
}
