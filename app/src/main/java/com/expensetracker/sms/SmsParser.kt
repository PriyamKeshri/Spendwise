package com.expensetracker.sms

import com.expensetracker.model.PaymentMode
import com.expensetracker.model.TxnType
import java.math.BigDecimal
import java.math.RoundingMode

data class ParsedSms(
    val type: TxnType,
    val amountPaise: Long,
    /** Who was paid, or who paid; a generic label such as "UPI payment" when the message doesn't say. */
    val merchant: String,
    val vpa: String?,
    val mode: PaymentMode,
    val bank: String?,
    val accountTail: String?,
    val refNo: String?,
    /** Paid to / received from an individual rather than a business. */
    val personTransfer: Boolean,
    /** Money moving between the user's own accounts, e.g. paying off a credit card. */
    val internalTransfer: Boolean,
)

/**
 * Turns an Indian bank, card or UPI alert into a transaction. Pure Kotlin so it can be unit tested
 * against real message formats. Returns null for anything that is not a completed transaction:
 * OTPs, promotions, payment reminders, mandates, failed payments and personal messages.
 */
object SmsParser {

    fun parse(sender: String, body: String): ParsedSms? {
        if (!isTransactionalSender(sender)) return null
        val text = body.replace(WHITESPACE, " ").trim()
        val lower = text.lowercase()
        if (REJECT.containsMatchIn(lower)) return null

        val type = detectType(lower) ?: return null
        if (type == TxnType.DEBIT && FAILED.containsMatchIn(lower)) return null

        val amountPaise = extractAmount(text) ?: return null
        val bank = bankName(sender)
        val accountTail = extractAccountTail(text)
        // Unknown senders must at least mention an account or card, which promotions never do.
        if (bank == null && accountTail == null) return null

        val vpaInText = VPA.find(text)?.value?.lowercase()
        val mode = detectMode(lower, hasVpa = vpaInText != null)
        val core = text.substring(0, DISCLAIMER.find(text)?.range?.first ?: text.length)
        val party = if (type == TxnType.DEBIT && mode == PaymentMode.ATM) Party.NONE else extractParty(core, type)

        val internal = when (type) {
            TxnType.DEBIT -> CARD_BILL_DEBIT.containsMatchIn(lower)
            TxnType.CREDIT -> CARD_BILL_CREDIT.containsMatchIn(lower)
        }
        val accountToAccount = type == TxnType.DEBIT && ACCOUNT_TO_ACCOUNT.containsMatchIn(lower)

        return ParsedSms(
            type = type,
            amountPaise = amountPaise,
            merchant = party.name ?: genericLabel(type, mode),
            vpa = party.vpa ?: vpaInText,
            mode = mode,
            bank = bank,
            accountTail = accountTail,
            refNo = extractRef(text),
            personTransfer = party.person || accountToAccount,
            internalTransfer = internal,
        )
    }

    /** Bank alerts come from alphanumeric DLT headers such as "VM-SBIUPI"; people have phone numbers. */
    fun isTransactionalSender(sender: String): Boolean {
        val s = sender.trim().uppercase()
        if (s.isEmpty() || NUMERIC_SENDER.matches(s)) return false
        if (DLT_HEADER.matches(s) && s.endsWith("-P")) return false // promotional route
        return s.any { it.isLetter() }
    }

    fun bankName(sender: String): String? {
        val header = senderHeader(sender)
        return BANKS.firstOrNull { (code, _) -> header.contains(code) }?.second
    }

    fun genericLabel(type: TxnType, mode: PaymentMode): String = when (type) {
        TxnType.DEBIT -> when (mode) {
            PaymentMode.ATM -> "ATM withdrawal"
            PaymentMode.UPI -> "UPI payment"
            PaymentMode.CARD -> "Card payment"
            PaymentMode.NET_BANKING -> "Bank transfer"
            PaymentMode.WALLET -> "Wallet payment"
            PaymentMode.OTHER -> "Bank debit"
        }
        TxnType.CREDIT -> when (mode) {
            PaymentMode.UPI -> "UPI received"
            PaymentMode.NET_BANKING -> "Bank transfer received"
            PaymentMode.CARD -> "Card credit"
            PaymentMode.WALLET -> "Wallet credit"
            PaymentMode.ATM, PaymentMode.OTHER -> "Money received"
        }
    }

