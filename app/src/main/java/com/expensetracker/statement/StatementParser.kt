package com.expensetracker.statement

import com.expensetracker.model.PaymentMode
import com.expensetracker.model.TxnType
import com.expensetracker.sms.SmsParser
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.util.Locale
import kotlin.math.floor
import kotlin.math.roundToLong

/** One completed payment or credit read from a statement row. */
data class StatementEntry(
    val timestamp: Long,
    val type: TxnType,
    val amountPaise: Long,
    val counterparty: String?,
    val vpa: String?,
    val refNo: String?,
    val bank: String?,
    val accountTail: String?,
    /** Paid from the UPI Lite balance, so the bank never sent an SMS for it. */
    val viaUpiLite: Boolean,
    val personTransfer: Boolean,
    val tags: List<String>,
    /** The row as "Column: value" lines, shown in the transaction details. */
    val summary: String,
    val mode: PaymentMode = PaymentMode.UPI,
    /** Bank statements give only the date, so the time is noon and matching uses the whole day. */
    val hasTime: Boolean = true,
    /** The row's description as written in the statement. */
    val description: String = "",
)

/** A UPI app's export (like Paytm's) lists each payment with a signed amount; a bank statement has debit and credit columns. */
enum class StatementKind { UPI_APP, BANK }

data class ParsedStatement(
    val kind: StatementKind,
    /** The bank named at the top of a bank statement, like "SBI". */
    val bank: String?,
    val entries: List<StatementEntry>,
    /** Failed, pending or reversed payments, which never moved money. */
    val skippedNotCompleted: Int,
    /** Money moved into UPI Lite: not spending, the Lite payments themselves are. */
    val skippedLiteTopUps: Int,
    /** Rows with a date that couldn't be turned into a transaction. */
    val unreadableRows: Int,
)

/**
 * Reads a UPI app statement (Paytm's Excel export, or similar) or a bank statement (SBI's, or similar).
 * Columns are recognised by their headings rather than their position, so a reordered or renamed
 * column doesn't break the import.
 */
object StatementParser {

    private enum class Field { DATE, TIME, DETAILS, PARTY_ID, ACCOUNT, AMOUNT, DEBIT, CREDIT, DIRECTION, REF, STATUS, TAGS, NOTES }

