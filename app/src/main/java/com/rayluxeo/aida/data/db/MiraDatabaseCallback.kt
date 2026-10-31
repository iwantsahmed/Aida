package com.rayluxeo.aida.data.db

import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * SQLite does not enforce foreign keys unless it is asked to, per connection.
 *
 * Room enables them for normal operation, but not inside a migration — the pragma is a no-op
 * within a transaction, so a migration that rebuilds a table can otherwise leave rows pointing
 * at parents that no longer exist. Re-asserting it on open, and checking it once the migration
 * has finished, is what makes the `RESTRICT` and `SET NULL` rules in the entities real rather
 * than decorative.
 */
object MiraDatabaseCallback : RoomDatabase.Callback() {

    override fun onOpen(db: SupportSQLiteDatabase) {
        super.onOpen(db)
        db.execSQL("PRAGMA foreign_keys = ON")
    }
}