    /** True when [merchant] is a fallback label rather than a real name, so it must not be learned. */
    fun isGenericLabel(merchant: String): Boolean = merchant in genericLabels

    private val genericLabels: Set<String> by lazy {
        TxnType.entries.flatMap { type -> PaymentMode.entries.map { genericLabel(type, it) } }.toSet()
    }

    // region Detection

    private fun senderHeader(sender: String): String {
        val s = sender.trim().uppercase()
        return DLT_HEADER.matchEntire(s)?.groupValues?.get(1) ?: s
    }

    /** Whichever direction word appears first wins: "debited ...; SWIGGY credited" is a debit. */
    private fun detectType(lower: String): TxnType? {
        val debitAt = DEBIT_WORDS.find(lower)?.range?.first
        val creditAt = CREDIT_WORDS.find(lower)?.range?.first
        return when {
            debitAt == null && creditAt == null -> null
            creditAt == null -> TxnType.DEBIT
            debitAt == null -> TxnType.CREDIT
            debitAt <= creditAt -> TxnType.DEBIT
            else -> TxnType.CREDIT
        }
    }

    private fun detectMode(lower: String, hasVpa: Boolean): PaymentMode = when {
        ATM_HINT.containsMatchIn(lower) -> PaymentMode.ATM
        hasVpa || UPI_HINT.containsMatchIn(lower) -> PaymentMode.UPI
        CARD_HINT.containsMatchIn(lower) -> PaymentMode.CARD
        NETBANKING_HINT.containsMatchIn(lower) -> PaymentMode.NET_BANKING
        WALLET_HINT.containsMatchIn(lower) -> PaymentMode.WALLET
        else -> PaymentMode.OTHER
    }

    /** First currency amount that isn't a balance or limit; SBI UPI alerts omit "Rs" entirely. */
    private fun extractAmount(text: String): Long? {
        for (match in CURRENCY_AMOUNT.findAll(text)) {
            val before = text.substring(maxOf(0, match.range.first - 25), match.range.first)
            if (BALANCE_BEFORE.containsMatchIn(before)) continue
            toPaise(match.groupValues[1])?.let { return it }
        }
        return BARE_AMOUNT.find(text)?.let { toPaise(it.groupValues[1]) }
    }

    private fun toPaise(raw: String): Long? {
        val digits = raw.replace(",", "")
        if (digits.isEmpty()) return null
        return runCatching {
            BigDecimal(digits).setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact()
        }.getOrNull()?.takeIf { it in 1..MAX_PAISE }
    }

    private fun extractAccountTail(text: String): String? =
        (ACCOUNT_TAIL.find(text) ?: MASKED_TAIL.find(text))?.groupValues?.get(1)?.takeLast(4)

    private fun extractRef(text: String): String? {
        val candidates = REF.findAll(text).map { it.groupValues[1] } +
            UPI_PATH.findAll(text).map { it.groupValues[2] } +
            UPI_COLON_REF.findAll(text).map { it.groupValues[1] }
        return candidates.firstOrNull { ref -> ref.count(Char::isDigit) >= 6 }?.uppercase()
    }

    // endregion

    // region Counterparty

    private data class Party(val name: String?, val vpa: String?, val person: Boolean) {
        companion object {
            val NONE = Party(null, null, false)
        }
    }

    private class PartyPattern(pattern: String, val lastFirst: Boolean = false) {
        val regex = Regex(pattern, RegexOption.IGNORE_CASE)
    }