    fun parse(rows: List<List<String>>, zone: ZoneId = ZoneId.systemDefault()): ParsedStatement {
        val headerIndex = rows.take(HEADER_SEARCH_ROWS).indexOfFirst(::isHeader)
        if (headerIndex < 0) throw StatementFormatException("Couldn't find the date and amount columns.")
        val headers = rows[headerIndex]
        val columns = HashMap<Field, Int>()
        headers.forEachIndexed { index, header -> classify(header)?.let { columns.putIfAbsent(it, index) } }
        // A bank statement names the bank and account above the table.
        val preamble = rows.take(headerIndex).joinToString(" ") { it.joinToString(" ") }
        val statementBank = knownBank(preamble.lowercase())
        // Debit and credit columns alone don't make a bank statement (some apps export that way too);
        // a running balance or a named bank does.
        val hasBalance = headers.any { it.contains("balance", ignoreCase = true) }
        val kind = if ((Field.DEBIT in columns || Field.CREDIT in columns) && (hasBalance || statementBank != null)) {
            StatementKind.BANK
        } else {
            StatementKind.UPI_APP
        }
        val statementTail = PREAMBLE_ACCOUNT.find(preamble)?.groupValues?.get(1)?.takeLast(4)

        val entries = ArrayList<StatementEntry>()
        var notCompleted = 0
        var liteTopUps = 0
        var unreadable = 0

        for (row in rows.drop(headerIndex + 1)) {
            if (row.all { it.isBlank() } || isHeader(row)) continue
            fun cell(field: Field) = columns[field]?.let { row.getOrNull(it) }.orEmpty().trim()

            val timestamp = parseTimestamp(cell(Field.DATE), cell(Field.TIME), zone) ?: continue // totals and footnotes
            val details = cell(Field.DETAILS)
            val account = cell(Field.ACCOUNT)
            val notes = cell(Field.NOTES)

            if (NOT_COMPLETED.containsMatchIn(cell(Field.STATUS).lowercase())) {
                notCompleted++
                continue
            }
            // A UPI app lists the Lite payments themselves, so its top-ups would count them twice. A bank
            // statement doesn't, so there the top-up is the only record of that money.
            if (kind == StatementKind.UPI_APP && LITE_TOP_UP.containsMatchIn("$details $account $notes".lowercase())) {
                liteTopUps++
                continue
            }
            val (type, amountPaise) = amountAndType(
                amount = cell(Field.AMOUNT),
                debit = cell(Field.DEBIT),
                credit = cell(Field.CREDIT),
                direction = cell(Field.DIRECTION),
                details = details,
            ) ?: run {
                unreadable++
                null
            } ?: continue

            val narration = if (kind == StatementKind.BANK) BankNarration.read(details) else null
            val vpa = narration?.vpa ?: (VPA.find(cell(Field.PARTY_ID)) ?: VPA.find(details))?.value?.lowercase()
            val name = if (narration?.structured == true) narration.name else counterpartyName(details, vpa)
            val phoneVpa = vpa?.substringBefore('@')?.let { PHONE.matches(it) } == true
            // SBI's reference column holds "TRANSFER TO <account>", not a payment reference.
            val refColumn = cell(Field.REF).takeUnless { kind == StatementKind.BANK && it.startsWith("TRANSFER", ignoreCase = true) }
            entries += StatementEntry(
                timestamp = timestamp,
                type = type,
                amountPaise = amountPaise,
                counterparty = name,
                vpa = vpa,
                refNo = narration?.ref ?: reference(refColumn.orEmpty()) ?: reference(INLINE_REF.find(details)?.groupValues?.get(1).orEmpty()),
                bank = if (kind == StatementKind.BANK) statementBank ?: knownBank(account.lowercase()) else bankName(account),
                accountTail = statementTail ?: ACCOUNT_TAIL.find(account)?.groupValues?.get(1)?.takeLast(4),
                viaUpiLite = kind == StatementKind.UPI_APP && (
                    account.contains("upi lite", ignoreCase = true) ||
                        (type == TxnType.DEBIT && details.contains("upi lite", ignoreCase = true))
                    ),
                personTransfer = phoneVpa || SENT_TO_PERSON.containsMatchIn(details.lowercase()),
                tags = cell(Field.TAGS).split(',', ';', '|', '#').map { it.trim() }.filter { it.isNotEmpty() },
                summary = summary(headers, row, columns[Field.DATE], columns[Field.TIME], timestamp, zone),
                mode = narration?.mode ?: PaymentMode.UPI,
                hasTime = hasTime(cell(Field.DATE), cell(Field.TIME)),
                description = details,
            )
        }
        return ParsedStatement(kind, statementBank, entries, notCompleted, liteTopUps, unreadable)
    }

    // region Columns

    private fun isHeader(row: List<String>): Boolean {
        val fields = row.mapNotNull(::classify).toSet()
        return fields.size >= 3 && Field.DATE in fields &&
            (Field.AMOUNT in fields || Field.DEBIT in fields || Field.CREDIT in fields)
    }

    private fun classify(header: String): Field? {
        val h = " " + header.lowercase().replace(HEADER_NOISE, " ").replace(SPACES, " ").trim() + " "
        if (h.isBlank()) return null
        fun has(vararg words: String) = words.any { h.contains(" $it ") }
        return when {
            has("order id", "transaction id", "txn id", "wallet txn id", "balance", "closing balance") -> null
            has("ref", "ref no", "reference", "rrn", "utr", "chq/ref no", "upi ref no") || h.contains(" ref ") -> Field.REF
            has("type", "dr/cr", "cr/dr", "debit/credit", "credit/debit") -> Field.DIRECTION
            has("date") -> Field.DATE
            has("time") -> Field.TIME
            has("status") -> Field.STATUS
            has("tag", "tags", "category") -> Field.TAGS
            has("upi id", "vpa", "other transaction details", "counterparty", "payee upi", "beneficiary") -> Field.PARTY_ID
            has("debit", "debits", "withdrawal", "withdrawals", "money out", "paid out", "dr") -> Field.DEBIT
            has("credit", "credits", "deposit", "deposits", "money in", "cr") -> Field.CREDIT
            has("amount", "amt", "value") -> Field.AMOUNT
            has("note", "notes", "comment", "comments", "remark", "remarks", "message") -> Field.NOTES
            has("your account", "account", "a/c", "paid from", "bank account", "payment source", "source") -> Field.ACCOUNT
            has("details", "description", "narration", "particulars", "paid to", "name", "transaction", "transaction details") ->
                Field.DETAILS
            else -> null
        }
    }

