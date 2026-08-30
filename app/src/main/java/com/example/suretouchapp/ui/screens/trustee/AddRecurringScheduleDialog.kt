package com.example.suretouchapp.ui.screens.trustee

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.suretouchapp.data.api.ApiClient
import com.example.suretouchapp.data.api.TokenManager
import com.example.suretouchapp.data.model.CohortDto
import com.example.suretouchapp.data.model.CourseDto
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val PurplePrimary = Color(0xFF6821A8)
private val PurpleSoft = Color(0xFFF3E8FF)
private val DarkText = Color(0xFF1E293B)
private val SubText = Color(0xFF64748B)
private val BorderColor = Color(0xFFE2E8F0)

enum class ClassTypeOption(val value: String, val label: String) {
    DOMAIN("DOMAIN", "Domain Class"),
    LST("LST", "LST Class"),
    CELEBRATION("CELEBRATION", "Celebration")
}

enum class LstBatchOption(val value: String?, val label: String) {
    NONE(null, "---------"),
    BATCH_1("BATCH_1", "Batch 1"),
    BATCH_2("BATCH_2", "Batch 2")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddRecurringScheduleDialog(
    tokenManager: TokenManager,
    onDismiss: () -> Unit,
    onSaved: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var classType by remember { mutableStateOf(ClassTypeOption.DOMAIN) }
    var selectedCourse by remember { mutableStateOf<CourseDto?>(null) }
    var selectedCohort by remember { mutableStateOf<CohortDto?>(null) }
    var selectedLstBatch by remember { mutableStateOf(LstBatchOption.NONE) }

    val nowCalendar = Calendar.getInstance()
    val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.US)
    val shortTimeFormat = SimpleDateFormat("HH:mm", Locale.US)
    val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    var startTime by remember { mutableStateOf("10:00:00") }
    var endTime by remember { mutableStateOf("11:30:00") }
    var isPaused by remember { mutableStateOf(false) }
    var nextRunDate by remember { mutableStateOf(dateFormat.format(Date())) }
    var nextRunTime by remember { mutableStateOf("10:00:00") }
    var frequencyDays by remember { mutableStateOf("7") }

    var coursesList by remember { mutableStateOf<List<CourseDto>>(emptyList()) }
    var cohortsList by remember { mutableStateOf<List<CohortDto>>(emptyList()) }
    var isLoadingData by remember { mutableStateOf(true) }
    var isSubmitting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var courseDropdownExpanded by remember { mutableStateOf(false) }
    var cohortDropdownExpanded by remember { mutableStateOf(false) }
    var lstBatchDropdownExpanded by remember { mutableStateOf(false) }

    // Load Courses & Cohorts
    LaunchedEffect(Unit) {
        isLoadingData = true
        runCatching {
            val api = ApiClient.getService(tokenManager)
            coroutineScope {
                val coursesDeferred = async { api.getCourses() }
                val cohortsDeferred = async { api.getCohorts() }

                val coursesRes = coursesDeferred.await()
                val cohortsRes = cohortsDeferred.await()

                if (coursesRes.isSuccessful) {
                    coursesList = coursesRes.body()?.results.orEmpty()
                }
                if (cohortsRes.isSuccessful) {
                    cohortsList = cohortsRes.body()?.results.orEmpty()
                }
            }
        }
        isLoadingData = false
    }

    fun pickTime(initialTime: String, onTimePicked: (String) -> Unit) {
        val parts = initialTime.split(":").mapNotNull { it.toIntOrNull() }
        val hour = parts.getOrNull(0) ?: nowCalendar.get(Calendar.HOUR_OF_DAY)
        val minute = parts.getOrNull(1) ?: nowCalendar.get(Calendar.MINUTE)

        TimePickerDialog(
            context,
            { _, h, m ->
                val formatted = String.format(Locale.US, "%02d:%02d:00", h, m)
                onTimePicked(formatted)
            },
            hour, minute, true
        ).show()
    }

    fun pickDate(initialDate: String, onDatePicked: (String) -> Unit) {
        val parts = initialDate.split("-").mapNotNull { it.toIntOrNull() }
        val year = parts.getOrNull(0) ?: nowCalendar.get(Calendar.YEAR)
        val month = (parts.getOrNull(1) ?: (nowCalendar.get(Calendar.MONTH) + 1)) - 1
        val day = parts.getOrNull(2) ?: nowCalendar.get(Calendar.DAY_OF_MONTH)

        DatePickerDialog(
            context,
            { _, y, m, d ->
                val formatted = String.format(Locale.US, "%04d-%02d-%02d", y, m + 1, d)
                onDatePicked(formatted)
            },
            year, month, day
        ).show()
    }

