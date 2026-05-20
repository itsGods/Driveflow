package com.example

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.outlined.DownloadForOffline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.DownloadRecord
import com.example.ui.theme.DriveJetTheme
import com.example.ui.theme.CloudSurface
import com.example.ui.theme.DarkDeepBlue
import com.example.ui.theme.DarkSlate
import com.example.ui.theme.MutedBorder
import com.example.ui.theme.PillSurface
import com.example.ui.theme.SoftAzure
import com.example.ui.theme.SoftBorder
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DriveJetTheme {
                DriveFlowApp()
            }
        }
    }
}

@Composable
fun DriveFlowApp(viewModel: MainViewModel = viewModel()) {
    val currentScreen by viewModel.currentScreen.collectAsStateWithLifecycle()
    val history by viewModel.historyRecords.collectAsStateWithLifecycle()
    
    Scaffold(
        containerColor = DarkSlate,
        modifier = Modifier.fillMaxSize(),
        topBar = { HeaderSection() },
        bottomBar = { BottomNavBar(currentScreen) { viewModel.setScreen(it) } }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            AnimatedContent(
                targetState = currentScreen,
                transitionSpec = {
                    fadeIn(animationSpec = tween(400)) togetherWith fadeOut(animationSpec = tween(400))
                },
                label = "ScreenTransition"
            ) { screen ->
                when (screen) {
                    Screen.HOME -> HomeScreen(viewModel)
                    Screen.HISTORY -> HistoryScreen(history, onClear = { viewModel.clearHistory() }, onDelete = { viewModel.deleteRecord(it) })
                    Screen.LIBRARY -> LibraryScreen()
                }
            }
        }
    }
}

@Composable
fun HomeScreen(viewModel: MainViewModel) {
    var url by remember { mutableStateOf("") }
    val context = LocalContext.current
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .verticalScroll(rememberScrollState())
    ) {
        HeadlineSection()

        Spacer(modifier = Modifier.height(24.dp))

        InputStreamCard(
            url = url,
            onUrlChange = { url = it }
        )

        Spacer(modifier = Modifier.height(24.dp))

        StatsGrid()

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp),
            contentAlignment = Alignment.Center
        ) {
            DownloadPulseButton(
                onClick = {
                    if (url.isNotBlank()) {
                        val fileId = extractFileId(url)
                        if (fileId != null) {
                            startDownload(context, url)
                            viewModel.addDownloadRecord(url, "DriveJet_$fileId")
                            url = ""
                        } else {
                            Toast.makeText(context, "Invalid Google Drive link", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(context, "Insert a valid stream link", Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }
    }
}

@Composable
fun HistoryScreen(history: List<DownloadRecord>, onClear: () -> Unit, onDelete: (Int) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Transfer Logs", color = TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.Light)
            if (history.isNotEmpty()) {
                Text(
                    "Clear All",
                    color = SoftAzure,
                    fontSize = 14.sp,
                    modifier = Modifier.clickable { onClear() }
                )
            }
        }

        if (history.isEmpty()) {
            EmptyState("Your cloud history is empty.", "Completed transfers will appear here.")
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(history, key = { it.id }) { record ->
                    HistoryItem(record, onDelete = { onDelete(record.id) })
                }
            }
        }
    }
}

@Composable
fun LibraryScreen() {
    val downloadedFiles = remember { getDownloadedFiles() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
    ) {
        Text(
            "Local Vault",
            color = TextPrimary,
            fontSize = 24.sp,
            fontWeight = FontWeight.Light,
            modifier = Modifier.padding(vertical = 16.dp)
        )

        if (downloadedFiles.isEmpty()) {
            EmptyState("No local files found.", "Downloaded media is stored in your Downloads folder.")
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(downloadedFiles) { file ->
                    FileItem(file)
                }
            }
        }
    }
}

