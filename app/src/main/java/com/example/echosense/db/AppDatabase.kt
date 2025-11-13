package com.echosense.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.echosense.models.*

@Database(
    entities = [Session::class, Speaker::class, TranscriptEntry::class, Note::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    
    abstract fun sessionDao(): SessionDao
    abstract fun noteDao(): NoteDao
    
    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null
        
        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "echosense_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}