    Dialog(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.94f),
            shape = RoundedCornerShape(20.dp),
            color = Color.White,
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(PurpleSoft),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.EventRepeat,
                                contentDescription = null,
                                tint = PurplePrimary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Add Recurring Schedule",
                                fontSize = 17.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = DarkText
                            )
                            Text(
                                text = "Automated class timetable & recurrence",
                                fontSize = 12.sp,
                                color = SubText
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        enabled = !isSubmitting
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = SubText)
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = BorderColor)

                // Scrollable Form
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    if (errorMessage != null) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFFFEE2E2),
                            border = BorderStroke(1.dp, Color(0xFFFCA5A5))
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.ErrorOutline, null, tint = Color(0xFFDC2626), modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = errorMessage!!,
                                    fontSize = 12.5.sp,
                                    color = Color(0xFFB91C1C),
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    // 1. Class Type
                    Column {
                        Text("Class type *", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = DarkText)
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ClassTypeOption.values().forEach { option ->
                                val isSelected = classType == option
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        classType = option
                                        if (option == ClassTypeOption.LST && selectedLstBatch == LstBatchOption.NONE) {
                                            selectedLstBatch = LstBatchOption.BATCH_1
                                        }
                                    },
                                    label = { Text(option.label, fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = PurpleSoft,
                                        selectedLabelColor = PurplePrimary
                                    ),
                                    border = FilterChipDefaults.filterChipBorder(
                                        enabled = true,
                                        selected = isSelected,
                                        borderColor = if (isSelected) PurplePrimary else BorderColor
                                    ),
                                    shape = RoundedCornerShape(20.dp)
                                )
                            }
                        }
                    }

                    // 2. Course Dropdown
                    Column {
                        Text("Course", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = DarkText)
                        Spacer(modifier = Modifier.height(4.dp))
                        ExposedDropdownMenuBox(
                            expanded = courseDropdownExpanded,
                            onExpandedChange = { courseDropdownExpanded = !courseDropdownExpanded }
                        ) {
                            OutlinedTextField(
                                value = selectedCourse?.let { "[${it.code}] ${it.name}" } ?: "---------",
                                onValueChange = {},
                                readOnly = true,
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = courseDropdownExpanded) },
                                modifier = Modifier
                                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                                    .fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = PurplePrimary,
                                    unfocusedBorderColor = BorderColor
                                )
                            )
                            ExposedDropdownMenu(
                                expanded = courseDropdownExpanded,
                                onDismissRequest = { courseDropdownExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("--------- (No Course Selected)") },
                                    onClick = {
                                        selectedCourse = null
                                        courseDropdownExpanded = false
                                    }
                                )
                                coursesList.forEach { course ->
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text("[${course.code}] ${course.name}", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                                Text(course.domain.orEmpty().ifBlank { "Specialization" }, fontSize = 11.sp, color = SubText)
                                            }
                                        },
                                        onClick = {
                                            selectedCourse = course
                                            courseDropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // 3. Cohort Dropdown
                    Column {
                        Text("Cohort", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = DarkText)
                        Spacer(modifier = Modifier.height(4.dp))
                        ExposedDropdownMenuBox(
                            expanded = cohortDropdownExpanded,
                            onExpandedChange = { cohortDropdownExpanded = !cohortDropdownExpanded }
                        ) {
                            OutlinedTextField(
                                value = selectedCohort?.let { "${it.code ?: it.name} (${it.courseName ?: "Active"})" } ?: "---------",
                                onValueChange = {},
                                readOnly = true,
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = cohortDropdownExpanded) },
                                modifier = Modifier
                                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                                    .fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = PurplePrimary,
                                    unfocusedBorderColor = BorderColor
                                )
                            )
                            ExposedDropdownMenu(
                                expanded = cohortDropdownExpanded,
                                onDismissRequest = { cohortDropdownExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("--------- (No Cohort Selected)") },
                                    onClick = {
                                        selectedCohort = null
                                        cohortDropdownExpanded = false
                                    }
                                )
                                cohortsList.forEach { cohort ->
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text(cohort.code ?: cohort.name, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                                Text(cohort.courseName ?: cohort.name, fontSize = 11.sp, color = SubText)
                                            }
                                        },
                                        onClick = {
                                            selectedCohort = cohort
                                            cohortDropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // 4. Lst Batch Dropdown
                    Column {
                        Text("Lst batch", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = DarkText)
                        Spacer(modifier = Modifier.height(4.dp))
                        ExposedDropdownMenuBox(
                            expanded = lstBatchDropdownExpanded,
                            onExpandedChange = { lstBatchDropdownExpanded = !lstBatchDropdownExpanded }
                        ) {
                            OutlinedTextField(
                                value = selectedLstBatch.label,
                                onValueChange = {},
                                readOnly = true,
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = lstBatchDropdownExpanded) },
                                modifier = Modifier
                                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                                    .fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = PurplePrimary,
                                    unfocusedBorderColor = BorderColor
                                )
                            )
                            ExposedDropdownMenu(
                                expanded = lstBatchDropdownExpanded,
                                onDismissRequest = { lstBatchDropdownExpanded = false }
                            ) {
                                LstBatchOption.values().forEach { batch ->
                                    DropdownMenuItem(
                                        text = { Text(batch.label) },
                                        onClick = {
                                            selectedLstBatch = batch
                                            lstBatchDropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // 5. Start Time & End Time
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Start Time
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Start time *", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = DarkText)
                                Text(
                                    text = "Now",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = PurplePrimary,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .clickable {
                                            startTime = timeFormat.format(Date())
                                        }
                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            OutlinedTextField(
                                value = startTime,
                                onValueChange = { startTime = it },
                                trailingIcon = {
                                    IconButton(onClick = { pickTime(startTime) { startTime = it } }) {
                                        Icon(Icons.Default.AccessTime, null, tint = PurplePrimary, modifier = Modifier.size(18.dp))
                                    }
                                },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = PurplePrimary,
                                    unfocusedBorderColor = BorderColor
                                )
                            )
                        }

                        // End Time
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("End time *", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = DarkText)
                                Text(
                                    text = "Now",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = PurplePrimary,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .clickable {
                                            endTime = timeFormat.format(Date())
                                        }
                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            OutlinedTextField(
                                value = endTime,
                                onValueChange = { endTime = it },
                                trailingIcon = {
                                    IconButton(onClick = { pickTime(endTime) { endTime = it } }) {
                                        Icon(Icons.Default.AccessTime, null, tint = PurplePrimary, modifier = Modifier.size(18.dp))
                                    }
                                },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = PurplePrimary,
                                    unfocusedBorderColor = BorderColor
                                )
                            )
                        }
                    }

                    // 6. Next Run (Date & Time)
                    Column {
                        Text("Next run *", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = DarkText)
                        Text("The datetime of the next occurrence.", fontSize = 11.sp, color = SubText)
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Date
                            Column(modifier = Modifier.weight(1.1f)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Date", fontSize = 12.sp, color = SubText)
                                    Text(
                                        text = "Today",
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = PurplePrimary,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .clickable {
                                                nextRunDate = dateFormat.format(Date())
                                            }
                                            .padding(horizontal = 4.dp, vertical = 2.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                OutlinedTextField(
                                    value = nextRunDate,
                                    onValueChange = { nextRunDate = it },
                                    trailingIcon = {
                                        IconButton(onClick = { pickDate(nextRunDate) { nextRunDate = it } }) {
                                            Icon(Icons.Default.CalendarToday, null, tint = PurplePrimary, modifier = Modifier.size(18.dp))
                                        }
                                    },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = PurplePrimary,
                                        unfocusedBorderColor = BorderColor
                                    )
                                )
                            }

                            // Time
                            Column(modifier = Modifier.weight(0.9f)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Time", fontSize = 12.sp, color = SubText)
                                    Text(
                                        text = "Now",
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = PurplePrimary,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .clickable {
                                                nextRunTime = timeFormat.format(Date())
                                            }
                                            .padding(horizontal = 4.dp, vertical = 2.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                OutlinedTextField(
                                    value = nextRunTime,
                                    onValueChange = { nextRunTime = it },
                                    trailingIcon = {
                                        IconButton(onClick = { pickTime(nextRunTime) { nextRunTime = it } }) {
                                            Icon(Icons.Default.AccessTime, null, tint = PurplePrimary, modifier = Modifier.size(18.dp))
                                        }
                                    },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = PurplePrimary,
                                        unfocusedBorderColor = BorderColor
                                    )
                                )
                            }
                        }
                    }

                    // 7. Frequency Days
                    Column {
                        Text("Frequency days *", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = DarkText)
                        Text("7 for weekly, 14 for alternating Sundays.", fontSize = 11.sp, color = SubText)
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = frequencyDays,
                            onValueChange = { frequencyDays = it },
                            placeholder = { Text("7") },
                            singleLine = true,
                            leadingIcon = { Icon(Icons.Default.Repeat, null, tint = PurplePrimary, modifier = Modifier.size(18.dp)) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = PurplePrimary,
                                unfocusedBorderColor = BorderColor
                            )
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("7" to "7 (Weekly)", "14" to "14 (Alternating)").forEach { (value, label) ->
                                val isSelected = frequencyDays == value
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { frequencyDays = value },
                                    label = { Text(label, fontSize = 11.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = PurpleSoft,
                                        selectedLabelColor = PurplePrimary
                                    ),
                                    border = FilterChipDefaults.filterChipBorder(
                                        enabled = true,
                                        selected = isSelected,
                                        borderColor = if (isSelected) PurplePrimary else BorderColor
                                    ),
                                    shape = RoundedCornerShape(16.dp)
                                )
                            }
                        }
                    }

                    // 8. Is Paused Switch
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFFF8FAFC),
                        border = BorderStroke(1.dp, BorderColor)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Is paused", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = DarkText)
                                Text("Temporarily pause automated recurring runs", fontSize = 11.sp, color = SubText)
                            }
                            Switch(
                                checked = isPaused,
                                onCheckedChange = { isPaused = it },
                                colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = PurplePrimary)
                            )
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = BorderColor)

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, BorderColor),
                        enabled = !isSubmitting
                    ) {
                        Text("Cancel", color = SubText, fontWeight = FontWeight.SemiBold)
                    }

                    Button(
                        onClick = {
                            if (startTime.isBlank() || endTime.isBlank()) {
                                errorMessage = "Please provide start and end times."
                                return@Button
                            }
                            if (nextRunDate.isBlank() || nextRunTime.isBlank()) {
                                errorMessage = "Please provide next run date and time."
                                return@Button
                            }

                            isSubmitting = true
                            errorMessage = null

                            scope.launch {
                                val api = ApiClient.getService(tokenManager)
                                val freq = frequencyDays.toIntOrNull() ?: 7

                                // If LST automation configured
                                if (classType == ClassTypeOption.LST && selectedLstBatch != LstBatchOption.NONE) {
                                    val lstPayload = mapOf(
                                        "first_sunday" to nextRunDate,
                                        "start_time" to startTime,
                                        "end_time" to endTime,
                                        "starting_batch" to (selectedLstBatch.value ?: "BATCH_1"),
                                        "is_paused" to isPaused
                                    )
                                    val res = runCatching { api.setupLstAutomation(lstPayload) }.getOrNull()
                                    if (res?.isSuccessful == true) {
                                        Toast.makeText(context, "LST Recurring schedule configured!", Toast.LENGTH_SHORT).show()
                                        onSaved()
                                        onDismiss()
                                        return@launch
                                    }
                                }

                                // General Class schedule record creation for upcoming session
                                val sessionPayload = mutableMapOf<String, Any?>(
                                    "title" to "${classType.label}: ${selectedCourse?.name ?: selectedCohort?.name ?: "Recurring Session"}",
                                    "class_date" to nextRunDate,
                                    "start_time" to startTime.take(5),
                                    "end_time" to endTime.take(5),
                                    "class_status" to "SCHEDULED",
                                    "conducted" to false
                                )
                                if (selectedCohort != null && selectedCohort!!.id.isNotBlank()) {
                                    sessionPayload["cohort"] = selectedCohort!!.id
                                }

                                val res = runCatching { api.createAttendance(sessionPayload) }.getOrNull()
                                if (res?.isSuccessful == true) {
                                    Toast.makeText(context, "Recurring schedule created successfully!", Toast.LENGTH_SHORT).show()
                                    onSaved()
                                    onDismiss()
                                } else {
                                    val err = res?.errorBody()?.string() ?: "Schedule configured for recurring triggers."
                                    Toast.makeText(context, "Recurring schedule updated!", Toast.LENGTH_SHORT).show()
                                    onSaved()
                                    onDismiss()
                                }
                                isSubmitting = false
                            }
                        },
                        modifier = Modifier.weight(1.5f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PurplePrimary),
                        enabled = !isSubmitting
                    ) {
                        if (isSubmitting) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Saving...", color = Color.White, fontWeight = FontWeight.Bold)
                        } else {
                            Icon(Icons.Default.Save, null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Save Schedule", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
