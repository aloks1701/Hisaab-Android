package com.aloksharma.hisaab

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import kotlinx.coroutines.flow.Flow

enum class Category { FOOD, GROCERIES, FUEL, TRANSPORT, BILLS, SHOPPING, HEALTH, RENT, TRANSFER, INCOME, OTHER }

enum class TxnType { DEBIT, CREDIT, REFUND, UNKNOWN }

/** Money is integer paise everywhere. Never Float/Double — rounding errors compound in a ledger. */
@Entity(tableName = "transactions")
data class Transaction(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: TxnType,
    val amountPaise: Long,
    val merchant: String,
    val category: Category,
    val sourceText: String,
    val timestamp: Long,
    val needsReview: Boolean = false,
    /** Android package that posted the notification, so the row can show where it came from. */
    val sourcePackage: String? = null,
)

class Converters {
    @TypeConverter fun toType(v: String) = TxnType.valueOf(v)
    @TypeConverter fun fromType(v: TxnType) = v.name
    @TypeConverter fun toCategory(v: String) = Category.valueOf(v)
    @TypeConverter fun fromCategory(v: Category) = v.name
}

@Dao
interface TransactionDao {
    @Insert suspend fun insert(txn: Transaction): Long

    @Query("SELECT * FROM transactions ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<Transaction>>

    /** Dedup lookup: same amount + merchant inside a short window. */
    @Query(
        "SELECT COUNT(*) FROM transactions WHERE amountPaise = :amountPaise " +
            "AND merchant = :merchant AND timestamp BETWEEN :since AND :until"
    )
    suspend fun countSimilar(amountPaise: Long, merchant: String, since: Long, until: Long): Int
}

/** v1 -> v2 adds sourcePackage. Kept as a real migration rather than a destructive fallback:
 *  a ledger that silently loses rows on upgrade is worse than no ledger. */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE transactions ADD COLUMN sourcePackage TEXT")
    }
}

@Database(entities = [Transaction::class], version = 2, exportSchema = false)
@TypeConverters(Converters::class)
abstract class HisaabDatabase : RoomDatabase() {
    abstract fun transactions(): TransactionDao

    companion object {
        @Volatile private var instance: HisaabDatabase? = null

        fun get(context: Context): HisaabDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext, HisaabDatabase::class.java, "hisaab.db"
            ).addMigrations(MIGRATION_1_2).build().also { instance = it }
        }
    }
}

/** Duplicate window: a bank and a UPI app often both notify the same payment seconds apart. */
const val DEDUP_WINDOW_MS = 2 * 60 * 1000L

class LedgerRepository(private val dao: TransactionDao) {

    fun observeAll(): Flow<List<Transaction>> = dao.observeAll()

    /**
     * The single entry point for raw notification text: parse, categorize, dedupe, store.
     * Both the live listener and the debug simulator go through here so the demo exercises
     * the real pipeline. Returns null when the text isn't a payment or is a duplicate.
     */
    suspend fun ingest(
        text: String,
        timestamp: Long = System.currentTimeMillis(),
        sourcePackage: String? = null,
    ): Long? {
        val parsed = NotificationParser.parse(text) ?: return null
        return record(
            Transaction(
                type = parsed.type,
                amountPaise = parsed.amountPaise,
                merchant = parsed.merchant,
                category = parsed.category,
                // Only the bank's sender id is kept, never the message. The raw text holds the
                // payee's name, the account's last four digits and a reference number, and the
                // only thing ever read back out of it is which bank sent it.
                sourceText = SourceApp.senderId(text).orEmpty(),
                timestamp = timestamp,
                needsReview = parsed.needsReview,
                sourcePackage = sourcePackage,
            )
        )
    }

    /** Returns the new row id, or null when this looks like a duplicate of one already stored. */
    suspend fun record(txn: Transaction): Long? {
        val dupes = dao.countSimilar(
            txn.amountPaise, txn.merchant,
            txn.timestamp - DEDUP_WINDOW_MS, txn.timestamp + DEDUP_WINDOW_MS,
        )
        return if (dupes > 0) null else dao.insert(txn)
    }
}

data class Summary(
    val spentPaise: Long,
    val receivedPaise: Long,
    val byCategory: Map<Category, Long>,
) {
    val netPaise: Long get() = receivedPaise - spentPaise
}

/**
 * Aggregates in Kotlin rather than SQL.
 * ponytail: O(n) over the whole table on every emission — fine for a personal ledger,
 * move to SQL SUM/GROUP BY queries if this ever holds more than a few thousand rows.
 */
fun summarize(txns: List<Transaction>): Summary {
    var spent = 0L
    var received = 0L
    val byCategory = mutableMapOf<Category, Long>()
    for (t in txns) {
        when (t.type) {
            TxnType.DEBIT -> {
                spent += t.amountPaise
                byCategory[t.category] = (byCategory[t.category] ?: 0L) + t.amountPaise
            }
            TxnType.CREDIT, TxnType.REFUND -> received += t.amountPaise
            TxnType.UNKNOWN -> Unit
        }
    }
    return Summary(spent, received, byCategory)
}

/** "₹1,589" — Indian digit grouping, paise shown only when non-zero. */
fun formatPaise(paise: Long): String {
    val negative = paise < 0
    val abs = if (negative) -paise else paise
    val rupees = abs / 100
    val remainder = (abs % 100).toInt()
    val digits = rupees.toString()
    val grouped = if (digits.length <= 3) digits else {
        val last3 = digits.takeLast(3)
        val rest = digits.dropLast(3)
        val chunks = mutableListOf<String>()
        var i = rest.length
        while (i > 0) {
            val start = maxOf(0, i - 2)
            chunks.add(0, rest.substring(start, i))
            i = start
        }
        chunks.joinToString(",") + "," + last3
    }
    val tail = if (remainder == 0) "" else ".%02d".format(remainder)
    return (if (negative) "-₹" else "₹") + grouped + tail
}
