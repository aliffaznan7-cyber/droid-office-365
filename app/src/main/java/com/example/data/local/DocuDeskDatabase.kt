package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        DocumentEntity::class,
        PdfMetadataEntity::class,
        AnnotationEntity::class,
        AutosaveEntity::class,
        SignatureEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(DocuDeskConverters::class)
abstract class DocuDeskDatabase : RoomDatabase() {
    abstract fun documentDao(): DocumentDao
    abstract fun annotationDao(): AnnotationDao
    abstract fun autosaveDao(): AutosaveDao
    abstract fun signatureDao(): SignatureDao

    companion object {
        @Volatile
        private var INSTANCE: DocuDeskDatabase? = null

        fun getInstance(context: Context): DocuDeskDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    DocuDeskDatabase::class.java,
                    "docudesk_workspace.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
