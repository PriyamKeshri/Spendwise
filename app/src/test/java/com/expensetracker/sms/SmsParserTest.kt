package com.expensetracker.sms

import com.expensetracker.model.PaymentMode
import com.expensetracker.model.TxnType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsParserTest {

    private fun parsed(sender: String, body: String): ParsedSms {
        val result = SmsParser.parse(sender, body)
        assertNotNull("Expected a transaction from: $body", result)
        return result!!
    }

    // region SBI

    @Test
    fun `SBI UPI debit without a currency symbol`() {
        val p = parsed(
            "VM-SBIUPI",
            "Dear UPI user A/C X1234 debited by 150.0 on date 15Oct24 trf to ZOMATO Refno 428912345678. " +
                "If not u? call 1800111109. -SBI",
        )
        assertEquals(TxnType.DEBIT, p.type)
        assertEquals(15_000L, p.amountPaise)
        assertEquals("Zomato", p.merchant)
        assertEquals(PaymentMode.UPI, p.mode)
        assertEquals("SBI", p.bank)
        assertEquals("1234", p.accountTail)
        assertEquals("428912345678", p.refNo)
        assertFalse(p.personTransfer)
        assertFalse(p.internalTransfer)
    }

    @Test
    fun `SBI UPI debit to a person is flagged as a person transfer`() {
        val p = parsed(
            "JD-SBIUPI-S",
            "Dear UPI user A/C X1234 debited by 20.00 on date 16Oct25 trf to Mr RAHUL KUMAR Refno 528901234567 " +
                "If not u? call-1800111109 for other services-18001234-SBI",
        )
        assertEquals(2_000L, p.amountPaise)
        assertEquals("Rahul Kumar", p.merchant)
        assertTrue(p.personTransfer)
    }

    @Test
    fun `Sri at the start of a shop name does not make it a person`() {
        val p = parsed(
            "VM-SBIUPI",
            "Dear UPI user A/C X1234 debited by 350.0 on date 12Oct26 trf to SRI LAKSHMI ENTERPRISES " +
                "Refno 628512345612. If not u? call 1800111109. -SBI",
        )
        assertEquals("Sri Lakshmi Enterprises", p.merchant)
        assertFalse(p.personTransfer)
    }

    @Test
    fun `UPI Lite top-ups are labelled as such`() {
        val p = parsed(
            "VM-SBIUPI",
            "Dear UPI user A/C X1234 debited by 500.0 on date 05Oct26 trf to UPI LITE Refno 627800000001. " +
                "If not u? call 1800111109. -SBI",
        )
        assertEquals(TxnType.DEBIT, p.type)
        assertEquals("UPI Lite top-up", p.merchant)
        assertEquals(50_000L, p.amountPaise)
    }

    @Test
    fun `SBI UPI credit with no payer name`() {
        val p = parsed("VM-SBIUPI", "Dear SBI UPI User, ur A/cX1234 credited by Rs500 on 15Oct24 by  (Ref no 428912345678)")
        assertEquals(TxnType.CREDIT, p.type)
        assertEquals(50_000L, p.amountPaise)
        assertEquals("UPI received", p.merchant)
        assertEquals(PaymentMode.UPI, p.mode)
        assertEquals("1234", p.accountTail)
        assertEquals("428912345678", p.refNo)
    }

    @Test
    fun `SBI transfer credit names the sender`() {
        val p = parsed(
            "BZ-SBIINB",
            "Dear SBI User, your A/c X1234-credited by Rs.5000 on 15Oct24 transfer from RAHUL KUMAR " +
                "Ref No 428912345678 -SBI",
        )
        assertEquals(TxnType.CREDIT, p.type)
        assertEquals(500_000L, p.amountPaise)
        assertEquals("Rahul Kumar", p.merchant)
    }

    @Test
    fun `SBI ATM withdrawal`() {
        val p = parsed(
            "CP-ATMSBI",
            "Dear Customer, Rs.2000 withdrawn at SBI ATM S1NB000123001 from A/cX1234 on 15Oct24 Transaction Number 1234. " +
                "Available Balance Rs.12345.67. If not withdrawn by you, forward this SMS to 7208933148 / call 1800111109 / " +
                "please call 1800111109 to block your card. -SBI",
        )
        assertEquals(TxnType.DEBIT, p.type)
        assertEquals(200_000L, p.amountPaise)
        assertEquals(PaymentMode.ATM, p.mode)
        assertEquals("ATM withdrawal", p.merchant)
        assertEquals("1234", p.accountTail)
    }

    @Test
    fun `SBI account debit ignores the available balance`() {
        val p = parsed(
            "VM-CBSSBI",
            "Dear Customer, Your A/C XXXXX1234 has a debit by transfer of Rs 2,000.00 on 15/10/24. " +
                "Avl Bal Rs 12,345.67.-SBI",
        )
        assertEquals(TxnType.DEBIT, p.type)
        assertEquals(200_000L, p.amountPaise)
    }

    @Test
    fun `SBI NEFT salary credit`() {
        val p = parsed(
            "VM-SBIINB",
            "Dear Customer, INR 50,000.00 credited to your A/c No XX1234 on 01/10/2024 through NEFT with UTR " +
                "SBIN124275123456 by ABC LTD, INFO: SALARY OCT. Avl Bal INR 62,345.67",
        )
        assertEquals(TxnType.CREDIT, p.type)
        assertEquals(5_000_000L, p.amountPaise)
        assertEquals("ABC LTD", p.merchant)
        assertEquals(PaymentMode.NET_BANKING, p.mode)
        assertEquals("SBIN124275123456", p.refNo)
    }

    @Test
    fun `SBI charges name what they were for`() {
        val p = parsed(
            "VM-CBSSBI",
            "Your A/C XXXXX1234 has been debited by Rs.236.00 on 15/10/24 towards SMS charges",
        )
        assertEquals(23_600L, p.amountPaise)
        assertEquals("SMS charges", p.merchant)
    }

    @Test
    fun `SBI credit card spend`() {
        val p = parsed(
            "AD-SBICRD",
            "Rs.1,234.00 spent on your SBI Credit Card ending 1234 at FLIPKART on 15/10/24. " +
                "Trxn. not done by you? Report at https://sbicard.com/Dispute",
        )
        assertEquals(TxnType.DEBIT, p.type)
        assertEquals(123_400L, p.amountPaise)
        assertEquals("Flipkart", p.merchant)
        assertEquals(PaymentMode.CARD, p.mode)
        assertEquals("SBI Card", p.bank)
        assertEquals("1234", p.accountTail)
        assertFalse(p.internalTransfer)
    }

    @Test
    fun `credit card bill payments are internal transfers`() {
        val paidFromAccount = parsed(
            "VM-SBIUPI",
            "Dear UPI user A/C X1234 debited by 10000.0 on date 05Oct24 trf to CRED Club Refno 427812345678. " +
                "If not u? call 1800111109. -SBI",
        )
        assertTrue(paidFromAccount.internalTransfer)

        val receivedOnCard = parsed(
            "AD-SBICRD",
            "We have received payment of Rs.10,000.00 via BBPS & the same has been credited to your " +
                "SBI Credit Card ending 1234 on 05/10/24",
        )
        assertEquals(TxnType.CREDIT, receivedOnCard.type)
        assertTrue(receivedOnCard.internalTransfer)
    }

    // endregion

    // region Other banks

    @Test
    fun `HDFC multi-line UPI alert`() {
        val p = parsed(
            "VM-HDFCBK",
            "Sent Rs.250.00\nFrom HDFC Bank A/C *1234\nTo ZOMATO\nOn 15/10/24\nRef 428912345678\nNot You?\n" +
                "Call 18002586161/SMS BLOCK UPI to 7308080808",
        )
        assertEquals(25_000L, p.amountPaise)
        assertEquals("Zomato", p.merchant)
        assertEquals(PaymentMode.UPI, p.mode)
        assertEquals("HDFC Bank", p.bank)
        assertEquals("1234", p.accountTail)
        assertEquals("428912345678", p.refNo)
    }

    @Test
    fun `HDFC alert with a UPI ID`() {
        val p = parsed(
            "VM-HDFCBK",
            "Rs.500.00 debited from a/c **1234 on 15-10-24 to VPA swiggy@icici(UPI Ref No 428912345678). " +
                "Not you? Call on 18002586161 to report",
        )
        assertEquals("Swiggy", p.merchant)
        assertEquals("swiggy@icici", p.vpa)
        assertEquals("428912345678", p.refNo)
    }

    @Test
    fun `HDFC card spend`() {
        val p = parsed(
            "VM-HDFCBK",
            "Spent Rs.1,299.00 On HDFC Bank Card 1234 At AMAZON PAY INDIA On 2024-10-15:12:34:56. Not You? " +
                "To Block+Reissue Call 18002323232/SMS BLOCK CC 1234 to 7308080808",
        )
        assertEquals(129_900L, p.amountPaise)
        assertEquals("Amazon Pay India", p.merchant)
        assertEquals(PaymentMode.CARD, p.mode)
        assertFalse(p.internalTransfer)
    }

    @Test
    fun `ICICI debit names the merchant before credited`() {
        val p = parsed(
            "JD-ICICIT",
            "ICICI Bank Acct XX123 debited for Rs 250.00 on 15-Oct-24; SWIGGY credited. UPI:428912345678. " +
                "Call 18002662 for dispute. SMS BLOCK 123 to 9215676766.",
        )
        assertEquals(TxnType.DEBIT, p.type)
        assertEquals("Swiggy", p.merchant)
        assertEquals("123", p.accountTail)
        assertEquals("428912345678", p.refNo)
    }

    @Test
    fun `Axis UPI path format`() {
        val p = parsed(
            "AX-AXISBK",
            "INR 250.00 debited\nA/c no. XX1234\n15-10-24, 12:34:56\nUPI/P2M/428912345678/SWIGGY\n" +
                "Not you? SMS BLOCKUPI Cust ID to 919951860002\nAxis Bank",
        )
        assertEquals(25_000L, p.amountPaise)
        assertEquals("Swiggy", p.merchant)
        assertEquals("428912345678", p.refNo)
        assertEquals("1234", p.accountTail)
    }

    @Test
    fun `Kotak credit from a UPI ID`() {
        val p = parsed(
            "VM-KOTAKB",
            "Received Rs.500.00 in your Kotak Bank AC X1234 from rahul.kumar@okaxis on 15-10-24.UPI Ref:428912345678.",
        )
        assertEquals(TxnType.CREDIT, p.type)
        assertEquals("Rahul Kumar", p.merchant)
        assertEquals("rahul.kumar@okaxis", p.vpa)
    }

    // endregion

    // region Messages that are not transactions

    @Test
    fun `ignores OTPs`() {
        assertNull(
            SmsParser.parse(
                "VM-SBIINB",
                "OTP for txn of Rs 500.00 at AMAZON on SBI card ending 1234 is 123456. Do not share it with anyone.",
            ),
        )
    }

    @Test
    fun `ignores personal numbers`() {
        assertNull(SmsParser.parse("+919876543210", "Sent Rs 500 to your A/c XX1234 for dinner"))
    }

    @Test
    fun `ignores the promotional route`() {
        assertNull(SmsParser.parse("JD-SBIINB-P", "Rs 5,000 cashback credited on spends with your A/c XX1234"))
    }

    @Test
    fun `ignores upcoming debits`() {
        assertNull(SmsParser.parse("AD-SBICRD", "Your SBI Card ending 1234 will be debited Rs.199 on 20/10/24 for Netflix"))
    }

    @Test
    fun `ignores failed payments`() {
        assertNull(
            SmsParser.parse(
                "VM-SBIINB",
                "Txn of Rs 500 at AMAZON on your SBI Debit Card XX1234 failed due to insufficient balance",
            ),
        )
    }

    @Test
    fun `ignores collect requests`() {
        assertNull(
            SmsParser.parse("VM-SBIUPI", "RAHUL has requested money of Rs 500 from your A/C X1234 on UPI. Ignore if unknown."),
        )
    }

    @Test
    fun `ignores bill reminders`() {
        assertNull(SmsParser.parse("AD-SBICRD", "Total Amt Due Rs.12,345.00 on your SBI Card XX1234 is due on 20/10/24"))
    }

    @Test
    fun `ignores merchant promotions without an account`() {
        assertNull(SmsParser.parse("AD-SWIGGY", "You paid less! Get Rs 100 off on orders above Rs 300. Order now"))
    }

    // endregion
}
