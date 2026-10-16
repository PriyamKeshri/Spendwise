package com.expensetracker.data.db

import com.expensetracker.model.CategoryKind

/** Fixed ids of the built-in categories; user-created categories are numbered after these. */
object CategoryIds {
    const val FOOD = 1L
    const val GROCERIES = 2L
    const val SHOPPING = 3L
    const val TRANSPORT = 4L
    const val FUEL = 5L
    const val BILLS = 6L
    const val RENT = 7L
    const val ENTERTAINMENT = 8L
    const val HEALTH = 9L
    const val TRAVEL = 10L
    const val EDUCATION = 11L
    const val SUBSCRIPTIONS = 12L
    const val TRANSFERS = 13L
    const val CASH = 14L
    const val OTHERS = 15L

    const val SALARY = 16L
    const val REFUNDS = 17L
    const val INTEREST = 18L
    const val TRANSFERS_IN = 19L
    const val OTHER_INCOME = 20L
}

object DefaultCategories {

    private fun expense(id: Long, name: String, emoji: String, color: Long, vararg keywords: String) =
        CategoryEntity(id, name, emoji, color, CategoryKind.EXPENSE, keywords.toList(), id.toInt(), isSystem = true)

    private fun income(id: Long, name: String, emoji: String, color: Long, vararg keywords: String) =
        CategoryEntity(id, name, emoji, color, CategoryKind.INCOME, keywords.toList(), id.toInt(), isSystem = true)

