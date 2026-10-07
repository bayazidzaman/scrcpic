package com.scrcpic.ui

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scrcpic.adb.RemoteFile
import com.scrcpic.adb.RemoteFileManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.DecimalFormat
import androidx.activity.compose.BackHandler

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileManagerScreen(
    remoteFileManager: RemoteFileManager,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    var currentPath by remember { mutableStateOf("/sdcard/") }
    var files by remember { mutableStateOf<List<RemoteFile>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var transferProgress by remember { mutableStateOf<String?>(null) }
    
    fun loadFiles(path: String) {
        isLoading = true
        coroutineScope.launch {
            files = remoteFileManager.listFiles(path)
            currentPath = path
            isLoading = false
        }
    }
    
    BackHandler {
        if (currentPath == "/" || currentPath == "/sdcard/") {
            onClose()
        } else {
            val parent = currentPath.substringBeforeLast("/", "")
            loadFiles(if (parent.isEmpty()) "/" else parent)
        }
    }
    
    LaunchedEffect(Unit) {
        loadFiles(currentPath)
    }

    val uploadLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            transferProgress = "Uploading file..."
            coroutineScope.launch {
                try {
                    // Copy URI to a temporary local file first
                    val tempFile = File(context.cacheDir, "upload_temp")
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            FileOutputStream(tempFile).use { output ->
                                input.copyTo(output)
                            }
                        }
                    }
                    
                    // Attempt to resolve file name from URI
                    var fileName = "uploaded_file"
                    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                        val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (nameIndex != -1 && cursor.moveToFirst()) {
                            fileName = cursor.getString(nameIndex)
                        }
                    }
                    
                    val remoteDest = if (currentPath.endsWith("/")) "$currentPath$fileName" else "$currentPath/$fileName"
                    val success = remoteFileManager.pushFile(tempFile, remoteDest)
                    if (success) {
                        Toast.makeText(context, "Upload successful", Toast.LENGTH_SHORT).show()
                        loadFiles(currentPath) // Refresh
                    } else {
                        Toast.makeText(context, "Upload failed", Toast.LENGTH_SHORT).show()
                    }
                    tempFile.delete()
                } catch (e: Exception) {
                    Log.e("FileManager", "Error uploading", e)
                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                } finally {
                    transferProgress = null
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        currentPath, 
                        maxLines = 1, 
                        overflow = TextOverflow.Ellipsis,
                        fontSize = 18.sp
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (currentPath == "/" || currentPath == "/sdcard/") {
                            onClose()
                        } else {
                            // Navigate up
                            val parent = currentPath.substringBeforeLast("/", "")
                            loadFiles(if (parent.isEmpty()) "/" else parent)
                        }
                    }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF1E293B),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { uploadLauncher.launch("*/*") },
                containerColor = Color(0xFF3B82F6)
            ) {
                Icon(Icons.Default.Upload, contentDescription = "Upload", tint = Color.White)
            }
        },
        containerColor = Color(0xFF0F172A)
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = Color(0xFF38BDF8)
                )
            } else if (files.isEmpty()) {
                Text(
                    "Folder is empty",
                    color = Color.Gray,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(files) { file ->
                        FileListItem(
                            file = file,
                            onClick = {
                                if (file.isDirectory) {
                                    loadFiles(file.path)
                                }
                            },
                            onDownload = {
                                transferProgress = "Downloading ${file.name}..."
                                coroutineScope.launch {
                                    try {
                                        val downloadsDir = File(
                                            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                                            "Scrcpic"
                                        )
                                        if (!downloadsDir.exists()) downloadsDir.mkdirs()
                                        
                                        val localDest = File(downloadsDir, file.name)
                                        val success = remoteFileManager.pullFile(file.path, localDest)
                                        
                                        if (success) {
                                            Toast.makeText(context, "Saved to Downloads/Scrcpic", Toast.LENGTH_LONG).show()
                                        } else {
                                            Toast.makeText(context, "Download failed", Toast.LENGTH_SHORT).show()
                                        }
                                    } catch (e: Exception) {
                                        Log.e("FileManager", "Error downloading", e)
                                        Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                                    } finally {
                                        transferProgress = null
                                    }
                                }
                            }
                        )
                    }
                }
            }
            
            // Progress Overlay
            if (transferProgress != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0x99000000)),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = Color(0xFF1E293B),
                        tonalElevation = 8.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(24.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(color = Color(0xFF38BDF8), modifier = Modifier.size(24.dp))
                            Spacer(Modifier.width(16.dp))
                            Text(transferProgress!!, color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FileListItem(
    file: RemoteFile,
    onClick: () -> Unit,
    onDownload: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (file.isDirectory) Icons.Default.Folder else Icons.Default.InsertDriveFile,
            contentDescription = null,
            tint = if (file.isDirectory) Color(0xFFFBBF24) else Color(0xFF94A3B8),
            modifier = Modifier.size(32.dp)
        )
        
        Spacer(Modifier.width(16.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = file.name,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!file.isDirectory) {
                Text(
                    text = "${formatSize(file.size)} • ${file.date}",
                    color = Color.Gray,
                    fontSize = 12.sp
                )
            } else {
                Text(
                    text = file.date,
                    color = Color.Gray,
                    fontSize = 12.sp
                )
            }
        }
        
        if (!file.isDirectory) {
            IconButton(onClick = onDownload) {
                Icon(Icons.Default.Download, contentDescription = "Download", tint = Color(0xFF38BDF8))
            }
        }
    }
}

fun formatSize(sizeBytes: Long): String {
    if (sizeBytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(sizeBytes.toDouble()) / Math.log10(1024.0)).toInt()
    return DecimalFormat("#,##0.#").format(sizeBytes / Math.pow(1024.0, digitGroups.toDouble())) + " " + units[digitGroups]
}
