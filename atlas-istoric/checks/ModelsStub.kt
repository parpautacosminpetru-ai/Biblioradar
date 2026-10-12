package ro.atlasistoric.data
// Test-only stand-ins: validate pure temporal, nesting and OCR logic without Android SDK.
data class HistoricalEntity(val id: String, val name: String, val entityType: String, val aliases: String = "")
data class HistoricalTopic(val id: String, val name: String, val parentId: String? = null)
data class EntityRelation(val fromEntityId: String, val toEntityId: String, val relationType: String, val yearStart: Int? = null, val yearEnd: Int? = null)
