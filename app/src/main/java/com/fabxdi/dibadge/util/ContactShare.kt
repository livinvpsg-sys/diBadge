package com.fabxdi.dibadge.util

import android.content.Context
import android.net.Uri
import android.provider.ContactsContract

/** A contact picked from the phone, ready to send in a chat. */
data class SharedContact(val name: String, val phones: List<String>, val emails: List<String>)

object ContactShare {
    /** The text of a contact message: the name on the first line, then "tel:" and "email:" lines. */
    fun encode(c: SharedContact): String = buildString {
        append(c.name.trim())
        c.phones.forEach { append("\ntel:").append(it.trim()) }
        c.emails.forEach { append("\nemail:").append(it.trim()) }
    }

    fun decode(text: String): SharedContact {
        val lines = text.split("\n")
        val name = lines.firstOrNull().orEmpty().trim()
        val phones = lines.drop(1).filter { it.startsWith("tel:") }.map { it.removePrefix("tel:").trim() }.filter { it.isNotBlank() }
        val emails = lines.drop(1).filter { it.startsWith("email:") }.map { it.removePrefix("email:").trim() }.filter { it.isNotBlank() }
        return SharedContact(name, phones, emails)
    }

    /** Reads the contact the person picked. The picker gives this app access to that one contact only. */
    fun read(context: Context, contactUri: Uri): SharedContact? {
        return try {
            var name = ""
            context.contentResolver.query(contactUri, arrayOf(ContactsContract.Contacts.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) name = it.getString(0).orEmpty()
            }
            val phones = LinkedHashSet<String>()
            val emails = LinkedHashSet<String>()
            val dataUri = Uri.withAppendedPath(contactUri, ContactsContract.Contacts.Data.CONTENT_DIRECTORY)
            context.contentResolver.query(
                dataUri,
                arrayOf(ContactsContract.Data.MIMETYPE, ContactsContract.Data.DATA1),
                null, null, null
            )?.use {
                while (it.moveToNext()) {
                    val value = it.getString(1).orEmpty().trim()
                    if (value.isBlank()) continue
                    when (it.getString(0)) {
                        ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE -> phones.add(value)
                        ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE -> emails.add(value)
                    }
                }
            }
            if (name.isBlank() && phones.isEmpty() && emails.isEmpty()) null
            else SharedContact(name.ifBlank { phones.firstOrNull() ?: emails.firstOrNull() ?: "Contact" }, phones.take(5), emails.take(3))
        } catch (e: Exception) {
            null
        }
    }
}