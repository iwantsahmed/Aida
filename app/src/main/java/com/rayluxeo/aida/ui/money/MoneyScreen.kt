package com.rayluxeo.aida.ui.money

import com.rayluxeo.aida.core.design.component.MiraIcons

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.clip
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rayluxeo.aida.core.design.MiraPalette
import com.rayluxeo.aida.core.design.MiraTheme
import com.rayluxeo.aida.core.design.fadingHorizontalEdges
import com.rayluxeo.aida.core.design.component.MiraButton
import com.rayluxeo.aida.core.design.component.MiraButtonStyle
import com.rayluxeo.aida.core.design.component.MiraCard
import com.rayluxeo.aida.core.design.component.MiraChip
import com.rayluxeo.aida.core.design.component.MiraDialog
import com.rayluxeo.aida.core.design.component.MiraDivider
import com.rayluxeo.aida.core.design.component.MiraEmptyState
import com.rayluxeo.aida.core.design.component.MiraIconButton
import com.rayluxeo.aida.core.design.component.MiraInlineNotice
import com.rayluxeo.aida.core.design.component.MiraNoticeTone
import com.rayluxeo.aida.core.design.component.MiraScaffold
import com.rayluxeo.aida.core.design.component.MiraSegmentedControl
import com.rayluxeo.aida.core.design.component.MiraSwitch
import com.rayluxeo.aida.core.design.component.MiraTextField
import com.rayluxeo.aida.core.design.component.MiraTopBar
import com.rayluxeo.aida.domain.model.Budget
import com.rayluxeo.aida.domain.model.CalculationRecord
import com.rayluxeo.aida.domain.model.Client
import com.rayluxeo.aida.domain.model.ExchangeRate
import com.rayluxeo.aida.domain.model.Expense
import com.rayluxeo.aida.domain.model.ExpenseCategory
import com.rayluxeo.aida.domain.model.IncomeEntry
import com.rayluxeo.aida.domain.model.Invoice
import com.rayluxeo.aida.domain.model.Money
import com.rayluxeo.aida.domain.model.Payment
import com.rayluxeo.aida.domain.model.PaymentMethod
import com.rayluxeo.aida.domain.model.Subscription
import com.rayluxeo.aida.domain.model.SubscriptionCycle
import com.rayluxeo.aida.domain.usecase.MoneyDashboardSummary
import com.rayluxeo.aida.ui.response.bengaliNumerals
import com.rayluxeo.aida.ui.response.moneyFigure
import java.time.ZoneId
import com.rayluxeo.aida.core.common.SpokenLanguage
import com.rayluxeo.aida.ui.common.LocalDateStyle
import com.rayluxeo.aida.ui.common.formatDate