    private fun extractParty(core: String, type: TxnType): Party {
        UPI_PATH.find(core)?.let { match ->
            resolve(match.groupValues[3])?.let { party ->
                return party.copy(person = party.person || match.groupValues[1].equals("p2a", ignoreCase = true))
            }
        }
        val patterns = if (type == TxnType.DEBIT) DEBIT_PARTY else CREDIT_PARTY
        for (pattern in patterns) {
            val matches = pattern.regex.findAll(core).toList()
            for (match in if (pattern.lastFirst) matches.asReversed() else matches) {
                resolve(match.groupValues[1])?.let { return it }
            }
        }
        INFO.find(core)?.let { match -> partyFromInfo(match.groupValues[1])?.let { return it } }
        VPA.find(core)?.let { return partyFromVpa(it.value) }
        return Party.NONE
    }

    private fun resolve(raw: String): Party? {
        VPA.find(raw)?.let { return partyFromVpa(it.value) }
        val candidate = raw.trim().trim(*TRIM_CHARS)
        val compact = candidate.replace(" ", "")
        if (PHONE.matches(compact)) return Party(compact.takeLast(10), null, person = true)

        var name = LEADING_NOISE.replace(candidate, "")
        var person = false
        BUSINESS_PREFIX.find(name)?.let { name = name.substring(it.range.last + 1) }
        HONORIFIC.find(name)?.let {
            person = true
            name = name.substring(it.range.last + 1)
        }
        name = name.replace(WHITESPACE, " ").trim().trim(*TRIM_CHARS)
        if (name.count { it.isLetter() } < 2 || NOT_A_NAME.containsMatchIn(name)) return null
        if (name.length > MAX_NAME) {
            name = name.take(MAX_NAME).substringBeforeLast(' ').ifBlank { name.take(MAX_NAME) }
        }
        return Party(prettify(name), null, person)
    }

    private fun partyFromVpa(vpa: String): Party {
        val address = vpa.lowercase()
        val handle = address.substringBefore('@')
        if (PHONE_HANDLE.containsMatchIn(handle)) {
            return Party(handle.take(10), address, person = true)
        }
        val words = handle
            .replace(VPA_SEPARATORS, " ")
            .replace(DIGITS, " ")
            .replace(WHITESPACE, " ")
            .trim()
        val unreadable = words.count { it.isLetter() } < 3 || QR_HANDLE.containsMatchIn(words)
        val name = if (unreadable) null else words.split(' ').joinToString(" ") { word ->
            word.replaceFirstChar { it.titlecase() }
        }
        return Party(name, address, person = false)
    }

    /** HDFC style "Info: UPI-SWIGGY-swiggy@icici-4289..." or "Info: POS 1234XXXX5678 AMAZON". */
    private fun partyFromInfo(raw: String): Party? {
        for (token in raw.split('-', '/', ':', '*')) {
            val trimmed = token.trim()
            if (VPA.matches(trimmed)) return partyFromVpa(trimmed)
            val words = trimmed.split(' ').filter { word ->
                word.lowercase() !in INFO_NOISE && word.count { it.isLetter() } >= 2 && !MASKED_NUMBER.matches(word)
            }
            if (words.isNotEmpty()) resolve(words.joinToString(" "))?.let { return it }
        }
        return null
    }

    /** "AMAZON PAY INDIA" reads better as "Amazon Pay India"; other short words stay as acronyms (SBI, KFC). */
    private fun prettify(name: String): String =
        if (name.any { it.isLowerCase() }) {
            name
        } else {
            name.split(' ').joinToString(" ") { word ->
                if (word.length <= 3 && word !in SHORT_WORDS) word else word.lowercase().replaceFirstChar { it.titlecase() }
            }
        }

    // endregion

    // region Patterns

    private val SHORT_WORDS = setOf(
        "PAY", "THE", "AND", "FOR", "NEW", "BIG", "TEA", "CAB", "BAR", "PUB", "INN", "DAY", "BAY", "ONE", "OIL",
        "GAS", "BUS", "CAR", "SON", "AIR", "SRI", "OF", "AT", "IN", "ON", "TO", "BY", "CO",
    )

