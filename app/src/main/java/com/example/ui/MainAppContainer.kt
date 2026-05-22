package com.example.ui

import android.app.Application
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.api.GeminiTutor
import com.example.data.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

// --- SCREEN STATES ---
enum class ActiveTab {
    DASHBOARD,
    AI_TUTOR,
    FOCUS_TIMER,
    BOOKMARKS
}

enum class DetailView {
    NONE,
    SUBJECT_HUB,
    NOTE_READER,
    QUIZ_SESSION
}

// --- VIEW MODEL ---
class PharmaViewModel(application: Application) : AndroidViewModel(application) {
    private val database = PharmaDatabase.getDatabase(application)
    private val repository = PharmaRepository(database.pharmaDao)

    // --- State variables ---
    var selectedCourseType by mutableStateOf(CourseType.BPHARM)
    var selectedSemesterIndex by mutableStateOf(0)
    
    // Navigation hierarchy
    var activeTab by mutableStateOf(ActiveTab.DASHBOARD)
    var activeDetailView by mutableStateOf(DetailView.NONE)
    var currentSubject by mutableStateOf<PharmaSubject?>(null)
    var currentNote by mutableStateOf<PharmaNote?>(null)

    // Syllabus tracking
    val allSyllabusProgress = repository.getAllProgress().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Bookmarks flow
    val bookmarkedItems = repository.getAllBookmarks().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Quiz log flow
    val quizAttemptsList = repository.getAllQuizAttempts().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Focus session flow
    val focusSessions = repository.getAllFocusSessions().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // --- AI Tutor State ---
    var aiChatHistory = mutableStateListOf<Pair<String, Boolean>>() // Turn: Text to IsUser
    var aiTutorInput by mutableStateOf("")
    var isAiLoading by mutableStateOf(false)

    // --- Active Practice Quiz State ---
    var quizScore by mutableStateOf(0)
    var quizCurrentQuestionIndex by mutableStateOf(0)
    var selectedOptionIndex by mutableStateOf<Int?>(null)
    var isQuizAnswerSubmitted by mutableStateOf(false)
    var isQuizFinished by mutableStateOf(false)

    // --- Focus Timer State ---
    var timerDurationSeconds by mutableStateOf(1500L) // Default 25 min (1500 sec)
    var isTimerRunning by mutableStateOf(false)
    var timerInitialSeconds by mutableStateOf(1500L)
    var studyBadgeUnlocks = mutableStateListOf<String>()
    
    private var timerJob: Job? = null

    init {
        // Initialize sample badge updates based on study minutes
        viewModelScope.launch {
            focusSessions.collect { sessions ->
                val totalSeconds = sessions.sumOf { it.durationSeconds }
                val totalMin = totalSeconds / 60
                val badges = mutableListOf("Freshman Practitioner")
                if (totalMin >= 1) badges.add("Diligent Pharmacologist")
                if (totalMin >= 10) badges.add("Laboratory Researcher")
                if (totalMin >= 30) badges.add("Clinical Pharmacy Guru")
                
                studyBadgeUnlocks.clear()
                studyBadgeUnlocks.addAll(badges)
            }
        }
    }

    // --- Actions ---
    fun changeCourse(type: CourseType) {
        selectedCourseType = type
        selectedSemesterIndex = 0
    }

    // Toggle syllabus progress
    fun toggleSyllabusProgress(subjectId: String, topicName: String) {
        viewModelScope.launch {
            val progressKey = "${subjectId}||${topicName}"
            val lists = allSyllabusProgress.value
            val current = lists.find { it.progressKey == progressKey }?.isCompleted ?: false
            repository.setSyllabusProgress(subjectId, topicName, !current)
        }
    }

    // Toggle Bookmarks
    fun toggleBookmark(type: String, subjectId: String, itemId: String, title: String, subtitle: String) {
        viewModelScope.launch {
            repository.toggleBookmark(type, subjectId, itemId, title, subtitle)
        }
    }

    fun removeBookmarkDirectly(itemId: String, type: String) {
        viewModelScope.launch {
            repository.removeBookmarkDirectly(itemId, type)
        }
    }

    fun getCourseByActiveSelector(): PharmaCourse {
        return StudyData.courses.first { it.type == selectedCourseType }
    }

    fun getActiveSemester(): PharmaSemester {
        val course = getCourseByActiveSelector()
        return if (selectedSemesterIndex in course.semesters.indices) {
            course.semesters[selectedSemesterIndex]
        } else {
            course.semesters.first()
        }
    }

    // Start a quiz session
    fun startNewQuiz(subject: PharmaSubject) {
        currentSubject = subject
        quizScore = 0
        quizCurrentQuestionIndex = 0
        selectedOptionIndex = null
        isQuizAnswerSubmitted = false
        isQuizFinished = false
        activeDetailView = DetailView.QUIZ_SESSION
    }

