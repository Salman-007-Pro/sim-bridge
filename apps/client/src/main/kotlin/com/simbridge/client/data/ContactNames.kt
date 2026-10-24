package com.simbridge.client.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/** Optional local contact lookup. Without READ_CONTACTS everything falls back to the number. */
@Singleton
class ContactNames @Inject constructor(@param:ApplicationContext private val context: Context) {
    private val cache = ConcurrentHashMap<String, String>()

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    fun nameFor(number: String?): String? {
        if (number.isNullOrBlank() || !hasPermission()) return null
        return cache.getOrPut(number) { lookup(number).orEmpty() }.ifEmpty { null }
    }

    fun displayName(number: String?): String = nameFor(number) ?: number?.takeIf { it.isNotBlank() } ?: "Unknown number"

    fun invalidate() = cache.clear()

    private fun lookup(number: String): String? = runCatching {
        val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
        context.contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.getOrNull()
}