    private const val MAX_NAME = 40
    private const val MAX_PAISE = 10_000_000_000L // ₹10 crore; anything bigger is a misparse
    private val TRIM_CHARS = charArrayOf('.', ',', ';', ':', '-', '(', ')', '\'', '"', '*', '/', ' ', '!', '?')

    private val WHITESPACE = Regex("""\s+""")
    private val DIGITS = Regex("""[0-9]+""")
    private val VPA_SEPARATORS = Regex("""[._\-]+""")

    private val NUMERIC_SENDER = Regex("""^\+?[0-9][0-9 \-]*$""")
    private val DLT_HEADER = Regex("""^[A-Z0-9]{2}-([A-Z0-9]{3,9})(?:-[A-Z])?$""")

    private val BANKS = listOf(
        "SBICRD" to "SBI Card", "SBICARD" to "SBI Card", "SBI" to "SBI",
        "HDFC" to "HDFC Bank", "ICICI" to "ICICI Bank", "AXIS" to "Axis Bank", "KOTAK" to "Kotak",
        "IDFC" to "IDFC First", "INDUS" to "IndusInd", "YESB" to "Yes Bank", "PNB" to "PNB",
        "BOB" to "Bank of Baroda", "BARODA" to "Bank of Baroda", "CANBNK" to "Canara Bank",
        "CANARA" to "Canara Bank", "UNIONB" to "Union Bank", "UBOI" to "Union Bank", "BOI" to "Bank of India",
        "CENTBK" to "Central Bank", "IOB" to "Indian Overseas Bank", "INDBNK" to "Indian Bank",
        "IDBI" to "IDBI Bank", "FEDBNK" to "Federal Bank", "FEDFIB" to "Federal Bank", "RBL" to "RBL Bank",
        "AUBANK" to "AU Bank", "AUSFB" to "AU Bank", "PAYTM" to "Paytm", "AMEX" to "Amex", "HSBC" to "HSBC",
        "CITI" to "Citi", "SCB" to "Standard Chartered", "STANC" to "Standard Chartered",
        "ONECRD" to "OneCard", "JUPITR" to "Jupiter", "FIMONY" to "Fi", "SLICE" to "slice",
    )

    /** Things that look like money movement but aren't a completed transaction. Matched on lowercase. */
    private val REJECT = Regex(
        listOf(
            """\botp\b""",
            """one[ -]?time password""",
            """verification code""",
            """\b(?:will|shall|to|would) be (?:auto[- ]?)?(?:debited|credited|charged|deducted|reversed|refunded|processed)\b""",
            """\bis due\b""",
            """\bdue (?:date|on|by)\b""",
            """\b(?:min(?:imum)?|total)\.? (?:amt|amount) due\b""",
            """\bstatement\b""",
            """\brequest(?:ed|s)?\b""",
            """\bpre-?approved\b""",
            """\beligible\b""",
            """\bconvert\b.{0,60}\bemi\b""",
        ).joinToString("|"),
    )

    private val FAILED = Regex("""\b(?:declined|failed|unsuccessful|not successful|could not be processed|insufficient)\b""")

    private val DEBIT_WORDS = Regex(
        """\b(?:debited|debit (?:by|of|for)|spent|sent|paid|withdrawn|withdrawal|purchased?|deducted|charged|used for|trf to|(?:txn|trxn|transaction)\.? of)\b""",
    )
    private val CREDIT_WORDS = Regex(
        """\b(?:credited|credit (?:by|of|for)|received|deposited|refund(?:ed)?|reversed|reversal)\b""",
    )

    private val CURRENCY_AMOUNT = Regex(
        """(?<![a-z])(?:rs\.?|inr|₹)\s*:?\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)""",
        RegexOption.IGNORE_CASE,
    )
    private val BARE_AMOUNT = Regex(
        """\b(?:debited|credited|debit|credit)\s+(?:by|for|with|of)\s+([0-9][0-9,]*(?:\.[0-9]{1,2})?)\b""",
        RegexOption.IGNORE_CASE,
    )
    private val BALANCE_BEFORE = Regex(
        """(?:bal|balance|avl|avail|available|limit|lmt|outstanding|due)\b[^0-9]{0,12}$""",
        RegexOption.IGNORE_CASE,
    )

