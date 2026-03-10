# PhonePay Transaction App - High-Level Information Flows

## 1. PDF Upload & Processing Flow

### Flow Diagram
```
┌─────────────────────────────────────────────────────────────────┐
│                    USER INTERACTION                             │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  1. User visits http://localhost:8080/upload.html              │
│     ↓                                                           │
│  2. User selects PDF file + enters password (optional)         │
│     ↓                                                           │
│  3. User clicks "Upload & Parse" button                        │
│     ↓                                                           │
│  4. JavaScript sends FormData via fetch()                      │
│     ├─ Method: POST                                            │
│     ├─ Endpoint: /api/transactions/upload                      │
│     ├─ Headers: CORS enabled                                   │
│     └─ Body: MultipartFile + password param                   │
│                                                                 │
└──────────────────────┬───────────────────────────────────────────┘
                       │
                       ↓
┌─────────────────────────────────────────────────────────────────┐
│                 BACKEND PROCESSING                              │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  5. TransactionController.uploadPdf() receives request         │
│     ├─ Create/Retrieve session ID                              │
│     ├─ Store session ID in HttpSession                         │
│     └─ Call service.processPdf()                               │
│     ↓                                                           │
│  6. TransactionService.processPdf()                            │
│     ├─ Validate: Check if file is empty                        │
│     ├─ Call: PdfUtil.extractText()                             │
│     │   └─ Uses Apache PDFBox library                          │
│     │   └─ Supports password-protected PDFs                    │
│     │   └─ Returns: Raw text string                            │
│     ├─ Call: StatementParser.parse()                           │
│     │   ├─ Clean extracted text (remove headers/footers)       │
│     │   ├─ Split by lines                                      │
│     │   ├─ Match date patterns (MMM dd, yyyy)                  │
│     │   ├─ Extract transaction details:                        │
│     │   │  ├─ Date (LocalDate)                                 │
│     │   │  ├─ Time (LocalTime)                                 │
│     │   │  ├─ Description                                      │
│     │   │  ├─ Transaction ID                                   │
│     │   │  ├─ UTR Number                                       │
│     │   │  ├─ Type (DEBIT/CREDIT)                              │
│     │   │  └─ Amount (Double)                                  │
│     │   └─ Returns: List<Transaction>                          │
│     ↓                                                           │
│  7. Duplicate Check & Database Insert                          │
│     └─ For each Transaction:                                   │
│        ├─ Check: existsByTransactionId()                       │
│        ├─ If NEW: repository.saveAndFlush()                    │
│        │   └─ Persists to MySQL                                │
│        │   └─ Increments insertedCount                         │
│        └─ If EXISTS: Skip (no duplicates)                      │
│     ↓                                                           │
│  8. Session Storage & Caching                                  │
│     ├─ Get all transactions: getAllTransactions()              │
│     ├─ Store in dataManager: storeTransactions(sessionId, list)│
│     └─ Set session attribute: transactionsLoaded=true          │
│     ↓                                                           │
│  9. Return Response to Client                                  │
│     └─ String response: "Parsed: X, Inserted: Y"               │
│                                                                 │
└──────────────────────┬───────────────────────────────────────────┘
                       │
                       ↓
┌─────────────────────────────────────────────────────────────────┐
│                   FRONTEND RESPONSE                             │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  10. JavaScript receives response                              │
│      ├─ Display success message                                │
│      ├─ Show parsed count & inserted count                     │
│      ├─ Redirect to dashboard (index.html or dashboard.html)   │
│      └─ Fetch and display transaction data                     │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### Key Decision Points
- **Empty File Check**: Validates file exists and has content
- **Password Handling**: Optional password parameter for encrypted PDFs
- **Duplicate Detection**: Unique constraint on transaction_id prevents duplicates
- **Batch Processing**: Uses saveAndFlush() for immediate persistence

---

## 2. Data Retrieval & Display Flow

### General Query Flow
```
┌──────────────────────────────────────────────────────────────┐
│                    FRONTEND REQUEST                          │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│  1. User navigates to Dashboard (index.html)                │
│  2. Page loads and calls multiple fetch() requests:         │
│     ├─ getSummary()     → /api/transactions/summary         │
│     ├─ getSpending()    → /api/transactions/spending-insights
│     ├─ getAllTransactions() → /api/transactions/all         │
│     ├─ getTopReceivers()   → /api/transactions/all + filter│
│     └─ getTopSenders()     → /api/transactions/all + filter │
│                                                              │
└──────────────┬──────────────────────────────────────────────┘
               │
               ↓
┌──────────────────────────────────────────────────────────────┐
│                   BACKEND PROCESSING                         │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│  3. TransactionController Routes to Appropriate Endpoint    │
│  4. TransactionService Executes Query via Repository        │
│  5. TransactionRepository Queries MySQL Database            │
│  6. JPA Converts Results to Transaction Objects             │
│  7. Service Aggregates/Transforms Data (if needed)          │
│  8. Controller Returns JSON Response                        │
│                                                              │
└──────────────┬──────────────────────────────────────────────┘
               │
               ↓
