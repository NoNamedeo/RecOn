package com.example.recon.data

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File

class RecordRepository(
    private val dao: RecordDao,
    private val recordingsDirectory: File,
    private val metadataReader: AudioMetadataReader,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    val records: Flow<List<RecordEntity>> = dao.observeAll()

    suspend fun insert(record: RecordEntity): Long = withContext(ioDispatcher) {
        require(File(record.filePath).isFile) { "Il file finale non esiste" }
        dao.insert(record)
    }

    suspend fun rename(id: Long, title: String) = withContext(ioDispatcher) {
        val normalized = title.trim()
        require(normalized.isNotEmpty()) { "Il titolo non può essere vuoto" }
        check(dao.rename(id, normalized) == 1) { "Registrazione non trovata" }
    }

    suspend fun delete(record: RecordEntity) = withContext(ioDispatcher) {
        val file = File(record.filePath)
        if (file.exists() && !file.delete()) {
            error("Impossibile eliminare il file audio")
        }
        check(dao.deleteById(record.id) == 1) { "Registrazione non trovata" }
    }

    fun observeBetween(fromInclusive: Long, toInclusive: Long): Flow<List<RecordEntity>> =
        dao.observeBetween(fromInclusive, toInclusive)

    suspend fun reconcile() = withContext(ioDispatcher) {
        recordingsDirectory.mkdirs()
        val databaseRecords = dao.getAll()
        databaseRecords.filterNot { File(it.filePath).isFile }.forEach { dao.deleteById(it.id) }

        val knownPaths = databaseRecords.mapTo(mutableSetOf()) { it.filePath }
        val files = recordingsDirectory.listFiles().orEmpty()
        files.filter { it.isFile && it.name.contains(".partial.") }.forEach { it.delete() }
        files
            .filter { it.isFile && it.extension.equals("m4a", ignoreCase = true) }
            .filterNot { it.name.contains(".partial.") }
            .filterNot { it.absolutePath in knownPaths }
            .forEach { file ->
                val duration = runCatching { metadataReader.durationMillis(file) }.getOrDefault(0L)
                val endedAt = file.lastModified().takeIf { it > 0L } ?: System.currentTimeMillis()
                dao.insert(
                    RecordEntity(
                        title = file.nameWithoutExtension,
                        filePath = file.absolutePath,
                        startedAt = (endedAt - duration).coerceAtLeast(0L),
                        endedAt = endedAt,
                        durationMillis = duration,
                        createdAt = endedAt,
                    ),
                )
            }
    }
}
