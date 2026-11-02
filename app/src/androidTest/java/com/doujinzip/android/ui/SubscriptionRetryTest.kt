package com.doujinzip.android.ui

import androidx.test.platform.app.InstrumentationRegistry
import com.doujinzip.android.data.SubscriptionRefresh
import com.doujinzip.android.data.SubscriptionStore
import com.doujinzip.android.model.DesktopProfile
import com.doujinzip.android.security.SubscriptionOptions
import com.doujinzip.android.security.SubscriptionPreferences
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

class SubscriptionRetryTest {
    @Test fun checksDoNotSyncAndCommonSyncRetriesWithoutDedicatedSchedule() = runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName.endsWith(".uitest"))
        val preferences=SubscriptionPreferences(context);val previous=preferences.load()
        val store=SubscriptionStore.get(context)
        val scope="synthetic-retry-desktop"
        val server=ServerSocket(0,10,InetAddress.getByName("127.0.0.1"))
        val requests=AtomicInteger();val unsupported=AtomicBoolean(true)
        val serving=thread(isDaemon=true) {
            while(!server.isClosed) try { server.accept().use { socket ->
                val input=socket.getInputStream().bufferedReader()
                while(true) { if(input.readLine().isNullOrEmpty()) break }
                requests.incrementAndGet()
                val missing=unsupported.get()
                val body=if(missing) "{}" else """{"success":true,"data":{"cursor":0,"hasMore":false,"changes":[]}}"""
                socket.getOutputStream().write(("HTTP/1.1 ${if(missing) "404 Not Found" else "200 OK"}\r\nContent-Type: application/json\r\nContent-Length: ${body.toByteArray().size}\r\nConnection: close\r\n\r\n"+body).toByteArray())
            } } catch(_:java.io.IOException) { if(!server.isClosed) throw AssertionError("Synthetic server failed") }
        }
        val profile=DesktopProfile(scope,"Synthetic desktop","http://127.0.0.1:${server.localPort}","synthetic-test-token")
        try {
            preferences.save(SubscriptionOptions())
            assertNull(SubscriptionRefresh.run(context,profile))
            assertEquals(0,requests.get())
            assertNotNull(SubscriptionRefresh.run(context,profile,forceSync=true,check=false))
            assertEquals(1,requests.get())
            unsupported.set(false)
            assertNull(SubscriptionRefresh.run(context,profile,forceSync=true,check=false))
            assertEquals(2,requests.get())
            assertNull(SubscriptionRefresh.run(context,profile,forceSync=true,check=false))
            assertEquals(3,requests.get())
            assertNull(SubscriptionRefresh.run(context,profile))
            assertEquals(3,requests.get())
        } finally {
            server.close();serving.join(5_000);preferences.save(previous)
            for(key in listOf("sync-attempt/$scope","sync-error/$scope","cursor/$scope")) store.writableDatabase.delete("state","key=?",arrayOf(key))
        }
    }
}
