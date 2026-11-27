package ru.viceburg.offline

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.util.ArrayDeque

internal data class MediaSummary(
    val imageCount: Int = 0,
    val videoCount: Int = 0,
    val audioCount: Int = 0,
    val otherCount: Int = 0,
    val foldersFound: Int = 0,
    val categoryCounts: Map<String, Int> = emptyMap(),
    val characterFaces: Map<String, Uri> = emptyMap()
) {
    val fileCount: Int get() = imageCount + videoCount + audioCount + otherCount
}

internal object MediaLibrary {
    private val faceName = Regex("^([MF]_\\d+)_Face\\.(jpg|jpeg|png|webp)$", RegexOption.IGNORE_CASE)

    fun scan(root: DocumentFile): MediaSummary {
        val directories = ArrayDeque<Pair<DocumentFile, String>>()
        directories.add(root to "")
        var images = 0
        var videos = 0
        var audio = 0
        var other = 0
        var folders = 0
        val categories = mutableMapOf<String, Int>()
        val characterFaces = mutableMapOf<String, Uri>()
        while (directories.isNotEmpty()) {
            val (directory, relativePath) = directories.removeFirst()
            val children = try { directory.listFiles() } catch (_: Exception) { emptyArray() }
            for (child in children) {
                val name = child.name ?: continue
                if (child.isDirectory) {
                    if (relativePath.isEmpty() && name.equals("backup", ignoreCase = true)) continue
                    folders++
                    directories.add(child to if (relativePath.isEmpty()) name else "$relativePath/$name")
                } else if (child.isFile) {
                    val mime = child.type?.substringBefore(';')?.lowercase().orEmpty()
                    when {
                        mime.startsWith("image/") || name.substringAfterLast('.', "").lowercase() in setOf("jpg", "jpeg", "png", "webp", "gif", "bmp") -> images++
                        mime.startsWith("video/") || name.substringAfterLast('.', "").lowercase() in setOf("mp4", "m4v", "webm", "mov", "mkv") -> videos++
                        mime.startsWith("audio/") || name.substringAfterLast('.', "").lowercase() in setOf("mp3", "m4a", "wav", "ogg", "flac") -> audio++
                        else -> other++
                    }
                    val category = relativePath.substringBefore('/').ifEmpty { "Корень" }
                    categories[category] = (categories[category] ?: 0) + 1
                    val parts = relativePath.split('/').filter { it.isNotEmpty() }
                    if (parts.size == 3 && parts[0] == "Персонажи" && parts[2] == "Face") {
                        val match = faceName.matchEntire(name)
                        if (match != null) characterFaces[match.groupValues[1].uppercase()] = child.uri
                    }
                }
            }
        }
        return MediaSummary(images, videos, audio, other, folders, categories.toSortedMap(), characterFaces)
    }

    fun exportBackup(context: Context, rootUri: Uri, json: String): String {
        val resolver = context.contentResolver
        val root = DocumentFile.fromTreeUri(context, rootUri)
            ?: throw IllegalStateException("Папка Media недоступна")
        val backup = root.findFile("backup")?.also {
            require(it.isDirectory) { "Media/backup уже существует и не является папкой" }
        } ?: root.createDirectory("backup") ?: throw IllegalStateException("Не удалось создать Media/backup")
        val timestamp = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"))
        var suffix = 0
        var name: String
        do {
            name = if (suffix == 0) "save_1_$timestamp.json" else "save_1_${timestamp}_%03d.json".format(suffix)
            suffix++
        } while (backup.findFile(name) != null)
        val file = backup.createFile("application/json", name) ?: throw IllegalStateException("Не удалось создать файл резервной копии")
        try {
            resolver.openOutputStream(file.uri, "w")?.bufferedWriter(Charsets.UTF_8)?.use { it.write(json) }
                ?: throw IllegalStateException("Android не дал открыть файл резервной копии")
        } catch (error: Exception) {
            file.delete()
            throw error
        }
        return name
    }
}

