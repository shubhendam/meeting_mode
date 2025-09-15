package com.example.meeting_mode_gemma.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.meeting_mode_gemma.data.MeetingSession
import com.example.meeting_mode_gemma.data.MeetingStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeetingScreen() {
    val context = LocalContext.current
    val viewModel: MeetingViewModel = viewModel {
        MeetingViewModel(context.applicationContext as android.app.Application)
    }
    val uiState by viewModel.uiState.collectAsState()
    var showMeetingList by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF0F0F0)) // Light grey background
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {

            Spacer(modifier = Modifier.weight(1f))

            // Status text
            Text(
                text = when (uiState.currentStatus) {
                    MeetingStatus.RECORDING -> "Recording in progress..."
                    MeetingStatus.PROCESSING_AUDIO -> "Processing audio..."
                    MeetingStatus.TRANSCRIBING -> "Transcribing audio..."
                    MeetingStatus.SUMMARIZING -> "Generating summary..."
                    MeetingStatus.COMPLETED -> "Meeting completed!"
                    MeetingStatus.ERROR -> "Error occurred"
                    else -> "Ready to record"
                },
                fontSize = 24.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                color = Color.Black,
                modifier = Modifier.padding(bottom = 32.dp)
            )

            // Show processing message if available
            if (uiState.processingMessage.isNotEmpty()) {
                Text(
                    text = uiState.processingMessage,
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center,
                    color = Color.Gray,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }

            // Progress indicator or buttons
            if (uiState.isProcessing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(64.dp),
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                // Record button
                Button(
                    onClick = {
                        if (uiState.isRecording) {
                            viewModel.stopRecording()
                        } else {
                            viewModel.startRecording()
                        }
                    },
                    modifier = Modifier
                        .size(120.dp)
                        .clip(CircleShape),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (uiState.isRecording) Color.Gray else Color.Red
                    )
                ) {
                    Icon(
                        imageVector = if (uiState.isRecording) Icons.Default.Stop else Icons.Default.PlayArrow,
                        contentDescription = if (uiState.isRecording) "Stop Recording" else "Start Recording",
                        modifier = Modifier.size(48.dp),
                        tint = Color.White
                    )
                }

                Text(
                    text = if (uiState.isRecording) "Stop Recording" else "Start Recording",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.Black,
                    modifier = Modifier.padding(top = 16.dp)
                )
            }

            // Show file location for completed recordings
            if (uiState.currentStatus == MeetingStatus.COMPLETED && uiState.currentSession != null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Recording Saved",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black
                        )
                        Text(
                            text = "Session: ${uiState.currentSession!!.folderName}",
                            fontSize = 14.sp,
                            color = Color.Gray,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                        Text(
                            text = "Files saved to: ${viewModel.getStorageLocationForUI()}",
                            fontSize = 12.sp,
                            color = Color.Gray,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                        Text(
                            text = "You can now find your recordings in your file manager!",
                            fontSize = 11.sp,
                            color = Color(0xFF4CAF50), // Green color
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))
        }

        // Hamburger menu button
        IconButton(
            onClick = { showMeetingList = true },
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
                .background(Color.White.copy(alpha = 0.8f), CircleShape)
        ) {
            Icon(
                Icons.Default.Menu,
                contentDescription = "Meeting History",
                modifier = Modifier.size(32.dp),
                tint = Color.Black
            )
        }

        // Meeting list bottom sheet
        if (showMeetingList) {
            ModalBottomSheet(
                onDismissRequest = { showMeetingList = false },
                modifier = Modifier.fillMaxHeight(0.7f)
            ) {
                MeetingListContent(
                    meetings = uiState.completedMeetings,
                    onMeetingClick = { meeting ->
                        viewModel.openMeetingSummary(meeting)
                        showMeetingList = false
                    }
                )
            }
        }

        // Show completion dialog
        if (uiState.showCompletedDialog && uiState.currentSession != null) {
            AlertDialog(
                onDismissRequest = { viewModel.dismissCompletedDialog() },
                title = { Text("Recording Complete") },
                text = {
                    Text("Your meeting has been recorded and saved to ${viewModel.getStorageLocationForUI()}. Processing will begin in the background.")
                },
                confirmButton = {
                    TextButton(onClick = { viewModel.dismissCompletedDialog() }) {
                        Text("OK")
                    }
                }
            )
        }
    }
}

@Composable
fun MeetingListContent(
    meetings: List<MeetingSession>,
    onMeetingClick: (MeetingSession) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Text(
            text = "Meeting History",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        if (meetings.isEmpty()) {
            Text(
                text = "No completed meetings yet",
                fontSize = 16.sp,
                color = Color.Gray,
                modifier = Modifier.padding(32.dp),
                textAlign = TextAlign.Center
            )
        } else {
            LazyColumn {
                items(meetings) { meeting ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        onClick = { onMeetingClick(meeting) }
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(
                                text = meeting.getDisplayName(),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Status: ${meeting.status}",
                                fontSize = 14.sp,
                                color = Color.Gray,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}