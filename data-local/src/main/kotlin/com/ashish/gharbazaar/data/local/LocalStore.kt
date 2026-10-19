package com.ashish.gharbazaar.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import com.ashish.gharbazaar.domain.Diet
import com.ashish.gharbazaar.domain.GroceryItem
import com.ashish.gharbazaar.domain.GroceryList
import com.ashish.gharbazaar.domain.Household
import com.ashish.gharbazaar.domain.Ingredient
import com.ashish.gharbazaar.domain.Meal
import com.ashish.gharbazaar.domain.MealPlan
import com.ashish.gharbazaar.domain.StoredProfile
import com.ashish.gharbazaar.domain.Substitution
import com.ashish.gharbazaar.domain.SyncOperation
import com.ashish.gharbazaar.domain.SyncState
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

private val Context.dataStore by preferencesDataStore(name = "ghar_bazaar")

@Entity(tableName = "records")
data class RecordEntity(
    @PrimaryKey val key: String,
    val json: String,
)

@Entity(tableName = "sync_ops")
data class SyncOpEntity(
    @PrimaryKey val idempotencyKey: String,
    val entityType: String,
    val entityVersion: Long,
    val timestampEpochMs: Long,
    val payload: String,
    val state: String,
    val retryCount: Int,
)

@Dao
interface RecordDao {
    @Query("SELECT * FROM records WHERE `key` = :key LIMIT 1")
    suspend fun get(key: String): RecordEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(entity: RecordEntity)

    @Query("SELECT * FROM sync_ops WHERE state != 'ACKNOWLEDGED'")
    suspend fun pendingOps(): List<SyncOpEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putOp(entity: SyncOpEntity)
}

@Database(entities = [RecordEntity::class, SyncOpEntity::class], version = 1, exportSchema = false)
abstract class GharDatabase : RoomDatabase() {
    abstract fun records(): RecordDao
}

class LocalRepository(context: Context) {
    private val appContext = context.applicationContext
    private val database = Room.databaseBuilder(appContext, GharDatabase::class.java, "ghar-bazaar.db").build()
    private val dao = database.records()

    suspend fun installationId(): String {
        val key = stringPreferencesKey("installation_id")
        val current = appContext.dataStore.data.first()[key]
        if (current != null) return current
        val created = UUID.randomUUID().toString()
        appContext.dataStore.edit { it[key] = created }
        return created
    }

    suspend fun language(): String =
        appContext.dataStore.data.first()[stringPreferencesKey("language")] ?: "en"

    suspend fun setLanguage(locale: String) {
        appContext.dataStore.edit { it[stringPreferencesKey("language")] = locale }
    }

    suspend fun agentEnabled(): Boolean =
        appContext.dataStore.data.first()[booleanPreferencesKey("agent_backed_flows")] ?: false

    suspend fun saveProfile(profile: StoredProfile) {
        dao.put(RecordEntity("profile", profile.toJson()))
        setLanguage(profile.household.language)
    }

    suspend fun loadProfile(): StoredProfile? = dao.get("profile")?.json?.let(::profileFromJson)

    suspend fun savePlan(plan: MealPlan) {
        dao.put(RecordEntity("plan", plan.toJson()))
    }

    suspend fun loadPlan(): MealPlan? = dao.get("plan")?.json?.let(::planFromJson)

    suspend fun saveGrocery(list: GroceryList) {
        dao.put(RecordEntity("grocery", list.toJson()))
    }

    suspend fun loadGrocery(): GroceryList? = dao.get("grocery")?.json?.let(::groceryFromJson)

    suspend fun enqueue(operation: SyncOperation) {
        dao.putOp(
            SyncOpEntity(
                operation.idempotencyKey,
                operation.entityType,
                operation.entityVersion,
                operation.timestampEpochMs,
                operation.payload,
                operation.state.name,
                operation.retryCount,
            ),
        )
    }

    suspend fun pendingOperations(): List<SyncOperation> = dao.pendingOps().map { entity ->
        SyncOperation(
            entity.idempotencyKey,
            entity.entityType,
            entity.entityVersion,
            entity.timestampEpochMs,
            entity.payload,
            SyncState.valueOf(entity.state),
            entity.retryCount,
        )
    }
}

private fun StoredProfile.toJson(): String = JSONObject()
    .put("installationId", installationId)
    .put("household", household.toJson())
    .toString()

private fun profileFromJson(json: String): StoredProfile {
    val root = JSONObject(json)
    return StoredProfile(householdFromJson(root.getJSONObject("household")), root.getString("installationId"))
}

private fun Household.toJson(): JSONObject = JSONObject()
    .put("id", id)
    .put("name", name)
    .put("memberCount", memberCount)
    .put("region", region)
    .put("weeklyBudgetInr", weeklyBudgetInr)
    .put("diet", diet.name)
    .put("allergies", JSONArray(allergies.toList()))
    .put("exclusions", JSONArray(exclusions.toList()))
    .put("language", language)
    .put("members", JSONArray(members))

