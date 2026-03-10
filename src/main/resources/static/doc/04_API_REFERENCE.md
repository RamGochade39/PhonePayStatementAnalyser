# PhonePay Transaction App - Complete API Reference

## Base URL
```
http://localhost:8080/api/transactions
```

## Authentication
Currently **No authentication required** (all endpoints are public)

## Response Format
All endpoints return JSON with standard structure:

### Success Response
```json
{
  "data": {},
  "timestamp": "2024-03-08T10:30:00Z"
}
```

### Error Response
```json
{
  "message": "Error description",
  "status": 400
}
```

---

## 1. UPLOAD ENDPOINTS

### 1.1 Upload PDF Statement
**Upload and parse a PhonePay statement PDF**

```
POST /upload
Content-Type: multipart/form-data
```

**Parameters**:
| Name | Type | Required | Description |
|------|------|----------|-------------|
| file | File | Yes | PDF statement file |
| password | String | No | Password for encrypted PDF |

**cURL Example**:
```bash
curl -X POST http://localhost:8080/api/transactions/upload \
  -F "file=@statement.pdf" \
  -F "password=mypass123"
```

**JavaScript Example**:
```javascript
const formData = new FormData();
formData.append('file', fileInput.files[0]);
formData.append('password', 'mypass123');

fetch('http://localhost:8080/api/transactions/upload', {
  method: 'POST',
  body: formData
})
.then(r => r.json())
.then(data => console.log(data));
```

**Response**:
```json
"Parsed: 42, Inserted: 38"
```
*Note: String response (not JSON object)*

**HTTP Status Codes**:
| Code | Meaning |
|------|---------|
| 200 | Successfully parsed and stored transactions |
| 400 | Empty file or invalid format |
| 401 | Incorrect PDF password |
| 500 | Server error during processing |

**Error Cases**:
```
❌ Empty file: RuntimeException("Uploaded file is empty.")
❌ Wrong password: RuntimeException("Invalid PDF password or corrupted file.")
❌ Corrupted PDF: RuntimeException from PDFBox
```

---

## 2. SUMMARY & OVERVIEW ENDPOINTS

### 2.1 Get Transaction Summary
**Get overall summary of all transactions**

```
GET /summary
```

**Parameters**: None

**Response**:
```json
{
  "totalCredit": 125000.50,
  "totalDebit": 85300.25,
  "balance": 39700.25,
  "creditCount": 28,
  "debitCount": 42,
  "totalTransactions": 70,
  "currency": "INR"
}
```

**Description of Fields**:
- `totalCredit`: Sum of all incoming money (CREDIT type)
- `totalDebit`: Sum of all outgoing money (DEBIT type)
- `balance`: Net balance = totalCredit - totalDebit
- `creditCount`: Number of CREDIT transactions
- `debitCount`: Number of DEBIT transactions
- `totalTransactions`: Total transaction count
- `currency`: Always "INR" (Indian Rupees)

---

### 2.2 Get Account Balance
**Get current account balance breakdown**

```
GET /balance
```

**Response**:
```json
{
  "totalCredit": 125000.50,
  "totalDebit": 85300.25,
  "balance": 39700.25,
  "currency": "INR"
}
```

---

### 2.3 Get Transaction Insights
**Get detailed insights about spending patterns**

```
GET /insights
```

**Response**:
```json
{
  "totalSent": 85300.25,
  "totalReceived": 125000.50,
  "netBalance": 39700.25
}
```

**Simple version** of spending insights (use /spending-insights for more detail).

---

### 2.4 Get Spending Insights
**Get comprehensive spending analysis**

```
GET /spending-insights
```

**Response**:
```json
{
  "totalIncome": 125000.50,
  "totalExpense": 85300.25,
  "netBalance": 39700.25,
  "averageIncome": 4464.30,
  "averageExpense": 2030.96,
  "incomeTransactions": 28,
  "expenseTransactions": 42,
  "savingsRate": 31.73,
  "currency": "INR"
}
```

**Field Definitions**:
- `totalIncome`: Total money received (CREDIT)
- `totalExpense`: Total money spent (DEBIT)
- `netBalance`: Income - Expense
- `averageIncome`: Income ÷ incomeTransactions
- `averageExpense`: Expense ÷ expenseTransactions
- `savingsRate`: (netBalance ÷ totalIncome) × 100 (%)
- `incomeTransactions`: Count of CREDIT transactions
- `expenseTransactions`: Count of DEBIT transactions

---

## 3. TRANSACTION RETRIEVAL ENDPOINTS

### 3.1 Get All Transactions
**Get complete list of all transactions (used for client-side filtering)**