    // endregion

    // region Values

    private data class Amount(val paise: Long, val sign: Int)

    private fun amountAndType(amount: String, debit: String, credit: String, direction: String, details: String): Pair<TxnType, Long>? {
        parseAmount(debit)?.takeIf { it.paise > 0 }?.let { return TxnType.DEBIT to it.paise }
        parseAmount(credit)?.takeIf { it.paise > 0 }?.let { return TxnType.CREDIT to it.paise }
        val value = parseAmount(amount)?.takeIf { it.paise > 0 } ?: return null
        val type = when {
            value.sign < 0 -> TxnType.DEBIT
            value.sign > 0 -> TxnType.CREDIT
            else -> directionOf(direction) ?: directionOf(details) ?: return null
        }
        return type to value.paise
    }

    /** "- ₹1,250.50", "+500", "250.00 Dr", "1,23,456" -> paise plus any explicit sign. */
    private fun parseAmount(text: String): Amount? {
        val t = text.trim()
        if (t.isEmpty()) return null
        val lower = t.lowercase()
        val sign = when {
            lower.startsWith("-") || lower.startsWith("(") || DR_MARK.containsMatchIn(lower) -> -1
            lower.startsWith("+") || CR_MARK.containsMatchIn(lower) -> 1
            else -> 0
        }
        val digits = NUMBER.find(t.replace(",", ""))?.value ?: return null
        val paise = runCatching {
            BigDecimal(digits).setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact()
        }.getOrNull() ?: return null
        return Amount(paise, sign)
    }

    private fun directionOf(text: String): TxnType? {
        val t = text.trim().lowercase()
        return when {
            t.isEmpty() -> null
            CREDIT_WORDS.containsMatchIn(t) -> TxnType.CREDIT
            DEBIT_WORDS.containsMatchIn(t) -> TxnType.DEBIT
            else -> null
        }
    }

    private fun counterpartyName(details: String, vpa: String?): String? {
        var name = details.replace(LEADING_VERB, "").replace(VPA, "").replace(INLINE_REF, "")
            .replace(SPACES, " ").trim().trim('-', ',', ':', '|', '.', ' ')
        if (name.count { it.isLetter() } < 2) name = ""
        if (name.isEmpty() && vpa != null) {
            val handle = vpa.substringBefore('@')
            name = if (PHONE.matches(handle)) handle else handle.replace(VPA_SEPARATORS, " ").replace(DIGITS, " ")
                .replace(SPACES, " ").trim().split(' ').filter { it.isNotEmpty() }
                .joinToString(" ") { word -> word.replaceFirstChar { it.titlecase() } }
            if (!PHONE.matches(name) && name.count { it.isLetter() } < 3) name = ""
        }
        return name.takeIf { it.isNotEmpty() }?.take(MAX_NAME)?.let(SmsParser::prettify)
    }

    private fun reference(text: String): String? =
        text.trim().uppercase().filter { it.isLetterOrDigit() }.takeIf { ref -> ref.count(Char::isDigit) >= 6 }

    /** The account column of a UPI app's export: a bank, UPI Lite, or blank for the app's own balance. */
    private fun bankName(account: String): String? {
        val a = account.lowercase()
        return when {
            a.isBlank() -> "Paytm"
            "upi lite" in a -> "UPI Lite"
            else -> knownBank(a) ?: account.replace(ACCOUNT_TAIL, "").trim().trim('-', ' ').take(24).ifEmpty { "Paytm" }
        }
    }

    /** A bank named in lowercase [text], by name or by its IFSC prefix. */
    private fun knownBank(text: String): String? = when {
        "state bank" in text || "sbin" in text || Regex("""\bsbi\b""").containsMatchIn(text) -> "SBI"
        "hdfc" in text -> "HDFC Bank"
        "icici" in text -> "ICICI Bank"
        "axis" in text || "utib" in text -> "Axis Bank"
        "kotak" in text || "kkbk" in text -> "Kotak"
        "yes bank" in text || "yesb" in text -> "Yes Bank"
        "paytm payments" in text -> "Paytm Payments Bank"
        "baroda" in text -> "Bank of Baroda"
        "punjab national" in text || Regex("""\bpnb\b""").containsMatchIn(text) -> "PNB"
        "canara" in text -> "Canara Bank"
        "union bank" in text -> "Union Bank"
        "idfc" in text -> "IDFC First"
        "indusind" in text -> "IndusInd"
        else -> null
    }

