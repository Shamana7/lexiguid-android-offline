package com.lexiguid.app.ui.profile

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onNavigateBack: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val profile by viewModel.profile.collectAsState()

    var firstName by remember(profile) { mutableStateOf(profile?.firstName ?: "") }
    var grade by remember(profile) { mutableStateOf(profile?.grade ?: "") }
    var country by remember(profile) { mutableStateOf(profile?.country ?: "") }
    var state by remember(profile) { mutableStateOf(profile?.state ?: "") }
    var board by remember(profile) { mutableStateOf(profile?.board ?: "") }
    var medium by remember(profile) { mutableStateOf(profile?.medium ?: "") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Student Profile") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "Your profile helps LexiGuid give grade-appropriate answers.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )

            OutlinedTextField(
                value = firstName,
                onValueChange = { firstName = it },
                label = { Text("First Name") },
                modifier = Modifier.fillMaxWidth()
            )

            // Grade dropdown
            var gradeExpanded by remember { mutableStateOf(false) }
            val grades = listOf(
                "Class_6", "Class_7", "Class_8", "Class_9", "Class_10",
                "Class_11", "Class_12"
            )
            ExposedDropdownMenuBox(
                expanded = gradeExpanded,
                onExpandedChange = { gradeExpanded = it }
            ) {
                OutlinedTextField(
                    value = grade.replace("_", " "),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Grade") },
                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(gradeExpanded) }
                )
                ExposedDropdownMenu(
                    expanded = gradeExpanded,
                    onDismissRequest = { gradeExpanded = false }
                ) {
                    grades.forEach { g ->
                        DropdownMenuItem(
                            text = { Text(g.replace("_", " ")) },
                            onClick = { grade = g; gradeExpanded = false }
                        )
                    }
                }
            }

            OutlinedTextField(
                value = country,
                onValueChange = { country = it },
                label = { Text("Country") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = state,
                onValueChange = { state = it },
                label = { Text("State") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = board,
                onValueChange = { board = it },
                label = { Text("Education Board") },
                placeholder = { Text("e.g., CBSE, KSEEB, ICSE") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = medium,
                onValueChange = { medium = it },
                label = { Text("Medium") },
                placeholder = { Text("e.g., English, Hindi, Kannada") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = {
                    viewModel.saveProfile(firstName, grade, country, state, board, medium)
                    onNavigateBack()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save Profile")
            }
        }
    }
}
