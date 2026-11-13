package com.expensetracker.statement

import com.expensetracker.model.PaymentMode
import com.expensetracker.sms.SmsParser

/** What a bank statement's description says about one entry. */
internal data class Narration(
    val name: String?,
    val ref: String?,
    val vpa: String?,
    val mode: PaymentMode,
    /** Money moved into UPI Lite. A bank statement never lists the Lite payments themselves. */
    val liteTopUp: Boolean,
    /** Whether the line had a known shape. Otherwise it's plain text, and [name] is left to the caller. */
    val structured: Boolean = true,
)

/**
 * Reads the description column of a bank statement. SBI writes a UPI payment as
 * "TO TRANSFER-UPI/DR/628906753303/CHAURASI/YESB/q025867483/UPI--" (the payee's name cut short), NEFT as
 * "BY TRANSFER-NEFT*HDFC0000001*N123456789*ACME LTD*SALARY--" and cash as "ATM WDL-ATM CASH 1234 ...".
 * Other banks' UPI lines are read more loosely: the 12-digit UPI reference, any UPI ID, and the first
 * word-like part as the name.
 */
internal object BankNarration {

    fun read(text: String): Narration {
        val clean = text.replace(SPACES, " ").trim()
        val liteTopUp = LITE.containsMatchIn(clean)

        SBI_UPI.find(clean)?.let { m ->
            val handle = m.groupValues[4].trim().lowercase().takeIf { it.any(Char::isLetterOrDigit) }
            val name = if (liteTopUp) LITE_TOP_UP else cutName(m.groupValues[2])
            return Narration(name, m.groupValues[1], handle, PaymentMode.UPI, liteTopUp)
        }
        if (UPI.containsMatchIn(clean)) {
            // "UPI-NAME-VPA-IFSC-REF-..." (HDFC): the UPI ID sits between the name and the IFSC, hyphens and all.
            val vpa = (HYPHENATED_VPA.find(clean)?.groupValues?.get(1) ?: VPA.find(clean)?.value)?.lowercase()
            val name = if (liteTopUp) LITE_TOP_UP else looseUpiName(clean)
            return Narration(name, UPI_REF.find(clean)?.value, vpa, PaymentMode.UPI, liteTopUp)
        }
        TRANSFER_STARS.find(clean)?.let { m ->
            return Narration(name(m.groupValues[2]), m.groupValues[1].trim().takeIf { it.length >= 6 }, null, PaymentMode.NET_BANKING, false)
        }
        val mode = when {
            ATM.containsMatchIn(clean) -> PaymentMode.ATM
            CARD.containsMatchIn(clean) -> PaymentMode.CARD
            NETBANKING.containsMatchIn(clean) -> PaymentMode.NET_BANKING
            else -> PaymentMode.OTHER
        }
        return when {
            mode == PaymentMode.ATM -> Narration(null, null, null, mode, false) // the importer calls it an ATM withdrawal
            INTEREST.containsMatchIn(clean) -> Narration("Interest", null, null, mode, false)
            else -> Narration(null, null, null, mode, false, structured = false)
        }
    }

    /** Payee names come cut short and in capitals ("ZOMATO L"); tidy them, but don't guess the rest. */
    private fun name(raw: String): String? =
        raw.replace(SPACES, " ").trim().trim('-', '*', '/', '.', ' ')
            .takeIf { n -> n.count(Char::isLetter) >= 2 }
            ?.take(MAX_NAME)
            ?.let(SmsParser::prettify)

    /**
     * SBI cuts UPI payee names to 8 characters, so a short last word is usually a cut-off one ("AMIT KUM"),
     * not an initialism, and reads better as "Amit Kum".
     */
    private fun cutName(raw: String): String? {
        val pretty = name(raw) ?: return null
        if (raw.trim().length != SBI_NAME_LENGTH) return pretty
        val words = pretty.split(' ')
        val last = words.last()
        return if (last.length in 2..3 && last.all(Char::isUpperCase)) {
            (words.dropLast(1) + last.lowercase().replaceFirstChar(Char::titlecase)).joinToString(" ")
        } else {
            pretty
        }
    }

    /** "UPI-ZOMATO LTD-zomato@ptybl-YESB0PTMUPI-432101234567-PAYMENT" or "UPI/432101234567/Payment/..." */
    private fun looseUpiName(text: String): String? = text.split('/', '-')
        .map { it.trim() }
        .firstOrNull { part ->
            part.count(Char::isLetter) >= 3 && part.uppercase() !in NOT_NAMES &&
                !VPA.matches(part) && !IFSC.matches(part) && !part.contains('@')
        }
        ?.let(::name)

    const val LITE_TOP_UP = "UPI Lite top-up"
    private const val MAX_NAME = 40
    private const val SBI_NAME_LENGTH = 8

    private val SPACES = Regex("""\s+""")
    private val SBI_UPI = Regex("""\bUPI/(?:DR|CR)/(\d{9,16})/([^/]*)/([^/]*)/([^/]*)""", RegexOption.IGNORE_CASE)
    private val TRANSFER_STARS = Regex("""\b(?:NEFT|RTGS|IMPS)\*[^*]*\*([^*]*)\*([^*]*)""", RegexOption.IGNORE_CASE)
    private val UPI = Regex("""\bUPI\b""", RegexOption.IGNORE_CASE)
    private val UPI_REF = Regex("""(?<!\d)\d{12}(?!\d)""")
    private val VPA = Regex("""(?i)[a-z0-9][a-z0-9._\-]{1,63}@[a-z][a-z0-9]{1,20}""")
    private val HYPHENATED_VPA = Regex("""(?i)\bUPI-[^-]+-(\S+@[a-z][a-z0-9]{1,20})-[a-z]{4}0[a-z0-9]{6}-""")
    private val IFSC = Regex("""(?i)[a-z]{4}0[a-z0-9]{6}""")
    private val LITE = Regex("""\bupi\s?lite\b""", RegexOption.IGNORE_CASE)
    private val ATM = Regex("""\batm\b.{0,20}\b(?:wdl|cash|withdrawal)\b|\b(?:atm|cash)\s*wdl\b""", RegexOption.IGNORE_CASE)
    private val CARD = Regex("""\b(?:debit card|pos|ecom)\b""", RegexOption.IGNORE_CASE)
    private val NETBANKING = Regex("""\b(?:neft|rtgs|imps|inb|net ?banking)\b""", RegexOption.IGNORE_CASE)
    private val INTEREST = Regex("""\b(?:credit interest|interest credit|int\.? ?pd|sb int)\b""", RegexOption.IGNORE_CASE)
    private val NOT_NAMES = setOf("UPI", "DR", "CR", "P2M", "P2A", "P2P", "PAYMENT", "PAYMENT FROM PHONE", "TRANSFER", "TO TRANSFER", "BY TRANSFER")
}
