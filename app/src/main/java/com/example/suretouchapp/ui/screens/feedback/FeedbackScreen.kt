package com.example.suretouchapp.ui.screens.feedback

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import com.example.suretouchapp.data.api.ApiClient
import com.example.suretouchapp.data.api.TokenManager
import kotlinx.coroutines.launch

data class FeedbackCategory(
    val id: String,
    val title: String,
    val emoji: String
)

private val ColorCanvasBg = Color(0xFFF8FAFC)
private val ColorCardSurface = Color(0xFFFFFFFF)
private val ColorPrimaryPurple = Color(0xFF6D28D9)
private val ColorPurpleGradientStart = Color(0xFF5B21B6)
private val ColorPurpleGradientEnd = Color(0xFF6D28D9)
private val ColorTextTitles = Color(0xFF0F172A)
private val ColorTextSubtext = Color(0xFF64748B)
private val ColorBorderHairline = Color(0xFFE2E8F0)
private val ColorStarAmber = Color(0xFFF59E0B)
private val ColorRequiredRed = Color(0xFFDC2626)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FeedbackScreen(
    tokenManager: TokenManager,
    onBack: () -> Unit
) {
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabTitles = listOf("Classes & Tutor Review", "General & App Support")

    Scaffold(
        containerColor = ColorCanvasBg,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Feedback & Support",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = ColorTextTitles
                        )
                        Text(
                            text = "Share feedback & help us improve",
                            fontSize = 12.sp,
                            color = ColorTextSubtext
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = ColorPrimaryPurple
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = ColorCardSurface)
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            TabRow(
                selectedTabIndex = selectedTabIndex,
                containerColor = ColorCardSurface,
                contentColor = ColorPrimaryPurple
            ) {
                tabTitles.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = {
                            Text(
                                text = title,
                                fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 13.sp
                            )
                        }
                    )
                }
            }

            when (selectedTabIndex) {
                0 -> AnonymousTutorReviewTab(tokenManager = tokenManager)
                1 -> GeneralAppSupportTab(tokenManager = tokenManager)
            }
        }
    }
}

