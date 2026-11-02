package com.doujinzip.android.data

import com.doujinzip.android.model.SearchSubscription
import com.doujinzip.android.model.SearchTagFilter
import com.doujinzip.android.model.applySearchTagFilters

internal suspend fun subscriptionFeedIds(
    subscriptions: List<SearchSubscription>, filters: Set<SearchTagFilter>, blockedTags: Set<String>,
    loadIds: suspend (String) -> List<Long>,
): List<Long> {
    val ids = hashSetOf<Long>()
    val queries = subscriptions.filter { it.enabled && !it.deleted }.flatMap { it.queries }.distinct()
    for (query in queries) ids.addAll(loadIds(applySearchTagFilters(query, filters, blockedTags)))
    return ids.sortedDescending()
}

internal fun subscriptionFeedRefreshDue(now: Long, lastSuccess: Long, hasCache: Boolean, force: Boolean): Boolean =
    force || !hasCache || lastSuccess <= 0 || now < lastSuccess || now-lastSuccess >= 30_000L

internal fun subscriptionFeedFullRefreshDue(now: Long, lastFull: Long, cachedProbe: String?, probe: String?, force: Boolean): Boolean =
    force || probe == null || cachedProbe != probe || lastFull <= 0 || now < lastFull || now-lastFull >= 24*60*60_000L