@Composable
fun MoneyScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MoneyViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val language = state.language
    val words = moneyWording(language)

    MiraScaffold(
        topBar = {
            MiraTopBar(
                title = words.title,
                subtitle = words.subtitle,
                onBack = onBack,
                backContentDescription = words.back,
            )
        },
        horizontalPadding = 0.dp,
        modifier = modifier,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            // Notice banner
            state.notice?.let { notice ->
                MiraInlineNotice(
                    text = words.notice(notice),
                    tone = MiraNoticeTone.CONFIRM,
                    onDismiss = { viewModel.clearNotice() },
                    modifier = Modifier.padding(
                        horizontal = MiraTheme.spacing.screenHorizontal,
                        vertical = MiraTheme.spacing.xs,
                    ),
                )
            }

            // Tab navigation. More tabs than fit on one line, so the row scrolls and its edges fade
            // wherever there is more — otherwise the tabs past the screen edge read as the whole set.
            val tabsState = rememberLazyListState()
            LazyRow(
                modifier = Modifier.fadingHorizontalEdges(tabsState),
                state = tabsState,
                contentPadding = PaddingValues(
                    horizontal = MiraTheme.spacing.screenHorizontal,
                    vertical = MiraTheme.spacing.xs,
                ),
                horizontalArrangement = Arrangement.spacedBy(MiraTheme.spacing.xs),
            ) {
                items(MoneyTab.entries) { tab ->
                    val isSelected = tab == state.selectedTab
                    MiraChip(
                        text = words.tabLabel(tab),
                        selected = isSelected,
                        onClick = { viewModel.selectTab(tab) },
                    )
                }
            }

            MiraDivider(modifier = Modifier.padding(vertical = MiraTheme.spacing.xxs))

            // Tab Content
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
            ) {
                when (state.selectedTab) {
                    MoneyTab.DASHBOARD -> DashboardTab(
                        summary = state.dashboard,
                        words = words,
                        language = language,
                        onNavigateToExpenses = { viewModel.selectTab(MoneyTab.EXPENSES_INCOME) },
                        onNavigateToInvoices = { viewModel.selectTab(MoneyTab.PAYMENTS_RECEIVABLES) },
                        onNavigateToSubs = { viewModel.selectTab(MoneyTab.BUDGETS_SUBSCRIPTIONS) },
                    )
                    MoneyTab.EXPENSES_INCOME -> ExpensesIncomeTab(
                        state = state,
                        words = words,
                        language = language,
                        onToggleShowExpenses = { viewModel.toggleShowExpenses(it) },
                        onFilterCategory = { viewModel.setExpenseCategoryFilter(it) },
                        onOpenAddExpense = { viewModel.setAddExpenseDialogVisible(true) },
                        onOpenAddIncome = { viewModel.setAddIncomeDialogVisible(true) },
                    )
                    MoneyTab.PAYMENTS_RECEIVABLES -> PaymentsReceivablesTab(
                        state = state,
                        words = words,
                        language = language,
                        onToggleShowPayments = { viewModel.toggleShowPayments(it) },
                        onOpenRecordPayment = { viewModel.setRecordPaymentDialogVisible(true) },
                    )
                    MoneyTab.BUDGETS_SUBSCRIPTIONS -> BudgetsSubscriptionsTab(
                        state = state,
                        words = words,
                        language = language,
                        onOpenAddBudget = { viewModel.setAddBudgetDialogVisible(true) },
                        onOpenAddSubscription = { viewModel.setAddSubscriptionDialogVisible(true) },
                        onToggleSubActive = { id, active -> viewModel.toggleSubscriptionActive(id, active) },
                    )
                    MoneyTab.CALCULATOR -> CalculatorTab(
                        state = state,
                        words = words,
                        language = language,
                        onExpressionChange = { viewModel.setCalcExpression(it) },
                        onAppend = { viewModel.appendToCalcExpression(it) },
                        onClear = { viewModel.clearCalcExpression() },
                        onEvaluate = { viewModel.evaluateCalculation() },
                        onClearHistory = { viewModel.clearCalculationHistory() },
                    )
                    MoneyTab.RATES -> ExchangeRatesTab(
                        state = state,
                        words = words,
                        onConvertAmountChange = { viewModel.setConvertAmount(it) },
                        onConvertFromChange = { viewModel.setConvertFrom(it) },
                        onConvertToChange = { viewModel.setConvertTo(it) },
                        onOpenAddRate = { viewModel.setAddRateDialogVisible(true) },
                        onResetRate = { base, target -> viewModel.removeManualRate(base, target) },
                    )
                }
            }
        }
    }

    // Dialogs
    if (state.showAddExpenseDialog) {
        AddExpenseDialog(
            words = words,
            onDismiss = { viewModel.setAddExpenseDialogVisible(false) },
            onConfirm = { amount, currency, desc, cat, billable ->
                viewModel.recordExpense(amount, currency, desc, cat, billable)
            },
        )
    }

    if (state.showAddIncomeDialog) {
        AddIncomeDialog(
            words = words,
            onDismiss = { viewModel.setAddIncomeDialogVisible(false) },
            onConfirm = { amount, currency, source, desc ->
                viewModel.recordIncome(amount, currency, source, desc)
            },
        )
    }

    if (state.showRecordPaymentDialog) {
        RecordPaymentDialog(
            unpaidInvoices = state.unpaidInvoices,
            clients = state.clients,
            words = words,
            language = language,
            onDismiss = { viewModel.setRecordPaymentDialogVisible(false) },
            onConfirm = { amount, currency, invoiceId, clientId, method, ref, note ->
                viewModel.recordPayment(amount, currency, invoiceId, clientId, method, ref, note)
            },
        )
    }

    if (state.showAddBudgetDialog) {
        AddBudgetDialog(
            words = words,
            onDismiss = { viewModel.setAddBudgetDialogVisible(false) },
            onConfirm = { name, limit, currency, cat ->
                viewModel.createBudget(name, limit, currency, cat)
            },
        )
    }

    if (state.showAddSubscriptionDialog) {
        AddSubscriptionDialog(
            words = words,
            onDismiss = { viewModel.setAddSubscriptionDialogVisible(false) },
            onConfirm = { name, amount, currency, vendor, cycle ->
                viewModel.createSubscription(name, amount, currency, vendor, cycle)
            },
        )
    }

    if (state.showAddRateDialog) {
        AddRateDialog(
            words = words,
            onDismiss = { viewModel.setAddRateDialogVisible(false) },
            onConfirm = { base, target, rate ->
                viewModel.setManualRate(base, target, rate)
            },
        )
    }
}

// -----------------------------------------------------------------------------
// DASHBOARD TAB
// -----------------------------------------------------------------------------