    fun submitQuizAnswer() {
        if (selectedOptionIndex == null) return
        val subject = currentSubject ?: return
        val mcq = subject.mcqs[quizCurrentQuestionIndex]
        if (selectedOptionIndex == mcq.correctAnswerIndex) {
            quizScore++
        }
        isQuizAnswerSubmitted = true
    }

    fun nextQuizQuestion() {
        val subject = currentSubject ?: return
        if (quizCurrentQuestionIndex + 1 < subject.mcqs.size) {
            quizCurrentQuestionIndex++
            selectedOptionIndex = null
            isQuizAnswerSubmitted = false
        } else {
            isQuizFinished = true
            // Save attempt to Room
            viewModelScope.launch {
                val percent = (quizScore.toFloat() / subject.mcqs.size * 100).toInt()
                repository.saveQuizAttempt(
                    QuizAttempt(
                        subjectId = subject.id,
                        subjectName = subject.name,
                        totalMcqs = subject.mcqs.size,
                        correctAnswers = quizScore,
                        scorePercent = percent
                    )
                )
            }
        }
    }

    // Timer Actions
    fun startTimer() {
        if (isTimerRunning) return
        isTimerRunning = true
        timerJob = viewModelScope.launch {
            while (timerDurationSeconds > 0) {
                delay(1000L)
                timerDurationSeconds--
            }
            // Timer finished! Log academic study session
            isTimerRunning = false
            repository.saveFocusSession(
                FocusSession(
                    durationSeconds = timerInitialSeconds,
                    category = currentSubject?.name ?: "General Study"
                )
            )
            // Reset to last setup
            timerDurationSeconds = timerInitialSeconds
        }
    }

    fun pauseTimer() {
        isTimerRunning = false
        timerJob?.cancel()
    }

    fun resetTimer() {
        isTimerRunning = false
        timerJob?.cancel()
        timerDurationSeconds = timerInitialSeconds
    }

    fun adjustTimerMinutes(minutes: Int) {
        pauseTimer()
        val newSec = (minutes * 60).toLong()
        timerInitialSeconds = newSec
        timerDurationSeconds = newSec
    }

    // AI Tutor Chat Actions
    fun askAiTutor(customPrompt: String? = null) {
        val message = (customPrompt ?: aiTutorInput).trim()
        if (message.isEmpty() || isAiLoading) return

        if (customPrompt == null) {
            aiTutorInput = ""
        }

        aiChatHistory.add(Pair(message, true))
        isAiLoading = true

        viewModelScope.launch {
            // Pack previous history for clinical context tracking
            val truncatedHistory = aiChatHistory.takeLast(10).dropLast(1)
            val reply = GeminiTutor.generatePharmaHelp(message, truncatedHistory)
            aiChatHistory.add(Pair(reply, false))
            isAiLoading = false
        }
    }

    fun clearAiChat() {
        aiChatHistory.clear()
    }
}