private fun householdFromJson(json: JSONObject) = Household(
    id = json.getString("id"),
    name = json.getString("name"),
    memberCount = json.getInt("memberCount"),
    region = json.getString("region"),
    weeklyBudgetInr = json.getInt("weeklyBudgetInr"),
    diet = Diet.valueOf(json.getString("diet")),
    allergies = json.getJSONArray("allergies").toSet(),
    exclusions = json.getJSONArray("exclusions").toSet(),
    language = json.getString("language"),
    members = json.getJSONArray("members").toList(),
)

private fun MealPlan.toJson(): String = JSONObject()
    .put("id", id)
    .put("days", days)
    .put("estimatedCostInr", estimatedCostInr)
    .put("budgetInr", budgetInr)
    .put("currency", currency)
    .put("note", note ?: JSONObject.NULL)
    .put("meals", JSONArray(meals.map { it.toJson() }))
    .toString()

private fun Meal.toJson(): JSONObject = JSONObject()
    .put("id", id)
    .put("day", day)
    .put("slot", slot)
    .put("recipeId", recipeId)
    .put("name", name)
    .put("costInr", costInr)
    .put("ingredients", JSONArray(ingredients.map { it.toJson() }))
    .put("substitution", substitution?.toJson() ?: JSONObject.NULL)

private fun Ingredient.toJson(): JSONObject = JSONObject()
    .put("id", id)
    .put("name", name)
    .put("quantity", quantity)
    .put("unit", unit)
    .put("diet", diet)
    .put("allergens", JSONArray(allergens.toList()))
    .put("jainRestricted", jainRestricted)
    .put("aisle", aisle)

private fun Substitution.toJson(): JSONObject = JSONObject()
    .put("id", id)
    .put("ingredientId", ingredientId)
    .put("replacementId", replacementId)
    .put("replacementName", replacementName)
    .put("reason", reason)
    .put("accepted", accepted ?: JSONObject.NULL)

private fun planFromJson(json: String): MealPlan {
    val root = JSONObject(json)
    val meals = root.getJSONArray("meals")
    return MealPlan(
        id = root.getString("id"),
        days = root.getInt("days"),
        meals = (0 until meals.length()).map { mealFromJson(meals.getJSONObject(it)) },
        estimatedCostInr = root.getInt("estimatedCostInr"),
        budgetInr = root.getInt("budgetInr"),
        currency = root.getString("currency"),
        note = if (root.isNull("note")) null else root.getString("note"),
    )
}

private fun mealFromJson(json: JSONObject): Meal {
    val ingredients = json.getJSONArray("ingredients")
    return Meal(
        id = json.getString("id"),
        day = json.getInt("day"),
        slot = json.getString("slot"),
        recipeId = json.getString("recipeId"),
        name = json.getString("name"),
        costInr = json.getInt("costInr"),
        ingredients = (0 until ingredients.length()).map { ingredientFromJson(ingredients.getJSONObject(it)) },
        substitution = if (json.isNull("substitution")) null else substitutionFromJson(json.getJSONObject("substitution")),
    )
}

private fun ingredientFromJson(json: JSONObject) = Ingredient(
    id = json.getString("id"),
    name = json.getString("name"),
    quantity = json.getDouble("quantity"),
    unit = json.getString("unit"),
    diet = json.getString("diet"),
    allergens = json.getJSONArray("allergens").toSet(),
    jainRestricted = json.getBoolean("jainRestricted"),
    aisle = json.getString("aisle"),
)

private fun substitutionFromJson(json: JSONObject) = Substitution(
    id = json.getString("id"),
    ingredientId = json.getString("ingredientId"),
    replacementId = json.getString("replacementId"),
    replacementName = json.getString("replacementName"),
    reason = json.getString("reason"),
    accepted = if (json.isNull("accepted")) null else json.getBoolean("accepted"),
)

private fun GroceryList.toJson(): String = JSONObject()
    .put("id", id)
    .put("servingsNote", servingsNote ?: JSONObject.NULL)
    .put("items", JSONArray(items.map { item ->
        JSONObject()
            .put("id", item.id)
            .put("name", item.name)
            .put("quantity", item.quantity)
            .put("unit", item.unit)
            .put("aisle", item.aisle)
            .put("purchased", item.purchased)
            .put("note", item.note)
    }))
    .toString()

private fun groceryFromJson(json: String): GroceryList {
    val root = JSONObject(json)
    val items = root.getJSONArray("items")
    return GroceryList(
        id = root.getString("id"),
        servingsNote = if (root.isNull("servingsNote")) null else root.getString("servingsNote"),
        items = (0 until items.length()).map { index ->
            val item = items.getJSONObject(index)
            GroceryItem(
                item.getString("id"),
                item.getString("name"),
                item.getDouble("quantity"),
                item.getString("unit"),
                item.getString("aisle"),
                item.getBoolean("purchased"),
                item.getString("note"),
            )
        },
    )
}

private fun JSONArray.toSet(): Set<String> = (0 until length()).map { getString(it) }.toSet()

private fun JSONArray.toList(): List<String> = (0 until length()).map { getString(it) }
