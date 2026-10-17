package com.expensetracker.model

/** Direction of money relative to the user's account. */
enum class TxnType { DEBIT, CREDIT }

enum class PaymentMode(val label: String) {
    UPI("UPI"),
    CARD("Card"),
    ATM("ATM"),
    NET_BANKING("Bank transfer"),
    WALLET("Wallet"),
    OTHER("Bank"),
}

/** Expense categories hold debits, income categories hold credits. */
enum class CategoryKind { EXPENSE, INCOME }

/** Where a transaction was found: a bank SMS, or a statement file the user imported. */
enum class TxnSource { SMS, PAYTM }
