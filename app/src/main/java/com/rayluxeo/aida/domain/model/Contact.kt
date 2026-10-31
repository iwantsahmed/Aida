package com.rayluxeo.aida.domain.model

/**
 * Someone in the phone's own address book.
 *
 * Mira does not keep a copy. This is a read-through of what Android already holds, carried only
 * as far as the screen that needs it — the id is the system's, and nothing here is written to
 * Mira's database. A client Abu bills is a [Client]; this is just a name and a number.
 */
data class Contact(
    /** ContactsContract.Contacts._ID on this device. */
    val systemId: Long,
    val displayName: String,
    val phoneNumber: String?,
    val email: String?,
) {
    /** A contact with no number cannot be called, and the screen must not offer to. */
    val isCallable: Boolean get() = !phoneNumber.isNullOrBlank()
}

/** Which field of a contact Abu specifically requested to inspect. */
enum class ContactField {
    ANY,
    PHONE,
    EMAIL,
}

/** The application used to deliver a composed message. */
enum class MessagingApp {
    SMS,
    WHATSAPP,
}