┌──────────────────────────────────────────────────────────────┐
│                   FRONTEND PROCESSING                        │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│  9. JavaScript receives JSON response                       │
│  10. Client-side aggregation/transformation (if needed)     │
│  11. Render data to DOM:                                    │
│      ├─ Update stat cards                                   │
│      ├─ Populate tables                                     │
│      ├─ Display charts (Chart.js)                           │
│      └─ Show analytics                                      │
│  12. User views processed data                              │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

---

## 3. Summary Endpoint Flow

### /api/transactions/summary
```
REQUEST: GET /api/transactions/summary
         │
         ↓
BACKEND:
  ├─ getTotalCredit()      → SUM(amount) WHERE type='CREDIT'
  ├─ getTotalDebit()       → SUM(amount) WHERE type='DEBIT'
  ├─ countByType(CREDIT)   → COUNT(*) WHERE type='CREDIT'
  ├─ countByType(DEBIT)    → COUNT(*) WHERE type='DEBIT'
  └─ CALCULATE:
     ├─ balance = totalCredit - totalDebit
     └─ totalTransactions = creditCount + debitCount
         │
         ↓
RESPONSE: JSON
{
  "totalCredit": 50000.00,
  "totalDebit": 30000.00,
  "balance": 20000.00,
  "creditCount": 25,
  "debitCount": 15,
  "totalTransactions": 40,
  "currency": "INR"
}
```

---

## 4. Spending Insights Flow

### /api/transactions/spending-insights
```
REQUEST: GET /api/transactions/spending-insights
         │
         ↓
BACKEND:
  ├─ getTotalCredit()      → Total income
  ├─ getTotalDebit()       → Total expenses
  ├─ getAverageCredit()    → Avg income per transaction
  ├─ getAverageDebit()     → Avg expense per transaction
  ├─ countByType(CREDIT)   → Number of income transactions
  ├─ countByType(DEBIT)    → Number of expense transactions
  └─ CALCULATE:
     ├─ netBalance = totalCredit - totalDebit
     └─ savingsRate = (netBalance / totalCredit) × 100
         │
         ↓
RESPONSE: JSON
{
  "totalIncome": 50000.00,
  "totalExpense": 30000.00,
  "netBalance": 20000.00,
  "averageIncome": 2000.00,
  "averageExpense": 2000.00,
  "incomeTransactions": 25,
  "expenseTransactions": 15,
  "savingsRate": 40.00,
  "currency": "INR"
}
```

---

## 5. Top Receivers/Senders Flow (Client-Side Processing)

### Flow Diagram
```
FRONTEND REQUEST:
  ├─ User clicks "View Top Receivers" button
  └─ Function: getTopReceivers()
     │
     ↓
BACKEND CALL: GET /api/transactions/all
     │
     ↓
RECEIVE: Array of ALL transactions (300+ records)
     │
     ↓
CLIENT-SIDE PROCESSING:
  1. Filter: Keep only CREDIT transactions
  2. Group by Description:
     {
       "Salary": { amount: 100000, count: 12 },
       "Refund": { amount: 5000, count: 2 },
       ...
     }
  3. Convert to Array and Sort by amount DESC
  4. Slice: Keep only top 5
  5. Format with Medal Ranking:
     [
       { rank: 1, medal: "🥇", name: "Salary", amount: 100000, count: 12 },
       { rank: 2, medal: "🥈", name: "Refund", amount: 5000, count: 2 },
       ...
     ]
     │
     ↓
RENDER:
  ├─ Create HTML cards with gradient background
  ├─ Display medal emoji in badge
  ├─ Show receiver name + amount + transaction count
  └─ Apply hover animations
```

### Similar Flow for Top Senders
- Filter: DEBIT transactions instead
- Group by Description (who you paid)
- Calculate total amount sent + count
- Rank and display top 5

---

## 6. Search & Filter Flow

### Search by Keyword
```
FRONTEND:
  ├─ User enters keyword in search box
  ├─ Triggers searchTransactions()
  └─ Sends GET /api/transactions/search?keyword=xyz
     │
     ↓
BACKEND:
  ├─ TransactionService.searchByDescription(keyword)
  ├─ Query: findByDescriptionContainingIgnoreCase(keyword)
  ├─ Match: LIKE '%keyword%' (case-insensitive)
  └─ Return filtered transactions
     │
     ↓
FRONTEND:
  ├─ Display matching transactions in table
  ├─ Show match count
  └─ Highlight relevant columns
```

---

## 7. Category Analysis Flow