    private val ACCOUNT_TAIL = Regex(
        """\b(?:a/c|acct|account|ac|card)(?:\s*no\.?)?(?:\s*ending(?:\s*(?:with|in))?)?\s*[:\-]?\s*[x*]*\s?([0-9]{3,6})\b""",
        RegexOption.IGNORE_CASE,
    )
    private val MASKED_TAIL = Regex("""(?<![a-z0-9])[x*]{2,}([0-9]{3,6})\b""", RegexOption.IGNORE_CASE)
    private val MASKED_NUMBER = Regex("""(?i)[0-9x*]{4,}""")

    private val REF = Regex(
        """\b(?:upi\s*ref(?:erence)?(?:\s*no)?|ref(?:erence)?(?:\s*(?:no|number|num))?|refno|rrn|utr(?:\s*no)?|txn\s*(?:id|no)|transaction\s*(?:id|number|no))\.?\s*[:\-#]?\s*([a-z0-9]{6,22})\b""",
        RegexOption.IGNORE_CASE,
    )
    private val UPI_COLON_REF = Regex("""\bupi\s*[:/]\s*([0-9]{9,16})\b""", RegexOption.IGNORE_CASE)

    /** Axis style "UPI/P2M/428912345678/SWIGGY": P2M pays a merchant, P2A pays a person. */
    private val UPI_PATH = Regex("""\bupi/(p2[am])/([0-9]{6,})/([^/]+)""", RegexOption.IGNORE_CASE)

    private val VPA = Regex(
        """(?<![a-z0-9._\-])[a-z0-9][a-z0-9._\-]{1,63}@[a-z][a-z0-9]{1,20}(?![a-z0-9]|\.[a-z])""",
        RegexOption.IGNORE_CASE,
    )
    private val PHONE = Regex("""^(?:\+?91)?[6-9][0-9]{9}$""")
    private val PHONE_HANDLE = Regex("""^(?:\+?91)?[6-9][0-9]{9}""")
    private val QR_HANDLE = Regex("""^(?:paytmqr|bharatpe|q\b|gpay|mab\b)""")

    private val ATM_HINT = Regex("""\batm\b|\bwithdrawn\b|\bwithdrawal\b|\batw\b|\bnwd\b|\bcash wdl\b""")
    private val UPI_HINT = Regex("""\bupi\b|\bvpa\b""")
    private val CARD_HINT = Regex("""\bcard\b|\bpos\b""")
    private val NETBANKING_HINT = Regex("""\b(?:neft|imps|rtgs|net ?banking|yono|inb)\b""")
    private val WALLET_HINT = Regex("""\bwallet\b""")

    /** Credit card bill payments: the card spends were already counted, so don't count them twice. */
    private val CARD_BILL_DEBIT = Regex(
        """\b(?:credit\s*card|cc|card)\s*(?:bill|dues|payment|pymt|repayment)\b|\btowards\s+(?:your\s+)?(?:[a-z]+\s+){0,3}credit\s*card\b|\bcred(?:\.club|\s+club)?\b|\bbilldesk\s*\*?\s*cred\b""",
    )
    private val CARD_BILL_CREDIT = Regex(
        """\b(?:payment|pymt)\b.{0,80}\bcredit\s*card\b|\bcredit\s*card\b.{0,80}\b(?:payment|pymt)\s*(?:of|received|credited)\b""",
    )
    private val ACCOUNT_TO_ACCOUNT = Regex("""\ba/c\s*(?:no\.?\s*)?[x*]+[0-9]{3,6}\s+credited\b""")