// --- MAIN APP CONTAINER COMPOSABLE ---
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MainAppContainer(viewModel: PharmaViewModel = viewModel()) {
    val currentSubjectProgress by viewModel.allSyllabusProgress.collectAsStateWithLifecycle()
    val bookmarkedItems by viewModel.bookmarkedItems.collectAsStateWithLifecycle()

    Scaffold(
        bottomBar = {
            if (viewModel.activeDetailView == DetailView.NONE) {
                NavigationBar(
                    modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars).testTag("bottom_nav_bar"),
                    tonalElevation = 8.dp
                ) {
                    NavigationBarItem(
                        selected = viewModel.activeTab == ActiveTab.DASHBOARD,
                        onClick = { viewModel.activeTab = ActiveTab.DASHBOARD },
                        icon = { Icon(if (viewModel.activeTab == ActiveTab.DASHBOARD) Icons.Filled.School else Icons.Outlined.School, contentDescription = "Syllabus") },
                        label = { Text("Dashboard", style = MaterialTheme.typography.labelMedium) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )
                    NavigationBarItem(
                        selected = viewModel.activeTab == ActiveTab.AI_TUTOR,
                        onClick = { viewModel.activeTab = ActiveTab.AI_TUTOR },
                        icon = { Icon(if (viewModel.activeTab == ActiveTab.AI_TUTOR) Icons.Filled.Psychology else Icons.Outlined.Psychology, contentDescription = "AI Assistant") },
                        label = { Text("Tutor AI", style = MaterialTheme.typography.labelMedium) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )
                    NavigationBarItem(
                        selected = viewModel.activeTab == ActiveTab.FOCUS_TIMER,
                        onClick = { viewModel.activeTab = ActiveTab.FOCUS_TIMER },
                        icon = { Icon(if (viewModel.activeTab == ActiveTab.FOCUS_TIMER) Icons.Filled.Timer else Icons.Outlined.HourglassEmpty, contentDescription = "Pomodoro") },
                        label = { Text("Study Timer", style = MaterialTheme.typography.labelMedium) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )
                    NavigationBarItem(
                        selected = viewModel.activeTab == ActiveTab.BOOKMARKS,
                        onClick = { viewModel.activeTab = ActiveTab.BOOKMARKS },
                        icon = { Icon(if (viewModel.activeTab == ActiveTab.BOOKMARKS) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder, contentDescription = "Library") },
                        label = { Text("Bookmarks", style = MaterialTheme.typography.labelMedium) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )
                }
            }
        },
        contentWindowInsets = WindowInsets.safeDrawing
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding)
        ) {
            AnimatedContent(
                targetState = viewModel.activeDetailView,
                transitionSpec = {
                    slideInVertically(initialOffsetY = { it }, animationSpec = tween(250)) togetherWith
                            slideOutVertically(targetOffsetY = { it }, animationSpec = tween(200))
                },
                label = "DetailNavTransition"
            ) { detailView ->
                when (detailView) {
                    DetailView.SUBJECT_HUB -> {
                        viewModel.currentSubject?.let { subject ->
                            SubjectHubScreen(subject = subject, viewModel = viewModel)
                        }
                    }
                    DetailView.NOTE_READER -> {
                        viewModel.currentNote?.let { note ->
                            NoteReaderScreen(note = note, viewModel = viewModel)
                        }
                    }
                    DetailView.QUIZ_SESSION -> {
                        viewModel.currentSubject?.let { subject ->
                            QuizSessionScreen(subject = subject, viewModel = viewModel)
                        }
                    }
                    DetailView.NONE -> {
                        // Display primary navigation tabs
                        when (viewModel.activeTab) {
                            ActiveTab.DASHBOARD -> DashboardHubScreen(viewModel = viewModel)
                            ActiveTab.AI_TUTOR -> TutorScreen(viewModel = viewModel)
                            ActiveTab.FOCUS_TIMER -> FocusTimerScreen(viewModel = viewModel)
                            ActiveTab.BOOKMARKS -> BookmarksScreen(viewModel = viewModel)
                        }
                    }
                }
            }
        }
    }
}


// --- 1. DASHBOARD HUB SCREEN ---
@Composable
fun DashboardHubScreen(viewModel: PharmaViewModel) {
    val activeCourse = viewModel.getCourseByActiveSelector()
    val activeSemester = viewModel.getActiveSemester()
    val progressList by viewModel.allSyllabusProgress.collectAsStateWithLifecycle()
    val focusSessionLogs by viewModel.focusSessions.collectAsStateWithLifecycle()

    val totalProgressPercent = remember(progressList, activeCourse) {
        val totalTopics = StudyData.courses.flatMap { it.semesters }.flatMap { it.subjects }.flatMap { it.syllabus }.flatMap { it.topics }.size
        if (totalTopics == 0) 0 else {
            val completedCount = progressList.count { it.isCompleted }
            (completedCount.toFloat() / totalTopics * 100).toInt().coerceIn(0, 100)
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("dashboard_scroll"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // App Academic Header
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(20.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "PHARMAID ACADEMY",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                letterSpacing = 1.5.sp
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            imageVector = Icons.Filled.LocalPharmacy,
                            contentDescription = "Clinics icon",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    Text(
                        text = "Authorized syllabus companion of Carewell Pharma class notes.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                        modifier = Modifier.padding(top = 4.dp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                "Study Analytics",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                            )
                            Text(
                                "Syllabus Accomplished: $totalProgressPercent%",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        CircularProgressIndicator(
                            progress = { totalProgressPercent.toFloat() / 100 },
                            modifier = Modifier.size(44.dp),
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 6.dp,
                            trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f)
                        )
                    }

                    LinearProgressIndicator(
                        progress = { totalProgressPercent.toFloat() / 100 },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .height(8.dp)
                            .clip(CircleShape),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f)
                    )
                }
            }
        }

        // Course Selector Tabs
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(4.dp)
                ) {
                    Button(
                        onClick = { viewModel.changeCourse(CourseType.BPHARM) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("bpharm_tab"),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (viewModel.selectedCourseType == CourseType.BPHARM) MaterialTheme.colorScheme.primary else Color.Transparent,
                            contentColor = if (viewModel.selectedCourseType == CourseType.BPHARM) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        elevation = if (viewModel.selectedCourseType == CourseType.BPHARM) ButtonDefaults.buttonElevation(defaultElevation = 2.dp) else null
                    ) {
                        Text("B. Pharmacy", fontWeight = FontWeight.SemiBold)
                    }
                    Button(
                        onClick = { viewModel.changeCourse(CourseType.DPHARM) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("dpharm_tab"),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (viewModel.selectedCourseType == CourseType.DPHARM) MaterialTheme.colorScheme.primary else Color.Transparent,
                            contentColor = if (viewModel.selectedCourseType == CourseType.DPHARM) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        elevation = if (viewModel.selectedCourseType == CourseType.DPHARM) ButtonDefaults.buttonElevation(defaultElevation = 2.dp) else null
                    ) {
                        Text("D. Pharmacy", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        // Semesters / Years Slider (Horizontal scroll list)
        item {
            Column {
                Text(
                    text = if (viewModel.selectedCourseType == CourseType.BPHARM) "Select Semester" else "Select Academic Year",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val semesters = StudyData.courses.first { it.type == viewModel.selectedCourseType }.semesters
                    semesters.forEachIndexed { index, sem ->
                        val isSelected = viewModel.selectedSemesterIndex == index
                        FilterChip(
                            selected = isSelected,
                            onClick = { viewModel.selectedSemesterIndex = index },
                            label = { Text(sem.name, fontWeight = FontWeight.SemiBold) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                            )
                        )
                    }
                }
            }
        }

        // Active Semester Subjects Cards grid
        item {
            Text(
                text = "${activeSemester.name} Subjects",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        if (activeSemester.subjects.isEmpty()) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = "Pending info",
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Curriculum Coming Soon",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            "Subject details for this phase are being prepared by academic advisors.",
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        } else {
            items(activeSemester.subjects) { subject ->
                // Calculate individual subject completion percent
                val subjectCompletedCount = remember(progressList) {
                    progressList.count { it.subjectId == subject.id && it.isCompleted }
                }
                val subjectTotalTopics = subject.syllabus.flatMap { it.topics }.size
                val subjectPercent = if (subjectTotalTopics == 0) 0 else {
                    ((subjectCompletedCount.toFloat() / subjectTotalTopics) * 100).toInt()
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            viewModel.currentSubject = subject
                            viewModel.activeDetailView = DetailView.SUBJECT_HUB
                        }
                        .testTag("subject_card_${subject.id}"),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
                    shape = RoundedCornerShape(12.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.Top) {
                            Column(modifier = Modifier.weight(1f)) {
                                Badge(
                                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                    contentColor = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.align(Alignment.Start)
                                ) {
                                    Text(
                                        subject.code,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = subject.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            IconButton(
                                onClick = {
                                    viewModel.currentSubject = subject
                                    viewModel.activeDetailView = DetailView.SUBJECT_HUB
                                }
                            ) {
                                Icon(Icons.Default.ChevronRight, contentDescription = "Enter Hub", tint = MaterialTheme.colorScheme.primary)
                            }
                        }

                        Text(
                            text = subject.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(vertical = 6.dp)
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Source,
                                    contentDescription = "Units",
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                                )
                                Text(
                                    text = "${subject.syllabus.size} Syllabus Units",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = 4.dp)
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "${subject.subjectPercentCompleted} ($subjectPercent%)",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        LinearProgressIndicator(
                            progress = { if (subjectTotalTopics == 0) 0f else subjectCompletedCount.toFloat() / subjectTotalTopics },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 6.dp)
                                .height(4.dp)
                                .clip(CircleShape),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                        )
                    }
                }
            }
        }

        // Badges Section based on accomplishments
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.WorkspacePremium, contentDescription = "Achievements", tint = MaterialTheme.colorScheme.tertiary)
                        Text(
                            "Pharmaid Badges Unlocked",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    if (viewModel.studyBadgeUnlocks.isEmpty()) {
                        Text("Finish focused Pomodoro study sessions to award expert pharmacology levels!", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        // Display badges in a simple flex flow
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            viewModel.studyBadgeUnlocks.forEach { badge ->
                                Card(
                                    shape = RoundedCornerShape(30.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Filled.Verified, contentDescription = "Verified badge", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(badge, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

val PharmaSubject.subjectPercentCompleted: String
    get() = "Studied"


// --- 2. SUBJECT DETAILS & NAVIGATION HUB SCREEN ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubjectHubScreen(subject: PharmaSubject, viewModel: PharmaViewModel) {
    val progressList by viewModel.allSyllabusProgress.collectAsStateWithLifecycle()
    val bookmarks by viewModel.bookmarkedItems.collectAsStateWithLifecycle()
    var selectedSubTab by remember { mutableStateOf(0) } // 0: Syllabus Tracker, 1: Class Notes, 2: Lectured Videos, 3: Exam MCQs

    Column(modifier = Modifier.fillMaxSize()) {
        // App header bar with back arrow
        TopAppBar(
            title = {
                Column {
                    Text(
                        subject.name,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(subject.code, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                }
            },
            navigationIcon = {
                IconButton(onClick = { viewModel.activeDetailView = DetailView.NONE }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Return home")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
        )

        // Horizontal tabs across options
        SecondaryTabRow(
            selectedTabIndex = selectedSubTab,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary
        ) {
            Tab(selected = selectedSubTab == 0, onClick = { selectedSubTab = 0 }) {
                Text("Syllabus", modifier = Modifier.padding(12.dp), fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
            Tab(selected = selectedSubTab == 1, onClick = { selectedSubTab = 1 }) {
                Text("Notes", modifier = Modifier.padding(12.dp), fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
            Tab(selected = selectedSubTab == 2, onClick = { selectedSubTab = 2 }) {
                Text("Videos", modifier = Modifier.padding(12.dp), fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
            Tab(selected = selectedSubTab == 3, onClick = { selectedSubTab = 3 }) {
                Text("MCQ Quiz", modifier = Modifier.padding(12.dp), fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
                .background(MaterialTheme.colorScheme.background)
        ) {
            when (selectedSubTab) {
                0 -> SyllabusTabContent(subject = subject, progressList = progressList, viewModel = viewModel)
                1 -> NotesTabContent(subject = subject, bookmarks = bookmarks, viewModel = viewModel)
                2 -> VideosTabContent(subject = subject, bookmarks = bookmarks, viewModel = viewModel)
                3 -> QuizTabContent(subject = subject, viewModel = viewModel)
            }
        }
    }
}

// --- SYLLABUS TAB CONTENT ---
@Composable
fun SyllabusTabContent(subject: PharmaSubject, progressList: List<SyllabusProgress>, viewModel: PharmaViewModel) {
    if (subject.syllabus.isEmpty()) {
        SyllabusPlaceholderUnit()
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(subject.syllabus) { unit ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = unit.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = unit.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 8.dp),
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                        )

                        unit.topics.forEach { topic ->
                            val progressKey = "${subject.id}||$topic"
                            val isCompleted = progressList.find { it.progressKey == progressKey }?.isCompleted ?: false

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.toggleSyllabusProgress(subject.id, topic) }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = isCompleted,
                                    onCheckedChange = { viewModel.toggleSyllabusProgress(subject.id, topic) },
                                    modifier = Modifier.testTag("checkbox_${topic.hashCode()}")
                                )
                                Text(
                                    text = topic,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (isCompleted) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurface,
                                    fontWeight = if (isCompleted) FontWeight.Normal else FontWeight.Medium,
                                    modifier = Modifier
                                        .padding(start = 8.dp)
                                        .weight(1f)
                                )
                                if (isCompleted) {
                                    Icon(Icons.Filled.CheckCircle, contentDescription = "Finished", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SyllabusPlaceholderUnit() {
    Box(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Filled.MenuBook, contentDescription = "Study Syllabus", modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
            Spacer(modifier = Modifier.height(12.dp))
            Text("Syllabus Outline Loading", fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onBackground)
            Text(
                "Our developers are indexing individual textbook chapters for this course to align directly with carewell resources.",
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
        }
    }
}

// --- NOTES TAB CONTENT ---
@Composable
fun NotesTabContent(subject: PharmaSubject, bookmarks: List<BookmarkItem>, viewModel: PharmaViewModel) {
    if (subject.notes.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Filled.Description, contentDescription = "Pharma notes", modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
                Spacer(modifier = Modifier.height(12.dp))
                Text("Carewell High-Yield Notes", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                Text(
                    "Detailed summary class notes, diagrams, and molecular formulas are being finalized.",
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(subject.notes) { note ->
                val isBookmarked = remember(bookmarks) {
                    bookmarks.any { it.itemId == note.id && it.type == "NOTE" }
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            viewModel.currentNote = note
                            viewModel.activeDetailView = DetailView.NOTE_READER
                        }
                        .testTag("note_item_${note.id}"),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = note.chapter,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = note.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            IconButton(onClick = {
                                viewModel.toggleBookmark(
                                    type = "NOTE",
                                    subjectId = subject.id,
                                    itemId = note.id,
                                    title = note.title,
                                    subtitle = note.chapter
                                )
                            }) {
                                Icon(
                                    imageVector = if (isBookmarked) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                                    contentDescription = "Save Book",
                                    tint = if (isBookmarked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Text(
                            text = note.content,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.AccessTime, contentDescription = "Timer", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    text = "${note.durationMin} min read duration",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = 4.dp)
                                )
                            }
                            TextButton(onClick = {
                                viewModel.currentNote = note
                                viewModel.activeDetailView = DetailView.NOTE_READER
                            }) {
                                Text("Read Full File", fontWeight = FontWeight.Bold)
                                Icon(Icons.Default.ArrowForward, contentDescription = null, modifier = Modifier.padding(start = 4.dp).size(14.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

// --- VIDEOS TAB CONTENT ---
@Composable
fun VideosTabContent(subject: PharmaSubject, bookmarks: List<BookmarkItem>, viewModel: PharmaViewModel) {
    val context = LocalContext.current

    if (subject.videos.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Filled.VideoLibrary, contentDescription = "Pharma lectures", modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
                Spacer(modifier = Modifier.height(12.dp))
                Text("Lectures & Diagrams", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                Text(
                    "Syllabus chapters video collections from Jitendra Sir are being linked shortly.",
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(subject.videos) { video ->
                val isBookmarked = remember(bookmarks) {
                    bookmarks.any { it.itemId == video.id && it.type == "VIDEO" }
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.Top) {
                            // Mock Thumbnail
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Filled.PlayArrow, contentDescription = "Play", modifier = Modifier.size(36.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = video.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "Instructor: ${video.tutor}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            IconButton(onClick = {
                                viewModel.toggleBookmark(
                                    type = "VIDEO",
                                    subjectId = subject.id,
                                    itemId = video.id,
                                    title = video.title,
                                    subtitle = "Watch Link"
                                )
                            }) {
                                Icon(
                                    imageVector = if (isBookmarked) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                                    contentDescription = "Save video",
                                    tint = if (isBookmarked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Text(
                            text = video.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp)
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                "Duration: ${video.duration}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )

                            // Play lecture
                            Button(
                                onClick = {
                                    // Normally launches YouTube intent, for safety in standalone emulator, we display a Toast state
                                    android.widget.Toast.makeText(context, "Streaming Video: ${video.title} on Google share.", android.widget.Toast.LENGTH_SHORT).show()
                                },
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Default.PlayCircleFilled, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Play Class", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }
        }
    }
}

// --- QUIZ INDEX TAB CONTENT ---
@Composable
fun QuizTabContent(subject: PharmaSubject, viewModel: PharmaViewModel) {
    val quizLog by viewModel.quizAttemptsList.collectAsStateWithLifecycle()
    val subjectAttempts = remember(quizLog, subject) {
        quizLog.filter { it.subjectId == subject.id }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Filled.AssignmentTurnedIn, contentDescription = "mcq tests", modifier = Modifier.size(44.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Syllabus Mock Test MCQ",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = "Evaluate your knowledge on Carewell chapter questions to reinforce your clinical preparations.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                    modifier = Modifier.padding(vertical = 4.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = { viewModel.startNewQuiz(subject) },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.testTag("start_quiz_btn")
                ) {
                    Text("Launch Practice Quiz (${subject.mcqs.size} MCQs)", fontWeight = FontWeight.Bold)
                }
            }
        }

        Text(
            "Your Score History",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.Start)
        )

        if (subjectAttempts.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "No quiz history logged for this subject. Try your first exam above!",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(subjectAttempts) { attempt ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .background(
                                        if (attempt.scorePercent >= 70) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                        else MaterialTheme.colorScheme.error.copy(alpha = 0.15f),
                                        CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "${attempt.scorePercent}%",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 12.sp,
                                    color = if (attempt.scorePercent >= 70) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Score: ${attempt.correctAnswers} / ${attempt.totalMcqs} Correct",
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                val format = SimpleDateFormat("MMM dd, yyyy - hh:mm a", Locale.getDefault())
                                Text(
                                    format.format(Date(attempt.timestamp)),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}


// --- NOTE READER FULL SCREEN ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteReaderScreen(note: PharmaNote, viewModel: PharmaViewModel) {
    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Text(
                    note.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Bold
                )
            },
            navigationIcon = {
                IconButton(onClick = { viewModel.activeDetailView = DetailView.SUBJECT_HUB }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Badge(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                Text(note.chapter, modifier = Modifier.padding(6.dp), fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(16.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = note.content,
                        style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 24.sp),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.TipsAndUpdates, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        "Study Hint: Use our built-in AI Tutor to ask further clarifications on formulas or biological cycles depicted in this chapter notes!",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    }
}


// --- ACTIVE QUIZ SESSION SCREEN ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuizSessionScreen(subject: PharmaSubject, viewModel: PharmaViewModel) {
    val mcq = subject.mcqs.getOrNull(viewModel.quizCurrentQuestionIndex)

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Active Practice Mock Test", fontWeight = FontWeight.Black) },
            navigationIcon = {
                IconButton(onClick = { viewModel.activeDetailView = DetailView.SUBJECT_HUB }) {
                    Icon(Icons.Filled.Close, contentDescription = "Cancel study")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
        )

        if (viewModel.isQuizFinished || mcq == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Filled.LightMode, contentDescription = null, modifier = Modifier.size(60.dp), tint = MaterialTheme.colorScheme.tertiary)
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "Clinical Practice Finished!",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "You solved all syllabus chapters questions of this B.Pharm/D.Pharm unit test.",
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )

                Spacer(modifier = Modifier.height(24.dp))

                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("MOCK TEST SCORE", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        Text(
                            "${viewModel.quizScore} / ${subject.mcqs.size}",
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.primary
                        )
                        val percent = (viewModel.quizScore.toFloat() / subject.mcqs.size * 100).toInt()
                        Text(
                            "Grade Percentage: $percent%",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = { viewModel.activeDetailView = DetailView.SUBJECT_HUB },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Return to Subject Hub")
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Progress
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Question ${viewModel.quizCurrentQuestionIndex + 1} of ${subject.mcqs.size}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        "Correct: ${viewModel.quizScore}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }

                LinearProgressIndicator(
                    progress = { (viewModel.quizCurrentQuestionIndex.toFloat()) / subject.mcqs.size },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp)
                        .height(6.dp)
                        .clip(CircleShape),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                )

                // Current question card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = mcq.question,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        mcq.options.forEachIndexed { index, option ->
                            val isSelected = viewModel.selectedOptionIndex == index
                            val isCorrect = index == mcq.correctAnswerIndex
                            val optionColor = when {
                                viewModel.isQuizAnswerSubmitted && isCorrect -> MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                viewModel.isQuizAnswerSubmitted && isSelected && !isCorrect -> MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
                                isSelected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                                else -> Color.Transparent
                            }
                            val borderColor = when {
                                viewModel.isQuizAnswerSubmitted && isCorrect -> MaterialTheme.colorScheme.primary
                                viewModel.isQuizAnswerSubmitted && isSelected && !isCorrect -> MaterialTheme.colorScheme.error
                                isSelected -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                            }

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp)
                                    .clickable(enabled = !viewModel.isQuizAnswerSubmitted) {
                                        viewModel.selectedOptionIndex = index
                                    }
                                    .testTag("option_${index}"),
                                colors = CardDefaults.cardColors(containerColor = optionColor),
                                border = BorderStroke(1.dp, borderColor)
                              ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = isSelected,
                                        onClick = { if (!viewModel.isQuizAnswerSubmitted) viewModel.selectedOptionIndex = index },
                                        enabled = !viewModel.isQuizAnswerSubmitted
                                    )
                                    Text(
                                        option,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding(start = 8.dp)
                                    )
                                }
                            }
                        }

                        if (viewModel.isQuizAnswerSubmitted) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = if (viewModel.selectedOptionIndex == mcq.correctAnswerIndex) Icons.Filled.Check else Icons.Filled.Help,
                                            contentDescription = "Correction feedback",
                                            tint = if (viewModel.selectedOptionIndex == mcq.correctAnswerIndex) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                        )
                                        Text(
                                            text = if (viewModel.selectedOptionIndex == mcq.correctAnswerIndex) "Well Done!" else "Incorrect Answer",
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(start = 8.dp),
                                            color = if (viewModel.selectedOptionIndex == mcq.correctAnswerIndex) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                        )
                                    }
                                    Text(
                                        text = mcq.explanation,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (!viewModel.isQuizAnswerSubmitted) {
                    Button(
                        onClick = { viewModel.submitQuizAnswer() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("submit_answer_btn"),
                        enabled = viewModel.selectedOptionIndex != null,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Validate Answer")
                    }
                } else {
                    Button(
                        onClick = { viewModel.nextQuizQuestion() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("next_question_btn"),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(if (viewModel.quizCurrentQuestionIndex + 1 < subject.mcqs.size) "Next question" else "Complete and log quiz score")
                    }
                }
            }
        }
    }
}


// --- 3. TUTOR CHAT SCREEN (GEMINI AI DRIVEN) ---
@Composable
fun TutorScreen(viewModel: PharmaViewModel) {
    val localKeyboard = LocalSoftwareKeyboardController.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("tutor_screen")
    ) {
        // Chat Header
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Psychology, contentDescription = "Tutor face", tint = MaterialTheme.colorScheme.onPrimary)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("PHARMAID TUTOR AI", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Text("Powered by Gemini 3.5. Clear exam topics instantly.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                if (viewModel.aiChatHistory.isNotEmpty()) {
                    IconButton(onClick = { viewModel.clearAiChat() }) {
                        Icon(Icons.Filled.DeleteSweep, contentDescription = "Clear History", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }

        if (viewModel.aiChatHistory.isEmpty()) {
            // Introductory Prompt Shortcuts Screen
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.HistoryEdu,
                    contentDescription = null,
                    modifier = Modifier.size(54.dp),
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "Ask the Pharmacy Academic Mentor",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center
                )
                Text(
                    "Formulas study aid, pharmacokinetics pathways calculations, or custom quizzes curated in seconds.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    "Tap Quick-Study Hotwords",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(8.dp))

                listOf(
                    "Explain G-Protein Coupled Receptors pharmacology cycles step-by-step",
                    "Explain Young's & Clark's Posology calculation criteria",
                    "List tableting diluents and lubricants with standard examples",
                    "List classifications of Autonomic Nervous System receptors"
                ).forEach { prompt ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable { viewModel.askAiTutor(prompt) }
                            .testTag("hotword_${prompt.hashCode()}"),
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.TipsAndUpdates, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(prompt, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        } else {
            // Discussion logs
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .testTag("ai_chat_scroll"),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(viewModel.aiChatHistory) { turn ->
                    val isUser = turn.second
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
                    ) {
                        Card(
                            shape = RoundedCornerShape(
                                topStart = 12.dp,
                                topEnd = 12.dp,
                                bottomStart = if (isUser) 12.dp else 2.dp,
                                bottomEnd = if (isUser) 2.dp else 12.dp
                            ),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                            ),
                            border = if (!isUser) BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)) else null,
                            modifier = Modifier.widthIn(max = 290.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = turn.first,
                                    style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp),
                                    color = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                
                if (viewModel.isAiLoading) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            horizontalArrangement = Arrangement.Start,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Analyzing pharmacist guidelines...", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }

        // Action Input text panel
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = viewModel.aiTutorInput,
                    onValueChange = { viewModel.aiTutorInput = it },
                    placeholder = { Text("Ask pharmacy helper...") },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("ai_input_field"),
                    maxLines = 2,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = {
                        viewModel.askAiTutor()
                        localKeyboard?.hide()
                    })
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = {
                        viewModel.askAiTutor()
                        localKeyboard?.hide()
                    },
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                        .testTag("ai_send_btn"),
                    enabled = viewModel.aiTutorInput.trim().isNotEmpty() && !viewModel.isAiLoading
                ) {
                    Icon(Icons.Filled.Send, contentDescription = "Query AI", tint = Color.White)
                }
            }
        }
    }
}


// --- 4. FOCUS TIMER SCREEN (POMODORO) ---
@Composable
fun FocusTimerScreen(viewModel: PharmaViewModel) {
    val totalSeconds = viewModel.timerDurationSeconds
    val min = totalSeconds / 60
    val sec = totalSeconds % 60
    val progressFraction = if (viewModel.timerInitialSeconds == 0L) 0f else (totalSeconds.toFloat() / viewModel.timerInitialSeconds)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("focus_timer_screen")
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Pharmaceutical Focus Cabin", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Text("Engage silent Pomodoro study loops. Your focused timeline will qualify library badges.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            }
        }

        Spacer(modifier = Modifier.weight(0.1f))

        // Timer Dial visual
        Box(
            modifier = Modifier.size(240.dp),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(
                progress = { progressFraction },
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.tertiary,
                strokeWidth = 10.dp,
                trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
            )

            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = String.format("%02d:%02d", min, sec),
                    style = MaterialTheme.typography.headlineLarge.copy(fontSize = 48.sp, fontWeight = FontWeight.Black),
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = if (viewModel.isTimerRunning) "FOCUSED ON NOTES" else "IDLE",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Hot intervals toggles
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            listOf(5, 15, 25, 45).forEach { minChip ->
                FilterChip(
                    selected = viewModel.timerInitialSeconds == (minChip * 60).toLong(),
                    onClick = { viewModel.adjustTimerMinutes(minChip) },
                    label = { Text("$minChip Min") }
                )
            }
        }

        // Controll row
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Button(
                onClick = { if (viewModel.isTimerRunning) viewModel.pauseTimer() else viewModel.startTimer() },
                modifier = Modifier.weight(1f).testTag("timer_toggle_btn"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (viewModel.isTimerRunning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(
                    imageVector = if (viewModel.isTimerRunning) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(if (viewModel.isTimerRunning) "Pause Loop" else "Start Study")
            }

            Button(
                onClick = { viewModel.resetTimer() },
                modifier = Modifier.weight(1f).testTag("timer_reset_btn"),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = "Reset timer")
                Spacer(modifier = Modifier.width(6.dp))
                Text("Reset")
            }
        }

        Spacer(modifier = Modifier.weight(0.2f))

        // Display Historic Focus Cabin duration
        val logs by viewModel.focusSessions.collectAsStateWithLifecycle()
        val minutesFlipped = logs.sumOf { it.durationSeconds } / 60

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.WorkspacePremium, contentDescription = "Study levels badge", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text("Total Time Devoted", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                    Text("$minutesFlipped Total Study Minutes Logged", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Black)
                }
            }
        }
    }
}


// --- 5. BOOKMARKS SCREEN ---
@Composable
fun BookmarksScreen(viewModel: PharmaViewModel) {
    val bookmarks by viewModel.bookmarkedItems.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("bookmarks_screen")
            .padding(16.dp)
    ) {
        Text(
            "Saved Academic Library",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Black,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        if (bookmarks.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Outlined.BookmarkBorder,
                        contentDescription = null,
                        modifier = Modifier.size(54.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Your library is empty", fontWeight = FontWeight.Bold)
                    Text("Tap the bookmark star inside semester notes or video lessons lectures to add them here.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(bookmarks) { bookmark ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(
                                        if (bookmark.type == "NOTE") MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                        else MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f),
                                        CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (bookmark.type == "NOTE") Icons.Filled.MenuBook else Icons.Filled.PlayCircle,
                                    contentDescription = null,
                                    tint = if (bookmark.type == "NOTE") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = bookmark.subtitle,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = bookmark.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            IconButton(onClick = { viewModel.removeBookmarkDirectly(bookmark.itemId, bookmark.type) }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Delete from library", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }
}
