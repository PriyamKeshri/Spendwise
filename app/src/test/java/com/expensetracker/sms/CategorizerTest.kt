package com.expensetracker.sms

import com.expensetracker.data.db.CategoryIds
import com.expensetracker.data.db.DefaultCategories
import com.expensetracker.data.db.MerchantRuleEntity
import com.expensetracker.model.PaymentMode
import com.expensetracker.model.TxnType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CategorizerTest {

    private val categorizer = Categorizer(DefaultCategories.all, emptyList())

    private fun debit(
        merchant: String,
        mode: PaymentMode = PaymentMode.UPI,
        vpa: String? = null,
        person: Boolean = false,
        internal: Boolean = false,
    ) = CategorizerInput(TxnType.DEBIT, mode, merchant, vpa, body = "", personTransfer = person, internalTransfer = internal)

    private fun credit(merchant: String, body: String = "", mode: PaymentMode = PaymentMode.NET_BANKING) =
        CategorizerInput(TxnType.CREDIT, mode, merchant, vpa = null, body = body, personTransfer = false, internalTransfer = false)

    @Test
    fun `known merchants land in their category`() {
        assertEquals(CategoryIds.FOOD, categorizer.categorize(debit("Zomato")))
        assertEquals(CategoryIds.GROCERIES, categorizer.categorize(debit("Swiggy Instamart")))
        assertEquals(CategoryIds.SHOPPING, categorizer.categorize(debit("Amazon Pay India")))
        assertEquals(CategoryIds.SUBSCRIPTIONS, categorizer.categorize(debit("Amazon Prime")))
        assertEquals(CategoryIds.SUBSCRIPTIONS, categorizer.categorize(debit("Netflix Entertainm")))
        assertEquals(CategoryIds.TRANSPORT, categorizer.categorize(debit("Uber India")))
        assertEquals(CategoryIds.FUEL, categorizer.categorize(debit("Indian Oil Petrol Pump")))
        assertEquals(CategoryIds.HEALTH, categorizer.categorize(debit("Apollo Pharmacy")))
        assertEquals(CategoryIds.TRAVEL, categorizer.categorize(debit("IRCTC")))
    }

    @Test
    fun `unknown merchants go to Others`() {
        assertEquals(CategoryIds.OTHERS, categorizer.categorize(debit("Sharma Traders")))
        assertEquals(CategoryIds.OTHERS, categorizer.categorize(debit("UPI payment")))
    }

    @Test
    fun `keywords match whole words only`() {
        // "ola" (Transport) must not match inside "Cola".
        assertEquals(CategoryIds.OTHERS, categorizer.categorize(debit("Coca Cola Beverages")))
    }

    @Test
    fun `the UPI ID is used when the name is missing`() {
        assertEquals(CategoryIds.FOOD, categorizer.categorize(debit("UPI payment", vpa = "zomato-order@ptybl")))
    }

    @Test
    fun `ATM withdrawals person transfers and own-account transfers`() {
        assertEquals(CategoryIds.CASH, categorizer.categorize(debit("ATM withdrawal", mode = PaymentMode.ATM)))
        assertEquals(CategoryIds.TRANSFERS, categorizer.categorize(debit("Rahul Kumar", person = true)))
        assertEquals(CategoryIds.TRANSFERS, categorizer.categorize(debit("Cred Club", internal = true)))
    }

    @Test
    fun `a learned merchant rule beats keywords`() {
        val learned = Categorizer(
            DefaultCategories.all,
            listOf(MerchantRuleEntity("sharma traders", CategoryIds.GROCERIES, "Sharma Traders")),
        )
        assertEquals(CategoryIds.GROCERIES, learned.categorize(debit("SHARMA TRADERS PVT")))
    }

    @Test
    fun `credits are categorised from the message body`() {
        assertEquals(CategoryIds.SALARY, categorizer.categorize(credit("ABC LTD", body = "by ABC LTD, INFO: SALARY OCT")))
        assertEquals(CategoryIds.REFUNDS, categorizer.categorize(credit("Amazon", body = "Refund of Rs.499 from AMAZON")))
        assertEquals(CategoryIds.TRANSFERS_IN, categorizer.categorize(credit("UPI received", mode = PaymentMode.UPI)))
        assertEquals(CategoryIds.OTHER_INCOME, categorizer.categorize(credit("Bank transfer received")))
    }

    @Test
    fun `rule keys skip generic labels`() {
        assertEquals("zomato", Categorizer.ruleKeyFor("Zomato", null))
        assertEquals("q123456789", Categorizer.ruleKeyFor("UPI payment", "q123456789@ybl"))
        assertNull(Categorizer.ruleKeyFor("UPI payment", null))
    }
}
