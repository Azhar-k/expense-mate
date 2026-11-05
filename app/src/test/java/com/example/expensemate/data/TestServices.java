package com.example.expensemate.data;

import com.example.expensemate.data.room.RoomStores;
import com.example.expensemate.domain.local.LocalServices;

/** The real services over a test database, with writes finished before each call returns. */
final class TestServices {
    private TestServices() {
    }

    static LocalServices over(AppDatabase database) {
        return new LocalServices(new RoomStores(database), Runnable::run);
    }
}