    val all: List<CategoryEntity> = listOf(
        expense(
            CategoryIds.FOOD, "Food & Dining", "🍔", 0xFFFF7043,
            "swiggy", "zomato", "eatsure", "dominos", "pizza hut", "pizza", "kfc", "mcdonalds", "mc donalds",
            "burger king", "burger", "subway", "starbucks", "chaayos", "chai point", "cafe coffee day", "ccd",
            "third wave coffee", "blue tokai", "haldiram", "haldirams", "barbeque nation", "behrouz", "faasos",
            "box8", "freshmenu", "wow momo", "biryani", "restaurant", "restaurants", "cafe", "coffee", "dhaba",
            "bakery", "bakers", "sweets", "kitchen", "food", "foods", "canteen", "eatery", "juice", "tea", "chai",
        ),
        expense(
            CategoryIds.GROCERIES, "Groceries", "🛒", 0xFF66BB6A,
            "bigbasket", "big basket", "bbnow", "bb daily", "blinkit", "grofers", "zepto", "instamart",
            "swiggy instamart", "dmart", "d mart", "avenue supermarts", "jiomart", "reliance fresh",
            "reliance smart", "smart bazaar", "more retail", "more supermarket", "spencers", "natures basket",
            "ratnadeep", "star bazaar", "kirana", "supermarket", "super market", "grocery", "groceries",
            "provision", "provisions", "general store", "vegetables", "vegetable", "fruits", "dairy", "milk",
            "country delight", "milkbasket", "licious", "freshtohome", "dunzo",
        ),
        expense(
            CategoryIds.SHOPPING, "Shopping", "🛍️", 0xFFEC407A,
            "amazon", "amazon pay", "flipkart", "myntra", "ajio", "meesho", "nykaa", "snapdeal", "tata cliq",
            "tatacliq", "croma", "reliance digital", "vijay sales", "decathlon", "ikea", "lifestyle",
            "shoppers stop", "pantaloons", "westside", "zudio", "max fashion", "trends", "uniqlo", "zara",
            "lenskart", "firstcry", "pepperfry", "urban ladder", "boat", "apple", "samsung", "mall", "fashion",
            "retail", "electronics", "footwear", "bata", "puma", "nike", "adidas", "store",
        ),
        expense(
            CategoryIds.TRANSPORT, "Transport", "🚕", 0xFF29B6F6,
            "uber", "ola", "olacabs", "ola cabs", "rapido", "namma yatri", "blusmart", "metro", "dmrc", "bmrc",
            "bmrcl", "mmrc", "delhi metro", "namma metro", "fastag", "toll", "parking", "cab", "cabs", "taxi",
            "auto", "autorickshaw", "yulu", "bounce",
        ),
        expense(
            CategoryIds.FUEL, "Fuel", "⛽", 0xFF8D6E63,
            "petrol", "diesel", "fuel", "fuels", "indian oil", "iocl", "ioc", "hpcl", "hindustan petroleum",
            "bpcl", "bharat petroleum", "shell", "nayara", "essar", "reliance petroleum", "jio bp", "jiobp",
            "filling station", "service station", "petroleum", "petro", "cng",
        ),
        expense(
            CategoryIds.BILLS, "Bills & Utilities", "💡", 0xFFFFCA28,
            "electricity", "electric", "bescom", "msedcl", "mahadiscom", "tata power", "adani electricity", "bses",
            "tneb", "tangedco", "kseb", "cesc", "torrent power", "uppcl", "pspcl", "wbsedcl", "water",
            "water board", "gas", "indane", "bharat gas", "bharatgas", "hp gas", "mahanagar gas", "igl",
            "adani gas", "broadband", "fibernet", "act fibernet", "hathway", "excitel", "airtel", "jio",
            "reliance jio", "vodafone", "vodafone idea", "bsnl", "mtnl", "recharge", "mobile recharge", "prepaid",
            "postpaid", "dth", "tata play", "tata sky", "dish tv", "d2h", "sun direct", "bill", "bills",
            "billpay", "bbps", "utility", "utilities", "insurance", "lic", "municipal", "property tax",
            "maintenance", "society",
        ),
        expense(
            CategoryIds.RENT, "Rent", "🏠", 0xFF5C6BC0,
            "rent", "house rent", "room rent", "nobroker", "nestaway", "housing com", "magicbricks", "landlord",
            "pg", "paying guest", "hostel", "rentomojo",
        ),
        expense(
            CategoryIds.ENTERTAINMENT, "Entertainment", "🎬", 0xFFAB47BC,
            "bookmyshow", "book my show", "pvr", "inox", "pvr inox", "cinepolis", "cinema", "cinemas", "movie",
            "movies", "multiplex", "theatre", "theater", "paytm insider", "gaming", "games", "steam",
            "playstation", "xbox", "nintendo", "wonderla", "imagica", "smaaash", "timezone", "bowling", "concert",
            "event", "events", "club", "pub", "brewery", "dream11", "my11circle",
        ),
        expense(
            CategoryIds.HEALTH, "Health", "💊", 0xFF26A69A,
            "apollo", "apollo pharmacy", "pharmacy", "pharma", "medplus", "netmeds", "1mg", "tata 1mg",
            "pharmeasy", "practo", "hospital", "hospitals", "clinic", "medical", "medicals", "medicine",
            "medicines", "chemist", "chemists", "druggist", "diagnostic", "diagnostics", "pathology", "lab", "labs",
            "dr lal", "lal pathlabs", "thyrocare", "healthians", "metropolis", "cult fit", "cultfit", "cure fit",
            "gym", "fitness", "dental", "dentist", "eye care", "optical", "healthkart", "nursing home",
        ),
        expense(
            CategoryIds.TRAVEL, "Travel", "✈️", 0xFF26C6DA,
            "makemytrip", "make my trip", "mmt", "goibibo", "cleartrip", "yatra", "ixigo", "easemytrip", "irctc",
            "indian railways", "railway", "railways", "indigo", "interglobe", "air india", "vistara", "spicejet",
            "akasa", "akasa air", "airasia", "air asia", "airline", "airlines", "airport", "oyo", "oyo rooms",
            "airbnb", "booking com", "agoda", "treebo", "fabhotels", "fab hotels", "hotels", "resort", "resorts",
            "redbus", "abhibus", "zoomcar", "travel", "travels", "tours", "tour", "holiday", "holidays", "trip",
        ),
        expense(
            CategoryIds.EDUCATION, "Education", "📚", 0xFFD4E157,
            "school", "college", "university", "academy", "institute", "tuition", "tuitions", "coaching",
            "classes", "course", "courses", "udemy", "coursera", "byjus", "unacademy", "vedantu", "upgrad",
            "simplilearn", "physicswallah", "physics wallah", "great learning", "scaler", "books", "book store",
            "bookstore", "stationery", "stationary", "xerox", "fees", "exam", "examination",
        ),
        expense(
            CategoryIds.SUBSCRIPTIONS, "Subscriptions", "🔁", 0xFF7E57C2,
            "netflix", "prime video", "primevideo", "amazon prime", "hotstar", "disney", "disney hotstar",
            "jiocinema", "jio cinema", "jiohotstar", "sonyliv", "sony liv", "zee5", "voot", "altbalaji",
            "mxplayer", "spotify", "youtube", "youtube premium", "google play", "play store", "playstore",
            "google one", "apple com", "apple services", "itunes", "icloud", "gaana", "wynk", "jiosaavn",
            "saavn", "audible", "kindle", "linkedin", "microsoft", "office 365", "adobe", "chatgpt", "openai",
            "anthropic", "notion", "canva", "github", "subscription", "membership", "renewal",
        ),
        expense(
            CategoryIds.TRANSFERS, "Transfers", "💸", 0xFF78909C,
            "self transfer", "own account", "fund transfer", "upi lite",
        ),
        expense(
            CategoryIds.CASH, "Cash / ATM", "🏧", 0xFFFFA726,
            "atm", "cash withdrawal", "cash wdl", "atm wdl",
        ),
        expense(CategoryIds.OTHERS, "Others", "📦", 0xFF9E9E9E),

        income(
            CategoryIds.SALARY, "Salary", "💼", 0xFF43A047,
            "salary", "sal", "salaries", "payroll", "wages", "stipend", "bonus", "reimbursement",
        ),
        income(
            CategoryIds.REFUNDS, "Refunds & Cashback", "↩️", 0xFF26A69A,
            "refund", "refunded", "refunds", "cashback", "cash back", "reversal", "reversed", "chargeback",
            "reward", "rewards",
        ),
        income(
            CategoryIds.INTEREST, "Interest & Dividends", "🏦", 0xFFFFB300,
            "interest", "int pd", "int paid", "int credit", "sb int", "savings interest", "fd interest",
            "dividend",
        ),
        income(
            CategoryIds.TRANSFERS_IN, "Transfers In", "📥", 0xFF42A5F5,
            "self transfer", "own account", "fund transfer",
        ),
        income(CategoryIds.OTHER_INCOME, "Other Income", "💰", 0xFF9E9E9E),
    )

    /** Colours handed out to categories the user creates. */
    val customPalette: List<Long> = listOf(
        0xFFEF5350, 0xFF8E24AA, 0xFF3949AB, 0xFF00897B, 0xFFC0CA33, 0xFFFB8C00, 0xFF6D4C41, 0xFF546E7A,
    )
}
