package com.example

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.net.Uri
import androidx.core.content.FileProvider
import android.os.Environment
import android.view.HapticFeedbackConstants
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Share
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.DownloadRecord
import com.example.ui.theme.*
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
        containerColor = Color.Transparent,
        modifier = Modifier.fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(DarkSlate, Color(0xFF0F1522))
                )
            ),
        topBar = { HeaderSection() },
        bottomBar = { BottomNavBar(currentScreen) { viewModel.setScreen(it) } }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            AnimatedContent(
                targetState = currentScreen,
                transitionSpec = {
                    val slideDirection = if (targetState.ordinal > initialState.ordinal) AnimatedContentTransitionScope.SlideDirection.Left else AnimatedContentTransitionScope.SlideDirection.Right
                    slideIntoContainer(slideDirection, tween(400, easing = FastOutSlowInEasing)) + fadeIn(tween(400)) togetherWith
                            slideOutOfContainer(slideDirection, tween(400, easing = FastOutSlowInEasing)) + fadeOut(tween(400))
                },
                label = "ScreenTransition"
            ) { screen ->
                when (screen) {
                    Screen.HOME -> HomeScreen(viewModel)
                    Screen.HISTORY -> HistoryScreen(history, onClear = { viewModel.clearHistory() }, onDelete = { viewModel.deleteRecord(it) })
                    Screen.LIBRARY -> LibraryScreen()
                    Screen.SETTINGS -> SettingsScreen(viewModel)
                }
            }
        }
    }
}

@Composable
fun HomeScreen(viewModel: MainViewModel) {
    var url by remember { mutableStateOf("") }
    val context = LocalContext.current
    val view = LocalView.current
    val activeDownloads by viewModel.activeDownloads.collectAsStateWithLifecycle()
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
    
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 100.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Download File",
                color = TextPrimary,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Paste a download link to start",
                color = TextSecondary,
                fontSize = 16.sp,
                modifier = Modifier.padding(top = 8.dp)
            )
            Spacer(modifier = Modifier.height(32.dp))
        }

        item {
            ModernInputCard(
                url = url,
                onUrlChange = { url = it },
                onPaste = {
                    clipboardManager.getText()?.text?.let {
                        url = it
                    }
                },
                onDownload = {
                    try { view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS) } catch (e: Exception) {}
                    if (url.isNotBlank()) {
                        val urls = url.split(Regex("\\s+")).filter { it.isNotBlank() }
                        urls.forEach { 
                            viewModel.startDownload(it, context)
                        }
                        url = ""
                    } else {
                        Toast.makeText(context, "Insert a valid download link", Toast.LENGTH_SHORT).show()
                    }
                }
            )
            Spacer(modifier = Modifier.height(32.dp))
        }

        // Active Transfers Section
        if (activeDownloads.isNotEmpty()) {
            item {
                Text(
                    text = "Active Transfers",
                    color = TextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }
            items(activeDownloads.values.toList().reversed(), key = { it.id }) { download ->
                ModernTransferCard(download = download, onCancel = { viewModel.cancelDownload(it, context) }, onPauseResume = { viewModel.togglePause(it, context) })
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
fun ModernInputCard(
    url: String,
    onUrlChange: (String) -> Unit,
    onPaste: () -> Unit,
    onDownload: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = CloudSurface,
        border = BorderStroke(1.dp, MutedBorder)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            // Text Field
            BasicTextField(
                value = url,
                onValueChange = onUrlChange,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = TextPrimary, fontSize = 16.sp),
                singleLine = false,
                maxLines = 5,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                modifier = Modifier.fillMaxWidth(),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(SoftAzure),
                decorationBox = { innerTextField ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(DarkSlate)
                            .border(1.dp, SoftBorder, RoundedCornerShape(16.dp))
                            .padding(16.dp),
                        contentAlignment = Alignment.TopStart
                    ) {
                        if (url.isEmpty()) {
                            Text(
                                "https://example.com/file1.zip\n...",
                                color = TextMuted,
                                fontSize = 16.sp,
                                maxLines = 5,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Box(modifier = Modifier.padding(end = 40.dp)) {
                            innerTextField()
                        }
                        
                        if (url.isEmpty()) {
                            IconButton(onClick = onPaste, modifier = Modifier.align(Alignment.TopEnd).offset(x = 16.dp, y = -16.dp)) {
                                Icon(Icons.Default.ContentPaste, contentDescription = "Paste", tint = SoftAzure)
                            }
                        } else {
                            IconButton(onClick = { onUrlChange("") }, modifier = Modifier.align(Alignment.TopEnd).offset(x = 16.dp, y = -16.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "Clear", tint = TextMuted)
                            }
                        }
                    }
                }
            )
            
            Spacer(modifier = Modifier.height(20.dp))
            
            Button(
                onClick = onDownload,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SoftAzure)
            ) {
                Icon(Icons.Default.Download, contentDescription = null, tint = DarkDeepBlue, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Start Download", color = DarkDeepBlue, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
    }
}

@Composable
fun ModernTransferCard(download: DownloadInfo, onCancel: (Long) -> Unit, onPauseResume: (Long) -> Unit) {
    val animatedProgress by animateFloatAsState(
        targetValue = download.progress.coerceIn(0f, 1f),
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "progress"
    )
    
    Surface(
        color = CloudSurface,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, MutedBorder),
        modifier = Modifier.fillMaxWidth().clickable { onPauseResume(download.id) }
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val isFailed = download.state == DownloadState.FAILED
                val isComplete = download.state == DownloadState.COMPLETED
                val isPaused = download.state == DownloadState.PAUSED
                val statusColor = if (isFailed) Color(0xFFE57373) else if (isComplete) Color(0xFF81C784) else if (isPaused) Color(0xFFFFB74D) else SoftAzure
                
                Box(modifier = Modifier.size(52.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        progress = { animatedProgress },
                        modifier = Modifier.fillMaxSize(),
                        color = statusColor,
                        trackColor = DarkSlate,
                        strokeWidth = 4.dp,
                        strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
                    )
                    if (isComplete) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF81C784))
                    } else if (isFailed) {
                        Icon(Icons.Default.Close, contentDescription = null, tint = Color(0xFFE57373))
                    } else if (isPaused) {
                        Text("||", color = Color(0xFFFFB74D), fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
                    } else {
                        Icon(Icons.Default.Download, contentDescription = null, tint = SoftAzure)
                    }
                }
                
                Spacer(modifier = Modifier.width(16.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(download.fileName, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(modifier = Modifier.height(4.dp))
                    val downloadedMb = download.bytesDownloaded / (1024 * 1024)
                    val totalMb = if (download.bytesTotal > 0) (download.bytesTotal / (1024 * 1024)).toString() else "?"
                    val speedText = formatSpeed(download.speed)
                    Text(
                        text = if (isFailed) "Failed: ${download.error ?: "Error"}" else if (isComplete) "Completed • $totalMb MB" else if (isPaused) "Paused" else "$downloadedMb MB of $totalMb MB",
                        color = TextSecondary,
                        fontSize = 13.sp
                    )
                    if (!isFailed && !isComplete && !isPaused && download.speed > 0) {
                        Text(speedText, color = SoftAzure, fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 2.dp))
                    }
                }
                
                IconButton(onClick = { onCancel(download.id) }) {
                    Icon(if (isComplete || isFailed) Icons.Default.Delete else Icons.Default.Cancel, contentDescription = "Cancel", tint = TextMuted)
                }
            }
        }
    }
}

