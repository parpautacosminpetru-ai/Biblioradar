package ro.atlasistoric.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ro.atlasistoric.data.HistoricalEntity

class GeographySearchTest {
    private val places = listOf(
        HistoricalEntity(id = "trans", name = "Transilvania", entityType = "PLACE",
            aliases = "Ardeal; Siebenbürgen; Erdély"),
        HistoricalEntity(id = "rome-modern", name = "Roma", entityType = "PLACE",
            aliases = "Rome"),
        HistoricalEntity(id = "rome-old", name = "Roma", entityType = "PLACE",
            description = "Centru antic, păstrat ca identificator distinct"),
        HistoricalEntity(id = "buc", name = "București", entityType = "PLACE"),
        HistoricalEntity(id = "person", name = "Roma", entityType = "PERSON")
    )

    @Test fun searchesHistoricalNamesAndRomanianDiacritics() {
        assertEquals("trans", lookupGeographicPlaces(places, "Siebenburgen").matches.single().id)
        assertEquals("trans", lookupGeographicPlaces(places, "erdely").matches.single().id)
        assertEquals("buc", lookupGeographicPlaces(places, "BUCURESTI").matches.single().id)
        assertEquals("trans", lookupGeographicPlaces(places, "ardEAL").matches.single().id)
    }

    @Test fun twoPlacesWithSameNameStayDistinctAndActorsAreExcluded() {
        val results = lookupGeographicPlaces(places, "Roma")
        assertEquals(2, results.count)
        assertEquals(setOf("rome-old", "rome-modern"), results.matches.map { it.id }.toSet())
    }

    @Test fun smallVisibleListDoesNotRestrictSearchUniverse() {
        val results = lookupGeographicPlaces(places, "", maxVisible = 1)
        assertEquals(4, results.count)
        assertEquals(1, results.matches.size)
        assertTrue(lookupGeographicPlaces(places, "București").matches.isNotEmpty())
    }

    @Test fun selectedRegionStaysFirstAmongAllSearchResults() {
        val result = lookupGeographicPlaces(places, "", maxVisible = 4,
            selectedIds = setOf("trans"))
        assertEquals("trans", result.matches.first().id)
    }

    @Test(expected = IllegalArgumentException::class)
    fun zeroVisibleItemsCannotHideAllSearchResults() {
        lookupGeographicPlaces(places, "", maxVisible = 0)
    }
}
