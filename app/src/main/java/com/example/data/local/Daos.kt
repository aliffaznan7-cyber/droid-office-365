package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.domain.model.SyncConflictState
import kotlinx.coroutines.flow.Flow

@Dao
interface DocumentDao {
    @Query("SELECT * FROM documents ORDER BY lastOpenedAt DESC")
    fun observeAllDocuments(): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE id = :id LIMIT 1")
    suspend fun getDocumentById(id: String): DocumentEntity?

    @Query("SELECT * FROM documents WHERE filePath = :path LIMIT 1")
    suspend fun getDocumentByPath(path: String): DocumentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDocument(document: DocumentEntity)

    @Update
    suspend fun updateDocument(document: DocumentEntity)

    @Query("UPDATE documents SET lastOpenedAt = :timestamp WHERE id = :id")
    suspend fun touchLastOpened(id: String, timestamp: Long)

    @Query("UPDATE documents SET syncConflictState = :state, lastModifiedAt = :modifiedAt WHERE id = :id")
    suspend fun updateSyncState(id: String, state: SyncConflictState, modifiedAt: Long)

    @Query("UPDATE documents SET isPinnedOffline = :pinned WHERE id = :id")
    suspend fun updatePinnedOffline(id: String, pinned: Boolean)

    @Query("DELETE FROM documents WHERE id = :id")
    suspend fun deleteDocumentById(id: String)

    @Query("SELECT * FROM pdf_metadata WHERE documentId = :documentId LIMIT 1")
    fun observePdfMetadata(documentId: String): Flow<PdfMetadataEntity?>

    @Query("SELECT * FROM pdf_metadata WHERE documentId = :documentId LIMIT 1")
    suspend fun getPdfMetadata(documentId: String): PdfMetadataEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPdfMetadata(metadata: PdfMetadataEntity)

    @Query("UPDATE pdf_metadata SET lastViewedPage = :page, lastZoomScale = :zoom WHERE documentId = :documentId")
    suspend fun updatePdfViewport(documentId: String, page: Int, zoom: Float)
}

@Dao
interface AnnotationDao {
    @Query("SELECT * FROM annotations WHERE documentId = :documentId ORDER BY createdAt ASC")
    fun observeAnnotationsForDocument(documentId: String): Flow<List<AnnotationEntity>>

    @Query("SELECT * FROM annotations WHERE documentId = :documentId AND isFlattened = 0 ORDER BY pageIndex ASC, createdAt ASC")
    suspend fun getUnflattenedAnnotations(documentId: String): List<AnnotationEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAnnotation(annotation: AnnotationEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(annotations: List<AnnotationEntity>)

    @Query("DELETE FROM annotations WHERE id = :annotationId")
    suspend fun deleteAnnotationById(annotationId: String)

    @Query("UPDATE annotations SET isFlattened = 1 WHERE documentId = :documentId")
    suspend fun markAllFlattened(documentId: String)

    @Query("DELETE FROM annotations WHERE documentId = :documentId AND isFlattened = 0")
    suspend fun clearUnflattenedForDocument(documentId: String)
}

@Dao
interface AutosaveDao {
    @Query("SELECT * FROM autosaves WHERE documentId = :documentId ORDER BY createdAt DESC LIMIT 12")
    fun observeAutosaves(documentId: String): Flow<List<AutosaveEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAutosave(autosave: AutosaveEntity)

    @Query("DELETE FROM autosaves WHERE documentId = :documentId AND id NOT IN (SELECT id FROM autosaves WHERE documentId = :documentId ORDER BY createdAt DESC LIMIT 8)")
    suspend fun pruneOldAutosaves(documentId: String)
}

@Dao
interface SignatureDao {
    @Query("SELECT * FROM signatures ORDER BY createdAt DESC")
    fun observeSignatures(): Flow<List<SignatureEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSignature(signature: SignatureEntity)

    @Query("DELETE FROM signatures WHERE id = :id")
    suspend fun deleteSignature(id: String)
}
