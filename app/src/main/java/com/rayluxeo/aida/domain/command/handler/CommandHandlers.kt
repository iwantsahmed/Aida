package com.rayluxeo.aida.domain.command.handler

import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.domain.command.AppCommand
import com.rayluxeo.aida.domain.command.MiraResponse

/**
 * The dispatcher's seams.
 *
 * [com.rayluxeo.aida.domain.command.CommandDispatcher] is a single exhaustive `when` over the
 * whole vocabulary, which is what makes adding a command without a handler a compile error. That
 * one `when` would otherwise have to hold twenty-three bodies and depend on every repository in
 * the app, so it delegates: each handler owns one area, and the dispatcher only routes.
 *
 * Every method returns [AppResult] rather than throwing, and a handler that cannot act because
 * Android has withheld a permission returns [MiraResponse.PermissionNeeded] as a SUCCESS — Mira
 * understood perfectly, and the screen should offer the grant rather than apologise.
 */

/** Tasks, reminders, alarms, timers and the day's brief. */
interface WorkCommandHandler {
    suspend fun createTask(command: AppCommand.CreateTask): AppResult<MiraResponse>
    suspend fun updateTask(command: AppCommand.UpdateTask): AppResult<MiraResponse>
    suspend fun completeTask(command: AppCommand.CompleteTask): AppResult<MiraResponse>
    suspend fun deleteTask(command: AppCommand.DeleteTask): AppResult<MiraResponse>
    suspend fun searchTasks(command: AppCommand.SearchTasks): AppResult<MiraResponse>
    suspend fun createReminder(command: AppCommand.CreateReminder): AppResult<MiraResponse>
    suspend fun queryReminders(command: AppCommand.QueryReminders): AppResult<MiraResponse>
    suspend fun snoozeReminder(command: AppCommand.SnoozeReminder): AppResult<MiraResponse>
    suspend fun cancelReminder(command: AppCommand.CancelReminder): AppResult<MiraResponse>
    suspend fun createPlaceReminder(command: AppCommand.CreatePlaceReminder): AppResult<MiraResponse>
    suspend fun createAlarm(command: AppCommand.CreateAlarm): AppResult<MiraResponse>
    suspend fun createTimer(command: AppCommand.CreateTimer): AppResult<MiraResponse>
    suspend fun dailyBrief(): AppResult<MiraResponse>
}

/** Notes and the facts Mira has been told to remember, and conversation history. */
interface KnowledgeCommandHandler {
    suspend fun createNote(command: AppCommand.CreateNote): AppResult<MiraResponse>
    suspend fun searchNotes(command: AppCommand.SearchNotes): AppResult<MiraResponse>
    suspend fun storeMemory(command: AppCommand.StoreMemory): AppResult<MiraResponse>
    suspend fun forgetMemory(command: AppCommand.ForgetMemory): AppResult<MiraResponse>
    suspend fun forgetFact(command: AppCommand.ForgetFact): AppResult<MiraResponse>
    suspend fun searchMemory(command: AppCommand.SearchMemory): AppResult<MiraResponse>
    suspend fun universalSearch(command: AppCommand.UniversalSearch): AppResult<MiraResponse>
    suspend fun newConversation(command: AppCommand.NewConversation): AppResult<MiraResponse>
    suspend fun deleteConversation(command: AppCommand.DeleteConversation): AppResult<MiraResponse>
    suspend fun deleteAllConversations(command: AppCommand.DeleteAllConversations): AppResult<MiraResponse>
    suspend fun searchConversations(command: AppCommand.SearchConversations): AppResult<MiraResponse>
}

/** Clients and the invoices raised against them. */
interface BusinessCommandHandler {
    suspend fun createClient(command: AppCommand.CreateClient): AppResult<MiraResponse>
    suspend fun searchClients(command: AppCommand.SearchClients): AppResult<MiraResponse>
    suspend fun createInvoiceDraft(command: AppCommand.CreateInvoiceDraft): AppResult<MiraResponse>
    suspend fun createQuoteDraft(command: AppCommand.CreateQuoteDraft): AppResult<MiraResponse>
    suspend fun createRecurringInvoice(command: AppCommand.CreateRecurringInvoice): AppResult<MiraResponse>
    suspend fun queryInvoice(command: AppCommand.QueryInvoice): AppResult<MiraResponse>
    suspend fun queryClientStatement(command: AppCommand.QueryClientStatement): AppResult<MiraResponse>
    suspend fun deliverInvoice(command: AppCommand.DeliverInvoice): AppResult<MiraResponse>
}

/** Money in, money out, and arithmetic. */
interface MoneyCommandHandler {
    suspend fun recordPayment(command: AppCommand.RecordPayment): AppResult<MiraResponse>
    suspend fun recordExpense(command: AppCommand.RecordExpense): AppResult<MiraResponse>
    suspend fun recordIncome(command: AppCommand.RecordIncome): AppResult<MiraResponse>
    suspend fun performCalculation(command: AppCommand.PerformCalculation): AppResult<MiraResponse>
    suspend fun queryExpenses(command: AppCommand.QueryExpenses): AppResult<MiraResponse>
    suspend fun queryIncome(command: AppCommand.QueryIncome): AppResult<MiraResponse>
    suspend fun querySubscription(command: AppCommand.QuerySubscription): AppResult<MiraResponse>
}

/** Prayer times, prayer tracking and the Tahajjud reminder — answered by local deterministic logic. */
interface SalahCommandHandler {
    suspend fun markPrayerPrayed(command: AppCommand.MarkPrayerPrayed): AppResult<MiraResponse>
    suspend fun queryNextPrayer(): AppResult<MiraResponse>
    suspend fun queryPrayerTimeRemaining(command: AppCommand.QueryPrayerTimeRemaining): AppResult<MiraResponse>
    suspend fun queryPrayerTime(command: AppCommand.QueryPrayerTime): AppResult<MiraResponse>
    suspend fun queryPrayerTracking(command: AppCommand.QueryPrayerTracking): AppResult<MiraResponse>
    suspend fun setTahajjudReminder(): AppResult<MiraResponse>
}

/** The parts of the phone Mira reaches into: its calendar and its address book. */
interface PhoneCommandHandler {
    suspend fun createCalendarEvent(command: AppCommand.CreateCalendarEvent): AppResult<MiraResponse>
    suspend fun querySchedule(command: AppCommand.QuerySchedule): AppResult<MiraResponse>
    suspend fun findFreeSlots(command: AppCommand.FindFreeSlots): AppResult<MiraResponse>
    suspend fun updateCalendarEvent(command: AppCommand.UpdateCalendarEvent): AppResult<MiraResponse>
    suspend fun deleteCalendarEvent(command: AppCommand.DeleteCalendarEvent): AppResult<MiraResponse>
    suspend fun findContact(command: AppCommand.FindContact): AppResult<MiraResponse>
    suspend fun prepareCall(command: AppCommand.PrepareCall): AppResult<MiraResponse>
    suspend fun prepareMessage(command: AppCommand.PrepareMessage): AppResult<MiraResponse>
}

