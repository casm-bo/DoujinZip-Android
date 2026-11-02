package com.doujinzip.android.data

import android.content.Context
import androidx.work.*
import com.doujinzip.android.model.DesktopProfile
import com.doujinzip.android.security.SecureProfileStore
import com.doujinzip.android.security.SubscriptionPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

object SubscriptionRefresh {
    private val mutex=Mutex()
    internal suspend fun <T> snapshot(block: suspend () -> T): T = mutex.withLock { block() }
    suspend fun run(context:Context,profile:DesktopProfile?,force:Boolean=false,forceSync:Boolean=false,check:Boolean=true):String? = mutex.withLock {
        withContext(Dispatchers.IO) {
            val store=SubscriptionStore.get(context)
            val scope=profile?.id.orEmpty()
            store.adoptLocal(scope)
            val options=SubscriptionPreferences(context).load()
            if(profile != null && forceSync) {
                try { SubscriptionSync(store).sync(profile);store.setState("sync-error/$scope","") }
                catch(error:CancellationException) { throw error }
                catch(error:Exception) { store.setState("sync-error/$scope",if(error is com.doujinzip.android.network.CompanionApiException && error.statusCode==404) "unsupported" else "offline") }
            }
            if(check) {
                SubscriptionChecker(store).check(scope,options.intervalMinutes*60_000L,force)
                SubscriptionNotifications.refresh(context)
            }
            when(store.state("sync-error/$scope")) {
                "unsupported" -> context.getString(com.doujinzip.android.R.string.sub_update_pc)
                "offline" -> context.getString(com.doujinzip.android.R.string.sub_sync_offline)
                else -> null
            }
        }
    }
}

class SubscriptionWorker(context:Context,parameters:WorkerParameters):CoroutineWorker(context,parameters) {
    override suspend fun doWork():Result = try {
        val profiles=withContext(Dispatchers.IO) { SecureProfileStore(applicationContext).load() }
        if(profiles.isEmpty()) SubscriptionRefresh.run(applicationContext,null)
        else SubscriptionRefresh.run(applicationContext,profiles.first())
        Result.success()
    } catch(error:CancellationException) { throw error }
    catch(_:Exception) { Result.retry() }

    companion object {
        const val WORK_NAME="subscription-checks"
        fun schedule(context:Context):Operation {
            val store=SubscriptionStore.get(context)
            val manager=WorkManager.getInstance(context)
            val profiles=SecureProfileStore(context).load()
            val options=SubscriptionPreferences(context).load()
            val hasChecks=(listOf("")+profiles.map { it.id }).any { scope -> store.records(scope).any { it.value.enabled && !it.value.deleted } }
            if(!hasChecks) {
                return manager.cancelUniqueWork(WORK_NAME)
            }
            val minutes=options.intervalMinutes
            val request=PeriodicWorkRequestBuilder<SubscriptionWorker>(minutes.coerceAtLeast(15).toLong(),TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build()
            return manager.enqueueUniquePeriodicWork(WORK_NAME,ExistingPeriodicWorkPolicy.UPDATE,request)
        }
    }
}