@Composable
private fun DashboardTab(
    summary: MoneyDashboardSummary?,
    words: MoneyWording,
    language: SpokenLanguage,
    onNavigateToExpenses: () -> Unit,
    onNavigateToInvoices: () -> Unit,
    onNavigateToSubs: () -> Unit,
) {
    if (summary == null) {
        MiraEmptyState(
            icon = MiraIcons.Paid,
            message = words.dashboardLoading,
        )
        return
    }

    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = MiraTheme.spacing.screenHorizontal, vertical = MiraTheme.spacing.sm),
        verticalArrangement = Arrangement.spacedBy(MiraTheme.spacing.md),
    ) {
        // High-level cashflow overview
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(MiraTheme.spacing.sm),
        ) {
            SummaryStatCard(
                title = words.incomeThisMonth,
                amount = summary.incomeThisMonth,
                accentColor = MiraPalette.Positive,
                language = language,
                modifier = Modifier.weight(1f),
            )
            SummaryStatCard(
                title = words.expensesThisMonth,
                amount = summary.expensesThisMonth,
                accentColor = MiraPalette.Muted,
                language = language,
                modifier = Modifier.weight(1f),
            )
        }

        // Net Local Business Summary
        MiraCard(
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(MiraTheme.spacing.xs),
            ) {
                Text(
                    text = words.businessCashflowSummary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MiraPalette.Platinum,
                )
                Text(
                    text = words.businessCashflowDetail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MiraPalette.Muted,
                )
                Spacer(modifier = Modifier.height(MiraTheme.spacing.xs))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(MiraTheme.spacing.sm),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = words.businessIncome, style = MaterialTheme.typography.bodySmall, color = MiraPalette.Muted)
                        Text(
                            text = formatMoney(summary.businessIncomeThisMonth, language),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            color = MiraPalette.Positive,
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = words.businessExpenses, style = MaterialTheme.typography.bodySmall, color = MiraPalette.Muted)
                        Text(
                            text = formatMoney(summary.businessExpensesThisMonth, language),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            color = MiraPalette.Platinum,
                        )
                    }
                    Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                        Text(text = words.netBusinessProfit, style = MaterialTheme.typography.bodySmall, color = MiraPalette.Muted)
                        val isPositive = summary.netBusinessSummary.amount.signum() >= 0
                        Text(
                            text = formatMoney(summary.netBusinessSummary, language),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isPositive) MiraPalette.Accent else MiraPalette.Danger,
                        )
                    }
                }
            }
        }

        // Receivables and Payments
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(MiraTheme.spacing.sm),
        ) {
            SummaryStatCard(
                title = words.outstandingReceivables,
                amount = summary.outstandingReceivables,
                accentColor = MiraPalette.Accent,
                subtitle = words.outstandingReceivablesSubtitle,
                language = language,
                onClick = onNavigateToInvoices,
                modifier = Modifier.weight(1f),
            )
            SummaryStatCard(
                title = words.paymentsReceived,
                amount = summary.paymentsReceivedThisMonth,
                accentColor = MiraPalette.Positive,
                subtitle = words.paymentsReceivedSubtitle,
                language = language,
                onClick = onNavigateToInvoices,
                modifier = Modifier.weight(1f),
            )
        }

        // Expense category breakdown
        if (summary.expenseCategories.isNotEmpty()) {
            MiraCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(MiraTheme.spacing.sm),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = words.expenseCategories,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MiraPalette.Platinum,
                        )
                        Text(
                            text = words.viewAll,
                            style = MaterialTheme.typography.bodySmall,
                            color = MiraPalette.Accent,
                            modifier = Modifier.clickable { onNavigateToExpenses() },
                        )
                    }

                    summary.expenseCategories.forEach { (cat, amount) ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = words.categoryName(cat),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MiraPalette.Platinum,
                            )
                            Text(
                                text = formatMoney(amount, language),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MiraPalette.Accent,
                            )
                        }
                    }
                }
            }
        }

        // Upcoming subscriptions
        MiraCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                verticalArrangement = Arrangement.spacedBy(MiraTheme.spacing.sm),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            text = words.upcomingSubscriptions,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MiraPalette.Platinum,
                        )
                        Text(
                            text = words.monthlyRunRate(formatMoney(summary.activeSubscriptionsMonthlyTotal, language)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MiraPalette.Muted,
                        )
                    }
                    Text(
                        text = words.manage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MiraPalette.Accent,
                        modifier = Modifier.clickable { onNavigateToSubs() },
                    )
                }

                if (summary.upcomingSubscriptions.isEmpty()) {
                    Text(
                        text = words.noActiveSubscriptions,
                        style = MaterialTheme.typography.bodySmall,
                        color = MiraPalette.Muted,
                    )
                } else {
                    summary.upcomingSubscriptions.take(5).forEach { sub ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column {
                                Text(
                                    text = sub.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MiraPalette.Platinum,
                                )
                                Text(
                                    text = words.due(formatDate(sub.nextChargeOn, LocalDateStyle.current, language)),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MiraPalette.Muted,
                                )
                            }
                            Text(
                                text = formatMoney(sub.amount, language),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MiraPalette.Platinum,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryStatCard(
    title: String,
    amount: Money,
    accentColor: androidx.compose.ui.graphics.Color,
    language: SpokenLanguage,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    MiraCard(
        modifier = modifier.then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(MiraTheme.spacing.xxs),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                color = MiraPalette.Muted,
            )
            Text(
                text = formatMoney(amount, language),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = accentColor,
            )
            subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MiraPalette.Muted,
                )
            }
        }
    }
}

