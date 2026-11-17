package com.example.data.local

import androidx.room.TypeConverter
import com.example.domain.model.DocumentFormat
import com.example.domain.model.NormalizedPoint
import com.example.domain.model.NormalizedRect
import com.example.domain.model.PdfAnnotationType
import com.example.domain.model.SyncConflictState
import org.json.JSONArray
import org.json.JSONObject

class DocuDeskConverters {
    @TypeConverter
    fun fromDocumentFormat(value: DocumentFormat): String = value.name

    @TypeConverter
    fun toDocumentFormat(value: String): DocumentFormat =
        runCatching { DocumentFormat.valueOf(value) }.getOrDefault(DocumentFormat.PDF)

    @TypeConverter
    fun fromSyncState(value: SyncConflictState): String = value.name

    @TypeConverter
    fun toSyncState(value: String): SyncConflictState =
        runCatching { SyncConflictState.valueOf(value) }.getOrDefault(SyncConflictState.CLEAN)

    @TypeConverter
    fun fromAnnotationType(value: PdfAnnotationType): String = value.name

    @TypeConverter
    fun toAnnotationType(value: String): PdfAnnotationType =
        runCatching { PdfAnnotationType.valueOf(value) }.getOrDefault(PdfAnnotationType.INK)

    companion object {
        fun pointsToJson(points: List<NormalizedPoint>): String {
            val array = JSONArray()
            points.forEach { pt ->
                array.put(
                    JSONObject().apply {
                        put("x", pt.x.toDouble())
                        put("y", pt.y.toDouble())
                        put("p", pt.pressure.toDouble())
                    }
                )
            }
            return array.toString()
        }

        fun jsonToPoints(json: String): List<NormalizedPoint> {
            if (json.isBlank()) return emptyList()
            return runCatching {
                val array = JSONArray(json)
                List(array.length()) { index ->
                    val obj = array.getJSONObject(index)
                    NormalizedPoint(
                        x = obj.optDouble("x", 0.0).toFloat(),
                        y = obj.optDouble("y", 0.0).toFloat(),
                        pressure = obj.optDouble("p", 1.0).toFloat()
                    )
                }
            }.getOrDefault(emptyList())
        }

        fun strokesToJson(strokes: List<List<NormalizedPoint>>): String {
            val outer = JSONArray()
            strokes.forEach { stroke ->
                val inner = JSONArray()
                stroke.forEach { pt ->
                    inner.put(
                        JSONObject().apply {
                            put("x", pt.x.toDouble())
                            put("y", pt.y.toDouble())
                            put("p", pt.pressure.toDouble())
                        }
                    )
                }
                outer.put(inner)
            }
            return outer.toString()
        }

        fun jsonToStrokes(json: String): List<List<NormalizedPoint>> {
            if (json.isBlank()) return emptyList()
            return runCatching {
                val outer = JSONArray(json)
                List(outer.length()) { sIdx ->
                    val inner = outer.getJSONArray(sIdx)
                    List(inner.length()) { pIdx ->
                        val obj = inner.getJSONObject(pIdx)
                        NormalizedPoint(
                            x = obj.optDouble("x", 0.0).toFloat(),
                            y = obj.optDouble("y", 0.0).toFloat(),
                            pressure = obj.optDouble("p", 1.0).toFloat()
                        )
                    }
                }
            }.getOrDefault(emptyList())
        }

        fun rectToJson(rect: NormalizedRect?): String {
            if (rect == null) return ""
            return JSONObject().apply {
                put("l", rect.left.toDouble())
                put("t", rect.top.toDouble())
                put("r", rect.right.toDouble())
                put("b", rect.bottom.toDouble())
            }.toString()
        }

        fun jsonToRect(json: String): NormalizedRect? {
            if (json.isBlank()) return null
            return runCatching {
                val obj = JSONObject(json)
                NormalizedRect(
                    left = obj.getDouble("l").toFloat(),
                    top = obj.getDouble("t").toFloat(),
                    right = obj.getDouble("r").toFloat(),
                    bottom = obj.getDouble("b").toFloat()
                )
            }.getOrNull()
        }
    }
}