```
GET /all
```

**Parameters**: None

**Response**:
```json
[
  {
    "id": 1,
    "transactionDate": "2024-01-15",
    "transactionTime": "14:30:00",
    "description": "Swiggy Order #12345",
    "transactionId": "TXN20240115000001",
    "utr": "224012210004444",
    "type": "DEBIT",
    "amount": 285.50
  },
  {
    "id": 2,
    "transactionDate": "2024-01-16",
    "transactionTime": "15:45:00",
    "description": "Salary Credit - January",
    "transactionId": "TXN20240116000002",
    "utr": "224012210005555",
    "type": "CREDIT",
    "amount": 50000.00
  }
]
```

**Field Descriptions**:
- `id`: Database primary key
- `transactionDate`: Date in YYYY-MM-DD format
- `transactionTime`: Time in HH:mm:ss format
- `description`: Merchant/purpose description
- `transactionId`: PhonePay's unique transaction identifier
- `utr`: Unique Transaction Reference
- `type`: "DEBIT" or "CREDIT"
- `amount`: Transaction amount in INR

**Performance Note**: Returns ALL transactions. For large datasets, consider pagination.

---

### 3.2 Get Debit Transactions
**Get only outgoing money transactions**

```
GET /debited
```

**Response**: Array of Transaction objects with `type: "DEBIT"`

---

### 3.3 Get Credit Transactions
**Get only incoming money transactions**

```
GET /credited
```

**Response**: Array of Transaction objects with `type: "CREDIT"`

---

### 3.4 Get Recent Transactions
**Get transactions from last N days**

```
GET /recent?days=7
```

**Parameters**:
| Name | Type | Default | Description |
|------|------|---------|-------------|
| days | Integer | 7 | Number of days to look back |

**Query Examples**:
```
/recent?days=7     # Last 7 days
/recent?days=30    # Last 30 days
/recent?days=1     # Last 24 hours
```

**Response**:
```json
{
  "days": 7,
  "count": 12,
  "transactions": [...]
}
```

---

### 3.5 Get Transactions by Date Range
**Get transactions between two specific dates**

```
GET /date-range?startDate=2024-01-01&endDate=2024-01-31
```

**Parameters**:
| Name | Type | Format | Required | Description |
|------|------|--------|----------|-------------|
| startDate | String | YYYY-MM-DD | Yes | Start date (inclusive) |
| endDate | String | YYYY-MM-DD | Yes | End date (inclusive) |

**cURL Example**:
```bash
curl "http://localhost:8080/api/transactions/date-range?startDate=2024-01-01&endDate=2024-01-31"
```

**JavaScript Example**:
```javascript
const params = new URLSearchParams({
  startDate: '2024-01-01',
  endDate: '2024-01-31'
});
fetch(`http://localhost:8080/api/transactions/date-range?${params}`)
  .then(r => r.json())
  .then(data => console.log(data));