// -----------------------------------------------------------------------------
// EXPENSES & INCOME TAB
// -----------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExpensesIncomeTab(
    state: MoneyUiState,
    words: MoneyWording,
    language: SpokenLanguage,
    onToggleShowExpenses: (Boolean) -> Unit,
    onFilterCategory: (ExpenseCategory?) -> Unit,
    onOpenAddExpense: () -> Unit,
    onOpenAddIncome: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = MiraTheme.spacing.screenHorizontal),
        verticalArrangement = Arrangement.spacedBy(MiraTheme.spacing.sm),
    ) {
        // Toggle bar & Add button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MiraSegmentedControl(
                options = listOf(words.expensesTab(state.expenses.size), words.incomeTab(state.income.size)),
                selectedIndex = if (state.showExpenses) 0 else 1,
                onSelect = { onToggleShowExpenses(it == 0) },
            )

            MiraButton(
                text = if (state.showExpenses) words.addExpense else words.addIncome,
                onClick = if (state.showExpenses) onOpenAddExpense else onOpenAddIncome,
            )
        }

        // Category filter chips if on expenses
        if (state.showExpenses) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(MiraTheme.spacing.xxs),
            ) {
                item {
                    MiraChip(
                        text = words.allCategories,
                        selected = state.expenseCategoryFilter == null,
                        onClick = { onFilterCategory(null) },
                    )
                }
                items(ExpenseCategory.entries) { cat ->
                    MiraChip(
                        text = words.categoryName(cat),
                        selected = state.expenseCategoryFilter == cat,
                        onClick = { onFilterCategory(cat) },
                    )
                }
            }
        }

        // List
        if (state.showExpenses) {
            val filteredExpenses = if (state.expenseCategoryFilter != null) {
                state.expenses.filter { it.category == state.expenseCategoryFilter }
            } else {
                state.expenses
            }

            if (filteredExpenses.isEmpty()) {
                MiraEmptyState(
                    icon = MiraIcons.Paid,
                    message = words.noExpenses,
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(MiraTheme.spacing.xs),
                    contentPadding = PaddingValues(vertical = MiraTheme.spacing.xs),
                ) {
                    items(filteredExpenses, key = { it.id }) { expense ->
                        MiraCard(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(MiraTheme.spacing.sm),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(MiraTheme.spacing.xxs)) {
                                    Text(
                                        text = expense.description,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Medium,
                                        color = MiraPalette.Platinum,
                                    )
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(MiraTheme.spacing.xs),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            text = words.categoryName(expense.category),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MiraPalette.Accent,
                                        )
                                        if (expense.isBillable) {
                                            Text(
                                                text = words.businessBillable,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MiraPalette.Positive,
                                            )
                                        }
                                        Text(
                                            text = "• ${formatInstant(expense.spentAt, language)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MiraPalette.Muted,
                                        )
                                    }
                                }
                                Text(
                                    text = formatMoney(expense.amount, language),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MiraPalette.Platinum,
                                )
                            }
                        }
                    }
                }
            }
        } else {
            // Income
            if (state.income.isEmpty()) {
                MiraEmptyState(
                    icon = MiraIcons.Paid,
                    message = words.noIncome,
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(MiraTheme.spacing.xs),
                    contentPadding = PaddingValues(vertical = MiraTheme.spacing.xs),
                ) {
                    items(state.income, key = { it.id }) { entry ->
                        MiraCard(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(MiraTheme.spacing.sm),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(MiraTheme.spacing.xxs)) {
                                    Text(
                                        text = entry.source,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Medium,
                                        color = MiraPalette.Platinum,
                                    )
                                    entry.description?.let {
                                        Text(text = it, style = MaterialTheme.typography.bodySmall, color = MiraPalette.Muted)
                                    }
                                    Text(
                                        text = formatInstant(entry.receivedAt, language),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MiraPalette.Muted,
                                    )
                                }
                                Text(
                                    text = "+${formatMoney(entry.amount, language)}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MiraPalette.Positive,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------
// PAYMENTS & RECEIVABLES TAB
// -----------------------------------------------------------------------------

@Composable
private fun PaymentsReceivablesTab(
    state: MoneyUiState,
    words: MoneyWording,
    language: SpokenLanguage,
    onToggleShowPayments: (Boolean) -> Unit,
    onOpenRecordPayment: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = MiraTheme.spacing.screenHorizontal),
        verticalArrangement = Arrangement.spacedBy(MiraTheme.spacing.sm),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MiraSegmentedControl(
                options = listOf(words.paymentsTab(state.payments.size), words.receivablesTab(state.unpaidInvoices.size)),
                selectedIndex = if (state.showPayments) 0 else 1,
                onSelect = { onToggleShowPayments(it == 0) },
            )

            MiraButton(
                text = words.recordPayment,
                onClick = onOpenRecordPayment,
            )
        }

        if (state.showPayments) {
            if (state.payments.isEmpty()) {
                MiraEmptyState(
                    icon = MiraIcons.Paid,
                    message = words.noPayments,
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(MiraTheme.spacing.xs),
                    contentPadding = PaddingValues(vertical = MiraTheme.spacing.xs),
                ) {
                    items(state.payments, key = { it.id }) { payment ->
                        val clientName = payment.clientId?.let { state.clients[it]?.name }
                        MiraCard(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(MiraTheme.spacing.sm),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(MiraTheme.spacing.xxs)) {
                                    Text(
                                        text = clientName ?: (payment.reference ?: words.paymentFallback),
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Medium,
                                        color = MiraPalette.Platinum,
                                    )
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(MiraTheme.spacing.xs),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            text = words.paymentMethodName(payment.method),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MiraPalette.Accent,
                                        )
                                        payment.reference?.let {
                                            Text(text = words.refInline(it), style = MaterialTheme.typography.labelSmall, color = MiraPalette.Muted)
                                        }
                                        Text(
                                            text = "• ${formatInstant(payment.receivedAt, language)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MiraPalette.Muted,
                                        )
                                    }
                                }
                                Text(
                                    text = "+${formatMoney(payment.amount, language)}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MiraPalette.Positive,
                                )
                            }
                        }
                    }
                }
            }
        } else {
            // Outstanding receivables
            if (state.unpaidInvoices.isEmpty()) {
                MiraEmptyState(
                    icon = MiraIcons.ReceiptLong,
                    message = words.noReceivables,
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(MiraTheme.spacing.xs),
                    contentPadding = PaddingValues(vertical = MiraTheme.spacing.xs),
                ) {
                    items(state.unpaidInvoices, key = { it.id }) { invoice ->
                        val clientName = state.clients[invoice.clientId]?.name ?: words.clientNumber(invoice.clientId)
                        MiraCard(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(MiraTheme.spacing.sm),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(MiraTheme.spacing.xxs)) {
                                    Text(
                                        text = invoice.number,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MiraPalette.Accent,
                                    )
                                    Text(
                                        text = clientName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MiraPalette.Platinum,
                                    )
                                    Text(
                                        text = words.dueStatus(
                                            formatDate(invoice.dueOn, LocalDateStyle.current, language),
                                            words.invoiceStatusName(invoice.status),
                                        ),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MiraPalette.Muted,
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = words.balanceLabel(formatMoney(invoice.outstanding, language)),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MiraPalette.Accent,
                                    )
                                    Text(
                                        text = words.totalLabel(formatMoney(invoice.total, language)),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MiraPalette.Muted,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------
// BUDGETS & SUBSCRIPTIONS TAB
// -----------------------------------------------------------------------------

@Composable
private fun BudgetsSubscriptionsTab(
    state: MoneyUiState,
    words: MoneyWording,
    language: SpokenLanguage,
    onOpenAddBudget: () -> Unit,
    onOpenAddSubscription: () -> Unit,
    onToggleSubActive: (Long, Boolean) -> Unit,
) {
    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = MiraTheme.spacing.screenHorizontal, vertical = MiraTheme.spacing.sm),
        verticalArrangement = Arrangement.spacedBy(MiraTheme.spacing.md),
    ) {
        // Budgets header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = words.spendingBudgets,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MiraPalette.Platinum,
                )
                Text(
                    text = words.spendingBudgetsDetail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MiraPalette.Muted,
                )
            }
            MiraButton(text = words.addBudget, onClick = onOpenAddBudget)
        }

        if (state.budgets.isEmpty()) {
            MiraCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = words.noBudgets,
                    style = MaterialTheme.typography.bodySmall,
                    color = MiraPalette.Muted,
                )
            }
        } else {
            state.budgets.forEach { budget ->
                MiraCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(MiraTheme.spacing.xs),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = budget.name,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MiraPalette.Platinum,
                            )
                            Text(
                                text = words.limitLabel(formatMoney(budget.limit, language)),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MiraPalette.Accent,
                            )
                        }
                        Text(
                            text = words.periodLabel(
                                formatDate(budget.periodStart, LocalDateStyle.current, language),
                                formatDate(budget.periodEnd, LocalDateStyle.current, language),
                                budget.category?.let { words.categoryName(it) },
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MiraPalette.Muted,
                        )

                        // Spend against the ceiling — the point of a budget, and inert until now.
                        state.budgetProgress.firstOrNull { it.budget.id == budget.id }?.let { prog ->
                            val limitAmount = budget.limit.amount.toFloat()
                            val fraction = if (limitAmount > 0f) {
                                (prog.spent.amount.toFloat() / limitAmount).coerceIn(0f, 1f)
                            } else {
                                0f
                            }
                            Spacer(modifier = Modifier.height(MiraTheme.spacing.xxs))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(MaterialTheme.shapes.small)
                                    .background(MiraPalette.Graphite),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(fraction)
                                        .fillMaxHeight()
                                        .background(if (prog.isOverspent) MiraPalette.Danger else MiraPalette.Accent),
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = words.spentLabel(formatMoney(prog.spent, language)),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MiraPalette.Muted,
                                )
                                Text(
                                    text = if (prog.isOverspent) {
                                        words.overByLabel(formatMoney(prog.spent - budget.limit, language))
                                    } else {
                                        words.leftLabel(formatMoney(prog.remaining, language))
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (prog.isOverspent) MiraPalette.Danger else MiraPalette.Positive,
                                )
                            }
                        }
                    }
                }
            }
        }

        MiraDivider()

        // Subscriptions header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = words.recurringSubscriptions,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MiraPalette.Platinum,
                )
                Text(
                    text = words.recurringSubscriptionsDetail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MiraPalette.Muted,
                )
            }
            MiraButton(text = words.addSubscription, onClick = onOpenAddSubscription)
        }

        if (state.subscriptions.isEmpty()) {
            MiraCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = words.noSubscriptions,
                    style = MaterialTheme.typography.bodySmall,
                    color = MiraPalette.Muted,
                )
            }
        } else {
            state.subscriptions.forEach { sub ->
                MiraCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(MiraTheme.spacing.md),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(MiraTheme.spacing.xxs)) {
                            Text(
                                text = sub.name,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MiraPalette.Platinum,
                            )
                            Text(
                                text = words.nextRenewal(
                                    words.cycleName(sub.cycle),
                                    formatDate(sub.nextChargeOn, LocalDateStyle.current, language),
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MiraPalette.Muted,
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = formatMoney(sub.amount, language),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MiraPalette.Accent,
                                modifier = Modifier.padding(end = MiraTheme.spacing.sm),
                            )
                            MiraSwitch(
                                checked = sub.isActive,
                                onCheckedChange = { onToggleSubActive(sub.id, sub.isActive) },
                            )
                        }
                    }
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------
// CALCULATOR TAB
// -----------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CalculatorTab(
    state: MoneyUiState,
    words: MoneyWording,
    language: SpokenLanguage,
    onExpressionChange: (String) -> Unit,
    onAppend: (String) -> Unit,
    onClear: () -> Unit,
    onEvaluate: () -> Unit,
    onClearHistory: () -> Unit,
) {
    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = MiraTheme.spacing.screenHorizontal, vertical = MiraTheme.spacing.sm),
        verticalArrangement = Arrangement.spacedBy(MiraTheme.spacing.md),
    ) {
        // Calculator Input Card
        MiraCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                verticalArrangement = Arrangement.spacedBy(MiraTheme.spacing.sm),
            ) {
                Text(
                    text = words.financialCalculator,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MiraPalette.Platinum,
                )
                Text(
                    text = words.financialCalculatorDetail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MiraPalette.Muted,
                )

                MiraTextField(
                    value = state.calcExpression,
                    onValueChange = onExpressionChange,
                    label = words.expressionLabel,
                    modifier = Modifier.fillMaxWidth(),
                )

                // Result Box
                state.calcResult?.let { res ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MiraPalette.Graphite, MaterialTheme.shapes.small)
                            .padding(MiraTheme.spacing.sm),
                    ) {
                        Column {
                            Text(text = words.resultLabel, style = MaterialTheme.typography.labelSmall, color = MiraPalette.Muted)
                            Text(
                                text = res,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = MiraPalette.Accent,
                            )
                        }
                    }
                }

                state.calcError?.let { err ->
                    Text(
                        text = words.errorLabel(err),
                        style = MaterialTheme.typography.bodySmall,
                        color = MiraPalette.Danger,
                    )
                }

                // Quick buttons. The symbols and the appended expression tokens are the calculator's
                // own grammar (English keywords the evaluator parses, per §7.12); only the words a
                // worded chip shows are translated, the formula it inserts stays canonical.
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(MiraTheme.spacing.xs),
                    verticalArrangement = Arrangement.spacedBy(MiraTheme.spacing.xs),
                ) {
                    MiraChip(text = "+", selected = false, onClick = { onAppend(" + ") })
                    MiraChip(text = "-", selected = false, onClick = { onAppend(" - ") })
                    MiraChip(text = "×", selected = false, onClick = { onAppend(" * ") })
                    MiraChip(text = "÷", selected = false, onClick = { onAppend(" / ") })
                    MiraChip(text = words.chipPercentOf, selected = false, onClick = { onAppend(" % of ") })
                    MiraChip(text = words.chipDiscount, selected = false, onClick = { onAppend(" with 10% discount") })
                    MiraChip(text = words.chipTax, selected = false, onClick = { onAppend(" with 15% tax") })
                    MiraChip(text = words.chipMarkup, selected = false, onClick = { onAppend(" with 20% markup") })
                    MiraChip(text = words.chipMargin, selected = false, onClick = { onAppend(" with 25% margin") })
                    MiraChip(text = words.chipSplit, selected = false, onClick = { onAppend(" split by 2") })
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(MiraTheme.spacing.sm),
                ) {
                    MiraButton(
                        text = words.clearAction,
                        onClick = onClear,
                        style = MiraButtonStyle.SECONDARY,
                        modifier = Modifier.weight(1f),
                    )
                    MiraButton(
                        text = words.calculateAction,
                        onClick = onEvaluate,
                        style = MiraButtonStyle.PRIMARY,
                        modifier = Modifier.weight(2f),
                    )
                }
            }
        }

        // Calculation History Card
        MiraCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                verticalArrangement = Arrangement.spacedBy(MiraTheme.spacing.sm),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = words.calculationHistory,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MiraPalette.Platinum,
                    )
                    if (state.calculations.isNotEmpty()) {
                        Text(
                            text = words.clearHistory,
                            style = MaterialTheme.typography.bodySmall,
                            color = MiraPalette.Danger,
                            modifier = Modifier.clickable { onClearHistory() },
                        )
                    }
                }

                if (state.calculations.isEmpty()) {
                    Text(
                        text = words.noCalculations,
                        style = MaterialTheme.typography.bodySmall,
                        color = MiraPalette.Muted,
                    )
                } else {
                    state.calculations.forEach { calc ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onExpressionChange(calc.expression) }
                                .padding(vertical = MiraTheme.spacing.xxs),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column {
                                Text(
                                    text = calc.expression,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MiraPalette.Platinum,
                                )
                                Text(
                                    text = formatInstant(calc.createdAt, language),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MiraPalette.Muted,
                                )
                            }
                            Text(
                                text = calc.resultText,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MiraPalette.Accent,
                            )
                        }
                        MiraDivider(modifier = Modifier.padding(vertical = MiraTheme.spacing.xxs))
                    }
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------
// EXCHANGE RATES TAB
// -----------------------------------------------------------------------------

