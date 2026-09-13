package ca.digitalcave.buddilive.mobile.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.google.gson.Gson

interface OfflineStore {
	fun saveCache(key: String, value: String)
	fun loadCache(key: String): String?
	fun deleteCachePrefix(prefix: String)
	fun upsertPendingTransaction(pendingTransaction: PendingTransaction)
	fun listPendingTransactions(): List<PendingTransaction>
	fun deletePendingTransaction(localUuid: String)

	fun forOwner(owner: String): OfflineStore = this

	fun <T> transaction(block: () -> T): T = block()
}

class AndroidOfflineStore(private val context: Context, databaseName: String = DATABASE_NAME) : SQLiteOpenHelper(
	context,
	databaseName,
	null,
	DATABASE_VERSION
), OfflineStore {

	private val gson = Gson()

	override fun forOwner(owner: String): OfflineStore {
		require(owner.matches(Regex("[a-f0-9]{64}")))
		return AndroidOfflineStore(context, "buddilive_$owner.db")
	}

	override fun <T> transaction(block: () -> T): T {
		val db = writableDatabase
		db.beginTransaction()
		return try {
			val result = block()
			db.setTransactionSuccessful()
			result
		}
		finally {
			db.endTransaction()
		}
	}

	override fun onCreate(db: SQLiteDatabase) {
		db.execSQL(
			"""
			create table offline_cache (
				cache_key text primary key not null,
				cache_value text not null,
				updated_at integer not null
			)
			""".trimIndent()
		)
		db.execSQL(
			"""
			create table pending_transactions (
				local_uuid text primary key not null,
				status text not null,
				date_iso text not null,
				description text not null,
				number text not null,
				amount text not null,
				from_id integer not null,
				to_id integer not null,
				memo text not null,
				created_at integer not null,
				modified_at integer not null,
				last_error text,
				attempt_count integer not null,
				sync_state text
			)
			""".trimIndent()
		)
	}

	override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
		if (oldVersion < 2) {
			db.execSQL("alter table pending_transactions add column sync_state text")
		}
	}

	override fun saveCache(key: String, value: String) {
		val values = ContentValues().apply {
			put(CACHE_KEY, key)
			put(CACHE_VALUE, value)
			put(UPDATED_AT, System.currentTimeMillis())
		}
		check(writableDatabase.insertWithOnConflict(OFFLINE_CACHE_TABLE, null, values, SQLiteDatabase.CONFLICT_REPLACE) != -1L) {
			"Unable to save offline data."
		}
	}

	override fun loadCache(key: String): String? {
		return readableDatabase.query(
			OFFLINE_CACHE_TABLE,
			arrayOf(CACHE_VALUE),
			"$CACHE_KEY = ?",
			arrayOf(key),
			null,
			null,
			null
		).use { cursor ->
			if (cursor.moveToFirst()) cursor.getString(0) else null
		}
	}

	override fun deleteCachePrefix(prefix: String) {
		writableDatabase.delete(OFFLINE_CACHE_TABLE, "$CACHE_KEY like ?", arrayOf("$prefix%"))
	}

	override fun upsertPendingTransaction(pendingTransaction: PendingTransaction) {
		val values = ContentValues().apply {
			put(LOCAL_UUID, pendingTransaction.localUuid)
			put(STATUS, pendingTransaction.status.name)
			put(DATE_ISO, pendingTransaction.dateIso)
			put(DESCRIPTION, pendingTransaction.description)
			put(NUMBER, pendingTransaction.number)
			put(AMOUNT, pendingTransaction.amount)
			put(FROM_ID, pendingTransaction.fromId)
			put(TO_ID, pendingTransaction.toId)
			put(MEMO, pendingTransaction.memo)
			put(CREATED_AT, pendingTransaction.createdAt)
			put(MODIFIED_AT, pendingTransaction.modifiedAt)
			put(LAST_ERROR, pendingTransaction.lastError)
			put(ATTEMPT_COUNT, pendingTransaction.attemptCount)
			put(SYNC_STATE, gson.toJson(pendingTransaction.sync))
		}
		check(writableDatabase.insertWithOnConflict(PENDING_TRANSACTIONS_TABLE, null, values, SQLiteDatabase.CONFLICT_REPLACE) != -1L) {
			"Unable to save this transaction on the device."
		}
	}

	override fun listPendingTransactions(): List<PendingTransaction> {
		return readableDatabase.query(
			PENDING_TRANSACTIONS_TABLE,
			null,
			null,
			null,
			null,
			null,
			"$CREATED_AT asc"
		).use { cursor ->
			buildList {
				while (cursor.moveToNext()) {
					add(cursor.toPendingTransaction())
				}
			}
		}
	}

	override fun deletePendingTransaction(localUuid: String) {
		writableDatabase.delete(PENDING_TRANSACTIONS_TABLE, "$LOCAL_UUID = ?", arrayOf(localUuid))
	}

	private fun Cursor.toPendingTransaction(): PendingTransaction {
		val status = runCatching {
			PendingTransactionStatus.valueOf(getString(getColumnIndexOrThrow(STATUS)))
		}.getOrDefault(PendingTransactionStatus.PENDING)
		return PendingTransaction(
			localUuid = getString(getColumnIndexOrThrow(LOCAL_UUID)),
			status = status,
			dateIso = getString(getColumnIndexOrThrow(DATE_ISO)),
			description = getString(getColumnIndexOrThrow(DESCRIPTION)),
			number = getString(getColumnIndexOrThrow(NUMBER)),
			amount = getString(getColumnIndexOrThrow(AMOUNT)),
			fromId = getInt(getColumnIndexOrThrow(FROM_ID)),
			toId = getInt(getColumnIndexOrThrow(TO_ID)),
			memo = getString(getColumnIndexOrThrow(MEMO)),
			createdAt = getLong(getColumnIndexOrThrow(CREATED_AT)),
			modifiedAt = getLong(getColumnIndexOrThrow(MODIFIED_AT)),
			lastError = getStringOrNull(getColumnIndexOrThrow(LAST_ERROR)),
			attemptCount = getInt(getColumnIndexOrThrow(ATTEMPT_COUNT)),
			sync = getStringOrNull(getColumnIndexOrThrow(SYNC_STATE))
				?.let { gson.fromJson(it, PendingSyncState::class.java) } ?: PendingSyncState()
		)
	}

	private fun Cursor.getStringOrNull(columnIndex: Int): String? {
		return if (isNull(columnIndex)) null else getString(columnIndex)
	}

	private companion object {
		const val DATABASE_NAME = "buddilive_offline.db"
		const val DATABASE_VERSION = 2
		const val OFFLINE_CACHE_TABLE = "offline_cache"
		const val PENDING_TRANSACTIONS_TABLE = "pending_transactions"
		const val CACHE_KEY = "cache_key"
		const val CACHE_VALUE = "cache_value"
		const val UPDATED_AT = "updated_at"
		const val LOCAL_UUID = "local_uuid"
		const val STATUS = "status"
		const val DATE_ISO = "date_iso"
		const val DESCRIPTION = "description"
		const val NUMBER = "number"
		const val AMOUNT = "amount"
		const val FROM_ID = "from_id"
		const val TO_ID = "to_id"
		const val MEMO = "memo"
		const val CREATED_AT = "created_at"
		const val MODIFIED_AT = "modified_at"
		const val LAST_ERROR = "last_error"
		const val ATTEMPT_COUNT = "attempt_count"
		const val SYNC_STATE = "sync_state"
	}
}