```

**Response**:
```json
{
  "startDate": "2024-01-01",
  "endDate": "2024-01-31",
  "count": 42,
  "transactions": [...]
}
```

---

### 3.6 Search Transactions by Keyword
**Search transactions by description (case-insensitive)**

```
GET /search?keyword=swiggy
```

**Parameters**:
| Name | Type | Required | Description |
|------|------|----------|-------------|
| keyword | String | Yes | Search term (LIKE query) |

**Query Examples**:
```
/search?keyword=swiggy      # All Swiggy transactions
/search?keyword=amazon      # All Amazon transactions
/search?keyword=salary      # All salary deposits
/search?keyword=transfer    # All transfers
```

**Response**:
```json
{
  "keyword": "swiggy",
  "count": 15,
  "transactions": [
    {
      "id": 5,
      "description": "Swiggy Order #54321",
      "type": "DEBIT",
      "amount": 350.00,
      ...
    }
  ]
}
```

**SQL Executed**:
```sql
SELECT * FROM transactions_ram 
WHERE description LIKE '%swiggy%' (case-insensitive)
```

---

## 4. AGGREGATE STATISTICS ENDPOINTS

### 4.1 Get Total Credit
**Get total money received**

```
GET /total-credit
```

**Response**:
```json
{
  "totalCredit": 125000.50,
  "currency": "INR"
}
```

---

### 4.2 Get Total Debit
**Get total money spent**

```
GET /total-debit
```

**Response**:
```json
{
  "totalDebit": 85300.25,
  "currency": "INR"
}
```

---

### 4.3 Get Credit Count
**Get number of credit transactions**

```
GET /credit-count
```

**Response**:
```json
{
  "creditTransactions": 28
}
```

---

### 4.4 Get Debit Count
**Get number of debit transactions**

```
GET /debit-count
```

**Response**:
```json
{
  "debitTransactions": 42
}
```

---

### 4.5 Get Average Credit
**Get average amount per credit transaction**

```
GET /average-credit
```

**Response**:
```json
{
  "averageCredit": 4464.30,
  "currency": "INR"
}
```

**Calculation**: totalCredit ÷ creditCount

---

### 4.6 Get Average Debit
**Get average amount per debit transaction**

```
GET /average-debit
```

**Response**:
```json
{
  "averageDebit": 2030.96,
  "currency": "INR"
}
```

---

### 4.7 Get Highest Transaction
**Get the largest transaction (by amount)**

```
GET /highest
```

**Response**:
```json
{
  "id": 42,
  "amount": 50000.00,
  "type": "CREDIT",
  "description": "Salary Credit - January",
  "date": "2024-01-01",
  "time": "08:00:00",
  "currency": "INR"
}
```

---

### 4.8 Get Lowest Transaction
**Get the smallest transaction (by amount)**

```
GET /lowest
```

**Response**:
```json
{
  "id": 15,
  "amount": 5.00,
  "type": "DEBIT",
  "description": "Google Play Purchase",
  "date": "2024-01-20",
  "time": "18:30:00",
  "currency": "INR"
}
```

---

### 4.9 Get Top N Transactions
**Get highest value transactions (limit)**

```
GET /top-transactions?limit=10
```

**Parameters**:
| Name | Type | Default | Description |
|------|------|---------|-------------|
| limit | Integer | 10 | Number of top transactions to return |

**Response**:
```json
{
  "limit": 10,
  "count": 10,
  "transactions": [
    {
      "id": 42,
      "amount": 50000.00,
      ...
    },
    ...
  ]
}
```

**Order**: Descending by amount

---

## 5. TIME-BASED AGGREGATION ENDPOINTS

### 5.1 Get Monthly Summary
**Get summary aggregated by month**

```
GET /monthly-summary
```

**Response**:
```json
{
  "monthlySummary": [
    {
      "month": "2024-01",
      "totalCredit": 50000.00,
      "totalDebit": 30000.00,
      "balance": 20000.00,
      "transactionCount": 25
    },
    {
      "month": "2024-02",
      "totalCredit": 45000.00,
      "totalDebit": 35000.00,
      "balance": 10000.00,
      "transactionCount": 22
    }
  ]
}
```

**Use Case**: Trend analysis, month-over-month comparison

---

### 5.2 Get Daily Summary
**Get summary aggregated by day**

```
GET /daily-summary
```

**Response**:
```json
{
  "dailySummary": [
    {
      "date": "2024-01-15",
      "totalCredit": 1500.00,
      "totalDebit": 2000.00,
      "balance": -500.00,
      "transactionCount": 3
    },
    {
      "date": "2024-01-16",
      "totalCredit": 50000.00,
      "totalDebit": 100.00,
      "balance": 49900.00,
      "transactionCount": 2
    }
  ]
}
```

**Use Case**: Daily spending patterns, cash flow analysis

---

## 6. CATEGORY ENDPOINTS

### 6.1 Get All Available Categories
**List all transaction categories used for classification**

```
GET /categories
```

**Response**:
```json
{
  "categories": [
    "Mobile Recharge",
    "Bills & Utilities",
    "Food & Dining",
    "Shopping",
    "Entertainment",
    "Medical & Health",
    "Transport & Travel",
    "Transfer & Payment",
    "Income & Deposits",
    "Groceries",
    "Gaming",
    "Other"
  ],
  "message": "All available transaction categories with smart pattern matching"
}
```

---

### 6.2 Get Category-Wise Spending
**Get spending totals and counts by category**

```
GET /category-wise-spending
```

**Response**:
```json
{
  "categoryTotals": {
    "Food & Dining": 15000.00,
    "Shopping": 20000.00,
    "Entertainment": 5000.00,
    "Bills & Utilities": 8000.00,
    "Other": 15000.00
  },
  "categoryCounts": {
    "Food & Dining": 30,
    "Shopping": 10,
    "Entertainment": 8,
    "Bills & Utilities": 12,
    "Other": 5
  }
}
```

---

### 6.3 Get Category Breakdown with Percentages
**Get spending by category with percentage breakdown**

```
GET /category-breakdown
```

**Response**:
```json
{
  "Food & Dining": {
    "total": 15000.00,
    "count": 30,
    "percentage": 25.42
  },
  "Shopping": {
    "total": 20000.00,
    "count": 10,
    "percentage": 33.90
  },
  "Entertainment": {
    "total": 5000.00,
    "count": 8,
    "percentage": 8.48
  },
  ...
}
```

**Percentage Calculation**: (categoryTotal ÷ totalDebit) × 100

---

### 6.4 Get Top Spending Categories
**Get top N categories by spending amount**

```
GET /top-categories?limit=5
```

**Parameters**:
| Name | Type | Default | Description |
|------|------|---------|-------------|
| limit | Integer | 5 | Number of top categories |

**Response**:
```json
{
  "topCategories": [
    {
      "category": "Shopping",
      "total": 20000.00,
      "count": 10,
      "percentage": 33.90
    },
    {
      "category": "Food & Dining",
      "total": 15000.00,
      "count": 30,
      "percentage": 25.42
    },
    ...
  ],
  "limit": 5
}
```

**Order**: Descending by total amount

---

### 6.5 Get Transactions by Category
**Get all transactions in a specific category**

```
GET /transactions-by-category?category=Food%20&%20Dining
```

**Parameters**:
| Name | Type | Required | Description |
|------|------|----------|-------------|
| category | String | Yes | Exact category name (URL encoded) |

**cURL Example**:
```bash
curl "http://localhost:8080/api/transactions/transactions-by-category?category=Food%20%26%20Dining"
```

**Response**:
```json
{
  "category": "Food & Dining",
  "count": 30,
  "transactions": [...]
}
```

---

## 7. ERROR RESPONSES

### 7.1 Common HTTP Status Codes

| Code | Scenario | Example |
|------|----------|---------|
| **200** | Success | Valid request completed |
| **400** | Bad Request | Invalid parameters (malformed date) |
| **404** | Not Found | Non-existent endpoint |
| **500** | Server Error | Database connection failure |

### 7.2 Error Response Format

```json
{
  "timestamp": "2024-03-08T10:35:22Z",
  "status": 500,
  "error": "Internal Server Error",
  "message": "Invalid PDF password or corrupted file.",
  "path": "/api/transactions/upload"
}
```

### 7.3 Common Error Messages

| Error | Cause | Solution |
|-------|-------|----------|
| "Uploaded file is empty." | File has no content | Select valid PDF |
| "Invalid PDF password or corrupted file." | Wrong password or bad PDF | Check password / validate PDF |
| Date parse error | Date format doesn't match | Ensure PDF is PhonePay format |
| Amount parse error | Invalid amount format | Check PDF text extraction |

---

## 8. RATE LIMITING & PERFORMANCE

**Current State**: No rate limiting implemented

**Recommended Limits**:
- Upload: 10 requests/minute per session
- Query: 100 requests/minute per session
- Search: 50 requests/minute per session

**Performance Tips**:
1. Use `/recent?days=30` instead of `/all` for large datasets
2. Filter client-side when fetching `/all` to reduce bandwidth
3. Cache category lookups (categories don't change)
4. Batch API calls to reduce request overhead

---

## 9. EXAMPLE API SEQUENCES

### Sequence 1: Upload and View Summary
```bash
# 1. Upload PDF
curl -X POST http://localhost:8080/api/transactions/upload \
  -F "file=@statement.pdf"

