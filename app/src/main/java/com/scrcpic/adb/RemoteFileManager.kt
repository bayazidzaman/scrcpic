package com.scrcpic.adb

import android.content.Context
import android.os.Environment
import android.util.Log
import dadb.Dadb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class RemoteFile(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long,
    val date: String
)

class RemoteFileManager(private val dadb: Dadb) {
    companion object {
        private const val TAG = "RemoteFileManager"
    }

    suspend fun listFiles(path: String): List<RemoteFile> = withContext(Dispatchers.IO) {
        val result = mutableListOf<RemoteFile>()
        try {
            // Use stat command or ls -lA to get file details
            val response = dadb.shell("ls -lA \"$path\"")
            if (response.exitCode == 0) {
                val lines = response.output.split("\n")
                for (line in lines) {
                    val trimmed = line.trim()
                    if (trimmed.isEmpty() || trimmed.startsWith("total ")) continue

                    // Parse ls -l output (e.g., "-rw-rw---- 1 u0_a152 u0_a152 12345 2023-01-01 12:00 file.txt")
                    // Columns: permissions, links, owner, group, size, date, time, name
                    val parts = trimmed.split(Regex("\\s+"), limit = 8)
                    if (parts.size >= 8) {
                        val permissions = parts[0]
                        val isDirectory = permissions.startsWith("d")
                        val size = parts[4].toLongOrNull() ?: 0L
                        val date = "${parts[5]} ${parts[6]}"
                        val name = parts[7]
                        if (name == "." || name == "..") continue
                        
                        val fullPath = if (path.endsWith("/")) "$path$name" else "$path/$name"
                        result.add(RemoteFile(name, fullPath, isDirectory, size, date))
                    }
                }
            } else {
                Log.w(TAG, "Failed to list directory: ${response.errorOutput}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception listing files at $path", e)
        }
        
        // Sort: directories first, then alphabetically
        result.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
    }

    suspend fun pullFile(remotePath: String, localFile: File): Boolean = withContext(Dispatchers.IO) {
        try {
            dadb.pull(localFile, remotePath)
            return@withContext true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to pull file $remotePath", e)
            return@withContext false
        }
    }

    suspend fun pushFile(localFile: File, remotePath: String): Boolean = withContext(Dispatchers.IO) {
        try {
            // 0644 in octal is 420
            dadb.push(localFile, remotePath, 420, System.currentTimeMillis())
            return@withContext true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to push file to $remotePath", e)
            return@withContext false
        }
    }
}
