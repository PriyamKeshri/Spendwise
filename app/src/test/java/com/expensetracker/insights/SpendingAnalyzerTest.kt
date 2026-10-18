package com.expensetracker.insights

import com.expensetracker.data.db.CategoryIds
import com.expensetracker.data.db.DefaultCategories
import com.expensetracker.data.db.TransactionEntity
import com.expensetracker.model.PaymentMode
import com.expensetracker.model.TxnType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId

class SpendingAnalyzerTest {

    private val zone = ZoneId.of("Asia/Kolkata")
    private val categories = DefaultCategories.all.associateBy { it.id }
    private val october = YearMonth.of(2026, 10)

    private fun txn(
        date: String,
        rupees: Long,
        merchant: String,
        categoryId: Long,
        type: TxnType = TxnType.DEBIT,
        excluded: Boolean = false,
    ) = TransactionEntity(
        smsId = null,
        type = type,
        amountPaise = rupees * 100,
        merchant = merchant,
        vpa = null,
        mode = PaymentMode.UPI,
        bank = "SBI",
        accountTail = "1234",
        refNo = null,
        timestamp = LocalDateTime.parse(date + "T12:00").atZone(zone).toInstant().toEpochMilli(),
        categoryId = categoryId,
        excluded = excluded,
        sender = "test",
        body = "",
    )

    /** Jul to Sep each spend ₹1,000 on shopping and pay Netflix from Sep; October gets busier. Weekdays only. */
    private val history = listOf(
        txn("2026-07-14", 1_000, "Myntra", CategoryIds.SHOPPING),
        txn("2026-08-12", 1_000, "Myntra", CategoryIds.SHOPPING),
        txn("2026-09-15", 1_000, "Myntra", CategoryIds.SHOPPING),
        txn("2026-09-07", 649, "Netflix", CategoryIds.SUBSCRIPTIONS),
    )

    private val octoberBusy = listOf(
        txn("2026-10-07", 649, "Netflix", CategoryIds.SUBSCRIPTIONS),
        txn("2026-10-08", 119, "Spotify", CategoryIds.SUBSCRIPTIONS),
        txn("2026-10-12", 3_000, "Myntra", CategoryIds.SHOPPING),
        txn("2026-10-05", 400, "Swiggy", CategoryIds.FOOD),
        txn("2026-10-06", 400, "Zomato", CategoryIds.FOOD),
        txn("2026-10-13", 400, "Swiggy", CategoryIds.FOOD),
        txn("2026-10-14", 400, "Dominos", CategoryIds.FOOD),
        txn("2026-10-20", 400, "Zomato", CategoryIds.FOOD),
        txn("2026-10-21", 500, "Swiggy Instamart", CategoryIds.GROCERIES),
        txn("2026-10-09", 10_000, "Cred Club", CategoryIds.TRANSFERS, excluded = true),
        txn("2026-10-01", 5_000, "ACME Ltd", CategoryIds.SALARY, type = TxnType.CREDIT),
    ) + (1..12).map { day -> txn("2026-10-%02d".format(listOf(5, 6, 7, 8, 9, 12, 13, 14, 15, 16, 19, 20)[day - 1]), 110, "Chai Point", CategoryIds.FOOD) }

