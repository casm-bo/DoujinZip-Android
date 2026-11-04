package com.doujinzip.android.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.doujinzip.android.model.LibraryBook
import com.doujinzip.android.model.LibraryPage
import com.doujinzip.android.model.StorageLocation
import java.io.File
import java.util.zip.ZipInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LibraryScanner(
    private val context: Context,
    private val identityStore: LibraryIdentityStore = LibraryIdentityStore(context),
) {
    private val contentResolver = context.contentResolver
    private val archiveIndexes = ArchiveIndexCache(File(context.cacheDir, "archive-indexes"))
    suspend fun scan(locations: List<StorageLocation>): ScanResult = withContext(Dispatchers.IO) {
        val books = mutableListOf<LibraryBook>()
        val errors = mutableListOf<String>()
        locations.forEach { location ->
            runCatching { scanLocation(location, books) }
                .onFailure { errors += "${location.displayName}: ${it.message ?: "폴더를 읽을 수 없습니다."}" }
        }
        archiveIndexes.trim()
        ScanResult(books.distinctBy(LibraryBook::id), errors)
    }

    private fun scanLocation(location: StorageLocation, books: MutableList<LibraryBook>) {
        val treeUri = Uri.parse(location.uri)
        val rootId = DocumentsContract.getTreeDocumentId(treeUri)
        scanDirectory(treeUri, rootId, location, location.displayName, books)
    }

    private fun scanDirectory(
        treeUri: Uri,
        documentId: String,
        location: StorageLocation,
        directoryName: String,
        books: MutableList<LibraryBook>,
    ) {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, documentId)
        val children = queryChildren(childrenUri)
        val imagePages = children.asSequence()
            .filter { !it.isDirectory && isSupportedImage(it.mimeType, it.name) }
            .sortedWith(compareBy<DocumentEntry> { naturalSortKey(it.name) }.thenBy { it.name })
            .map { LibraryPage(it.uri.toString(), it.name) }
            .toList()

        if (imagePages.isNotEmpty()) {
            val parsedInfo = children.firstOrNull { !it.isDirectory && it.name.equals("info.txt", true) }
                ?.let { readInfo(it.uri) } ?: ParsedInfoTxt()
            val folderUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId).toString()
            val identity = identityStore.resolve(
                sourceKey = "folder:$folderUri",
                sourceUri = folderUri,
                relativePath = documentId.removePrefix(DocumentsContract.getTreeDocumentId(treeUri)).trim('/').ifEmpty { "." },
                parsedInfo = parsedInfo,
                modifiedAt = children.maxOfOrNull(DocumentEntry::modifiedAt) ?: 0L,
            )
            val info = identity.info
            books += LibraryBook(
                id = LibraryIdentityStore.stableFileId(identity.syncId),
                title = info.title ?: directoryName.ifBlank { location.displayName },
                locationUri = location.uri,
                locationName = location.displayName,
                folderUri = folderUri,
                pages = imagePages,
                modifiedAt = children.maxOfOrNull(DocumentEntry::modifiedAt) ?: 0L,
                metadata = info.metadata,
                syncId = identity.syncId,
            )
        }

        children.filter { !it.isDirectory && isArchive(it.name) }.forEach { archive ->
            val sidecar = children.firstOrNull { candidate ->
                !candidate.isDirectory &&
                    (candidate.name.equals("${archive.name}.info.txt", true) ||
                        candidate.name.equals("${archive.name.substringBeforeLast('.')}.info.txt", true))
            }
            scanArchive(archive, location, sidecar)?.let(books::add)
        }

        children.filter(DocumentEntry::isDirectory).forEach { child ->
            scanDirectory(treeUri, child.documentId, location, child.name, books)
        }
    }

    private fun scanArchive(
        entry: DocumentEntry,
        location: StorageLocation,
        sidecar: DocumentEntry?,
    ): LibraryBook? {
        val source = entry.uri.toString()
        val listing = archiveIndexes.read(source, entry.modifiedAt, entry.size) ?: readArchiveIndex(entry).also {
            archiveIndexes.write(source, entry.modifiedAt, entry.size, it)
        }
        val pageEntries = listing.pages
        var info = mergeParsedInfo(sidecar?.let { readInfo(it.uri) } ?: ParsedInfoTxt(), listing.info)
        val coverUri = listing.cover
        if (pageEntries.isEmpty()) return null
        val identity = identityStore.resolve(
            sourceKey = "archive:${entry.uri}", sourceUri = source, parsedInfo = info, modifiedAt = entry.modifiedAt,
            relativePath = DocumentsContract.getDocumentId(entry.uri).removePrefix(DocumentsContract.getTreeDocumentId(Uri.parse(location.uri))).trim('/'),
        )
        info = identity.info
        val pages = pageEntries.mapIndexed { index, name ->
            LibraryPage(uri = if (index == 0) coverUri else "", name = name, archiveUri = source, archiveEntry = name)
        }
        return LibraryBook(id = LibraryIdentityStore.stableFileId(identity.syncId),
            title = info.title ?: entry.name.substringBeforeLast('.'), locationUri = location.uri,
            locationName = location.displayName, folderUri = source, pages = pages, modifiedAt = entry.modifiedAt,
            metadata = info.metadata, syncId = identity.syncId, coverUriOverride = coverUri)
    }

    private fun readArchiveIndex(entry: DocumentEntry): ArchiveIndex {
        val pageEntries = mutableListOf<String>()
        var info = ParsedInfoTxt()
        contentResolver.openInputStream(entry.uri)?.buffered()?.use { input ->
            ZipInputStream(input).use { zip ->
                while (true) {
                    val item = zip.nextEntry ?: break
                    if (!item.isDirectory && isSupportedImage("", item.name)) {
                        pageEntries += item.name
                    } else if (
                        !item.isDirectory &&
                        item.name.substringAfterLast('/').equals("info.txt", true)
                    ) {
                        info = mergeParsedInfo(
                            preferred = info,
                            fallback = InfoTxtParser.parse(zip.readBytes().toString(Charsets.UTF_8)),
                        )
                    }
                    zip.closeEntry()
                }
            }
        }
        val sorted = pageEntries.sortedBy(::naturalSortKey)
        val cover = sorted.firstOrNull()?.let { firstPage -> readArchiveCover(entry, firstPage) }.orEmpty()
        return ArchiveIndex(sorted, info, cover)
    }

    private fun readArchiveCover(entry: DocumentEntry, imageName: String): String {
        val directory = File(context.cacheDir, "library-covers").apply(File::mkdirs)
        val fingerprint = archiveFingerprint(entry.uri.toString(), entry.modifiedAt, entry.size)
        val file = archiveCacheFile(directory, "$fingerprint-first-page", imageName)
        if (entry.modifiedAt <= 0 || entry.size <= 0 || !file.isFile || file.length() == 0L) {
            val temporary = File(directory, "${file.name}.tmp")
            try {
                requireNotNull(contentResolver.openInputStream(entry.uri)).buffered().use { input ->
                    ZipInputStream(input).use { zip ->
                        check(generateSequence { zip.nextEntry }.any { !it.isDirectory && it.name == imageName })
                        temporary.outputStream().buffered().use(zip::copyTo)
                    }
                }
                check(temporary.renameTo(file)) { "Cannot cache archive cover" }
            } finally { temporary.delete() }
        }
        return file.toURI().toString()
    }

    private fun readInfo(uri: Uri): ParsedInfoTxt = runCatching {
        contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { reader ->
            InfoTxtParser.parse(reader.readText())
        }
    }.getOrNull() ?: ParsedInfoTxt()

    private fun queryChildren(childrenUri: Uri): List<DocumentEntry> {
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            DocumentsContract.Document.COLUMN_SIZE,
        )
        return buildList {
            contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                val idIndex = cursor.getColumnIndexOrThrow(projection[0])
                val nameIndex = cursor.getColumnIndexOrThrow(projection[1])
                val mimeIndex = cursor.getColumnIndexOrThrow(projection[2])
                val modifiedIndex = cursor.getColumnIndexOrThrow(projection[3])
                val sizeIndex = cursor.getColumnIndexOrThrow(projection[4])
                while (cursor.moveToNext()) {
                    val documentId = cursor.getString(idIndex)
                    add(
                        DocumentEntry(
                            documentId = documentId,
                            name = cursor.getString(nameIndex).orEmpty(),
                            mimeType = cursor.getString(mimeIndex).orEmpty(),
                            modifiedAt = if (cursor.isNull(modifiedIndex)) 0L else cursor.getLong(modifiedIndex),
                            size = if (cursor.isNull(sizeIndex)) 0L else cursor.getLong(sizeIndex),
                            uri = DocumentsContract.buildDocumentUriUsingTree(childrenUri, documentId),
                        ),
                    )
                }
            }
        }
    }

    private fun isSupportedImage(mimeType: String, name: String): Boolean =
        mimeType.startsWith("image/") || name.substringAfterLast('.', "").lowercase() in IMAGE_EXTENSIONS

    private fun isArchive(name: String) = name.substringAfterLast('.', "").lowercase() in ARCHIVE_EXTENSIONS

    private data class DocumentEntry(
        val documentId: String,
        val name: String,
        val mimeType: String,
        val modifiedAt: Long,
        val size: Long,
        val uri: Uri,
    ) {
        val isDirectory: Boolean get() = mimeType == DocumentsContract.Document.MIME_TYPE_DIR
    }

    companion object {
        private val naturalParts = Regex("(\\d+|\\D+)")
        private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "avif")
        private val ARCHIVE_EXTENSIONS = setOf("zip", "cbz")

        internal fun naturalSortKey(value: String): String =
            naturalParts.findAll(value.lowercase()).map { part ->
                val text = part.value
                if (text.firstOrNull()?.isDigit() == true) text.padStart(20, '0') else text
            }.joinToString("\u0000")
    }
}

data class ScanResult(
    val books: List<LibraryBook>,
    val errors: List<String>,
)
