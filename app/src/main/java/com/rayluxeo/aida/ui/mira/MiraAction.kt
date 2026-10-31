package com.rayluxeo.aida.ui.mira

import com.rayluxeo.aida.domain.command.MiraCapability

/**
 * Something Mira has prepared and is holding out for Abu to press.
 *
 * She prepares, she never places: a name misheard by one syllable is a call to a stranger, so the
 * last movement is always his. An action belongs to the reply that produced it and lives exactly
 * as long — the next thing he says clears it, so a control can never outlive the sentence that
 * explains what it will do.
 */
sealed interface MiraAction {

    /** Opens the dialler with the number already in it. The green button is Abu's. */
    data class Call(val number: String) : MiraAction

    /** Hands the messaging app a composed message. Nothing is sent until he sends it. */
    data class Message(
        val number: String,
        val body: String?,
        val app: com.rayluxeo.aida.domain.model.MessagingApp = com.rayluxeo.aida.domain.model.MessagingApp.SMS,
    ) : MiraAction

    /**
     * Android is withholding something Mira needs, and this is the way to give it to her.
     *
     * [viaSettings] is raised once the dialog is no longer on offer — either Android has been
     * refused twice and will not ask again, or the capability was never granted by a dialog in
     * the first place, as with exact alarms — and the control then goes to the system screen
     * that can still grant it.
     */
    data class Grant(val capability: MiraCapability, val viaSettings: Boolean) : MiraAction

    /** An invoice delivery has been prepared and is ready for confirmation and sending. */
    data class DeliverInvoice(val draft: com.rayluxeo.aida.domain.model.InvoiceDeliveryDraft) : MiraAction
}