@Composable
private fun ExchangeRatesTab(
    state: MoneyUiState,
    words: MoneyWording,
    onConvertAmountChange: (String) -> Unit,
    onConvertFromChange: (String) -> Unit,
    onConvertToChange: (String) -> Unit,
    onOpenAddRate: () -> Unit,
    onResetRate: (String, String) -> Unit,
) {
    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = MiraTheme.spacing.screenHorizontal, vertical = MiraTheme.spacing.sm),
        verticalArrangement = Arrangement.spacedBy(MiraTheme.spacing.md),
    ) {
        // Converter tool
        MiraCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                verticalArrangement = Arrangement.spacedBy(MiraTheme.spacing.sm),
            ) {
                Text(
                    text = words.currencyConverter,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MiraPalette.Platinum,
                )
                Text(
                    text = words.currencyConverterDetail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MiraPalette.Muted,
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(MiraTheme.spacing.sm),
                ) {
                    MiraTextField(
                        value = state.convertAmount,
                        onValueChange = onConvertAmountChange,
                        label = words.amount,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1.5f),
                    )
                    MiraTextField(
                        value = state.convertFrom,
                        onValueChange = onConvertFromChange,
                        label = words.fromLabel,
                        modifier = Modifier.weight(1f),
                    )
                    MiraTextField(
                        value = state.convertTo,
                        onValueChange = onConvertToChange,
                        label = words.toLabel,
                        modifier = Modifier.weight(1f),
                    )
                }

                state.convertedResult?.let { res ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MiraPalette.Graphite, MaterialTheme.shapes.small)
                            .padding(MiraTheme.spacing.sm),
                    ) {
                        Text(
                            text = "${state.convertAmount} ${state.convertFrom} = $res",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MiraPalette.Accent,
                        )
                    }
                }
            }
        }

        // Configured Rates Table
        MiraCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                verticalArrangement = Arrangement.spacedBy(MiraTheme.spacing.sm),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            text = words.exchangeRates,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MiraPalette.Platinum,
                        )
                        Text(
                            text = words.offlineRatesNote,
                            style = MaterialTheme.typography.bodySmall,
                            color = MiraPalette.Muted,
                        )
                    }
                    // No "refresh" control: these rates are offline and manual by design (the note
                    // above says so), so a refresh had nothing to fetch and only reported a success
                    // that never happened. Setting a rate by hand is the real, honest action.
                    MiraButton(text = words.setRate, onClick = onOpenAddRate)
                }

                state.exchangeRates.forEach { rate ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = MiraTheme.spacing.xxs),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(MiraTheme.spacing.xs),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = rate.pairKey,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MiraPalette.Platinum,
                                )
                                if (rate.isManual) {
                                    Text(
                                        text = words.badgeManual,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MiraPalette.Accent,
                                    )
                                } else {
                                    Text(
                                        text = words.badgeDefault,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MiraPalette.Muted,
                                    )
                                }
                            }
                            Text(
                                text = "1 ${rate.baseCurrencyCode} = ${rate.rate.toPlainString()} ${rate.targetCurrencyCode}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MiraPalette.Muted,
                            )
                        }

                        if (rate.isManual) {
                            Text(
                                text = words.reset,
                                style = MaterialTheme.typography.bodySmall,
                                color = MiraPalette.Danger,
                                modifier = Modifier.clickable {
                                    onResetRate(rate.baseCurrencyCode, rate.targetCurrencyCode)
                                },
                            )
                        }
                    }
                    MiraDivider(modifier = Modifier.padding(vertical = MiraTheme.spacing.xxs))
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------
// DIALOGS
// -----------------------------------------------------------------------------