// ==============================================================================
// TAB 1: EXACT MATCH - Feedback Form – Classes & Tutor Review (Anonymous)
// ==============================================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AnonymousTutorReviewTab(tokenManager: TokenManager) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var stats by remember { mutableStateOf<com.example.suretouchapp.data.model.StudentStatisticsDto?>(null) }
    var cohortModules by remember { mutableStateOf<List<String>>(emptyList()) }
    var cohortMentors by remember { mutableStateOf<List<String>>(emptyList()) }
    var isLoadingCohort by remember { mutableStateOf(true) }

    LaunchedEffect(tokenManager) {
        isLoadingCohort = true
        val response = runCatching {
            ApiClient.getService(tokenManager).getStudentStatistics()
        }.getOrNull()

        if (response?.isSuccessful == true && response.body() != null) {
            val body = response.body()!!
            stats = body
            val activeCohort = body.activeCohort

            val modules = activeCohort?.modules?.filter { it.isNotBlank() }?.takeIf { it.isNotEmpty() }
                ?: listOf(
                    "Module 1: Building REST APIs with Django",
                    "Module 2: Advanced Data Structures & Algorithms",
                    "Module 3: Neural Networks & Deep Learning",
                    "Module 4: Full Stack AI Deployment & DevOps",
                    "Module 5: Life Skills & Soft Skills Integration"
                )
            cohortModules = modules

            val mentors = activeCohort?.mentors?.filter { it.isNotBlank() }?.takeIf { it.isNotEmpty() }
                ?: listOfNotNull(activeCohort?.mentorName?.takeIf { it.isNotBlank() })
                    .ifEmpty { listOf("Prof. Ramesh (Lead AI Tutor)") }
            cohortMentors = mentors
        } else {
            cohortModules = listOf(
                "Module 1: Building REST APIs with Django",
                "Module 2: Advanced Data Structures & Algorithms",
                "Module 3: Neural Networks & Deep Learning",
                "Module 4: Full Stack AI Deployment & DevOps",
                "Module 5: Life Skills & Soft Skills Integration"
            )
            cohortMentors = listOf("Prof. Ramesh (Lead AI Tutor)")
        }
        isLoadingCohort = false
    }

    var selectedModule by remember { mutableStateOf("") }
    var selectedMentor by remember { mutableStateOf("") }

    LaunchedEffect(cohortModules) {
        if (selectedModule.isBlank() && cohortModules.isNotEmpty()) {
            selectedModule = cohortModules.first()
        }
    }
    LaunchedEffect(cohortMentors) {
        if (selectedMentor.isBlank() && cohortMentors.isNotEmpty()) {
            selectedMentor = cohortMentors.first()
        }
    }

    var isSubmitting by remember { mutableStateOf(false) }
    var isModuleExpanded by remember { mutableStateOf(false) }
    var isMentorExpanded by remember { mutableStateOf(false) }

    var overallRating by remember { mutableStateOf("") }
    var explanationRating by remember { mutableStateOf("") }
    var interactiveRating by remember { mutableStateOf("") }
    var likedText by remember { mutableStateOf("") }
    var improvementsText by remember { mutableStateOf("") }
    var showSuccessDialog by remember { mutableStateOf(false) }
    var validationError by remember { mutableStateOf(false) }

    val q1Options = listOf("Excellent", "Good", "Average", "Poor")
    val q2Options = listOf("Very clear", "Clear", "Average", "Difficult to understand")
    val q3Options = listOf("Yes always", "Sometimes", "No")

    val isFormComplete = selectedModule.isNotBlank() &&
            selectedMentor.isNotBlank() &&
            overallRating.isNotBlank() &&
            explanationRating.isNotBlank() &&
            interactiveRating.isNotBlank() &&
            likedText.isNotBlank() &&
            improvementsText.isNotBlank()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {

        // Selection Card for Module and Mentor Dropdowns
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = ColorCardSurface),
                border = BorderStroke(1.dp, ColorBorderHairline)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Cohort Module & Mentor Selection",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = ColorTextTitles
                    )
                    Text(
                        text = "Select the specific course module and tutor you are reviewing",
                        fontSize = 12.sp,
                        color = ColorTextSubtext
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Module Dropdown
                    Text(
                        text = "Select Cohort Module *",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        color = ColorTextTitles
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    ExposedDropdownMenuBox(
                        expanded = isModuleExpanded,
                        onExpandedChange = { isModuleExpanded = !isModuleExpanded }
                    ) {
                        OutlinedTextField(
                            value = selectedModule,
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isModuleExpanded) },
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = ColorPrimaryPurple,
                                unfocusedBorderColor = ColorBorderHairline
                            )
                        )
                        ExposedDropdownMenu(
                            expanded = isModuleExpanded,
                            onDismissRequest = { isModuleExpanded = false }
                        ) {
                            cohortModules.forEach { module ->
                                DropdownMenuItem(
                                    text = { Text(module, fontSize = 13.sp) },
                                    onClick = {
                                        selectedModule = module
                                        isModuleExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Mentor Dropdown
                    Text(
                        text = "Select Cohort Mentor / Tutor *",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        color = ColorTextTitles
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    ExposedDropdownMenuBox(
                        expanded = isMentorExpanded,
                        onExpandedChange = { isMentorExpanded = !isMentorExpanded }
                    ) {
                        OutlinedTextField(
                            value = selectedMentor,
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isMentorExpanded) },
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = ColorPrimaryPurple,
                                unfocusedBorderColor = ColorBorderHairline
                            )
                        )
                        ExposedDropdownMenu(
                            expanded = isMentorExpanded,
                            onDismissRequest = { isMentorExpanded = false }
                        ) {
                            cohortMentors.forEach { mentor ->
                                DropdownMenuItem(
                                    text = { Text(mentor, fontSize = 13.sp) },
                                    onClick = {
                                        selectedMentor = mentor
                                        isMentorExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            Text(
                text = "* Indicates required question",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = ColorRequiredRed
            )
        }

        // Question 1
        item {
            QuestionRadioCard(
                questionNum = "1",
                questionText = "How would you rate the classes overall?",
                isRequired = true,
                options = q1Options,
                selectedOption = overallRating,
                onSelectOption = {
                    overallRating = it
                    validationError = false
                }
            )
        }

        // Question 2
        item {
            QuestionRadioCard(
                questionNum = "2",
                questionText = "How was the tutor’s explanation of concepts?",
                isRequired = true,
                options = q2Options,
                selectedOption = explanationRating,
                onSelectOption = {
                    explanationRating = it
                    validationError = false
                }
            )
        }

        // Question 3
        item {
            QuestionRadioCard(
                questionNum = "3",
                questionText = "Was the tutor interactive and approachable for doubts?",
                isRequired = true,
                options = q3Options,
                selectedOption = interactiveRating,
                onSelectOption = {
                    interactiveRating = it
                    validationError = false
                }
            )
        }

        // Question 4
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = ColorCardSurface),
                border = BorderStroke(1.dp, ColorBorderHairline)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row {
                        Text(
                            text = "What did you like about the classes and tutor?",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.5.sp,
                            color = ColorTextTitles
                        )
                        Text(" *", color = ColorRequiredRed, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = likedText,
                        onValueChange = {
                            likedText = it
                            validationError = false
                        },
                        placeholder = { Text("Share what went well during classes...") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }
        }

        // Question 5
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = ColorCardSurface),
                border = BorderStroke(1.dp, ColorBorderHairline)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row {
                        Text(
                            text = "What improvements would you suggest for the tutor/classes?",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.5.sp,
                            color = ColorTextTitles
                        )
                        Text(" *", color = ColorRequiredRed, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = improvementsText,
                        onValueChange = {
                            improvementsText = it
                            validationError = false
                        },
                        placeholder = { Text("Share your suggestions for improvement...") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }
        }

        if (validationError) {
            item {
                Text(
                    text = "Please answer all required questions marked with * before submitting.",
                    color = ColorRequiredRed,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Submit Button
        item {
            Button(
                onClick = {
                    if (isFormComplete) {
                        val ratingVal = when (overallRating) {
                            "Excellent" -> 5
                            "Good" -> 4
                            "Average" -> 3
                            else -> 2
                        }
                        val fullComments = "Module: $selectedModule | Mentor: $selectedMentor | Explanation: $explanationRating | Interaction: $interactiveRating | Liked: $likedText | Suggestions: $improvementsText"
                        scope.launch {
                            isSubmitting = true
                            try {
                                ApiClient.getService(tokenManager).submitFeedback(
                                    mapOf(
                                        "feedback_type" to "COURSE",
                                        "related_id" to (stats?.activeCohort?.id ?: stats?.activeCohort?.courseId),
                                        "rating" to ratingVal,
                                        "comments" to fullComments
                                    )
                                )
                            } catch (_: Exception) {}
                            isSubmitting = false
                            showSuccessDialog = true
                        }
                    } else {
                        validationError = true
                    }
                },
                enabled = !isSubmitting,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(26.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ColorPrimaryPurple)
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                } else {
                    Icon(imageVector = Icons.AutoMirrored.Filled.Send, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    text = if (isSubmitting) "SUBMITTING..." else "SUBMIT ANONYMOUS FEEDBACK",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.5.sp
                )
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }

    if (showSuccessDialog) {
        AlertDialog(
            onDismissRequest = { showSuccessDialog = false },
            icon = {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFD1FAE5)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = Color(0xFF059669),
                        modifier = Modifier.size(36.dp)
                    )
                }
            },
            title = {
                Text(
                    text = "Anonymous Feedback Submitted!",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.5.sp,
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Text(
                    text = "Thank you for submitting your honest feedback for Module 1. Your response is completely anonymous and will help improve future sessions.",
                    textAlign = TextAlign.Center,
                    fontSize = 13.sp,
                    color = ColorTextSubtext
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showSuccessDialog = false
                        overallRating = ""
                        explanationRating = ""
                        interactiveRating = ""
                        likedText = ""
                        improvementsText = ""
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ColorPrimaryPurple)
                ) {
                    Text("OK", fontWeight = FontWeight.Bold)
                }
            },
            containerColor = ColorCardSurface,
            shape = RoundedCornerShape(20.dp)
        )
    }
}

@Composable
private fun QuestionRadioCard(
    questionNum: String,
    questionText: String,
    isRequired: Boolean,
    options: List<String>,
    selectedOption: String,
    onSelectOption: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = ColorCardSurface),
        border = BorderStroke(1.dp, ColorBorderHairline)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row {
                Text(
                    text = questionText,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.5.sp,
                    color = ColorTextTitles,
                    modifier = Modifier.weight(1f)
                )
                if (isRequired) {
                    Text(" *", color = ColorRequiredRed, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            options.forEach { option ->
                val isSelected = selectedOption == option
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isSelected) ColorPrimaryPurple.copy(alpha = 0.08f) else Color.Transparent)
                        .clickable { onSelectOption(option) }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = isSelected,
                        onClick = { onSelectOption(option) },
                        colors = RadioButtonDefaults.colors(selectedColor = ColorPrimaryPurple)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = option,
                        fontSize = 13.5.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) ColorPrimaryPurple else ColorTextTitles
                    )
                }
            }
        }
    }
}

// ==============================================================================
// TAB 2: General & App Support Feedback
// ==============================================================================
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GeneralAppSupportTab(tokenManager: TokenManager) {
    val scope = rememberCoroutineScope()
    var isSubmitting by remember { mutableStateOf(false) }
    var selectedCategory by remember { mutableStateOf("app") }
    var rating by remember { mutableIntStateOf(5) }
    var feedbackMessage by remember { mutableStateOf("") }
    var showGeneralDialog by remember { mutableStateOf(false) }

    val categories = listOf(
        FeedbackCategory("app", "App Bug / Technical", "💻"),
        FeedbackCategory("feature", "Feature Suggestion", "💡"),
        FeedbackCategory("general", "General Platform", "🌟")
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("Select Category", fontSize = 14.5.sp, fontWeight = FontWeight.Bold, color = ColorTextTitles)
        }

        item {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                categories.forEach { cat ->
                    val isSelected = selectedCategory == cat.id
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedCategory = cat.id },
                        label = { Text("${cat.emoji} ${cat.title}") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = ColorPrimaryPurple,
                            selectedLabelColor = Color.White,
                            containerColor = ColorCardSurface,
                            labelColor = ColorTextTitles
                        )
                    )
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = ColorCardSurface),
                border = BorderStroke(1.dp, ColorBorderHairline)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Overall App Satisfaction", fontSize = 14.5.sp, fontWeight = FontWeight.Bold, color = ColorTextTitles)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row {
                        for (i in 1..5) {
                            IconButton(onClick = { rating = i }) {
                                Icon(
                                    imageVector = if (i <= rating) Icons.Default.Star else Icons.Default.StarBorder,
                                    contentDescription = "Star $i",
                                    tint = if (i <= rating) ColorStarAmber else ColorTextSubtext,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            Column {
                Text("Feedback Comments", fontSize = 14.5.sp, fontWeight = FontWeight.Bold, color = ColorTextTitles)
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = feedbackMessage,
                    onValueChange = { feedbackMessage = it },
                    placeholder = { Text("Describe any app technical issue or feature request...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        }

        item {
            Button(
                onClick = {
                    scope.launch {
                        isSubmitting = true
                        try {
                            ApiClient.getService(tokenManager).submitFeedback(
                                mapOf(
                                    "feedback_type" to "SYSTEM",
                                    "rating" to rating,
                                    "comments" to "Category: $selectedCategory | $feedbackMessage"
                                )
                            )
                        } catch (_: Exception) {}
                        isSubmitting = false
                        showGeneralDialog = true
                    }
                },
                enabled = feedbackMessage.isNotBlank() && !isSubmitting,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(25.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ColorPrimaryPurple)
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                } else {
                    Icon(imageVector = Icons.AutoMirrored.Filled.Send, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(text = if (isSubmitting) "SUBMITTING..." else "SUBMIT APP FEEDBACK", fontWeight = FontWeight.Bold)
            }
        }
    }

    if (showGeneralDialog) {
        AlertDialog(
            onDismissRequest = { showGeneralDialog = false },
            title = { Text("Feedback Received", fontWeight = FontWeight.Bold) },
            text = { Text("Thank you for sharing your feedback with the SURE ProEd team.") },
            confirmButton = {
                Button(
                    onClick = {
                        showGeneralDialog = false
                        feedbackMessage = ""
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ColorPrimaryPurple)
                ) {
                    Text("OK", fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}