    // endregion

    // region Dates

    private fun parseTimestamp(date: String, time: String, zone: ZoneId): Long? {
        val dateText = date.clean()
        if (dateText.isEmpty()) return null
        val dateTime = if (time.isNotBlank()) {
            val day = parseDate(dateText) ?: return null
            day.atTime(parseTime(time.clean()) ?: LocalTime.NOON)
        } else {
            parseDateTime(dateText) ?: parseDate(dateText)?.atTime(LocalTime.NOON) ?: return null
        }
        return dateTime.atZone(zone).toInstant().toEpochMilli()
    }

    /** Whether the row says when in the day it happened: a time column, a time in the date, or an Excel time fraction. */
    private fun hasTime(date: String, time: String): Boolean {
        if (time.isNotBlank() || CLOCK.containsMatchIn(date)) return true
        val serial = date.clean().toDoubleOrNull() ?: return false
        return serial in EXCEL_SERIAL_RANGE && serial % 1.0 != 0.0
    }

    private fun String.clean() =replace(' ', ' ').replace(' ', ' ').replace(" IST", "")
        .replace(SPACES, " ").trim()

    private fun parseDateTime(text: String): LocalDateTime? {
        excelSerial(text)?.let { return it }
        for (format in DATE_TIME_FORMATS) runCatching { return LocalDateTime.parse(text, format) }
        return null
    }

    private fun parseDate(text: String): LocalDate? {
        excelSerial(text)?.let { return it.toLocalDate() }
        for (format in DATE_FORMATS) runCatching { return LocalDate.parse(text, format) }
        // A date-time in the date column, e.g. "10 Oct 2025, 2:30 PM" with a separate time column.
        return parseDateTime(text)?.toLocalDate()
    }

    private fun parseTime(text: String): LocalTime? {
        text.toDoubleOrNull()?.takeIf { it in 0.0..1.0 }?.let { fraction ->
            return LocalTime.ofSecondOfDay((fraction * SECONDS_PER_DAY).roundToLong().coerceIn(0, SECONDS_PER_DAY - 1))
        }
        for (format in TIME_FORMATS) runCatching { return LocalTime.parse(text, format) }
        return null
    }

    /** Excel stores dates as days since 30 Dec 1899, with the time as the fraction. */
    private fun excelSerial(text: String): LocalDateTime? {
        val value = text.toDoubleOrNull()?.takeIf { it in EXCEL_SERIAL_RANGE } ?: return null
        val days = floor(value).toLong()
        val seconds = ((value - days) * SECONDS_PER_DAY).roundToLong().coerceIn(0, SECONDS_PER_DAY - 1)
        return EXCEL_EPOCH.plusDays(days).atStartOfDay().plusSeconds(seconds)
    }

    private fun summary(
        headers: List<String>,
        row: List<String>,
        dateColumn: Int?,
        timeColumn: Int?,
        timestamp: Long,
        zone: ZoneId,
    ): String = headers.indices.mapNotNull { index ->
        val header = headers[index].trim()
        val value = row.getOrNull(index).orEmpty().trim()
        when {
            header.isEmpty() || value.isEmpty() || index == timeColumn -> null
            index == dateColumn -> "$header: " + SUMMARY_DATE.format(Instant.ofEpochMilli(timestamp).atZone(zone))
            else -> "$header: $value"
        }
    }.joinToString("\n")

    private fun formats(vararg patterns: String): List<DateTimeFormatter> = patterns.map {
        DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern(it).toFormatter(Locale.ENGLISH)
    }

