package in.insta.entity;

public enum TransactionCategory {
    MOBILE("Mobile Recharge", "mobile|recharge|airtel|vodafone|idea|jio|prepaid|postpaid"),
    UTILITIES("Bills & Utilities", "bill|electricity|water|gas|internet|dth|broadband"),
    FOOD("Food & Dining", "hotel|restaurant|cafe|zomato|swiggy|food|pizza|burger|bakery|tiffin|bhojnalaya|idli|chai"),
    SHOPPING("Shopping", "flipkart|amazon|store|shop|mall|clothes|fashion|apparel|footwear|general store|kirana|dress"),
    ENTERTAINMENT("Entertainment", "dream11|movie|cinema|game|gaming|concert|music"),
    MEDICAL("Medical & Health", "medical|hospital|doctor|pharmacy|medplus|treatment|clinic|dental|health"),
    TRANSPORT("Transport & Travel", "uber|ola|taxi|travel|flight|bus|parking|fuel|petrol|jio mobility"),
    TRANSFER("Transfer & Payment", "transfer|googlepay|google pay|payment|bharatpe|deposit|upi"),
    INCOME("Income & Deposits", "received from|payment received|salary|deposit|income"),
    GROCERIES("Groceries", "fruit|vegetable|market|provisions"),
    ENTERTAINMENT_GAMING("Gaming", "dream11|gaming|game|fantasy"),
    OTHER("Other", "");

    private String displayName;
    private String keywords;

    TransactionCategory(String displayName, String keywords) {
        this.displayName = displayName;
        this.keywords = keywords;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getKeywords() {
        return keywords;
    }

    /**
     * Categorize based on description using pattern matching
     */
    public static TransactionCategory categorize(String description) {
        if (description == null || description.isEmpty()) {
            return OTHER;
        }

        String desc = description.toLowerCase().trim();

        // Check for income first (highest priority)
        if (desc.contains("received from") || desc.contains("payment received")) {
            return INCOME;
        }

        for (TransactionCategory category : TransactionCategory.values()) {
            if (category == OTHER) continue;
            
            String[] keywords = category.getKeywords().split("\\|");
            for (String keyword : keywords) {
                if (desc.contains(keyword.toLowerCase())) {
                    return category;
                }
            }
        }

        return OTHER;
    }
}