@Composable
private fun AddExpenseDialog(
    words: MoneyWording,
    onDismiss: () -> Unit,
    onConfirm: (amount: String, currency: String, desc: String, category: ExpenseCategory, isBillable: Boolean) -> Unit,
) {
    var amount by remember { mutableStateOf("") }
    var currency by remember { mutableStateOf("BDT") }
    var description by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(ExpenseCategory.SOFTWARE) }
    var isBillable by remember { mutableStateOf(false) }

    MiraDialog(
        title = words.titleAddExpense,
        onDismissRequest = onDismiss,
        confirmText = words.saveExpense,
        onConfirm = {
            if (amount.isNotBlank() && description.isNotBlank()) {
                onConfirm(amount, currency, description, category, isBillable)
            }
        },
        dismissText = words.cancel,
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(MiraTheme.spacing.sm)) {
                Row(horizontalArrangement = Arrangement.spacedBy(MiraTheme.spacing.xs)) {
                    MiraTextField(
                        value = amount,
                        onValueChange = { amount = it },
                        label = words.amount,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(2f),
                    )
                    MiraTextField(
                        value = currency,
                        onValueChange = { currency = it },
                        label = words.currency,
                        modifier = Modifier.weight(1f),
                    )
                }
                MiraTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = words.merchantDescription,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(text = words.categoryLabel(words.categoryName(category)), style = MaterialTheme.typography.bodySmall, color = MiraPalette.Muted)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(MiraTheme.spacing.xxs)) {
                    items(ExpenseCategory.entries) { cat ->
                        MiraChip(
                            text = words.categoryName(cat),
                            selected = category == cat,
                            onClick = { category = cat },
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(text = words.businessBillableToClient, style = MaterialTheme.typography.bodyMedium, color = MiraPalette.Platinum)
                    MiraSwitch(checked = isBillable, onCheckedChange = { isBillable = it })
                }
            }
        },
    )
}