    private val DATE_TIME_FORMATS = formats(
        "dd/MM/yyyy HH:mm:ss", "dd/MM/yyyy HH:mm", "dd/MM/yyyy hh:mm:ss a", "dd/MM/yyyy hh:mm a", "dd/MM/yyyy h:mm a",
        "dd-MM-yyyy HH:mm:ss", "dd-MM-yyyy HH:mm", "dd-MM-yyyy hh:mm a",
        "dd MMM yyyy HH:mm:ss", "dd MMM yyyy HH:mm", "dd MMM yyyy hh:mm a", "dd MMM yyyy h:mm a",
        "dd MMM yyyy, hh:mm a", "dd MMM yyyy, h:mm a", "d MMM yyyy, h:mm a", "d MMM yyyy h:mm a",
        "dd MMM, yyyy hh:mm a", "dd MMM yyyy, HH:mm", "dd MMM yy hh:mm a", "dd-MMM-yyyy HH:mm:ss",
        "yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd'T'HH:mm:ss", "yyyy-MM-dd HH:mm", "dd/MM/yy HH:mm", "dd/MM/yy hh:mm a",
        "hh:mm a, dd MMM yyyy", "h:mm a, d MMM yyyy",
    )

    private val DATE_FORMATS = formats(
        "dd/MM/yyyy", "d/M/yyyy", "dd-MM-yyyy", "dd.MM.yyyy", "dd MMM yyyy", "d MMM yyyy", "dd MMM, yyyy",
        "d MMMM yyyy", "dd-MMM-yyyy", "dd-MMM-yy", "dd MMM yy", "yyyy-MM-dd", "dd/MM/yy",
    )

    private val TIME_FORMATS = formats("HH:mm:ss", "HH:mm", "hh:mm:ss a", "hh:mm a", "h:mm a", "h:mm:ss a")

    private val SUMMARY_DATE = DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a", Locale.ENGLISH)
    private val EXCEL_EPOCH: LocalDate = LocalDate.of(1899, 12, 30)
    private val EXCEL_SERIAL_RANGE = 30_000.0..80_000.0 // 1982 to 2119
    private const val SECONDS_PER_DAY = 86_400L

    // endregion

    private const val HEADER_SEARCH_ROWS = 40
    private const val MAX_NAME = 40

    private val SPACES = Regex("""\s+""")
    private val DIGITS = Regex("""[0-9]+""")
    private val HEADER_NOISE = Regex("""[^a-z0-9/ ]""")
    private val VPA_SEPARATORS = Regex("""[._\-]+""")
    private val NUMBER = Regex("""[0-9]+(?:\.[0-9]+)?""")
    private val DR_MARK = Regex("""\bdr\.?\b|\bdebit""")
    private val CR_MARK = Regex("""\bcr\.?\b|\bcredit""")
    private val DEBIT_WORDS = Regex("""^(dr\b|debit|paid|payment|sent|money sent|transferred to|withdrawal|purchase|bill)""")
    private val CREDIT_WORDS = Regex(
        """^(cr\b|credit(?! card)|received|money received|payment received|deposit|refund|cashback|reversal)""",
    )
    private val NOT_COMPLETED = Regex("""fail|pending|declin|cancel|revers|processing|expired|in progress""")
    private val LITE_TOP_UP = Regex(
        """(add|added|adding|load|loaded|top[ -]?up|topup)\b.{0,30}upi lite|upi lite\b.{0,30}(top[ -]?up|topup|load|added)""",
    )
    private val SENT_TO_PERSON = Regex("""^(money )?sent to""")
    // Paytm describes autopays as "Automatic payment for Youtube" (or "of ₹375 setup for Naukri" when one is set up).
    private val LEADING_VERB = Regex(
        """^\s*(paid to|payment to|money sent to|sent to|transferred to|received from|money received from|refund from|cashback from|paid for|bill paid to|automatic payment (?:of \S+ setup )?for|recharge of|paid)\s*:?\s+""",
        RegexOption.IGNORE_CASE,
    )
    private val INLINE_REF = Regex("""(?i)\b(?:upi\s*)?ref(?:erence)?\.?\s*(?:no\.?)?\s*[:#-]?\s*([0-9]{9,16})\b""")
    private val VPA = Regex("""(?i)[a-z0-9][a-z0-9._\-]{1,63}@[a-z][a-z0-9]{1,20}""")
    private val PHONE = Regex("""^(?:\+?91)?[6-9][0-9]{9}$""")
    private val ACCOUNT_TAIL = Regex("""[x*\s-]*([0-9]{2,6})\s*\)?\s*$""", RegexOption.IGNORE_CASE)
    private val PREAMBLE_ACCOUNT = Regex("""(?i)\b(?:account|a/c)\s*(?:number|no\.?)\s*:?\s*[x*]*([0-9]{4,})""")
    private val CLOCK = Regex("""\d{1,2}:\d{2}""")
}
