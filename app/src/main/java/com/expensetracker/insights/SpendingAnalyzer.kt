package com.expensetracker.insights

import com.expensetracker.data.db.CategoryEntity
import com.expensetracker.data.db.CategoryIds
import com.expensetracker.data.db.TransactionEntity
import com.expensetracker.model.CategoryKind
import com.expensetracker.model.TxnType
import com.expensetracker.sms.Categorizer
import com.expensetracker.sms.SmsParser
import com.expensetracker.util.toLocalDate
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlin.math.abs

/** A suggestion backed by the user's own numbers. [monthlySavingPaise] is an estimate, null when it can't be put in rupees. */
sealed interface SavingTip {
    val monthlySavingPaise: Long?

    data class SpendingAboveIncome(val overPaise: Long) : SavingTip {
        override val monthlySavingPaise: Long? = null
    }

    /** Cooking one in four of these meals at home is the saving suggested. */
    data class FoodDelivery(val orders: Int, val totalPaise: Long) : SavingTip {
        override val monthlySavingPaise = totalPaise / 4
    }

    /** [comparablePaise] is the month's spend, or the projected spend for a month still in progress. */
    data class CategoryAboveUsual(
        val category: CategoryEntity,
        val comparablePaise: Long,
        val usualPaise: Long,
        val onPace: Boolean,
    ) : SavingTip {
        override val monthlySavingPaise = comparablePaise - usualPaise
    }

    /** Cancelling the cheapest subscription is the saving suggested. */
    data class Subscriptions(val names: List<String>, val monthlyPaise: Long, val cheapestName: String, val cheapestPaise: Long) :
        SavingTip {
        override val monthlySavingPaise = cheapestPaise
    }

    /** Trimming a quarter of these is the saving suggested. */
    data class SmallSpends(val count: Int, val totalPaise: Long) : SavingTip {
        override val monthlySavingPaise = totalPaise / 4
    }

    data class CashHeavy(val totalPaise: Long, val share: Float) : SavingTip {
        override val monthlySavingPaise: Long? = null
    }

    /** Budgeting weekends to save 15% of them is the saving suggested. */
    data class WeekendHeavy(val share: Float, val weekendPaise: Long) : SavingTip {
        override val monthlySavingPaise = weekendPaise * 15 / 100
    }
}

data class CategoryTrend(
    val category: CategoryEntity,
    val amountPaise: Long,
    /** Average for this category over recent months that have data; null without history. */
    val usualPaise: Long?,
    /** Change of the month (or its projection, while in progress) against [usualPaise]; null when not comparable yet. */
    val change: Float?,
)

data class RecurringPayment(val name: String, val category: CategoryEntity?, val monthlyPaise: Long, val months: Int, val lastPaid: Long)

data class MerchantTotal(val name: String, val category: CategoryEntity?, val amountPaise: Long, val count: Int)

data class SpendingPatterns(
    val weekendShare: Float?,
    val smallCount: Int,
    val smallTotalPaise: Long,
    val cashPaise: Long,
    val busiestDay: DayOfWeek?,
    val busiestDayPaise: Long,
)

data class Insights(
    val month: YearMonth,
    val spentPaise: Long,
    val receivedPaise: Long,
    /** Share of money received that wasn't spent; null when nothing was received. */
    val savingsRate: Float?,
    val inProgress: Boolean,
    val projectedSpendPaise: Long?,
    val usualSpendPaise: Long?,
    val tips: List<SavingTip>,
    val categoryTrends: List<CategoryTrend>,
    val recurring: List<RecurringPayment>,
    val topMerchants: List<MerchantTotal>,
    val patterns: SpendingPatterns,
)

/**
 * Turns transactions into insights and saving tips with plain rules, so it runs entirely on the
 * phone. Expects the selected month plus up to five months before it, for "usual" baselines and
 * recurring payments.
 */
object SpendingAnalyzer {

