package com.example.paperly.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RecentDocumentDao {
  @Query("SELECT * FROM recent_documents ORDER BY lastOpenedAt DESC")
  fun observeAll(): Flow<List<RecentDocumentEntity>>

  @Query("SELECT * FROM recent_documents WHERE isFavorite = 1 ORDER BY lastOpenedAt DESC")
  fun observeFavorites(): Flow<List<RecentDocumentEntity>>

  @Query("SELECT * FROM recent_documents WHERE uriString = :uri LIMIT 1")
  suspend fun findByUri(uri: String): RecentDocumentEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsert(doc: RecentDocumentEntity)

  @Query("UPDATE recent_documents SET isFavorite = :favorite WHERE uriString = :uri")
  suspend fun setFavorite(uri: String, favorite: Boolean)

  @Query("UPDATE recent_documents SET lastPageIndex = :pageIndex, lastZoom = :zoom, lastOpenedAt = :openedAt WHERE uriString = :uri")
  suspend fun updatePdfProgress(uri: String, pageIndex: Int, zoom: Float, openedAt: Long)

  @Query("UPDATE recent_documents SET lastSheetIndex = :sheetIndex, lastOpenedAt = :openedAt WHERE uriString = :uri")
  suspend fun updateSheetProgress(uri: String, sheetIndex: Int, openedAt: Long)

  @Query("DELETE FROM recent_documents WHERE uriString = :uri")
  suspend fun deleteByUri(uri: String)

  @Query("DELETE FROM recent_documents")
  suspend fun clearAll()
}

@Dao
interface BookmarkDao {
  @Query("SELECT * FROM bookmarks WHERE documentUri = :uri ORDER BY pageIndex ASC")
  fun observeForDocument(uri: String): Flow<List<BookmarkEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insert(bookmark: BookmarkEntity)

  @Query("DELETE FROM bookmarks WHERE id = :id")
  suspend fun deleteById(id: Long)

  @Query("DELETE FROM bookmarks WHERE documentUri = :uri AND pageIndex = :pageIndex")
  suspend fun deleteAtPage(uri: String, pageIndex: Int)
}

@Dao
interface CustomThemeDao {
  @Query("SELECT * FROM custom_themes ORDER BY updatedAt DESC")
  fun observeAll(): Flow<List<CustomThemeEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsert(entity: CustomThemeEntity)

  @Query("DELETE FROM custom_themes WHERE id = :id")
  suspend fun deleteById(id: String)
}

@Dao
interface PrivacyProjectDao {
  @Query("SELECT * FROM recent_privacy_projects ORDER BY updatedAt DESC")
  fun observeAll(): Flow<List<RecentPrivacyProjectEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsert(entity: RecentPrivacyProjectEntity)

  @Query("DELETE FROM recent_privacy_projects WHERE id = :id")
  suspend fun deleteById(id: String)

  @Query("DELETE FROM recent_privacy_projects")
  suspend fun clearAll()
}