@Composable
private fun AddIncomeDialog(
    words: MoneyWording,
    onDismiss: () -> Unit,
    onConfirm: (amount: String, currency: String, source: String, desc: String?) -> Unit,
) {
    var amount by remember { mutableStateOf("") }
    var currency by remember { mutableStateOf("BDT") }
    var source by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    MiraDialog(
        title = words.titleAddIncome,
        onDismissRequest = onDismiss,
        confirmText = words.saveIncome,
        onConfirm = {
            if (amount.isNotBlank() && source.isNotBlank()) {
                onConfirm(amount, currency, source, notes.ifBlank { null })
            }
        },
        dismissText = words.cancel,
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(MiraTheme.spacing.sm)) {
                Row(horizontalArrangement = Arrangement.spacedBy(MiraTheme.spacing.xs)) {
                    MiraTextField(
                        value = amount,
                        onValueChange = { amount = it },
                        label = words.amount,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(2f),
                    )
                    MiraTextField(
                        value = currency,
                        onValueChange = { currency = it },
                        label = words.currency,
                        modifier = Modifier.weight(1f),
                    )
                }
                MiraTextField(
                    value = source,
                    onValueChange = { source = it },
                    label = words.sourceLabel,
                    modifier = Modifier.fillMaxWidth(),
                )
                MiraTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = words.notesOptional,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
    )
}

@Composable
private fun RecordPaymentDialog(
    unpaidInvoices: List<Invoice>,
    clients: Map<Long, Client>,
    words: MoneyWording,
    language: SpokenLanguage,
    onDismiss: () -> Unit,
    onConfirm: (
        amount: String,
        currency: String,
        invoiceId: Long?,
        clientId: Long?,
        method: PaymentMethod,
        ref: String?,
        note: String?,
    ) -> Unit,
) {
    var amount by remember { mutableStateOf("") }
    var currency by remember { mutableStateOf("BDT") }
    var selectedInvoiceId by remember { mutableStateOf<Long?>(null) }
    var method by remember { mutableStateOf(PaymentMethod.BANK_TRANSFER) }
    var reference by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    MiraDialog(
        title = words.titleRecordPayment,
        onDismissRequest = onDismiss,
        confirmText = words.recordPaymentConfirm,
        onConfirm = {
            if (amount.isNotBlank()) {
                val inv = unpaidInvoices.firstOrNull { it.id == selectedInvoiceId }
                onConfirm(
                    amount,
                    currency,
                    selectedInvoiceId,
                    inv?.clientId,
                    method,
                    reference.ifBlank { null },
                    notes.ifBlank { null },
                )
            }
        },
        dismissText = words.cancel,
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(MiraTheme.spacing.sm)) {
                Row(horizontalArrangement = Arrangement.spacedBy(MiraTheme.spacing.xs)) {
                    MiraTextField(
                        value = amount,
                        onValueChange = { amount = it },
                        label = words.amount,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(2f),
                    )
                    MiraTextField(
                        value = currency,
                        onValueChange = { currency = it },
                        label = words.currency,
                        modifier = Modifier.weight(1f),
                    )
                }

                if (unpaidInvoices.isNotEmpty()) {
                    Text(text = words.matchInvoiceOptional, style = MaterialTheme.typography.bodySmall, color = MiraPalette.Muted)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(MiraTheme.spacing.xxs)) {
                        item {
                            MiraChip(
                                text = words.none,
                                selected = selectedInvoiceId == null,
                                onClick = { selectedInvoiceId = null },
                            )
                        }
                        items(unpaidInvoices) { inv ->
                            MiraChip(
                                text = "${inv.number} (${formatMoney(inv.outstanding, language)})",
                                selected = selectedInvoiceId == inv.id,
                                onClick = {
                                    selectedInvoiceId = inv.id
                                    amount = inv.outstanding.amount.toPlainString()
                                    currency = inv.currency.currencyCode
                                },
                            )
                        }
                    }
                }

                Text(text = words.methodLabel(words.paymentMethodName(method)), style = MaterialTheme.typography.bodySmall, color = MiraPalette.Muted)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(MiraTheme.spacing.xxs)) {
                    items(PaymentMethod.entries) { m ->
                        MiraChip(
                            text = words.paymentMethodName(m),
                            selected = method == m,
                            onClick = { method = m },
                        )
                    }
                }

                MiraTextField(
                    value = reference,
                    onValueChange = { reference = it },
                    label = words.transactionReference,
                    modifier = Modifier.fillMaxWidth(),
                )
                MiraTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = words.notesOptional,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
    )
}

