package com.expensetracker.statement

import com.expensetracker.model.PaymentMode
import com.expensetracker.model.TxnSource
import com.expensetracker.model.TxnType
import com.expensetracker.sms.SmsParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

class BankStatementTest {

    private val zone = ZoneId.of("Asia/Kolkata")

    /** SBI's ".xls" download is tab-separated text, with account details above the table. Made-up data. */
    private val sbiStatement = listOf(
        "Account Name\t:\tMr. Sample User",
        "Address\t:\t12, MG Road, Near Clock Tower, Ranchi, Jharkhand",
        "Date\t:\t13 Nov 2026",
        "Account Number\t:\t00000012345678901",
        "Account Description\t:\tREGULAR SB CHQ-INDIVIDUALS",
        "IFS Code\t:\tSBIN0001234",
        "Balance as on 1 Oct 2026\t:\t12,345.67",
        "",
        "Txn Date\tValue Date\tDescription\tRef No./Cheque No.\t        Debit\tCredit\tBalance",
        "1 Oct 2026\t1 Oct 2026\tBY TRANSFER-NEFT*HDFC0000001*N123456789*ACME LTD*SALARY OCT--\tTRANSFER FROM 3199412044306\t \t50,000.00\t62,345.67",
        "5 Oct 2026\t5 Oct 2026\tTO TRANSFER-UPI/DR/629000000008/SWIGGY/YESB/swiggy.stores/UPI--\tTRANSFER TO 4897694162092\t420.00\t \t61,925.67",
        "9 Oct 2026\t9 Oct 2026\tTO TRANSFER-UPI/DR/631000000003/CRED Club/AXIS/cred.club@axisb/CC Bill--\tTRANSFER TO 4897694162092\t5,000.00\t \t56,925.67",
        "18 Oct 2026\t18 Oct 2026\tATM WDL-ATM CASH 1234 SBI RANCHI MAIN\t \t2,000.00\t \t54,925.67",
        "20 Oct 2026\t20 Oct 2026\tBY TRANSFER-UPI/CR/631000000004/AMIT KUM/SBIN/9876543210@ybl/UPI--\tTRANSFER FROM 4897694162092\t \t1,500.00\t56,425.67",
        "22 Oct 2026\t22 Oct 2026\tCREDIT INTEREST--\t \t \t312.00\t56,737.67",
        "25 Oct 2026\t25 Oct 2026\tTO TRANSFER-UPI/DR/631000000005/UPI LITE/SBIN/upilite@sbi/Add Money--\tTRANSFER TO 4897694162092\t500.00\t \t56,237.67",
        "**This is a computer generated statement and does not require a signature",
    ).joinToString("\n")

    private fun parsed() = StatementParser.parse(SheetReader.read(sbiStatement.toByteArray()), zone)

    @Test
    fun `reads SBI's tab-separated statement despite the commas in the address`() {
        val statement = parsed()

        assertEquals(StatementKind.BANK, statement.kind)
        assertEquals("SBI", statement.bank)
        assertEquals(7, statement.entries.size)
        assertTrue(statement.entries.all { it.bank == "SBI" && it.accountTail == "8901" && !it.hasTime })
        assertEquals(0, statement.unreadableRows)
        // No time of day, so noon on the date.
        assertEquals(LocalDateTime.of(2026, 10, 5, 12, 0), localTime(statement.entries[1].timestamp))
    }

    @Test
    fun `takes the payee and UPI reference from the description`() {
        val (salary, swiggy, cred, atm, fromAmit, interest, lite) = parsed().entries

        assertEquals(TxnType.CREDIT, salary.type)
        assertEquals(5_000_000L, salary.amountPaise)
        assertEquals("Acme LTD", salary.counterparty)
        assertEquals(PaymentMode.NET_BANKING, salary.mode)

        assertEquals(TxnType.DEBIT, swiggy.type)
        assertEquals(42_000L, swiggy.amountPaise)
        assertEquals("Swiggy", swiggy.counterparty)
        assertEquals("629000000008", swiggy.refNo)
        assertEquals("swiggy.stores", swiggy.vpa)
        assertEquals(PaymentMode.UPI, swiggy.mode)

        assertEquals("CRED Club", cred.counterparty)
        assertTrue(SmsParser.isCardBillPayment("${cred.counterparty} ${cred.description}", cred.type))
        assertFalse(SmsParser.isCardBillPayment("${swiggy.counterparty} ${swiggy.description}", swiggy.type))

        assertEquals(PaymentMode.ATM, atm.mode)
        assertNull(atm.counterparty)
        assertNull(atm.refNo) // the reference column holds account numbers, not references

        assertEquals("Amit Kum", fromAmit.counterparty)
        assertTrue(fromAmit.personTransfer)
        assertEquals("Interest", interest.counterparty)

        // A bank statement keeps Lite top-ups: it never lists the Lite payments themselves.
        assertEquals(BankNarration.LITE_TOP_UP, lite.counterparty)
        assertFalse(lite.viaUpiLite)
    }

    @Test
    fun `reads other banks' UPI lines loosely`() {
        val hdfc = BankNarration.read("UPI-ZOMATO LTD-zomato-order@ptybl-YESB0PTMUPI-432101234567-PAYMENT FROM PHONE")
        assertEquals("Zomato LTD", hdfc.name)
        assertEquals("432101234567", hdfc.ref)
        assertEquals("zomato-order@ptybl", hdfc.vpa)

        val slashes = BankNarration.read("UPI/432109876543/Dinner/rahul.k@okaxis/ICICI Bank/ICI1234")
        assertEquals("432109876543", slashes.ref)
        assertEquals("Dinner", slashes.name)
        assertEquals("rahul.k@okaxis", slashes.vpa)
    }

    @Test
    fun `gives rows that would share a key their own`() {
        val charge = parsed().entries.single { it.counterparty == "Interest" }.copy(type = TxnType.DEBIT, counterparty = "SMS charges")
        val keys = StatementImporter.importKeys(listOf(charge, charge, parsed().entries[1]), TxnSource.BANK)

        assertEquals(3, keys.toSet().size)
        assertEquals(keys[0] + "#2", keys[1])
        assertEquals("bank:629000000008:DEBIT", keys[2])
    }

    private fun localTime(millis: Long): LocalDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), zone)

    private operator fun <T> List<T>.component6() = this[5]
    private operator fun <T> List<T>.component7() = this[6]
}