@Composable
fun HistoryItem(record: DownloadRecord, onDelete: () -> Unit) {
    Surface(
        color = CloudSurface,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, MutedBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(PillSurface),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.CloudSync, contentDescription = null, tint = SoftAzure, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(record.fileName, color = TextPrimary, fontWeight = FontWeight.Medium, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(Date(record.timestamp)), color = TextSecondary, fontSize = 12.sp)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = TextMuted, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
fun FileItem(file: File) {
    Surface(
        color = CloudSurface,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, MutedBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(PillSurface),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Description, contentDescription = null, tint = SoftAzure, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(file.name, color = TextPrimary, fontWeight = FontWeight.Medium, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${file.length() / (1024 * 1024)} MB", color = TextSecondary, fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun EmptyState(title: String, subtitle: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 80.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(CloudSurface)
                .border(1.dp, MutedBorder, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Folder, contentDescription = null, tint = SoftAzure.copy(alpha = 0.3f), modifier = Modifier.size(32.dp))
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text(title, color = TextPrimary, fontWeight = FontWeight.Medium, fontSize = 16.sp)
        Text(subtitle, color = TextSecondary, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
fun BottomNavBar(currentScreen: Screen, onScreenSelected: (Screen) -> Unit) {
    Surface(
        modifier = Modifier
            .navigationBarsPadding()
            .padding(24.dp)
            .fillMaxWidth()
            .height(72.dp),
        shape = RoundedCornerShape(100.dp),
        color = CloudSurface,
        border = BorderStroke(1.dp, MutedBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            NavPill(icon = Icons.Default.Home, label = "Home", active = currentScreen == Screen.HOME, modifier = Modifier.weight(1f)) {
                onScreenSelected(Screen.HOME)
            }
            NavPill(icon = Icons.Default.History, label = "History", active = currentScreen == Screen.HISTORY, modifier = Modifier.weight(1f)) {
                onScreenSelected(Screen.HISTORY)
            }
            NavPill(icon = Icons.Default.Folder, label = "Library", active = currentScreen == Screen.LIBRARY, modifier = Modifier.weight(1f)) {
                onScreenSelected(Screen.LIBRARY)
            }
        }
    }
}

@Composable
fun NavPill(
    icon: ImageVector,
    label: String,
    active: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(100.dp))
            .background(if (active) PillSurface else Color.Transparent)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (active) SoftAzure else TextSecondary,
                modifier = Modifier.size(18.dp)
            )
            if (active) {
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = label,
                    color = SoftAzure,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

fun getDownloadedFiles(): List<File> {
    val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
    return dir.listFiles { file -> file.name.contains("DriveJet") }?.toList() ?: emptyList()
}

@Composable
fun HeaderSection() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 24.dp, vertical = 24.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(SoftAzure),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "D",
                    color = DarkDeepBlue,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "DriveFlow",
                color = TextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.5).sp
            )
        }

        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(CloudSurface)
                .border(1.dp, MutedBorder, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "Settings",
                tint = SoftAzure,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun HeadlineSection() {
    Column(modifier = Modifier.padding(top = 16.dp)) {
        Text(
            text = buildAnnotatedString {
                append("Ready to ")
                withStyle(style = SpanStyle(color = SoftAzure, fontWeight = FontWeight.Medium)) {
                    append("pull")
                }
                append(" any file size.")
            },
            color = TextPrimary,
            fontSize = 36.sp,
            fontWeight = FontWeight.Light,
            lineHeight = 44.sp
        )
        Text(
            text = "Bypass quotas and restrictions instantly.",
            color = TextSecondary,
            fontSize = 14.sp,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

@Composable
fun InputStreamCard(
    url: String,
    onUrlChange: (String) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = CloudSurface,
        border = BorderStroke(1.dp, MutedBorder)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "INPUT STREAM",
                    color = SoftAzure,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    letterSpacing = 1.5.sp
                )
                Surface(
                    color = PillSurface,
                    shape = RoundedCornerShape(100.dp)
                ) {
                    Text(
                        text = if (url.contains("drive.google.com")) "Detected" else "Ready",
                        color = TextPrimary,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            BasicTextField(
                value = url,
                onValueChange = onUrlChange,
                textStyle = MaterialTheme.typography.bodySmall.copy(color = TextPrimary),
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { innerTextField ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(DarkSlate)
                            .border(
                                1.dp,
                                SoftBorder,
                                RoundedCornerShape(16.dp)
                            )
                            .padding(horizontal = 16.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (url.isEmpty()) {
                            Text(
                                "https://drive.google.com/...",
                                color = TextMuted,
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        innerTextField()
                    }
                }
            )
        }
    }
}

@Composable
fun StatsGrid() {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        StatCard(
            modifier = Modifier.weight(1f),
            icon = Icons.Default.Speed,
            value = "980",
            unit = "Mbps",
            label = "Peak Link Speed"
        )
        StatCard(
            modifier = Modifier.weight(1f),
            icon = Icons.Default.CloudSync,
            value = "16",
            unit = "Chunks",
            label = "Parallel Logic"
        )
    }
}

@Composable
fun StatCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    value: String,
    unit: String,
    label: String
) {
    Surface(
        modifier = modifier.height(128.dp),
        shape = RoundedCornerShape(24.dp),
        color = CloudSurface,
        border = BorderStroke(1.dp, MutedBorder)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = SoftAzure,
                modifier = Modifier.size(24.dp)
            )
            Column {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = value,
                        color = TextPrimary,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = " $unit",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                }
                Text(
                    text = label.uppercase(),
                    color = TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
fun DownloadPulseButton(onClick: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val glowSize by infiniteTransition.animateFloat(
        initialValue = -32f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "glowSize"
    )
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.15f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "glowAlpha"
    )

    Box(contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(96.dp + glowSize.dp.times(-1))
                .clip(CircleShape)
                .background(SoftAzure.copy(alpha = glowAlpha))
                .blur(32.dp)
        )

        Button(
            onClick = onClick,
            modifier = Modifier
                .size(96.dp)
                .shadow(
                    elevation = 20.dp,
                    shape = CircleShape,
                    spotColor = SoftAzure.copy(alpha = 0.4f)
                ),
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(containerColor = SoftAzure),
            contentPadding = PaddingValues(0.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.DownloadForOffline,
                contentDescription = "Download",
                tint = DarkDeepBlue,
                modifier = Modifier.size(40.dp)
            )
        }
    }
}

fun startDownload(context: Context, link: String) {
    try {
        val fileId = extractFileId(link)
        if (fileId == null) {
            Toast.makeText(context, "Invalid Google Drive link", Toast.LENGTH_LONG).show()
            return
        }

        val downloadUrl = "https://drive.google.com/uc?export=download&id=$fileId"
        val request = DownloadManager.Request(Uri.parse(downloadUrl))
            .setTitle("DriveFlow Download")
            .setDescription("Fetching file from Cloud...")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "DriveJet_$fileId")
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)

        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        dm.enqueue(request)
        Toast.makeText(context, "Download Engine Initialized", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        Toast.makeText(context, "Error: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
    }
}

fun extractFileId(url: String): String? {
    val patterns = listOf(
        "id=([^&]+)".toRegex(),
        "d/([^/]+)".toRegex(),
        "file/d/([^/]+)".toRegex()
    )
    for (pattern in patterns) {
        val match = pattern.find(url)
        if (match != null) return match.groupValues[1]
    }
    return null
}