@Composable
private fun AddBudgetDialog(
    words: MoneyWording,
    onDismiss: () -> Unit,
    onConfirm: (
        name: String,
        limit: String,
        currency: String,
        category: ExpenseCategory?,
    ) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var limit by remember { mutableStateOf("") }
    var currency by remember { mutableStateOf("BDT") }
    var category by remember { mutableStateOf<ExpenseCategory?>(null) }

    MiraDialog(
        title = words.titleAddBudget,
        onDismissRequest = onDismiss,
        confirmText = words.createBudget,
        onConfirm = {
            // The budget period is worked out in the view model from the injected clock, never from
            // LocalDate.now() here (§4 rule 4); the dialog just hands up what Abu typed.
            if (name.isNotBlank() && limit.isNotBlank()) {
                onConfirm(name, limit, currency, category)
            }
        },
        dismissText = words.cancel,
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(MiraTheme.spacing.sm)) {
                MiraTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = words.budgetName,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(MiraTheme.spacing.xs)) {
                    MiraTextField(
                        value = limit,
                        onValueChange = { limit = it },
                        label = words.limitLabelField,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(2f),
                    )
                    MiraTextField(
                        value = currency,
                        onValueChange = { currency = it },
                        label = words.currency,
                        modifier = Modifier.weight(1f),
                    )
                }
                Text(text = words.categoryOptional, style = MaterialTheme.typography.bodySmall, color = MiraPalette.Muted)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(MiraTheme.spacing.xxs)) {
                    item {
                        MiraChip(
                            text = words.all,
                            selected = category == null,
                            onClick = { category = null },
                        )
                    }
                    items(ExpenseCategory.entries) { cat ->
                        MiraChip(
                            text = words.categoryName(cat),
                            selected = category == cat,
                            onClick = { category = cat },
                        )
                    }
                }
            }
        },
    )
}

@Composable
private fun AddSubscriptionDialog(
    words: MoneyWording,
    onDismiss: () -> Unit,
    onConfirm: (
        name: String,
        amount: String,
        currency: String,
        vendor: String?,
        cycle: SubscriptionCycle,
    ) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var currency by remember { mutableStateOf("BDT") }
    var vendor by remember { mutableStateOf("") }
    var cycle by remember { mutableStateOf(SubscriptionCycle.MONTHLY) }

    MiraDialog(
        title = words.titleAddSubscription,
        onDismissRequest = onDismiss,
        confirmText = words.addSubscriptionConfirm,
        onConfirm = {
            // The first renewal date is worked out in the view model from the injected clock, never
            // from LocalDate.now() here (§4 rule 4); the dialog just hands up what Abu typed.
            if (name.isNotBlank() && amount.isNotBlank()) {
                onConfirm(name, amount, currency, vendor.ifBlank { null }, cycle)
            }
        },
        dismissText = words.cancel,
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(MiraTheme.spacing.sm)) {
                MiraTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = words.subscriptionName,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(MiraTheme.spacing.xs)) {
                    MiraTextField(
                        value = amount,
                        onValueChange = { amount = it },
                        label = words.amount,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(2f),
                    )
                    MiraTextField(
                        value = currency,
                        onValueChange = { currency = it },
                        label = words.currency,
                        modifier = Modifier.weight(1f),
                    )
                }
                MiraTextField(
                    value = vendor,
                    onValueChange = { vendor = it },
                    label = words.vendorOptional,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(text = words.billingFrequency, style = MaterialTheme.typography.bodySmall, color = MiraPalette.Muted)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(MiraTheme.spacing.xxs)) {
                    items(SubscriptionCycle.entries) { c ->
                        MiraChip(
                            text = words.cycleName(c),
                            selected = cycle == c,
                            onClick = { cycle = c },
                        )
                    }
                }
            }
        },
    )
}

@Composable
private fun AddRateDialog(
    words: MoneyWording,
    onDismiss: () -> Unit,
    onConfirm: (base: String, target: String, rate: String) -> Unit,
) {
    var base by remember { mutableStateOf("USD") }
    var target by remember { mutableStateOf("BDT") }
    var rate by remember { mutableStateOf("") }

    MiraDialog(
        title = words.titleSetRate,
        onDismissRequest = onDismiss,
        confirmText = words.saveRate,
        onConfirm = {
            if (base.isNotBlank() && target.isNotBlank() && rate.isNotBlank()) {
                onConfirm(base, target, rate)
            }
        },
        dismissText = words.cancel,
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(MiraTheme.spacing.sm)) {
                Row(horizontalArrangement = Arrangement.spacedBy(MiraTheme.spacing.xs)) {
                    MiraTextField(
                        value = base,
                        onValueChange = { base = it.uppercase() },
                        label = words.baseCurrency,
                        modifier = Modifier.weight(1f),
                    )
                    MiraTextField(
                        value = target,
                        onValueChange = { target = it.uppercase() },
                        label = words.targetCurrency,
                        modifier = Modifier.weight(1f),
                    )
                }
                MiraTextField(
                    value = rate,
                    onValueChange = { rate = it },
                    label = words.rateHint(base, target),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
    )
}

// -----------------------------------------------------------------------------
// FORMATTERS
// -----------------------------------------------------------------------------

/**
 * The amount as Abu reads it: the grouped figure (shared, never a `Double` — §8), the taka sign for
 * BDT or the currency code otherwise, and Bengali numerals when Mira is speaking Bengali, exactly as
 * the spoken phrasebook does it.
 */
private fun formatMoney(money: Money, language: SpokenLanguage): String {
    val symbol = if (money.currency.currencyCode == "BDT") "৳" else "${money.currency.currencyCode} "
    val figure = moneyFigure(money)
    val digits = if (language == SpokenLanguage.BENGALI) bengaliNumerals(figure) else figure
    return "$symbol$digits"
}

@Composable
private fun formatInstant(instant: java.time.Instant, language: SpokenLanguage): String {
    val date = instant.atZone(ZoneId.systemDefault()).toLocalDate()
    return formatDate(date, LocalDateStyle.current, language)
}