fun formatSpeed(bytesPerSec: Long): String {
    if (bytesPerSec == 0L) return "Calculating..."
    val kb = bytesPerSec / 1024
    if (kb < 1024) return "$kb KB/s"
    val mb = kb / 1024f
    return String.format(Locale.getDefault(), "%.1f MB/s", mb)
}

@Composable
fun HistoryScreen(history: List<DownloadRecord>, onClear: () -> Unit, onDelete: (Int) -> Unit) {
    val view = LocalView.current
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
                    modifier = Modifier.clickable { 
                        try { view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK) } catch (e: Exception) {}
                        onClear() 
                    }.padding(8.dp)
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
                    HistoryItem(record, onDelete = { 
                        try { view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP) } catch (e: Exception) {}
                        onDelete(record.id) 
                    })
                }
            }
        }
    }
}

@Composable
fun ExoPlayerView(file: File) {
    val context = LocalContext.current
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            val uri = Uri.fromFile(file)
            val mediaItem = MediaItem.fromUri(uri)
            setMediaItem(mediaItem)
            prepare()
            playWhenReady = true
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
        }
    }

    AndroidView(
        factory = {
            PlayerView(context).apply {
                player = exoPlayer
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}

@Composable
fun LibraryScreen() {
    var downloadedFiles by remember { mutableStateOf(getDownloadedFiles()) }
    val context = LocalContext.current
    val view = LocalView.current
    var playingFile by remember { mutableStateOf<File?>(null) }

    if (playingFile != null) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            ExoPlayerView(playingFile!!)
            IconButton(
                onClick = { playingFile = null },
                modifier = Modifier.padding(16.dp).align(Alignment.TopStart)
            ) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
            }
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Local Vault", color = TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.Light)
                Text("${downloadedFiles.size} items", color = SoftAzure, fontSize = 14.sp)
            }

            if (downloadedFiles.isEmpty()) {
                EmptyState("No local files found.", "Downloaded media is stored in your Downloads.")
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    items(downloadedFiles) { file ->
                        FileItem(
                            file = file,
                            onDelete = {
                                try { view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP) } catch (e: Exception) {}
                                if (file.delete()) {
                                    downloadedFiles = getDownloadedFiles()
                                    Toast.makeText(context, "File removed from device", Toast.LENGTH_SHORT).show()
                                }
                            },
                            onOpen = {
                                val extension = file.extension.lowercase()
                                if (extension in listOf("mp4", "mkv", "mp3", "wav", "m4a")) {
                                    playingFile = file
                                } else {
                                    try {
                                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
                                        val intent = Intent(Intent.ACTION_VIEW).apply {
                                            setDataAndType(uri, "*/*")
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(Intent.createChooser(intent, "Open file"))
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Cannot open this file", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                        onShare = {
                            try {
                                val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "*/*"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(intent, "Share file"))
                            } catch (e: Exception) {
                                Toast.makeText(context, "Cannot share this file", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }
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
fun FileItem(file: File, onDelete: () -> Unit, onOpen: () -> Unit, onShare: () -> Unit) {
    Surface(
        color = CloudSurface,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, MutedBorder),
        modifier = Modifier.fillMaxWidth().clickable { onOpen() }
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
            Column(modifier = Modifier.weight(1f)) {
                Text(file.name, color = TextPrimary, fontWeight = FontWeight.Medium, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val mb = file.length() / (1024 * 1024)
                Text(if (mb > 0) "$mb MB" else "${file.length() / 1024} KB", color = TextSecondary, fontSize = 12.sp)
            }
            IconButton(onClick = onShare) {
                Icon(Icons.Default.Share, contentDescription = "Share", tint = SoftAzure, modifier = Modifier.size(20.dp))
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = TextMuted, modifier = Modifier.size(20.dp))
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

fun getDownloadedFiles(): List<File> {
    return try {
        val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        dir.listFiles { file -> file.name.contains("DriveFlow") || file.name.contains("DriveJet") }?.toList()?.sortedByDescending { it.lastModified() } ?: emptyList()
    } catch(e: Exception) {
        emptyList()
    }
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
    }
}



@Composable
fun BottomNavBar(currentScreen: Screen, onScreenSelected: (Screen) -> Unit) {
    val view = LocalView.current
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
                try { view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) } catch (e: Exception) {}
                onScreenSelected(Screen.HOME)
            }
            NavPill(icon = Icons.Default.History, label = "Logs", active = currentScreen == Screen.HISTORY, modifier = Modifier.weight(1f)) {
                try { view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) } catch (e: Exception) {}
                onScreenSelected(Screen.HISTORY)
            }
            NavPill(icon = Icons.Default.Folder, label = "Vault", active = currentScreen == Screen.LIBRARY, modifier = Modifier.weight(1f)) {
                try { view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) } catch (e: Exception) {}
                onScreenSelected(Screen.LIBRARY)
            }
            NavPill(icon = Icons.Default.Settings, label = "Settings", active = currentScreen == Screen.SETTINGS, modifier = Modifier.weight(1f)) {
                try { view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) } catch (e: Exception) {}
                onScreenSelected(Screen.SETTINGS)
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
            AnimatedVisibility(visible = active) {
                Row {
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
}

@Composable
fun SettingsScreen(viewModel: MainViewModel) {
    val wifiOnly by viewModel.wifiOnly.collectAsStateWithLifecycle()
    val concurrentLimit by viewModel.concurrentLimit.collectAsStateWithLifecycle()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Settings", color = TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.Light)
        }
        
        Spacer(modifier = Modifier.height(16.dp))

        Surface(
            color = CloudSurface,
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, MutedBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Wi-Fi Only Downloads", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                        Text("Pause transfers on cellular networks", color = TextSecondary, fontSize = 13.sp)
                    }
                    Switch(
                        checked = wifiOnly,
                        onCheckedChange = { viewModel.setWifiOnly(it) },
                        colors = SwitchDefaults.colors(checkedThumbColor = DarkDeepBlue, checkedTrackColor = SoftAzure)
                    )
                }
                
                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp), color = MutedBorder)
                
                val limits = listOf(1, 2, 4, 8)
                Text("Concurrent Download Limit", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    limits.forEach { limit ->
                        val isSelected = concurrentLimit == limit
                        Surface(
                            shape = CircleShape,
                            color = if (isSelected) SoftAzure else DarkSlate,
                            modifier = Modifier.size(48.dp).clickable { viewModel.setConcurrentLimit(limit) },
                            border = BorderStroke(1.dp, if (isSelected) SoftAzure else MutedBorder)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("$limit", color = if (isSelected) DarkDeepBlue else TextPrimary, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}