    @Test
    fun `finds the month's totals and ranks tips by what they would save`() {
        val insights = SpendingAnalyzer.analyze(history + octoberBusy, categories, october, today = LocalDate.of(2026, 11, 15), zone = zone)

        // Excluded card bill payment ignored: 649 + 119 + 3000 + 5×400 + 500 + 12×110.
        assertEquals(758_800L, insights.spentPaise)
        assertEquals(500_000L, insights.receivedPaise)
        assertFalse(insights.inProgress)
        assertEquals(-0.5176f, insights.savingsRate!!, 0.001f)

        val tips = insights.tips
        assertEquals(SavingTip.SpendingAboveIncome(258_800L), tips[0])
        val shopping = tips[1] as SavingTip.CategoryAboveUsual
        assertEquals(CategoryIds.SHOPPING, shopping.category.id)
        assertEquals(200_000L, shopping.monthlySavingPaise)
        assertEquals(100_000L, shopping.usualPaise)

        // Instamart is groceries, not a food order.
        assertTrue(SavingTip.FoodDelivery(5, 200_000L) in tips)
        // Spotify (₹119) is small too.
        assertTrue(SavingTip.SmallSpends(13, 143_900L) in tips)
        val subscriptions = tips.filterIsInstance<SavingTip.Subscriptions>().single()
        assertEquals(listOf("Netflix", "Spotify"), subscriptions.names)
        assertEquals(76_800L, subscriptions.monthlyPaise)
        assertEquals("Spotify", subscriptions.cheapestName)

        assertEquals("Myntra", insights.topMerchants.first().name)
        val netflix = insights.recurring.single { it.name == "Netflix" }
        assertEquals(64_900L, netflix.monthlyPaise)
        assertEquals(2, netflix.months)
        assertTrue(insights.recurring.none { it.name == "Spotify" })
    }

    @Test
    fun `projects a month in progress once a week has passed`() {
        val november = YearMonth.of(2026, 11)
        val spend = listOf(txn("2026-11-02", 1_000, "Big Bazaar", CategoryIds.GROCERIES))

        val tenDaysIn = SpendingAnalyzer.analyze(spend, categories, november, today = LocalDate.of(2026, 11, 10), zone = zone)
        assertTrue(tenDaysIn.inProgress)
        assertEquals(300_000L, tenDaysIn.projectedSpendPaise)

        val threeDaysIn = SpendingAnalyzer.analyze(spend, categories, november, today = LocalDate.of(2026, 11, 3), zone = zone)
        assertNull(threeDaysIn.projectedSpendPaise)
    }

    @Test
    fun `needs two months of history before calling a category above usual`() {
        val oneMonthBack = listOf(txn("2026-09-15", 1_000, "Myntra", CategoryIds.SHOPPING))
        val october = listOf(txn("2026-10-12", 4_000, "Myntra", CategoryIds.SHOPPING))

        val thin = SpendingAnalyzer.analyze(oneMonthBack + october, categories, this.october, LocalDate.of(2026, 11, 15), zone)
        assertTrue(thin.tips.none { it is SavingTip.CategoryAboveUsual })

        val twoMonthsBack = oneMonthBack + txn("2026-08-12", 1_000, "Myntra", CategoryIds.SHOPPING)
        val enough = SpendingAnalyzer.analyze(twoMonthsBack + october, categories, this.october, LocalDate.of(2026, 11, 15), zone)
        assertTrue(enough.tips.any { it is SavingTip.CategoryAboveUsual })
    }

    @Test
    fun `doesn't flag food twice when food delivery already has a tip`() {
        val usualFood = listOf(
            txn("2026-08-12", 500, "Haldirams", CategoryIds.FOOD),
            txn("2026-09-15", 500, "Haldirams", CategoryIds.FOOD),
        )
        val deliveries = listOf("05", "06", "07", "08", "09").map { day -> txn("2026-10-$day", 500, "Swiggy", CategoryIds.FOOD) }

        val insights = SpendingAnalyzer.analyze(usualFood + deliveries, categories, october, LocalDate.of(2026, 11, 15), zone)

        assertTrue(insights.tips.any { it is SavingTip.FoodDelivery })
        assertTrue(insights.tips.none { it is SavingTip.CategoryAboveUsual })
    }

    @Test
    fun `projects the rest of a month at the usual rate rather than scaling one-offs`() {
        val history = listOf(
            txn("2026-08-12", 3_000, "Big Bazaar", CategoryIds.GROCERIES),
            txn("2026-09-15", 3_000, "Big Bazaar", CategoryIds.GROCERIES),
        )
        val soFar = listOf(txn("2026-10-02", 1_000, "Big Bazaar", CategoryIds.GROCERIES))

        val insights = SpendingAnalyzer.analyze(history + soFar, categories, october, LocalDate.of(2026, 10, 10), zone)

        // ₹1,000 so far plus the usual ₹3,000 a month for the 21 of 31 days left.
        assertEquals(100_000L + 300_000L * 21 / 31, insights.projectedSpendPaise)
    }

