package com.fabxdi.dibadge.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface DocDao {
    @Query("SELECT * FROM user_documents ORDER BY createdAt DESC")
    fun getAllDocuments(): Flow<List<DocEntity>>

    @Query("SELECT * FROM user_documents WHERE id = :id LIMIT 1")
    suspend fun getDocumentById(id: String): DocEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDocument(doc: DocEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllDocuments(docs: List<DocEntity>)

    @Update
    suspend fun updateDocument(doc: DocEntity)

    @Delete
    suspend fun deleteDocument(doc: DocEntity)

    @Query("DELETE FROM user_documents WHERE id IN (:ids)")
    suspend fun deleteDocumentsByIds(ids: List<String>)

    @Query("DELETE FROM user_documents")
    suspend fun deleteAllDocuments()
}
