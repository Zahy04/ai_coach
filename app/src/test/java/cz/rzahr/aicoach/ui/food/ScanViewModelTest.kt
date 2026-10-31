package cz.rzahr.aicoach.ui.food

import cz.rzahr.aicoach.data.db.dao.FoodEntryDao
import cz.rzahr.aicoach.data.db.dao.ScannedProductDao
import cz.rzahr.aicoach.data.db.entity.FoodEntryEntity
import cz.rzahr.aicoach.data.db.entity.ScannedProductEntity
import cz.rzahr.aicoach.data.repo.FoodRepository
import cz.rzahr.aicoach.llm.OpenFoodFactsClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Ručně uložené produkty pod kódem (skener, issue #1) – bez sítě, lokální nález má přednost před OFF. */
@OptIn(ExperimentalCoroutinesApi::class)
class ScanViewModelTest {

    private val code = "8592084422868"
    private val products = FakeScannedProductDao()
    private val food = FakeFoodEntryDao()
    private lateinit var viewModel: ScanViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        viewModel = ScanViewModel(
            OpenFoodFactsClient(OkHttpClient(), Json { ignoreUnknownKeys = true }),
            FoodRepository(food),
            products
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `ulozeny produkt se najde lokalne a gramaz ho zapise`() {
        products.store[code] = ScannedProductEntity(code, "Corny Big Banana Vanilla", 400, 30.0, 40.0, 12.0)

        viewModel.onBarcode(code)
        val product = viewModel.product.value
        assertNotNull(product)
        assertTrue(product!!.local)
        assertEquals("Corny Big Banana Vanilla", product.nutrition.productName)

        viewModel.saveGrams(50)
        val entry = food.inserted.single()
        assertEquals("Corny Big Banana Vanilla", entry.name)
        assertEquals(200, entry.calories)
        assertEquals(15.0, entry.proteinG!!, 0.001)
        assertEquals(FoodEntryEntity.SOURCE_SCAN, entry.source)
    }

    @Test
    fun `uprava ulozeneho produktu predvyplni a prepise hodnoty`() {
        products.store[code] = ScannedProductEntity(code, "Corny", 400, 30.0, 40.0, 12.0)
        viewModel.onBarcode(code)

        viewModel.startManualEntry()
        val entry = viewModel.manualEntry.value
        assertEquals(code, entry?.code)
        assertEquals("Corny", entry?.initial?.productName)
        assertNull(viewModel.product.value)

        viewModel.saveManual("Corny Big Banana Vanilla", null, 30.0, 40.0, 10.0)
        val saved = products.store.getValue(code)
        assertEquals("Corny Big Banana Vanilla", saved.name)
        assertEquals(370, saved.caloriesPer100g) // 30*4 + 40*4 + 10*9
        assertNull(viewModel.manualEntry.value)
        assertEquals("Corny Big Banana Vanilla", viewModel.product.value?.nutrition?.productName)
    }

    @Test
    fun `necislene kody skener ignoruje`() {
        viewModel.onBarcode("https://example.com/p/1")
        assertNull(viewModel.product.value)
        assertTrue(!viewModel.lookingUp.value)
    }

    private class FakeScannedProductDao : ScannedProductDao {
        val store = mutableMapOf<String, ScannedProductEntity>()
        override suspend fun get(code: String) = store[code]
        override suspend fun upsert(product: ScannedProductEntity) {
            store[product.code] = product
        }
    }

    private class FakeFoodEntryDao : FoodEntryDao {
        val inserted = mutableListOf<FoodEntryEntity>()
        override fun observeAllDesc(): Flow<List<FoodEntryEntity>> = emptyFlow()
        override fun observeCaloriesSince(since: Long): Flow<Int> = emptyFlow()
        override fun observeProteinSince(since: Long): Flow<Double> = emptyFlow()
        override suspend fun since(since: Long): List<FoodEntryEntity> = inserted
        override suspend fun insert(entry: FoodEntryEntity): Long {
            inserted += entry
            return inserted.size.toLong()
        }
        override suspend fun update(
            id: Long,
            name: String,
            calories: Int?,
            proteinG: Double?,
            carbsG: Double?,
            fatG: Double?,
            grams: Int?
        ) = Unit
        override suspend fun deleteById(id: Long) = Unit
    }
}