    fun analyze(
        transactions: List<TransactionEntity>,
        categories: Map<Long, CategoryEntity>,
        month: YearMonth,
        today: LocalDate = LocalDate.now(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): Insights {
        val counted = transactions.filterNot { it.excluded }
        val byMonth = counted.groupBy { YearMonth.from(it.timestamp.toLocalDate(zone)) }
        val debits = byMonth[month].orEmpty().filter { it.type == TxnType.DEBIT }
        val credits = byMonth[month].orEmpty().filter { it.type == TxnType.CREDIT }
        val spent = debits.sumOf { it.amountPaise }
        val received = credits.sumOf { it.amountPaise }

        val inProgress = month == YearMonth.from(today)
        val daysInMonth = month.lengthOfMonth()
        val daysSoFar = if (inProgress) today.dayOfMonth else daysInMonth
        val remainingShare = (daysInMonth - daysSoFar).toDouble() / daysInMonth

        // Months before an import look empty rather than cheap, so only months with data count as "usual".
        val history = (1..5).map { month.minusMonths(it.toLong()) }
            .filter { m -> byMonth[m].orEmpty().any { it.type == TxnType.DEBIT } }
            .take(BASELINE_MONTHS)
        fun usual(filter: (TransactionEntity) -> Boolean): Long? = history.takeIf { it.isNotEmpty() }?.let { months ->
            months.sumOf { m -> byMonth[m].orEmpty().filter { it.type == TxnType.DEBIT && filter(it) }.sumOf { it.amountPaise } } / months.size
        }

        /**
         * The month's amount, or for a month in progress, what it's heading for: spending so far plus the
         * usual rate for the days left. Scaling up the days so far instead would multiply one-off purchases,
         * so that's only the fallback without history, and only once a week has passed.
         */
        fun comparable(amount: Long, usual: Long?): Long? = when {
            !inProgress -> amount
            usual != null -> amount + (usual * remainingShare).toLong()
            daysSoFar >= MIN_DAYS_FOR_PACE -> amount * daysInMonth / daysSoFar
            else -> null
        }

        val trends = debits.groupBy { it.categoryId }.mapNotNull { (categoryId, items) ->
            val category = categories[categoryId] ?: return@mapNotNull null
            val amount = items.sumOf { it.amountPaise }
            val usual = usual { it.categoryId == categoryId }
            val now = comparable(amount, usual)
            CategoryTrend(
                category = category,
                amountPaise = amount,
                usualPaise = usual,
                change = if (usual != null && usual > 0 && now != null) (now - usual).toFloat() / usual else null,
            )
        }.sortedByDescending { it.amountPaise }

        val patterns = patterns(debits, spent, zone)
        val usualSpend = usual { true }
        val foodTip = foodDelivery(debits)
        val tips = buildList {
            if (received > 0 && spent > received) add(SavingTip.SpendingAboveIncome(spent - received))
            foodTip?.let(::add)
            // One month of history is too thin a baseline to call anything "above usual".
            if (history.size >= MIN_BASELINE_MONTHS_FOR_TIPS) {
                // Food delivery already covers the food category, so it isn't flagged twice.
                val skip = if (foodTip != null) setOf(CategoryIds.FOOD) else emptySet()
                addAll(categoriesAboveUsual(trends.filter { it.category.id !in skip }, ::comparable, inProgress))
            }
            subscriptions(counted, month, zone)?.let(::add)
            if (patterns.smallCount >= SMALL_MIN_COUNT && patterns.smallTotalPaise >= SMALL_MIN_TOTAL) {
                add(SavingTip.SmallSpends(patterns.smallCount, patterns.smallTotalPaise))
            }
            if (spent > 0 && patterns.cashPaise >= CASH_MIN && patterns.cashPaise.toFloat() / spent >= CASH_MIN_SHARE) {
                add(SavingTip.CashHeavy(patterns.cashPaise, patterns.cashPaise.toFloat() / spent))
            }
            val weekendShare = patterns.weekendShare
            if (weekendShare != null && weekendShare >= WEEKEND_MIN_SHARE && spent >= WEEKEND_MIN_SPEND) {
                add(SavingTip.WeekendHeavy(weekendShare, (spent * weekendShare).toLong()))
            }
        }.sortedWith(
            compareBy<SavingTip> { if (it is SavingTip.SpendingAboveIncome) 0 else 1 }
                .thenByDescending { it.monthlySavingPaise ?: -1L },
        )

        return Insights(
            month = month,
            spentPaise = spent,
            receivedPaise = received,
            savingsRate = if (received > 0) (received - spent).toFloat() / received else null,
            inProgress = inProgress,
            projectedSpendPaise = if (inProgress) comparable(spent, usualSpend) else null,
            usualSpendPaise = usualSpend,
            tips = tips,
            categoryTrends = trends,
            recurring = recurring(counted, categories, zone),
            topMerchants = topMerchants(debits, categories),
            patterns = patterns,
        )
    }

    private fun foodDelivery(debits: List<TransactionEntity>): SavingTip.FoodDelivery? {
        val orders = debits.filter { txn ->
            val text = " " + Categorizer.normalize(txn.merchant + " " + txn.vpa?.substringBefore('@').orEmpty()) + " "
            DELIVERY_APPS.any { text.contains(" $it ") } && NOT_DELIVERY.none { text.contains(" $it ") }
        }
        val total = orders.sumOf { it.amountPaise }
        return if (orders.size >= DELIVERY_MIN_ORDERS && total >= DELIVERY_MIN_TOTAL) SavingTip.FoodDelivery(orders.size, total) else null
    }

    private fun categoriesAboveUsual(
        trends: List<CategoryTrend>,
        comparable: (Long, Long?) -> Long?,
        inProgress: Boolean,
    ): List<SavingTip.CategoryAboveUsual> = trends
        .filter { it.category.kind == CategoryKind.EXPENSE && it.category.id !in NOT_DISCRETIONARY }
        .mapNotNull { trend ->
            val usual = trend.usualPaise ?: return@mapNotNull null
            val now = comparable(trend.amountPaise, usual) ?: return@mapNotNull null
            if (usual < SPIKE_MIN_USUAL || now < usual * SPIKE_RATIO || now - usual < SPIKE_MIN_EXTRA) return@mapNotNull null
            SavingTip.CategoryAboveUsual(trend.category, now, usual, onPace = inProgress)
        }
        .sortedByDescending { it.monthlySavingPaise }
        .take(MAX_SPIKE_TIPS)

    /** Subscriptions paid this month or last; each counted once at its latest price. */
    private fun subscriptions(counted: List<TransactionEntity>, month: YearMonth, zone: ZoneId): SavingTip.Subscriptions? {
        val months = setOf(month, month.minusMonths(1))
        val latest = counted
            .filter {
                it.type == TxnType.DEBIT && it.categoryId == CategoryIds.SUBSCRIPTIONS &&
                    YearMonth.from(it.timestamp.toLocalDate(zone)) in months
            }
            .groupBy { recurringKey(it) ?: it.merchant }
            .values.map { items -> items.maxBy { it.timestamp } }
        if (latest.isEmpty()) return null
        val cheapest = latest.minBy { it.amountPaise }
        return SavingTip.Subscriptions(
            names = latest.sortedByDescending { it.amountPaise }.map { it.merchant },
            monthlyPaise = latest.sumOf { it.amountPaise },
            cheapestName = cheapest.merchant,
            cheapestPaise = cheapest.amountPaise,
        )
    }

    /** Same payee in at least two months, for a steady amount, no more than about twice a month. */
    private fun recurring(counted: List<TransactionEntity>, categories: Map<Long, CategoryEntity>, zone: ZoneId): List<RecurringPayment> =
        counted.filter { it.type == TxnType.DEBIT && it.categoryId != CategoryIds.CASH }
            .groupBy { recurringKey(it) }
            .filterKeys { it != null }
            .values.mapNotNull { items ->
                val months = items.map { YearMonth.from(it.timestamp.toLocalDate(zone)) }.toSet()
                if (months.size < 2 || items.size > months.size * 2) return@mapNotNull null
                val amounts = items.map { it.amountPaise }.sorted()
                val median = amounts[amounts.size / 2]
                val steady = items.count { abs(it.amountPaise - median) <= median * RECURRING_TOLERANCE }
                if (steady < items.size * RECURRING_STEADY_SHARE) return@mapNotNull null
                val last = items.maxBy { it.timestamp }
                RecurringPayment(last.merchant, categories[last.categoryId], median, months.size, last.timestamp)
            }
            .sortedByDescending { it.monthlyPaise }

    private fun recurringKey(txn: TransactionEntity): String? = when {
        !SmsParser.isGenericLabel(txn.merchant) -> Categorizer.normalize(txn.merchant).takeIf { it.isNotEmpty() }
        txn.vpa != null -> "vpa:" + txn.vpa
        else -> null
    }

    private fun topMerchants(debits: List<TransactionEntity>, categories: Map<Long, CategoryEntity>): List<MerchantTotal> =
        debits.filterNot { SmsParser.isGenericLabel(it.merchant) }
            .groupBy { Categorizer.normalize(it.merchant) }
            .values.map { items ->
                val last = items.maxBy { it.timestamp }
                MerchantTotal(last.merchant, categories[last.categoryId], items.sumOf { it.amountPaise }, items.size)
            }
            .sortedByDescending { it.amountPaise }
            .take(TOP_MERCHANTS)

    private fun patterns(debits: List<TransactionEntity>, spent: Long, zone: ZoneId): SpendingPatterns {
        val byDay = debits.groupBy { it.timestamp.toLocalDate(zone).dayOfWeek }.mapValues { (_, items) -> items.sumOf { it.amountPaise } }
        val weekend = (byDay[DayOfWeek.SATURDAY] ?: 0) + (byDay[DayOfWeek.SUNDAY] ?: 0)
        val small = debits.filter { it.amountPaise < SMALL_PAYMENT }
        val busiest = byDay.maxByOrNull { it.value }
        return SpendingPatterns(
            weekendShare = if (spent > 0) weekend.toFloat() / spent else null,
            smallCount = small.size,
            smallTotalPaise = small.sumOf { it.amountPaise },
            cashPaise = debits.filter { it.categoryId == CategoryIds.CASH }.sumOf { it.amountPaise },
            busiestDay = busiest?.key,
            busiestDayPaise = busiest?.value ?: 0,
        )
    }

    private const val MIN_DAYS_FOR_PACE = 7
    private const val BASELINE_MONTHS = 3
    private const val MIN_BASELINE_MONTHS_FOR_TIPS = 2
    private const val TOP_MERCHANTS = 5
    private const val MAX_SPIKE_TIPS = 2

    private const val RUPEE = 100L
    private const val SMALL_PAYMENT = 200 * RUPEE
    private const val SMALL_MIN_COUNT = 12
    private const val SMALL_MIN_TOTAL = 1_200 * RUPEE
    private const val DELIVERY_MIN_ORDERS = 4
    private const val DELIVERY_MIN_TOTAL = 1_000 * RUPEE
    private const val SPIKE_MIN_USUAL = 300 * RUPEE
    private const val SPIKE_MIN_EXTRA = 500 * RUPEE
    private const val SPIKE_RATIO = 1.3
    private const val CASH_MIN = 2_000 * RUPEE
    private const val CASH_MIN_SHARE = 0.2f
    private const val WEEKEND_MIN_SHARE = 0.5f
    private const val WEEKEND_MIN_SPEND = 3_000 * RUPEE
    private const val RECURRING_TOLERANCE = 0.15
    private const val RECURRING_STEADY_SHARE = 0.8

    /** Rent, transfers and cash aren't spending habits a tip can trim. */
    private val NOT_DISCRETIONARY = setOf(CategoryIds.RENT, CategoryIds.TRANSFERS, CategoryIds.CASH, CategoryIds.OTHERS)
    private val DELIVERY_APPS = listOf(
        "swiggy", "zomato", "eatsure", "dominos", "faasos", "box8", "behrouz", "ovenstory", "pizza hut", "mcdonalds",
        "kfc", "burger king", "wow momo",
    )
    private val NOT_DELIVERY = listOf("instamart", "dineout", "district")
}
