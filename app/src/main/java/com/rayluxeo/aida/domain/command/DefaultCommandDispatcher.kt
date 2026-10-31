package com.rayluxeo.aida.domain.command

import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.domain.command.handler.BusinessCommandHandler
import com.rayluxeo.aida.domain.command.handler.KnowledgeCommandHandler
import com.rayluxeo.aida.domain.command.handler.MoneyCommandHandler
import com.rayluxeo.aida.domain.command.handler.PhoneCommandHandler
import com.rayluxeo.aida.domain.command.handler.SalahCommandHandler
import com.rayluxeo.aida.domain.command.handler.WorkCommandHandler
import javax.inject.Inject

/**
 * The only place a command becomes an effect.
 *
 * The `when` is exhaustive over [AppCommand] with no `else`, so a new command cannot be added
 * without a handler being written for it — the compiler refuses. Each branch is one line because
 * the work lives in the area handlers; this file's whole job is to make the routing total.
 */
class DefaultCommandDispatcher @Inject constructor(
    private val work: WorkCommandHandler,
    private val knowledge: KnowledgeCommandHandler,
    private val business: BusinessCommandHandler,
    private val money: MoneyCommandHandler,
    private val phone: PhoneCommandHandler,
    private val salah: SalahCommandHandler,
) : CommandDispatcher {

    override suspend fun dispatch(command: AppCommand): AppResult<MiraResponse> = when (command) {
        is AppCommand.CreateTask -> work.createTask(command)
        is AppCommand.UpdateTask -> work.updateTask(command)
        is AppCommand.CompleteTask -> work.completeTask(command)
        is AppCommand.DeleteTask -> work.deleteTask(command)
        is AppCommand.SearchTasks -> work.searchTasks(command)
        is AppCommand.CreateReminder -> work.createReminder(command)
        is AppCommand.QueryReminders -> work.queryReminders(command)
        is AppCommand.SnoozeReminder -> work.snoozeReminder(command)
        is AppCommand.CancelReminder -> work.cancelReminder(command)
        is AppCommand.CreatePlaceReminder -> work.createPlaceReminder(command)
        is AppCommand.CreateAlarm -> work.createAlarm(command)
        is AppCommand.CreateTimer -> work.createTimer(command)
        AppCommand.GetDailyBrief -> work.dailyBrief()
        AppCommand.Greet -> AppResult.Success(MiraResponse.Greeting)
        // Nothing to carry out: the answer was formed by the cloud engine and only needs saying.
        is AppCommand.Conversational -> AppResult.Success(MiraResponse.Conversational(command.answer))

        is AppCommand.CreateNote -> knowledge.createNote(command)
        is AppCommand.SearchNotes -> knowledge.searchNotes(command)
        is AppCommand.StoreMemory -> knowledge.storeMemory(command)
        is AppCommand.ForgetMemory -> knowledge.forgetMemory(command)
        is AppCommand.ForgetFact -> knowledge.forgetFact(command)
        is AppCommand.SearchMemory -> knowledge.searchMemory(command)
        is AppCommand.UniversalSearch -> knowledge.universalSearch(command)

        is AppCommand.CreateClient -> business.createClient(command)
        is AppCommand.SearchClients -> business.searchClients(command)
        is AppCommand.CreateInvoiceDraft -> business.createInvoiceDraft(command)
        is AppCommand.CreateQuoteDraft -> business.createQuoteDraft(command)
        is AppCommand.CreateRecurringInvoice -> business.createRecurringInvoice(command)
        is AppCommand.QueryInvoice -> business.queryInvoice(command)
        is AppCommand.QueryClientStatement -> business.queryClientStatement(command)
        is AppCommand.DeliverInvoice -> business.deliverInvoice(command)

        is AppCommand.NewConversation -> knowledge.newConversation(command)
        is AppCommand.DeleteConversation -> knowledge.deleteConversation(command)
        is AppCommand.DeleteAllConversations -> knowledge.deleteAllConversations(command)
        is AppCommand.SearchConversations -> knowledge.searchConversations(command)

        is AppCommand.RecordPayment -> money.recordPayment(command)
        is AppCommand.RecordExpense -> money.recordExpense(command)
        is AppCommand.RecordIncome -> money.recordIncome(command)
        is AppCommand.PerformCalculation -> money.performCalculation(command)
        is AppCommand.QueryExpenses -> money.queryExpenses(command)
        is AppCommand.QueryIncome -> money.queryIncome(command)
        is AppCommand.QuerySubscription -> money.querySubscription(command)

        is AppCommand.MarkPrayerPrayed -> salah.markPrayerPrayed(command)
        AppCommand.QueryNextPrayer -> salah.queryNextPrayer()
        is AppCommand.QueryPrayerTimeRemaining -> salah.queryPrayerTimeRemaining(command)
        is AppCommand.QueryPrayerTime -> salah.queryPrayerTime(command)
        is AppCommand.QueryPrayerTracking -> salah.queryPrayerTracking(command)
        AppCommand.SetTahajjudReminder -> salah.setTahajjudReminder()

        is AppCommand.CreateCalendarEvent -> phone.createCalendarEvent(command)
        is AppCommand.QuerySchedule -> phone.querySchedule(command)
        is AppCommand.FindFreeSlots -> phone.findFreeSlots(command)
        is AppCommand.UpdateCalendarEvent -> phone.updateCalendarEvent(command)
        is AppCommand.DeleteCalendarEvent -> phone.deleteCalendarEvent(command)
        is AppCommand.FindContact -> phone.findContact(command)
        is AppCommand.PrepareCall -> phone.prepareCall(command)
        is AppCommand.PrepareMessage -> phone.prepareMessage(command)
    }
}
