package com.example.recon.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RecordDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(record: RecordEntity): Long

    @Query("SELECT * FROM records ORDER BY startedAt DESC")
    fun observeAll(): Flow<List<RecordEntity>>

    @Query("SELECT * FROM records ORDER BY startedAt DESC")
    suspend fun getAll(): List<RecordEntity>

    @Query("SELECT * FROM records WHERE startedAt BETWEEN :fromInclusive AND :toInclusive ORDER BY startedAt DESC")
    fun observeBetween(fromInclusive: Long, toInclusive: Long): Flow<List<RecordEntity>>

    @Query("SELECT * FROM records WHERE filePath = :filePath LIMIT 1")
    suspend fun findByPath(filePath: String): RecordEntity?

    @Query("UPDATE records SET title = :title WHERE id = :id")
    suspend fun rename(id: Long, title: String): Int

    @Query("DELETE FROM records WHERE id = :id")
    suspend fun deleteById(id: Long): Int
}
