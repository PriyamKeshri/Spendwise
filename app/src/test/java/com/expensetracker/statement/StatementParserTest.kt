package com.expensetracker.statement

import com.expensetracker.data.db.CategoryIds
import com.expensetracker.data.db.DefaultCategories
import com.expensetracker.model.CategoryKind
import com.expensetracker.model.TxnType
import com.expensetracker.sms.Categorizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

class StatementParserTest {

    private val zone = ZoneId.of("Asia/Kolkata")

    private fun localTime(millis: Long): LocalDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), zone)

    @Test
    fun `reads a Paytm style statement with title rows, signed amounts and tags`() {
        val rows = listOf(
            listOf("Paytm UPI Statement"),
            listOf("For 01 Oct 2025 to 31 Oct 2025"),
            listOf(
                "Date", "Time", "Transaction Details", "Other Transaction Details (UPI ID or A/c No)", "Your Account",
                "Amount", "UPI Ref No.", "Order ID", "Remarks", "Tags",
            ),
            listOf("10/10/2025", "1:00 PM", "Paid to SWIGGY", "swiggy@icici", "State Bank Of India - 21", "- Rs.380.00", "528312345607", "", "", "#Food"),
            listOf("11/10/2025", "08:15 AM", "Paid to Chai Point", "chaipoint@paytm", "UPI Lite", "-20", "528412345608", "", "", ""),
            listOf("11/10/2025", "08:00 AM", "Added to UPI Lite", "", "State Bank Of India - 21", "-500", "528412345601", "", "", ""),
            listOf("12/10/2025", "6:30 PM", "Received from RAHUL KUMAR", "9876543210@ptsbi", "State Bank Of India - 21", "+ ₹750.00", "528512345609", "", "Dinner", ""),
            listOf("Total", "", "", "", "", "-400", "", "", "", ""),
        )

        val parsed = StatementParser.parse(rows, zone)

        assertEquals(3, parsed.entries.size)
        assertEquals(1, parsed.skippedLiteTopUps)
        assertEquals(0, parsed.unreadableRows)

        val swiggy = parsed.entries[0]
        assertEquals(TxnType.DEBIT, swiggy.type)
        assertEquals(38_000L, swiggy.amountPaise)
        assertEquals("Swiggy", swiggy.counterparty)
        assertEquals("swiggy@icici", swiggy.vpa)
        assertEquals("528312345607", swiggy.refNo)
        assertEquals("SBI", swiggy.bank)
        assertEquals("21", swiggy.accountTail)
        assertEquals(listOf("Food"), swiggy.tags)
        assertEquals(LocalDateTime.of(2025, 10, 10, 13, 0), localTime(swiggy.timestamp))
        assertFalse(swiggy.viaUpiLite)

        val chai = parsed.entries[1]
        assertTrue(chai.viaUpiLite)
        assertEquals("UPI Lite", chai.bank)
        assertEquals(2_000L, chai.amountPaise)

        val received = parsed.entries[2]
        assertEquals(TxnType.CREDIT, received.type)
        assertEquals(75_000L, received.amountPaise)
        assertEquals("Rahul Kumar", received.counterparty)
        assertTrue(received.personTransfer)
        assertTrue(received.summary.contains("Remarks: Dinner"))
    }

    @Test
    fun `reads debit and credit columns, Excel dates, and skips failed payments`() {
        val rows = listOf(
            listOf("Txn Date", "Description", "Ref No", "Debit", "Credit", "Status"),
            listOf("45940.5416666667", "Paid to Zomato", "528312345611", "250.00", "", "SUCCESS"),
            listOf("45941", "Refund from Amazon", "528312345612", "", "1,299.00", "Success"),
            listOf("45942.25", "Paid to Uber", "528312345613", "180.00", "", "FAILED"),
        )

        val parsed = StatementParser.parse(rows, zone)

        assertEquals(2, parsed.entries.size)
        assertEquals(1, parsed.skippedNotCompleted)
        assertEquals(LocalDateTime.of(2025, 10, 10, 13, 0), localTime(parsed.entries[0].timestamp))
        assertEquals(TxnType.DEBIT, parsed.entries[0].type)
        assertEquals(25_000L, parsed.entries[0].amountPaise)
        assertEquals(TxnType.CREDIT, parsed.entries[1].type)
        assertEquals(129_900L, parsed.entries[1].amountPaise)
        assertEquals("Amazon", parsed.entries[1].counterparty)
    }

    @Test
    fun `takes the direction from the description when the amount has no sign`() {
        val rows = listOf(
            listOf("Date & Time", "Transaction Details", "Amount"),
            listOf("15 Oct 2025, 09:41 PM", "Money sent to Priya", "₹1,000"),
            listOf("16 Oct 2025, 10:05 AM", "Payment received from Acme Pvt Ltd", "₹5,000"),
            listOf("17 Oct 2025, 11:00 AM", "Something unclear", "₹70"),
        )

        val parsed = StatementParser.parse(rows, zone)

        assertEquals(2, parsed.entries.size)
        assertEquals(1, parsed.unreadableRows)
        assertEquals(TxnType.DEBIT, parsed.entries[0].type)
        assertTrue(parsed.entries[0].personTransfer)
        assertEquals(LocalDateTime.of(2025, 10, 15, 21, 41), localTime(parsed.entries[0].timestamp))
        assertEquals(TxnType.CREDIT, parsed.entries[1].type)
        assertNull(parsed.entries[1].refNo)
    }

    @Test
    fun `names autopays and recharges after the merchant`() {
        val rows = listOf(
            listOf("Date", "Time", "Transaction Details", "Amount"),
            listOf("14/10/2026", "7:11 AM", "Automatic payment for Youtube", "-59"),
            listOf("25/09/2026", "5:07 PM", "Automatic payment of ₹2383.43 setup for LinkedIn", "-2"),
            listOf("15/10/2026", "2:24 PM", "Recharge of Jio Mobile 7909016044", "-49"),
        )

        val names = StatementParser.parse(rows, zone).entries.map { it.counterparty }

        assertEquals(listOf("Youtube", "LinkedIn", "Jio Mobile 7909016044"), names)
    }

    @Test(expected = StatementFormatException::class)
    fun `refuses a file without date and amount columns`() {
        StatementParser.parse(listOf(listOf("Name", "Phone"), listOf("Rahul", "98765")), zone)
    }

    @Test
    fun `maps Paytm tags to categories by their first word`() {
        val categories = DefaultCategories.all
        assertEquals(CategoryIds.FOOD, StatementImporter.categoryForTag("Food", categories, CategoryKind.EXPENSE))
        assertEquals(CategoryIds.BILLS, StatementImporter.categoryForTag("Bills & Recharges", categories, CategoryKind.EXPENSE))
        assertEquals(CategoryIds.SALARY, StatementImporter.categoryForTag("salary", categories, CategoryKind.INCOME))
        assertNull(StatementImporter.categoryForTag("Personal", categories, CategoryKind.EXPENSE))
        assertNull(StatementImporter.categoryForTag("Food", categories, CategoryKind.INCOME))
    }

    @Test
    fun `maps Paytm's own tag names that don't match a category's`() {
        val categories = DefaultCategories.all
        fun expense(tag: String) = StatementImporter.categoryForTag(tag, categories, CategoryKind.EXPENSE)
        fun income(tag: String) = StatementImporter.categoryForTag(tag, categories, CategoryKind.INCOME)

        assertEquals(CategoryIds.BILLS, expense("Bill Payments"))
        assertEquals(CategoryIds.HEALTH, expense("Medical"))
        assertEquals(CategoryIds.TRANSPORT, expense("Taxi"))
        assertEquals(CategoryIds.TRANSFERS, expense("Money Transfer"))
        assertEquals(CategoryIds.TRANSFERS_IN, income("Money Received"))
        assertEquals(CategoryIds.TRANSFERS_IN, income("Money Transfer"))
        assertEquals(CategoryIds.REFUNDS, income("↩️ Refund"))

        // Paytm's catch-alls are left to the keyword rules, and a tag only fits one kind of payment.
        assertNull(expense("Miscellaneous"))
        assertNull(expense("Services"))
        assertNull(expense("Money Received"))
        assertNull(income("Medical"))

        // A category the user made wins, and a deleted one isn't brought back.
        val withOwn = categories + categories.first { it.id == CategoryIds.HEALTH }.copy(id = 99, name = "Medical bills")
        assertEquals(99L, StatementImporter.categoryForTag("Medical", withOwn, CategoryKind.EXPENSE))
        assertNull(StatementImporter.categoryForTag("Medical", categories.filter { it.id != CategoryIds.HEALTH }, CategoryKind.EXPENSE))
    }

    @Test
    fun `keeps subscriptions that Paytm tags as bill payments`() {
        val categories = DefaultCategories.all
        val categorizer = Categorizer(categories, emptyList())
        fun category(name: String, vpa: String, tag: String) = StatementImporter.categorize(
            StatementEntry(
                timestamp = 0, type = TxnType.DEBIT, amountPaise = 5_900, counterparty = name, vpa = vpa, refNo = null,
                bank = "SBI", accountTail = "24", viaUpiLite = false, personTransfer = false, tags = listOf(tag), summary = "",
            ),
            name, categories, categorizer,
        )

        assertEquals(CategoryIds.SUBSCRIPTIONS to false, category("Automatic payment for Youtube", "youtube1.bd@axisbank", "Bill Payments"))
        assertEquals(CategoryIds.BILLS to true, category("Recharge of Jio Mobile", "paybil3066@ptybl", "Bill Payments"))
        assertEquals(CategoryIds.HEALTH to true, category("Apollo Pharmacy", "apollo@icici", "Medical"))
    }

    @Test
    fun `keeps a refund apart from the payment whose reference it shares`() {
        val payment = StatementEntry(
            timestamp = 0, type = TxnType.DEBIT, amountPaise = 58_860, counterparty = "IRCTC Ticketing", vpa = null,
            refNo = "616729022131", bank = "SBI", accountTail = "24", viaUpiLite = false, personTransfer = false,
            tags = listOf("Travel"), summary = "",
        )
        val refund = payment.copy(timestamp = 86_400_000, type = TxnType.CREDIT, counterparty = "Paytm", tags = listOf("Refund"))

        assertFalse(StatementImporter.importKey(payment) == StatementImporter.importKey(refund))
        assertEquals(StatementImporter.importKey(payment), StatementImporter.importKey(payment.copy()))
    }
}