    @Test
    fun `flags heavy cash and weekend spending`() {
        val month = listOf(
            txn("2026-10-07", 3_000, "ATM withdrawal", CategoryIds.CASH),
            txn("2026-10-10", 2_500, "PVR Inox", CategoryIds.ENTERTAINMENT), // Saturday
            txn("2026-10-11", 1_500, "Social", CategoryIds.FOOD), // Sunday
            txn("2026-10-14", 500, "Bescom", CategoryIds.BILLS),
        )

        val insights = SpendingAnalyzer.analyze(month, categories, october, today = LocalDate.of(2026, 11, 15), zone = zone)

        val cash = insights.tips.filterIsInstance<SavingTip.CashHeavy>().single()
        assertEquals(300_000L, cash.totalPaise)
        val weekend = insights.tips.filterIsInstance<SavingTip.WeekendHeavy>().single()
        assertEquals(4_000f / 7_500f, weekend.share, 0.001f)
        assertEquals(60_000L, weekend.monthlySavingPaise)
        // No history yet, so nothing to compare with.
        assertNull(insights.usualSpendPaise)
        assertTrue(insights.categoryTrends.all { it.change == null })
    }

    @Test
    fun `doesn't suggest cutting health or education spending`() {
        val usual = listOf("2026-08-12", "2026-09-15").flatMap { day ->
            listOf(txn(day, 1_000, "Apollo Pharmacy", CategoryIds.HEALTH), txn(day, 2_000, "Coursera", CategoryIds.EDUCATION))
        }
        val october = listOf(
            txn("2026-10-16", 6_595, "Pet Hospital", CategoryIds.HEALTH),
            txn("2026-10-12", 9_000, "Exam Fees", CategoryIds.EDUCATION),
        )

        val insights = SpendingAnalyzer.analyze(usual + october, categories, this.october, LocalDate.of(2026, 11, 15), zone)

        assertTrue(insights.tips.none { it is SavingTip.CategoryAboveUsual })
        // Still shown against the usual, just not as something to cut.
        assertEquals(5.595f, insights.categoryTrends.single { it.category.id == CategoryIds.HEALTH }.change!!, 0.001f)
    }

    @Test
    fun `leaves autopay setup charges out of subscriptions`() {
        val month = listOf(
            txn("2026-10-07", 649, "Netflix", CategoryIds.SUBSCRIPTIONS),
            txn("2026-10-09", 2, "LinkedIn", CategoryIds.SUBSCRIPTIONS),
        )

        val tip = SpendingAnalyzer.analyze(month, categories, october, LocalDate.of(2026, 11, 15), zone)
            .tips.filterIsInstance<SavingTip.Subscriptions>().single()

        assertEquals(listOf("Netflix"), tip.names)
    }

    @Test
    fun `compares a month in progress by where it's heading`() {
        val history = listOf(
            txn("2026-08-12", 3_000, "Myntra", CategoryIds.SHOPPING),
            txn("2026-09-15", 3_000, "Myntra", CategoryIds.SHOPPING),
        )
        val soFar = listOf(txn("2026-10-02", 100, "Myntra", CategoryIds.SHOPPING))

        val shopping = SpendingAnalyzer.analyze(history + soFar, categories, october, LocalDate.of(2026, 10, 18), zone)
            .categoryTrends.single()

        assertEquals(10_000L, shopping.amountPaise)
        // ₹100 so far plus the usual ₹3,000 for the 13 of 31 days left, and the change is measured from that.
        val heading = 10_000L + 300_000L * 13 / 31
        assertEquals(heading, shopping.projectedPaise)
        assertEquals(heading, shopping.comparedPaise)
        assertEquals((heading - 300_000L).toFloat() / 300_000L, shopping.change!!, 0.0001f)

        // A finished month is compared as it is.
        val september = SpendingAnalyzer.analyze(history, categories, YearMonth.of(2026, 9), LocalDate.of(2026, 10, 18), zone)
            .categoryTrends.single()
        assertNull(september.projectedPaise)
        assertEquals(300_000L, september.comparedPaise)
    }
}
