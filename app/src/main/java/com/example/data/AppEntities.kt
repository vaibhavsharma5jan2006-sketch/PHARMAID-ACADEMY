package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "syllabus_progress")
data class SyllabusProgress(
    @PrimaryKey val progressKey: String, // subjectId + "||" + topicName
    val subjectId: String,
    val topicName: String,
    val isCompleted: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "bookmarks")
data class BookmarkItem(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val type: String, // "NOTE" "VIDEO"
    val subjectId: String,
    val itemId: String, // noteId or videoId
    val title: String,
    val subtitle: String, // chapter name for notes, duration/tutor for videos
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "quiz_attempts")
data class QuizAttempt(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val subjectId: String,
    val subjectName: String,
    val totalMcqs: Int,
    val correctAnswers: Int,
    val scorePercent: Int,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "focus_sessions")
data class FocusSession(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val durationSeconds: Long,
    val category: String, // e.g. "HAP", "Pharmaceutics", "General"
    val timestamp: Long = System.currentTimeMillis()
)
