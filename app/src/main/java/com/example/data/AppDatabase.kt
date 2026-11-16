package com.example.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "desk_state")
data class DeskStateEntity(
    @PrimaryKey val key: String,
    val jsonValue: String,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "broker_records")
data class BrokerRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestampIst: String,
    val date: String,
    val fingerprint: String,
    val symbol: String,
    val exchange: String,
    val side: String,
    val quantity: Int,
    val product: String,
    val price: Double,
    val stopPrice: Double?,
    val targetPrice: Double?,
    val chargesInr: Double,
    val paper: Boolean,
    val status: String,
    val confirmationReply: String,
    val plainSummary: String
)

@Entity(tableName = "alert_rules")
data class AlertRuleEntity(
    @PrimaryKey val id: String,
    val symbol: String,
    val exchange: String,
    val typeCode: String,
    val value: Double?,
    val timeHm: String?,
    val label: String,
    val armed: Boolean,
    val lastTriggeredDate: String?,
    val lastTriggeredInfo: String?
)

@Entity(tableName = "followed_investors")
data class FollowedInvestorEntity(
    @PrimaryKey val name: String,
    val addedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "agent_cycles")
data class AgentCycleEntity(
    @PrimaryKey val cycleId: String,
    val startedAtIst: String,
    val selectedSymbol: String?,
    val outcome: String,
    val succeeded: Boolean,
    val snapshotJson: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Dao
interface DeskDao {
    @Query("SELECT * FROM desk_state WHERE `key` = :key LIMIT 1")
    fun observeState(key: String): Flow<DeskStateEntity?>

    @Query("SELECT * FROM desk_state WHERE `key` = :key LIMIT 1")
    suspend fun getState(key: String): DeskStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertState(entity: DeskStateEntity)

    @Query("SELECT * FROM broker_records ORDER BY id DESC")
    fun observeBrokerRecords(): Flow<List<BrokerRecordEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBrokerRecord(record: BrokerRecordEntity)

    @Query("DELETE FROM broker_records")
    suspend fun clearBrokerRecords()

    @Query("SELECT * FROM alert_rules ORDER BY symbol, typeCode")
    fun observeAlerts(): Flow<List<AlertRuleEntity>>

    @Query("SELECT * FROM alert_rules")
    suspend fun getAlertsSnapshot(): List<AlertRuleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAlerts(alerts: List<AlertRuleEntity>)

    @Query("DELETE FROM alert_rules WHERE id = :id")
    suspend fun deleteAlert(id: String)

    @Query("DELETE FROM alert_rules")
    suspend fun clearAlerts()

    @Query("SELECT * FROM followed_investors ORDER BY name ASC")
    fun observeFollowedInvestors(): Flow<List<FollowedInvestorEntity>>

    @Query("SELECT * FROM followed_investors ORDER BY name ASC")
    suspend fun getFollowedInvestorsSnapshot(): List<FollowedInvestorEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertInvestor(investor: FollowedInvestorEntity)

    @Query("DELETE FROM followed_investors WHERE name = :name")
    suspend fun deleteInvestor(name: String)

    @Query("SELECT * FROM agent_cycles ORDER BY createdAt DESC LIMIT 20")
    fun observeAgentCycles(): Flow<List<AgentCycleEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAgentCycle(cycle: AgentCycleEntity)

    @Query("DELETE FROM agent_cycles")
    suspend fun clearAgentCycles()
}

@Database(
    entities = [
        DeskStateEntity::class,
        BrokerRecordEntity::class,
        AlertRuleEntity::class,
        FollowedInvestorEntity::class,
        AgentCycleEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun deskDao(): DeskDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "tradedesk_in.db"
                ).fallbackToDestructiveMigration(true).build().also { INSTANCE = it }
            }
        }
    }
}
