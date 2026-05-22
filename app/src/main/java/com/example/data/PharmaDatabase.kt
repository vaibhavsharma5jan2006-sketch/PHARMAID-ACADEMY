package com.example.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PharmaDao {
    // --- Syllabus Progress ---
    @Query("SELECT * FROM syllabus_progress WHERE subjectId = :subjectId")
    fun getProgressForSubject(subjectId: String): Flow<List<SyllabusProgress>>

    @Query("SELECT * FROM syllabus_progress")
    fun getAllProgress(): Flow<List<SyllabusProgress>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateProgress(progress: SyllabusProgress)

    // --- Bookmarks ---
    @Query("SELECT * FROM bookmarks ORDER BY timestamp DESC")
    fun getAllBookmarks(): Flow<List<BookmarkItem>>

    @Query("SELECT * FROM bookmarks WHERE itemId = :itemId AND type = :type LIMIT 1")
    fun getBookmark(itemId: String, type: String): Flow<BookmarkItem?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addBookmark(bookmark: BookmarkItem)

    @Query("DELETE FROM bookmarks WHERE itemId = :itemId AND type = :type")
    suspend fun removeBookmark(itemId: String, type: String)

    // --- Quiz Attempts ---
    @Query("SELECT * FROM quiz_attempts ORDER BY timestamp DESC")
    fun getAllQuizAttempts(): Flow<List<QuizAttempt>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuizAttempt(attempt: QuizAttempt)

    // --- Focus Sessions ---
    @Query("SELECT * FROM focus_sessions ORDER BY timestamp DESC")
    fun getAllFocusSessions(): Flow<List<FocusSession>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFocusSession(session: FocusSession)
}

@Database(
    entities = [
        SyllabusProgress::class,
        BookmarkItem::class,
        QuizAttempt::class,
        FocusSession::class
    ],
    version = 1,
    exportSchema = false
)
abstract class PharmaDatabase : RoomDatabase() {
    abstract val pharmaDao: PharmaDao

    companion object {
        @Volatile
        private var INSTANCE: PharmaDatabase? = null

        fun getDatabase(context: Context): PharmaDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    PharmaDatabase::class.java,
                    "pharmaid_academy_db"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