### /api/transactions/category-wise-spending
```
REQUEST: GET /api/transactions/category-wise-spending
         │
         ↓
BACKEND:
  1. Get ALL transactions from DB
  2. For each transaction:
     ├─ Call TransactionCategory.categorize(description)
     ├─ Match against patterns:
     │  ├─ Mobile Recharge (Airtel, Jio, VI, etc.)
     │  ├─ Bills & Utilities (Electric, Water, etc.)
     │  ├─ Food & Dining (Swiggy, Zomato, Restaurant, etc.)
     │  ├─ Shopping (Amazon, Flipkart, Myntra, etc.)
     │  ├─ Entertainment (Netflix, Prime, Games, etc.)
     │  ├─ Medical & Health (Apollo, Doctor, Medicine, etc.)
     │  ├─ Transport & Travel (Uber, OLA, Flight, etc.)
     │  ├─ Transfer & Payment (GPay, PhonePay, Bank, etc.)
     │  ├─ Income & Deposits (Salary, Transfer In, etc.)
     │  ├─ Groceries (BigBasket, DMart, etc.)
     │  ├─ Gaming (Steam, PlayStore, etc.)
     │  └─ Other (unmatched)
     └─ Group by category
     │
  3. Aggregate:
     ├─ Total amount per category
     └─ Count per category
         │
         ↓
RESPONSE: JSON
{
  "categoryTotals": {
    "Food & Dining": 15000,
    "Shopping": 20000,
    ...
  },
  "categoryCounts": {
    "Food & Dining": 30,
    "Shopping": 10,
    ...
  }
}
```

### /api/transactions/category-breakdown
```
Extends category-wise-spending by:
  1. Getting total DEBIT only
  2. For each category, calculate percentage
  3. Format response with percentage field
     
RESPONSE: JSON
{
  "Food & Dining": {
    "total": 15000,
    "count": 30,
    "percentage": 25.00
  },
  "Shopping": {
    "total": 20000,
    "count": 10,
    "percentage": 33.33
  },
  ...
}
```

---

## 8. Date Range Query Flow

### /api/transactions/date-range
```
REQUEST: GET /api/transactions/date-range?startDate=2024-01-01&endDate=2024-01-31
         │
         ↓
BACKEND:
  ├─ Parse date parameters
  ├─ Query: findByDateRange(startDate, endDate)
  ├─ SQL: SELECT * WHERE transactionDate BETWEEN ? AND ?
  ├─ Order: By transaction date DESC
  └─ Return filtered transactions
     │
     ↓
RESPONSE: JSON
{
  "startDate": "2024-01-01",
  "endDate": "2024-01-31",
  "count": 42,
  "transactions": [...]
}
```

---

## 9. Recent Transactions Flow

### /api/transactions/recent
```
REQUEST: GET /api/transactions/recent?days=7
         │
         ↓
BACKEND:
  ├─ Calculate startDate = TODAY - N days
  ├─ Query: findByTransactionDateAfter(startDate)
  ├─ SQL: SELECT * WHERE transactionDate >= ?
  ├─ Order: DESC
  └─ Return transactions
     │
     ↓
RESPONSE: JSON
{
  "days": 7,
  "count": 25,
  "transactions": [...]
}
```

---

## 10. Monthly/Daily Summary Flow

### /api/transactions/monthly-summary
```
BACKEND:
  1. Get all transactions
  2. Group by YEAR-MONTH
  3. For each month:
     ├─ Sum of CREDIT transactions
     ├─ Sum of DEBIT transactions
     ├─ Count of transactions
     └─ Calculate net balance
     │
     ↓
RESPONSE: JSON
{
  "monthlySummary": [
    {
      "month": "2024-01",
      "totalCredit": 50000,
      "totalDebit": 30000,
      "balance": 20000,
      "transactionCount": 25
    },
    ...
  ]
}
```

### Similar for Daily Summary
- Group by DATE instead of MONTH

---

## Summary of Information Flows

| Flow | Trigger | Entry Point | Processing | Output |
|------|---------|------------|-----------|--------|
| **Upload PDF** | User action | POST /upload | Text extraction → Parsing → DB insert | Success message + redirect |
| **Summary** | Page load | GET /summary | Aggregation query | Total credit/debit/balance |
| **Insights** | Page load | GET /spending-insights | Complex aggregation | Income/expense analysis |
| **All Transactions** | Page load | GET /all | Simple query | Complete transaction list |
| **Top Receivers** | User action | GET /all + filter | Client-side grouping & sort | Top 5 receivers with totals |
| **Top Senders** | User action | GET /all + filter | Client-side grouping & sort | Top 5 senders with totals |
| **Category Breakdown** | Page load | GET /category-breakdown | Pattern matching + aggregation | Spending by category % |
| **Date Range** | User input | GET /date-range | Range filter + sort | Transactions in date range |
| **Search** | User input | GET /search | LIKE query | Matching transactions |
| **Monthly Summary** | Dashboard | GET /monthly-summary | Group by month | Monthly aggregation |