    /** Boilerplate after which no merchant name appears ("Not you? Call ...", "Avl Bal ..."). */
    private val DISCLAIMER = Regex(
        """\b(?:not you|not u\b|not done by|if not (?:done|you|u|withdrawn|initiated|made)|call\s*(?:us|on|@|at)?\s*[-:]?\s*[0-9]|sms block|to block|report at|for dispute|avl\.? bal|avail(?:able)? bal|available (?:credit )?limit|avl\.? lmt|avl\.? limit)""",
        RegexOption.IGNORE_CASE,
    )

    /** Where a merchant name ends: a keyword that starts the next clause, sentence punctuation, or the end. */
    private const val STOP =
        """(?=\s+(?:on|ref|refno|rrn|utr|upi|avl|via|from|using|if|not|info|dated|date|txn|trxn|by|has|is|was|for|at|with|towards)\b|\.(?:\s|$)|[;,(]|\s+-|$)"""

    private val DEBIT_PARTY = listOf(
        PartyPattern("""\btrf to\s+(.+?)$STOP"""), // SBI UPI: "trf to ZOMATO Refno 4289..."
        PartyPattern("""\bto\s+vpa\s+(\S+)"""), // HDFC: "to VPA swiggy@icici"
        PartyPattern(""";\s*(.+?)\s+credited\b"""), // ICICI: "on 15-Oct-24; SWIGGY credited"
        PartyPattern("""\bat\s+(.+?)$STOP"""), // cards: "at FLIPKART on 15/10/24"
        PartyPattern("""\bon\s+[0-9a-z\-/]{5,12}\s+on\s+(.+?)$STOP"""), // ICICI card: "on 15-Oct-24 on AMAZON."
        PartyPattern("""\bto\s+(.+?)$STOP"""), // HDFC, Kotak, Paytm: "To ZOMATO On 15/10/24"
        PartyPattern("""\btowards\s+(.+?)$STOP"""), // SBI: "towards SMS charges"
    )

    private val CREDIT_PARTY = listOf(
        PartyPattern("""\blinked to vpa\s+(\S+)"""), // HDFC: "by a/c linked to VPA abc@okaxis"
        PartyPattern("""\bfrom\s+(?:vpa\s+)?(.+?)$STOP"""), // SBI: "transfer from RAHUL KUMAR Ref No ..."
        PartyPattern("""\bby\s+(.+?)$STOP""", lastFirst = true), // SBI NEFT: "on 01/10/24 by ABC PVT LTD."
    )

    private val INFO = Regex("""\binfo\s*[:\-]?\s*([^.]+)""", RegexOption.IGNORE_CASE)
    private val INFO_NOISE = setOf(
        "upi", "pos", "imps", "neft", "rtgs", "ach", "nach", "ecs", "ib", "mb", "atw", "nwd", "bil", "onl", "inb",
        "vps", "vin", "dr", "cr", "ref", "txn", "p2a", "p2m", "mmt", "ft", "tpt", "bbps", "billpay", "ecom", "pur",
        "purchase", "payment",
    )

    private val LEADING_NOISE = Regex("""^(?:ach|nach|ecs|pos|bil|onl)(?:[-/\s]+(?:dr|cr))?[-/\s]+""", RegexOption.IGNORE_CASE)
    private val BUSINESS_PREFIX = Regex("""^m/s\.?\s*""", RegexOption.IGNORE_CASE)
    // Titles that mark an individual. "Sri"/"Shri" and "Dr" are left out: they head many shop and clinic names.
    private val HONORIFIC = Regex("""^(?:mr|mrs|ms|miss|smt|kumari)\.?\s+""", RegexOption.IGNORE_CASE)
    private val NOT_A_NAME = Regex(
        """^(?:rs\.?\s*[0-9]|inr\b|₹|a/c\b|ac\b|acct\b|account\b|your\b|you\b|ur\b|card\b|ref\b|upi\b|neft\b|imps\b|rtgs\b|transfer\b|transaction\b|txn\b|trxn\b|the\b|vpa\b|xx|\*|date\b)""",
        RegexOption.IGNORE_CASE,
    )

    // endregion
}