# 2. Get summary
curl http://localhost:8080/api/transactions/summary

# 3. Get spending insights
curl http://localhost:8080/api/transactions/spending-insights
```

### Sequence 2: Find Top Spenders & Get Details
```bash
# 1. Get all transactions
curl http://localhost:8080/api/transactions/all

# 2. Client-side: Filter DEBIT, group by description, sort by amount
# 3. Display top 5

# 4. Get category breakdown (backup if client-side fails)
curl http://localhost:8080/api/transactions/category-breakdown
```

### Sequence 3: Monthly Trend Analysis
```bash
# 1. Get monthly summary
curl http://localhost:8080/api/transactions/monthly-summary

# 2. For each month, get transactions
curl "http://localhost:8080/api/transactions/date-range?startDate=2024-01-01&endDate=2024-01-31"
```

---

## 10. CORS HEADERS

All endpoints support CORS with the following headers:

```
Access-Control-Allow-Origin: *
Access-Control-Allow-Methods: GET, POST, OPTIONS
Access-Control-Allow-Headers: *
Access-Control-Allow-Credentials: true
```

**Browser Example**:
```javascript
fetch('http://localhost:8080/api/transactions/all', {
  method: 'GET',
  headers: {
    'Content-Type': 'application/json'
  }
})
.then(r => r.json())
.then(data => console.log(data));
```

---

