package com.example.paperly.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
  entities = [
    RecentDocumentEntity::class,
    BookmarkEntity::class,
    CustomThemeEntity::class,
    RecentPrivacyProjectEntity::class
  ],
  version = 2,
  exportSchema = false
)
abstract class PaperlyDatabase : RoomDatabase() {
  abstract fun recentDocumentDao(): RecentDocumentDao
  abstract fun bookmarkDao(): BookmarkDao
  abstract fun customThemeDao(): CustomThemeDao
  abstract fun privacyProjectDao(): PrivacyProjectDao

  companion object {
    fun build(context: Context): PaperlyDatabase =
      Room.databaseBuilder(context.applicationContext, PaperlyDatabase::class.java, "paperly_workspace.db")
        .fallbackToDestructiveMigration(dropAllTables = true)
        .build()
  }
}
