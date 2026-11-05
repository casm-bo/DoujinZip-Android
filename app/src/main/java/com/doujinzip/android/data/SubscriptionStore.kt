package com.doujinzip.android.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.doujinzip.android.model.SearchSubscription
import com.doujinzip.android.model.GallerySummary
import com.doujinzip.android.model.subscriptionNewIds
import com.doujinzip.android.security.BrowserPreferenceStore
import java.util.UUID
import org.json.JSONObject

data class SubscriptionRecord(val value: SearchSubscription, val mutationId: String?, val conflict: SearchSubscription?,
    val baseline: Long?, val checkedAt: Long, val error: String?)
data class SubscriptionNotice(val galleryId: Long, val title: String, val artist: String, val thumbnail: String?,
    val read: Boolean, val subscriptions: List<String>)

class SubscriptionStore internal constructor(context: Context, databaseName: String? = "subscriptions.db") : SQLiteOpenHelper(context, databaseName, null, 2) {
    val changes = kotlinx.coroutines.flow.MutableStateFlow(0L)
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE subscriptions(scope TEXT NOT NULL,id TEXT NOT NULL,payload TEXT NOT NULL,mutation TEXT,conflict TEXT,baseline INTEGER,checked INTEGER NOT NULL DEFAULT 0,error TEXT,PRIMARY KEY(scope,id))")
        db.execSQL("CREATE TABLE notices(scope TEXT NOT NULL,gallery INTEGER NOT NULL,title TEXT NOT NULL,artist TEXT NOT NULL,thumbnail TEXT,details TEXT,PRIMARY KEY(scope,gallery))")
        db.execSQL("CREATE TABLE matches(scope TEXT NOT NULL,gallery INTEGER NOT NULL,subscription TEXT NOT NULL,PRIMARY KEY(scope,gallery,subscription))")
        db.execSQL("CREATE TABLE reads(scope TEXT NOT NULL,gallery INTEGER NOT NULL,dirty INTEGER NOT NULL DEFAULT 1,PRIMARY KEY(scope,gallery))")
        db.execSQL("CREATE TABLE state(key TEXT PRIMARY KEY,value TEXT NOT NULL)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) { if(oldVersion<2) db.execSQL("ALTER TABLE notices ADD COLUMN details TEXT") }
    @Synchronized fun records(scope: String): List<SubscriptionRecord> = readableDatabase.rawQuery(
        "SELECT payload,mutation,conflict,baseline,checked,error FROM subscriptions WHERE scope=? ORDER BY rowid", arrayOf(scope)).use { c -> buildList {
        while (c.moveToNext()) add(SubscriptionRecord(SearchSubscription.fromJson(JSONObject(c.getString(0))), c.getString(1),
            c.getString(2)?.let { SearchSubscription.fromJson(JSONObject(it)) }, if (c.isNull(3)) null else c.getLong(3), c.getLong(4), c.getString(5)))
    } }
    @Synchronized fun save(scope: String, value: SearchSubscription) = transaction {
        value.validate()
        require(records(scope).none { !it.value.deleted && it.value.id != value.id && it.value.conditionKey == value.conditionKey }) { "같은 검색조건이 이미 있습니다." }
        val old = records(scope).find { it.value.id == value.id }
        require(old?.conflict == null) { "PC와 충돌한 변경을 먼저 해결하세요." }
        put(scope, value.copy(version = old?.value?.version ?: value.version), UUID.randomUUID().toString())
        if (old?.value?.conditionKey != value.conditionKey) writableDatabase.execSQL("UPDATE subscriptions SET baseline=NULL,checked=0 WHERE scope=? AND id=?", arrayOf(scope, value.id))
    }
    private fun put(scope: String, value: SearchSubscription, mutation: String?, conflict: SearchSubscription? = null) {
        val values = ContentValues().apply { put("payload", value.json().toString()); put("mutation", mutation); put("conflict", conflict?.json()?.toString()) }
        if (writableDatabase.update("subscriptions", values, "scope=? AND id=?", arrayOf(scope, value.id)) == 0) {
            values.put("scope", scope); values.put("id", value.id); writableDatabase.insertOrThrow("subscriptions", null, values)
        }
    }
    @Synchronized fun migrateFavorites(context: Context, scope: String) = transaction {
        if (state("favorites_migrated") != null) return@transaction
        BrowserPreferenceStore(context).loadFavorites().forEach { favorite ->
            val value = SearchSubscription(id = favorite.id, name = favorite.name, query = favorite.query)
            if (records(scope).none { it.value.conditionKey == value.conditionKey }) save(scope, value)
        }
        setState("favorites_migrated", "1")
    }
    @Synchronized fun adoptLocal(scope: String) = transaction {
        if (scope.isEmpty()) return@transaction
        records("").forEach { record ->
            val destination = records(scope).find { it.value.conditionKey == record.value.conditionKey }
            if (destination == null) {
                save(scope, record.value.copy(version = 0))
                writableDatabase.execSQL("UPDATE subscriptions SET baseline=?,checked=? WHERE scope=? AND id=?", arrayOf<Any?>(record.baseline,record.checkedAt,scope,record.value.id))
            }
            writableDatabase.execSQL("INSERT OR IGNORE INTO matches SELECT ?,gallery,? FROM matches WHERE scope='' AND subscription=?",arrayOf(scope,destination?.value?.id ?: record.value.id,record.value.id))
            writableDatabase.delete("subscriptions", "scope='' AND id=?", arrayOf(record.value.id))
        }
        writableDatabase.execSQL("INSERT OR IGNORE INTO notices SELECT ?,gallery,title,artist,thumbnail,details FROM notices WHERE scope=''",arrayOf(scope))
        writableDatabase.execSQL("INSERT OR IGNORE INTO reads SELECT ?,gallery,1 FROM reads WHERE scope=''",arrayOf(scope))
        for(table in listOf("notices","reads","matches")) writableDatabase.delete(table,"scope=''",null)
    }
    @Synchronized fun checked(scope: String, expected: SearchSubscription, ids: List<Long>, galleries: List<GallerySummary>, now: Long): Boolean = transaction {
        val current = records(scope).find { it.value.id == expected.id } ?: return@transaction false
        if (current.value != expected || !current.value.enabled || current.value.deleted) return@transaction false
        val newIds = subscriptionNewIds(ids, current.baseline).toSet()
        require(galleries.map { it.id }.toSet().containsAll(newIds))
        galleries.filter { it.id in newIds }.forEach { gallery ->
            val values = ContentValues().apply { put("scope", scope); put("gallery", gallery.id); put("title", gallery.title); put("artist", gallery.artists.joinToString(", ")); put("thumbnail", gallery.thumbnailUrl);put("details",gallery.json().toString()) }
            writableDatabase.insertWithOnConflict("notices", null, values, SQLiteDatabase.CONFLICT_IGNORE)
            writableDatabase.execSQL("INSERT OR IGNORE INTO matches VALUES(?,?,?)", arrayOf<Any>(scope, gallery.id, expected.id))
        }
        writableDatabase.execSQL("UPDATE subscriptions SET baseline=?,checked=?,error=NULL WHERE scope=? AND id=?", arrayOf<Any>(maxOf(current.baseline ?: 0, ids.maxOrNull() ?: 0), now, scope, expected.id))
        true
    }
    @Synchronized fun failed(scope: String, id: String, error: String) {
        writableDatabase.execSQL("UPDATE subscriptions SET error=? WHERE scope=? AND id=?", arrayOf(error.take(300), scope, id))
        changes.value++
    }
    @Synchronized fun notices(scope: String): List<SubscriptionNotice> = readableDatabase.rawQuery(
        "SELECT n.gallery,n.title,n.artist,n.thumbnail,EXISTS(SELECT 1 FROM reads r WHERE r.scope=n.scope AND r.gallery=n.gallery) FROM notices n WHERE n.scope=? ORDER BY n.gallery DESC", arrayOf(scope)).use { c -> buildList {
        val names = records(scope).associate { it.value.id to it.value.name }
        while(c.moveToNext()) {
            val id = c.getLong(0)
            val subscriptions = readableDatabase.rawQuery("SELECT subscription FROM matches WHERE scope=? AND gallery=?", arrayOf(scope,id.toString())).use { m -> buildList { while(m.moveToNext()) add(names[m.getString(0)] ?: m.getString(0)) } }
            add(SubscriptionNotice(id,c.getString(1),c.getString(2),c.getString(3),c.getInt(4)!=0,subscriptions))
        }
    } }
    @Synchronized fun markRead(scope: String, id: Long, dirty: Boolean = true) {
        require(id > 0)
        writableDatabase.execSQL("INSERT OR IGNORE INTO reads VALUES(?,?,?)", arrayOf<Any>(scope,id,if(dirty) 1 else 0))
        changes.value++
    }
    @Synchronized fun gallery(scope:String,id:Long):GallerySummary? = readableDatabase.rawQuery("SELECT details,title,artist,thumbnail FROM notices WHERE scope=? AND gallery=?",arrayOf(scope,id.toString())).use { c ->
        if(!c.moveToFirst()) null else if(!c.isNull(0)) JSONObject(c.getString(0)).gallery()
        else GallerySummary(id,c.getString(1),listOf(c.getString(2)),emptyList(),null,emptyList(),c.getString(3),0,null)
    }
    @Synchronized fun pendingReads(scope: String): List<Long> = readableDatabase.rawQuery("SELECT gallery FROM reads WHERE scope=? AND dirty=1 LIMIT 200", arrayOf(scope)).use { c -> buildList { while(c.moveToNext()) add(c.getLong(0)) } }
    @Synchronized fun acknowledgeReads(scope: String, ids: List<Long>) = transaction {
        ids.forEach { writableDatabase.execSQL("UPDATE reads SET dirty=0 WHERE scope=? AND gallery=?", arrayOf<Any>(scope,it)) }
    }
    @Synchronized fun receive(scope: String, value: SearchSubscription, mutationId: String? = null) = transaction {
        val old = records(scope).find { it.value.id == value.id }
        when {
            mutationId != null && old?.mutationId == mutationId -> put(scope,value,null)
            mutationId != null && old != null && value.version >= old.value.version -> put(scope,old.value.copy(version=value.version),old.mutationId,old.conflict)
            old == null -> put(scope,value,null)
            value.version <= maxOf(old.value.version, old.conflict?.version ?: 0) -> Unit
            old.value.copy(version = value.version) == value -> put(scope, value, null)
            old.mutationId != null -> put(scope,old.value,old.mutationId,value)
            else -> {
                put(scope,value,null)
                if(old.value.conditionKey != value.conditionKey) writableDatabase.execSQL("UPDATE subscriptions SET baseline=NULL,checked=0 WHERE scope=? AND id=?", arrayOf(scope,value.id))
            }
        }
    }
    @Synchronized fun conflict(scope: String, id: String, remote: SearchSubscription) {
        val old = records(scope).find { it.value.id == id } ?: return
        if (old.value.id == remote.id && remote.version < maxOf(old.value.version, old.conflict?.version ?: 0)) return
        if (old.value.copy(version = remote.version) == remote) put(scope, remote, null)
        else put(scope,old.value,old.mutationId,remote)
    }

    @Synchronized fun reconcileMatchingConflicts() = transaction {
        val scopes = readableDatabase.rawQuery(
            "SELECT DISTINCT scope FROM subscriptions WHERE conflict IS NOT NULL", null,
        ).use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.getString(0)) } }
        for (scope in scopes) {
            for (record in records(scope)) {
                val remote = record.conflict ?: continue
                if (remote.version >= record.value.version && record.value.copy(version = remote.version) == remote) {
                    put(scope, remote, null)
                }
            }
        }
    }

    @Synchronized fun resolve(scope: String, id: String, keepPhone: Boolean) = transaction {
        val old = records(scope).find { it.value.id == id } ?: return@transaction
        val remote = old.conflict ?: return@transaction
        if(remote.id != id) {
            writableDatabase.execSQL("INSERT OR IGNORE INTO matches SELECT scope,gallery,? FROM matches WHERE scope=? AND subscription=?",arrayOf(remote.id,scope,id))
            writableDatabase.delete("matches","scope=? AND subscription=?",arrayOf(scope,id))
            writableDatabase.delete("subscriptions", "scope=? AND id=?", arrayOf(scope,id))
        }
        val selected=if(keepPhone) old.value.copy(id=remote.id,version=remote.version) else remote
        put(scope,selected,if(keepPhone) UUID.randomUUID().toString() else null)
        if(selected.conditionKey != old.value.conditionKey) writableDatabase.execSQL("UPDATE subscriptions SET baseline=NULL,checked=0 WHERE scope=? AND id=?",arrayOf(scope,selected.id))
    }
    @Synchronized fun state(key: String): String? = readableDatabase.rawQuery("SELECT value FROM state WHERE key=?", arrayOf(key)).use { if(it.moveToFirst()) it.getString(0) else null }
    @Synchronized fun setState(key: String, value: String) { writableDatabase.execSQL("INSERT OR REPLACE INTO state VALUES(?,?)", arrayOf(key,value)) }
    private fun <T> transaction(block: () -> T): T {
        val db = writableDatabase
        db.beginTransaction()
        return try { block().also { db.setTransactionSuccessful() } } finally { db.endTransaction(); changes.value++ }
    }
    companion object {
        @Volatile private var instance: SubscriptionStore? = null
        fun get(context: Context): SubscriptionStore = instance ?: synchronized(this) { instance ?: SubscriptionStore(context.applicationContext).also { instance = it } }
    }
}
