package com.example.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull

class PharmaRepository(private val dao: PharmaDao) {

    // --- Syllabus Progress ---
    fun getProgressForSubject(subjectId: String): Flow<List<SyllabusProgress>> {
        return dao.getProgressForSubject(subjectId)
    }

    fun getAllProgress(): Flow<List<SyllabusProgress>> {
        return dao.getAllProgress()
    }

    suspend fun setSyllabusProgress(subjectId: String, topicName: String, isCompleted: Boolean) {
        val key = "${subjectId}||${topicName}"
        val progress = SyllabusProgress(
            progressKey = key,
            subjectId = subjectId,
            topicName = topicName,
            isCompleted = isCompleted
        )
        dao.insertOrUpdateProgress(progress)
    }

    // --- Bookmarks ---
    fun getAllBookmarks(): Flow<List<BookmarkItem>> {
        return dao.getAllBookmarks()
    }

    fun getBookmark(itemId: String, type: String): Flow<BookmarkItem?> {
        return dao.getBookmark(itemId, type)
    }

    suspend fun toggleBookmark(type: String, subjectId: String, itemId: String, title: String, subtitle: String) {
        val currentBookmark = dao.getBookmark(itemId, type).firstOrNull()
        if (currentBookmark != null) {
            dao.removeBookmark(itemId, type)
        } else {
            val bookmark = BookmarkItem(
                type = type,
                subjectId = subjectId,
                itemId = itemId,
                title = title,
                subtitle = subtitle
            )
            dao.addBookmark(bookmark)
        }
    }

    suspend fun removeBookmarkDirectly(itemId: String, type: String) {
        dao.removeBookmark(itemId, type)
    }

    // --- Quiz Attempts ---
    fun getAllQuizAttempts(): Flow<List<QuizAttempt>> {
        return dao.getAllQuizAttempts()
    }

    suspend fun saveQuizAttempt(attempt: QuizAttempt) {
        dao.insertQuizAttempt(attempt)
    }

    // --- Focus Sessions ---
    fun getAllFocusSessions(): Flow<List<FocusSession>> {
        return dao.getAllFocusSessions()
    }

    suspend fun saveFocusSession(session: FocusSession) {
        dao.insertFocusSession(session)
    }
}
