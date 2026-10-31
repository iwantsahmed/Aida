package com.rayluxeo.aida.data.system

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.DispatcherProvider
import com.rayluxeo.aida.core.common.MiraErrorCode
import com.rayluxeo.aida.core.common.failure
import com.rayluxeo.aida.domain.model.Contact
import com.rayluxeo.aida.domain.repository.ContactsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext

/**
 * The phone's own address book, read through ContactsContract.
 *
 * Nothing read here is cached and nothing is written to Mira's database. A name is fetched at the
 * moment Abu asks about one and is gone again afterwards — a second copy of the address book
 * inside Mira would be a second place it could leak from, and it would go stale besides.
 *
 * A name resolves in two queries rather than one per person: the provider's own filter URI decides
 * who matches, then a single Data query collects the numbers and addresses for all of them at
 * once. On an address book of a few thousand rows that difference is audible.
 */
@Singleton
class ContactsRepositoryImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val dispatchers: DispatcherProvider,
) : ContactsRepository {

    override fun canRead(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) ==
            PackageManager.PERMISSION_GRANTED

    override suspend fun search(query: String, limit: Int): AppResult<List<Contact>> {
        val term = query.trim()
        // A search for nothing has no honest answer, and the whole address book is not one.
        if (term.isEmpty()) return AppResult.Success(emptyList())
        if (!canRead()) return failure(MiraErrorCode.PERMISSION_REQUIRED, "search contacts")
        val capped = limit.coerceIn(1, MAX_LIMIT)
        return providerCall("search contacts") {
            val uri = Uri.withAppendedPath(
                ContactsContract.Contacts.CONTENT_FILTER_URI,
                Uri.encode(term),
            )
            withDetails(readNames(uri, selection = null, arguments = null, limit = capped))
        }
    }

    override suspend fun byId(systemId: Long): AppResult<Contact?> {
        if (!canRead()) return failure(MiraErrorCode.PERMISSION_REQUIRED, "load contact $systemId")
        return providerCall("load contact $systemId") {
            val names = readNames(
                uri = ContactsContract.Contacts.CONTENT_URI,
                selection = "${ContactsContract.Contacts._ID} = ?",
                arguments = arrayOf(systemId.toString()),
                limit = 1,
            )
            withDetails(names).firstOrNull()
        }
    }

    /** Contact id to display name, in the order the provider ranked them, capped at [limit]. */
    private fun readNames(
        uri: Uri,
        selection: String?,
        arguments: Array<String>?,
        limit: Int,
    ): Map<Long, String> {
        val names = LinkedHashMap<Long, String>()
        context.contentResolver.query(
            uri,
            arrayOf(
                ContactsContract.Contacts._ID,
                ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
            ),
            selection,
            arguments,
            "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} ASC",
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(ContactsContract.Contacts._ID)
            val nameColumn =
                cursor.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY)
            while (names.size < limit && cursor.moveToNext()) {
                val name = cursor.getString(nameColumn)?.trim()
                if (name.isNullOrEmpty()) continue
                names[cursor.getLong(idColumn)] = name
            }
        }
        return names
    }

    /** One Data query for the whole match set: numbers and addresses for everyone at once. */
    private fun withDetails(names: Map<Long, String>): List<Contact> {
        if (names.isEmpty()) return emptyList()
        val ids = names.keys.toList()
        val placeholders = ids.joinToString(separator = ",") { "?" }
        val selection = "${ContactsContract.Data.CONTACT_ID} IN ($placeholders) AND " +
            "${ContactsContract.Data.MIMETYPE} IN (?, ?)"
        val arguments = (
            ids.map(Long::toString) + listOf(
                ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE,
                ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE,
            )
            ).toTypedArray()
        val phones = HashMap<Long, String>(names.size)
        val emails = HashMap<Long, String>(names.size)
        context.contentResolver.query(
            ContactsContract.Data.CONTENT_URI,
            arrayOf(
                ContactsContract.Data.CONTACT_ID,
                ContactsContract.Data.MIMETYPE,
                ContactsContract.Data.DATA1,
            ),
            selection,
            arguments,
            // Abu's chosen default first, so a contact with three numbers offers the right one.
            "${ContactsContract.Data.IS_SUPER_PRIMARY} DESC, ${ContactsContract.Data.IS_PRIMARY} DESC",
        )?.use { cursor -> readDetails(cursor, phones, emails) }
        return names.map { (id, name) ->
            Contact(
                systemId = id,
                displayName = name,
                phoneNumber = phones[id],
                email = emails[id],
            )
        }
    }

    private fun readDetails(
        cursor: Cursor,
        phones: HashMap<Long, String>,
        emails: HashMap<Long, String>,
    ) {
        val idColumn = cursor.getColumnIndexOrThrow(ContactsContract.Data.CONTACT_ID)
        val typeColumn = cursor.getColumnIndexOrThrow(ContactsContract.Data.MIMETYPE)
        val valueColumn = cursor.getColumnIndexOrThrow(ContactsContract.Data.DATA1)
        while (cursor.moveToNext()) {
            val value = cursor.getString(valueColumn)?.trim()
            if (value.isNullOrEmpty()) continue
            val id = cursor.getLong(idColumn)
            when (cursor.getString(typeColumn)) {
                ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE ->
                    if (id !in phones) phones[id] = value

                ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE ->
                    if (id !in emails) emails[id] = value
            }
        }
    }

    /**
     * ContentResolver throws where the port returns values. A SecurityException means the grant
     * went away between [canRead] and the query, which is a permission to ask for rather than a
     * fault — the caller turns PERMISSION_REQUIRED into an offer, not an apology.
     */
    private suspend fun <T> providerCall(detail: String, block: () -> T): AppResult<T> =
        withContext(dispatchers.io) {
            try {
                AppResult.Success(block())
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (denied: SecurityException) {
                failure(MiraErrorCode.PERMISSION_REQUIRED, detail, denied)
            } catch (error: Exception) {
                failure(MiraErrorCode.UNKNOWN, detail, error)
            }
        }

    private companion object {
        /** A spoken name never means five hundred people; a runaway match is a bug, not an answer. */
        const val MAX_LIMIT = 50
    }
}
