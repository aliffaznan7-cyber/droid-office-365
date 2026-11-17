package com.example.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.domain.model.DocumentFormat
import com.example.domain.model.PdfAnnotationType
import com.example.domain.model.SyncConflictState

@Entity(tableName = "documents")
data class DocumentEntity(
    @PrimaryKey val id: String,
    val title: String,
    val fileName: String,
    val filePath: String,
    val format: DocumentFormat,
    val fileSizeBytes: Long,
    val pageOrSheetCount: Int,
    val isPinnedOffline: Boolean = true,
    val syncConflictState: SyncConflictState = SyncConflictState.CLEAN,
    val department: String = "Architecture",
    val classification: String = "Technical",
    val checksumSha256: String = "",
    val lastOpenedAt: Long = System.currentTimeMillis(),
    val lastModifiedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "pdf_metadata",
    foreignKeys = [
        ForeignKey(
            entity = DocumentEntity::class,
            parentColumns = ["id"],
            childColumns = ["documentId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("documentId")]
)
data class PdfMetadataEntity(
    @PrimaryKey val documentId: String,
    val pdfVersion: String = "1.7",
    val pageCount: Int = 1,
    val title: String = "",
    val author: String = "",
    val subject: String = "",
    val creator: String = "",
    val lastViewedPage: Int = 0,
    val lastZoomScale: Float = 1.0f,
    val scrollOffsetY: Int = 0,
    val isEncrypted: Boolean = false,
    val hasUnsavedAnnotations: Boolean = false
)

@Entity(
    tableName = "annotations",
    foreignKeys = [
        ForeignKey(
            entity = DocumentEntity::class,
            parentColumns = ["id"],
            childColumns = ["documentId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("documentId"), Index(value = ["documentId", "pageIndex"])]
)
data class AnnotationEntity(
    @PrimaryKey val id: String,
    val documentId: String,
    val pageIndex: Int,
    val type: PdfAnnotationType,
    val colorArgb: Long,
    val strokeWidthNormalized: Float,
    val pointsJson: String,
    val boundsJson: String,
    val textContent: String,
    val fontSizeSp: Float,
    val author: String,
    val createdAt: Long,
    val isFlattened: Boolean = false
)

@Entity(
    tableName = "autosaves",
    foreignKeys = [
        ForeignKey(
            entity = DocumentEntity::class,
            parentColumns = ["id"],
            childColumns = ["documentId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("documentId")]
)
data class AutosaveEntity(
    @PrimaryKey val id: String,
    val documentId: String,
    val snapshotPath: String,
    val summary: String,
    val byteSize: Long,
    val createdAt: Long,
    val isRestored: Boolean = false
)

@Entity(tableName = "signatures")
data class SignatureEntity(
    @PrimaryKey val id: String,
    val label: String,
    val strokesJson: String,
    val colorArgb: Long,
    val createdAt: Long
)
