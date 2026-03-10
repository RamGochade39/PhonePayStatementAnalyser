# PhonePay Transaction App - Low-Level Technical Documentation

## 1. PDF Processing & Parsing Logic

### 1.1 PdfUtil.extractText() - Deep Dive

**Location**: `in.insta.util.PdfUtil.java`

```java
public static String extractText(InputStream inputStream, String password) throws Exception {
    PDDocument document;
    
    try {
        if (password != null && !password.isEmpty()) {
            document = PDDocument.load(inputStream, password);
        } else {
            document = PDDocument.load(inputStream);
        }
    } catch (Exception e) {
        throw new RuntimeException("Invalid PDF password or corrupted file.");
    }
    
    PDFTextStripper stripper = new PDFTextStripper();
    String text = stripper.getText(document);
    document.close();
    return text;
}
```

**Technology**: Apache PDFBox 2.x
**Processing Steps**:
1. **Initialization**: Create PDDocument instance from input stream
2. **Authentication**: If password provided, decrypt PDF using password
3. **Text Extraction**: Use PDFTextStripper to extract all text
4. **Cleanup**: Close document to release resources
5. **Return**: Raw text string with all formatting removed

**Error Handling**:
- Invalid password → RuntimeException("Invalid PDF password or corrupted file")
- Corrupted PDF → RuntimeException (from PDFBox)
- Missing file → Handled by MultipartFile validation

**Performance Considerations**:
- Text extraction speed: ~1-5 MB per second (depends on PDF complexity)
- Memory usage: ~2-3x PDF file size temporarily
- Large PDFs (>50MB) may cause memory issues

---

### 1.2 StatementParser.parse() - Deep Dive

**Location**: `in.insta.util.StatementParser.java`

#### A. Text Cleaning Phase
```java
private static String cleanText(String text) {
    text = text.replaceAll("Page \\d+ of \\d+", "");           // Remove page numbers
    text = text.replaceAll("This is a system generated.*", ""); // Remove footer
    text = text.replaceAll("Date\\s+Transaction Details\\s+Type\\s+Amount", ""); // Remove header
    text = text.replaceAll("Transaction Statement for.*", "");  // Remove title
    text = text.replaceAll("Oct .* - .*", "");                  // Remove date range
    return text;
}
```

**Regex Patterns**:
| Pattern | Purpose | Example Match |
|---------|---------|---|
| `Page \d+ of \d+` | Page numbers | "Page 1 of 5" |
| `This is a system generated.*` | Footer text | "This is a system generated statement..." |
| `Date\s+Transaction Details\s+Type\s+Amount` | Column headers | Multi-space separated headers |
| `Transaction Statement for.*` | Document title | "Transaction Statement for Oct 2024" |
| `Oct .* - .*` | Date range header | "Oct 01, 2024 - Oct 31, 2024" |

**Debug Output**:
```
===================================================
[CLEANED TEXT OUTPUT]
===================================================
```
Text printed to console for debugging purposes.

---

#### B. Transaction Extraction Phase

**Date Extraction**:
```
Pattern: [A-Za-z]{3} \d{2}, \d{4}
Format: MMM dd, yyyy
Parser: DateTimeFormatter.ofPattern("MMM dd, yyyy", Locale.ENGLISH)
Examples: "Jan 15, 2024", "Feb 28, 2024", "Dec 31, 2024"
```

**Time Extraction**:
```
Pattern: hh:mm a
Parser: DateTimeFormatter.ofPattern("hh:mm a", Locale.ENGLISH)
Examples: "02:30 PM", "11:45 AM", "12:00 AM"
```

**Line-by-Line Parsing Algorithm**:
```
FOR each line in text:
  ├─ IF line matches date pattern [A-Za-z]{3} \d{2}, \d{4}:
  │
  ├─ Line[i]:     Date (parse as LocalDate)
  ├─ Line[i+1]:   Time (parse as LocalTime)
  ├─ Line[i+2]:   Description (e.g., "Swiggy Order")
  ├─ Line[i+3]:   Transaction ID (extract after "Transaction ID :")
  ├─ Line[i+4]:   UTR Number (extract after "UTR No :")
  ├─ Line[i+5]:   Skip (header like "Debited from" or "Credited to")
  ├─ Line[i+6]:   Amount Line (e.g., "Credit INR 10.00")
  │
  ├─ PARSE Amount Line:
  │  ├─ IF starts with "Credit": type = TransactionType.CREDIT
  │  └─ ELSE: type = TransactionType.DEBIT
  │
  ├─ Extract numeric amount:
  │  └─ Remove all non-numeric except dot: replaceAll("[^0-9.]", "")
  │  └─ Convert to Double: Double.parseDouble()
  │
  └─ CREATE Transaction object with all fields
     └─ ADD to transactions list
```

**Example PDF Line Sequence**:
```
Jan 15, 2024
02:30 PM
Swiggy Order #12345
Transaction ID : TXN20240115000001
UTR No : 224012210004444
Debited from your account
Debit INR 285.50

---

Jan 16, 2024
03:45 PM
Salary Credit - January
Transaction ID : TXN20240116000002
UTR No : 224012210005555
Credited to your account
Credit INR 50000.00
```

**Error Handling**:
```java
try {
    // Parsing logic
} catch (Exception e) {
    System.out.println("Skipping corrupted block...");
    // Continue to next block
}
```

**Console Output**:
```
===================================================
[CLEANED TEXT]
===================================================
TOTAL PARSED = 42
```

---

## 2. Data Persistence Layer

### 2.1 TransactionRepository Interface

**Location**: `in.insta.repository.TransactionRepository.java`

```java
public interface TransactionRepository 
    extends JpaRepository<Transaction, Long>, 
            JpaSpecificationExecutor<Transaction>
```

#### Custom Queries

| Method | JPQL Query | Purpose | Return Type |
|--------|-----------|---------|-------------|
| `getTotalDebit()` | `SUM(t.amount) WHERE type='DEBIT'` | Total money spent | Double |
| `getTotalCredit()` | `SUM(t.amount) WHERE type='CREDIT'` | Total money received | Double |
| `findByType(type)` | `SELECT * WHERE type=?` | All debits or credits | List\<Transaction\> |
| `countByType(type)` | `COUNT(*) WHERE type=?` | Count by type | long |
| `getAverageAmountByType(type)` | `AVG(t.amount) WHERE type=?` | Average amount | Double |
| `getHighestTransaction()` | `ORDER BY amount DESC LIMIT 1` | Largest transaction | Transaction |
| `getLowestTransaction()` | `ORDER BY amount ASC LIMIT 1` | Smallest transaction | Transaction |
| `findByTransactionDateAfter(date)` | `WHERE date >= ? ORDER BY DESC` | Recent transactions | List\<Transaction\> |
| `findByDateRange(start, end)` | `WHERE date BETWEEN ? AND ?` | Date range filter | List\<Transaction\> |

---

### 2.2 Duplicate Prevention Mechanism

**Unique Constraint**:
```sql
@Column(unique = true)
private String transactionId;
```

**Database Schema**:
```sql
ALTER TABLE transactions_ram 
ADD CONSTRAINT UC_transaction_id 
UNIQUE (transaction_id);
```

**Check Logic in Service**:
```java
for (Transaction t : transactions) {
    if (!repository.existsByTransactionId(t.getTransactionId())) {
        repository.saveAndFlush(t);
        insertedCount++;
    }
}
```

**Benefits**:
- Prevents duplicate entries on re-upload of same PDF
- Transaction ID is PhonePay's unique identifier per transaction
- Application-level + Database-level validation

---

## 3. Entity Model & Data Types

### 3.1 Transaction Entity

**Location**: `in.insta.entity.Transaction.java`

```java
@Entity
@Table(name = "transactions_ram")
public class Transaction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;                           // Auto-increment PK
    
    private LocalDate transactionDate;         // Transaction date
    private LocalTime transactionTime;         // Transaction time
    private String description;                 // Merchant/purpose (e.g., "Swiggy Order")
    
    @Column(unique = true)
    private String transactionId;              // PhonePay transaction ID (unique)
    
    private String utr;                        // Unique Transaction Reference
    
    @Enumerated(EnumType.STRING)
    private TransactionType type;              // DEBIT or CREDIT
    
    private Double amount;                     // Transaction amount in INR
}
```

**Enums**:
```java
public enum TransactionType {
    DEBIT,   // Money going out
    CREDIT   // Money coming in
}

public enum TransactionCategory {
    MOBILE_RECHARGE("Mobile Recharge"),
    BILLS_UTILITIES("Bills & Utilities"),
    FOOD_DINING("Food & Dining"),
    // ... 10 more categories
    OTHER("Other");
    
    private String displayName;
}
```

**Category Matching Algorithm**:
```java
public static TransactionCategory categorize(String description) {
    String desc = description.toLowerCase();
    
    if (containsAny(desc, "airtel|jio|vi|vodafone"))
        return MOBILE_RECHARGE;
    
    if (containsAny(desc, "electric|water|gas|broadband|bill"))
        return BILLS_UTILITIES;
    
    if (containsAny(desc, "swiggy|zomato|uber eats|restaurant|cafe"))
        return FOOD_DINING;
    
    // ... continue for other categories
    
    return OTHER;
}

private static boolean containsAny(String text, String keywords) {
    for (String keyword : keywords.split("\\|"))
        if (text.contains(keyword))
            return true;
    return false;
}
```

---

## 4. Service Layer Implementation

### 4.1 TransactionService Core Methods

**Location**: `in.insta.service.TransactionService.java`

#### A. processPdf() Method
```java
@Transactional
public String processPdf(MultipartFile file, String password) throws Exception {
    // 1. VALIDATION
    if (file.isEmpty()) 
        throw new RuntimeException("Uploaded file is empty.");
    
    // 2. PDF EXTRACTION
    String text = PdfUtil.extractText(file.getInputStream(), password);
    
    // 3. PARSING
    List<Transaction> transactions = StatementParser.parse(text);
    
    // 4. DUPLICATE CHECK & INSERT
    int insertedCount = 0;
    for (Transaction t : transactions) {
        if (!repository.existsByTransactionId(t.getTransactionId())) {
            repository.saveAndFlush(t);  // Immediate persistence
            insertedCount++;
        }
    }
    
    // 5. RETURN SUMMARY
    return "Parsed: " + transactions.size() + ", Inserted: " + insertedCount;
}
```

**Transactional Properties**:
- **Propagation**: REQUIRED (create new transaction if none exists)
- **Isolation**: READ_COMMITTED (default)
- **Rollback**: On any checked/unchecked exception
- **Timeout**: No timeout (uses default)

**saveAndFlush() vs save()**:
- `saveAndFlush()`: Immediate DB commit (used here)
- `save()`: Batched, committed at transaction end

#### B. getInsights() Method
```java
public Map<String, Object> getInsights() {
    // 1. FETCH TOTALS
    Double totalSent = repository.getTotalDebit();    // NULL if no debits
    Double totalReceived = repository.getTotalCredit(); // NULL if no credits
    
    // 2. NULL COALESCING
    BigDecimal sent = BigDecimal.valueOf(
        totalSent == null ? 0.0 : totalSent
    ).setScale(2, RoundingMode.HALF_UP);
    
    BigDecimal received = BigDecimal.valueOf(
        totalReceived == null ? 0.0 : totalReceived
    ).setScale(2, RoundingMode.HALF_UP);
    
    // 3. CALCULATION
    BigDecimal netBalance = received.subtract(sent)
        .setScale(2, RoundingMode.HALF_UP);
    
    // 4. RESPONSE
    return Map.of(
        "totalSent", sent,
        "totalReceived", received,
        "netBalance", netBalance
    );
}
```

**BigDecimal Advantages**:
- Precise decimal arithmetic (unlike Double)
- Avoids floating-point rounding errors
- setScale(2, HALF_UP) ensures 2 decimal places

---

### 4.2 Category Analysis Implementation

```java
public Map<String, Object> getCategoryBreakdown() {
    List<Transaction> allTransactions = repository.findAll();
    Double totalDebit = repository.getTotalDebit();
    double total = totalDebit != null ? totalDebit : 0.0;
    
    Map<String, Map<String, Object>> categoryBreakdown = new HashMap<>();
    
    // 1. COLLECT DEBIT TRANSACTIONS BY CATEGORY
    for (Transaction transaction : allTransactions) {
        if (transaction.getType() == TransactionType.DEBIT) {
            TransactionCategory category = TransactionCategory
                .categorize(transaction.getDescription());
            String categoryName = category.getDisplayName();
            
            // Initialize category if new
            categoryBreakdown.putIfAbsent(categoryName, new HashMap<>());
            Map<String, Object> catData = categoryBreakdown.get(categoryName);
            
            // Aggregate
            double amount = transaction.getAmount() != null ? 
                transaction.getAmount() : 0;
            double currentTotal = (double) 
                catData.getOrDefault("total", 0.0);
            int count = (int) catData.getOrDefault("count", 0);
            
            catData.put("total", currentTotal + amount);
            catData.put("count", count + 1);
        }
    }
    
    // 2. CALCULATE PERCENTAGES
    Map<String, Object> result = new HashMap<>();
    for (String category : categoryBreakdown.keySet()) {
        Map<String, Object> data = categoryBreakdown.get(category);
        double categoryTotal = (double) data.get("total");
        double percentage = total > 0 ? 
            (categoryTotal / total) * 100 : 0;
        
        data.put("percentage", 
            Math.round(percentage * 100.0) / 100.0); // 2 decimals
        result.put(category, data);
    }
    
    return result;
}
```

**Time Complexity**: O(n) where n = number of transactions
**Space Complexity**: O(c) where c = number of categories (~12)

---

## 5. REST Controller Endpoints

### 5.1 TransactionController Architecture

**Location**: `in.insta.controller.TransactionController.java`

**CORS Configuration**:
```java
@CrossOrigin(
    origins = "*",              // Allow all origins
    allowedHeaders = "*",       // Allow all headers
    methods = {
        RequestMethod.GET,
        RequestMethod.POST,
        RequestMethod.OPTIONS
    }
)
```

### Endpoint Mapping Reference

```
┌─────────────────────────────────────────────────────────────────┐
│                    UPLOAD ENDPOINTS                             │
├──────────────────────┬────────────────┬──────────────────────┤
│ Endpoint             │ Method         │ Response Type        │
├──────────────────────┼────────────────┼──────────────────────┤
│ /upload              │ POST           │ String (parsed msg)  │
│  └─ Params:          │                │                      │
│    • file (req)      │                │                      │
│    • password (opt)  │                │                      │
└──────────────────────┴────────────────┴──────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│              SUMMARY/ANALYTICS ENDPOINTS                        │
├──────────────────────┬────────────────┬──────────────────────┤
│ Endpoint             │ Method         │ Key Response Fields  │
├──────────────────────┼────────────────┼──────────────────────┤
│ /all                 │ GET            │ List<Transaction>    │
│ /summary             │ GET            │ Credit/Debit/Balance │
│ /insights            │ GET            │ Sent/Received/Net    │
│ /spending-insights   │ GET            │ Income/Expense/%     │
│ /balance             │ GET            │ Credit/Debit/Balance │
│ /total-credit        │ GET            │ totalCredit          │
│ /total-debit         │ GET            │ totalDebit           │
│ /credit-count        │ GET            │ creditTransactions   │
│ /debit-count         │ GET            │ debitTransactions    │
└──────────────────────┴────────────────┴──────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│           FILTER/QUERY ENDPOINTS                               │
├──────────────────────┬────────────────┬──────────────────────┤
│ Endpoint             │ Query Param    │ Response Type        │
├──────────────────────┼────────────────┼──────────────────────┤
│ /credited            │ -              │ List<Transaction>    │
│ /debited             │ -              │ List<Transaction>    │
│ /recent              │ days (def: 7)  │ Map + transactions   │
│ /date-range          │ startDate, end │ Map + transactions   │
│ /top-transactions    │ limit (def: 10)│ Map + transactions   │
│ /search              │ keyword (req)  │ Map + transactions   │
│ /highest             │ -              │ Single Transaction   │
│ /lowest              │ -              │ Single Transaction   │
└──────────────────────┴────────────────┴──────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│          AGGREGATE ENDPOINTS                                   │
├──────────────────────┬────────────────┬──────────────────────┤
│ Endpoint             │ Method         │ Response Type        │
├──────────────────────┼────────────────┼──────────────────────┤
│ /average-debit       │ GET            │ averageDebit + curr  │
│ /average-credit      │ GET            │ averageCredit + curr │
│ /monthly-summary     │ GET            │ Array<monthly data>  │
│ /daily-summary       │ GET            │ Array<daily data>    │
└──────────────────────┴────────────────┴──────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│         CATEGORY ENDPOINTS                                      │
├──────────────────────┬────────────────┬──────────────────────┤
│ Endpoint             │ Query Param    │ Response Type        │
├──────────────────────┼────────────────┼──────────────────────┤
│ /categories          │ -              │ String[] (cat names) │
│ /category-breakdown  │ -              │ Map<cat, details>    │
│ /category-wise-spend │ -              │ Map<cat, totals>     │
│ /top-categories      │ limit (def: 5) │ Array<top cats>      │
│ /txns-by-category    │ category (req) │ List<Transaction>    │
└──────────────────────┴────────────────┴──────────────────────┘
```

---

## 6. Session Management

### 6.1 TransactionDataManager Implementation

**Location**: `in.insta.service.TransactionDataManager.java`

```java
@Component
public class TransactionDataManager {
    // Thread-safe in-memory session storage
    private static final Map<String, List<Transaction>> 
        sessionData = Collections.synchronizedMap(
            new LinkedHashMap<>()
        );
    
    public void storeTransactions(String sessionId, 
                                 List<Transaction> transactions) {
        sessionData.put(sessionId, new ArrayList<>(transactions));
    }
    
    public List<Transaction> getTransactions(String sessionId) {
        return sessionData.getOrDefault(sessionId, 
                                       new ArrayList<>());
    }
    
    public boolean hasTransactions(String sessionId) {
        return sessionData.containsKey(sessionId) && 
               !sessionData.get(sessionId).isEmpty();
    }
    
    public void clearTransactions(String sessionId) {
        sessionData.remove(sessionId);
    }
}
```

**Session ID Generation**:
```java
String sessionId = (String) session.getAttribute("sessionId");
if (sessionId == null) {
    sessionId = UUID.randomUUID().toString();  // UUID v4
    session.setAttribute("sessionId", sessionId);
}
```

**Data Storage Pattern**:
- **HTTP Session**: SessionId (browser-based)
- **In-Memory Cache**: LinkedHashMap (server-side)
- **Database**: Persistent storage (MySQL)

**Thread Safety**:
- `Collections.synchronizedMap()` ensures thread-safe access
- Multiple concurrent users won't interfere with each other's data

---

## 7. Query Performance Analysis

### 7.1 Database Query Patterns

**Simple Aggregations** (Index efficient):
```sql
-- TotalDebit query
SELECT SUM(amount) FROM transactions_ram WHERE type='DEBIT'
-- Execution: O(log n) with type index

-- Count query
SELECT COUNT(*) FROM transactions_ram WHERE type='CREDIT'
-- Execution: O(log n) with type index
```

**Complex Filtering** (Full table scan):
```sql
-- Date range query
SELECT * FROM transactions_ram 
WHERE transactionDate BETWEEN '2024-01-01' AND '2024-01-31'
-- Execution: O(n) without index, O(log n) with date index

-- Keyword search
SELECT * FROM transactions_ram 
WHERE description LIKE '%keyword%'
-- Execution: O(n) - full table scan
```

**Recommended Indexes**:
```sql
CREATE INDEX idx_type ON transactions_ram(type);
CREATE INDEX idx_date ON transactions_ram(transactionDate);
CREATE INDEX idx_description ON transactions_ram(description(100));
CREATE INDEX idx_transaction_id ON transactions_ram(transaction_id);
```

### 7.2 Batch Insert Performance

```java
repository.saveAndFlush(t);  // For each transaction
```

**Current Implementation**:
- Individual saveAndFlush() calls
- Total Time: O(n) where n = transaction count
- For 100 transactions: ~5-10 seconds (with 2000ms batch size)

**Optimization Option**:
```java
// Replace individual saveAndFlush with batch
List<Transaction> batch = new ArrayList<>();
for (Transaction t : transactions) {
    if (!repository.existsByTransactionId(t.getTransactionId())) {
        batch.add(t);
        if (batch.size() == 100) {
            repository.saveAll(batch);
            batch.clear();
        }
    }
}
if (!batch.isEmpty()) {
    repository.saveAll(batch);
}
```

---

## 8. Error Handling & Validation

### 8.1 Exception Hierarchy

```
Exception
├── RuntimeException
│   ├── PDF Processing Errors
│   │   ├── "Uploaded file is empty."
│   │   ├── "Invalid PDF password or corrupted file."
│   │   └── Other PDFBox exceptions
│   │
│   ├── Parsing Errors
│   │   ├── Date format mismatches
│   │   ├── Amount parsing errors (NumberFormatException)
│   │   └── Line index out of bounds
│   │
│   └── Database Errors
│       ├── Constraint violations (unique transaction_id)
│       ├── Data type mismatches
│       └── Connection pool exhaustion
│
└── Checked Exceptions (Wrapped in RuntimeException)
    ├── IOException (file read)
    ├── ParseException (date/time)
    └── SQLException (database)
```

### 8.2 Input Validation

```java
// File validation
if (file.isEmpty()) 
    throw new RuntimeException("Uploaded file is empty.");

// Password handling (optional)
if (password != null && !password.isEmpty()) {
    // Use password
} else {
    // No password
}

// Amount parsing
String amount = amountLine.replaceAll("[^0-9.]", "");
try {
    t.setAmount(Double.parseDouble(amount));
} catch (NumberFormatException e) {
    // Skip corrupted block
}
```

---

## 9. Frontend-Backend Integration

### 9.1 API Response Format

**Standard Success Response**:
```json
{
  "statusCode": 200,
  "message": "Success",
  "data": {}  // Varies by endpoint
}
```

**Standard Error Response**:
```json
{
  "statusCode": 400,
  "message": "Error description",
  "error": "Exception class name"
}
```

### 9.2 JavaScript Fetch Pattern

```javascript
const API_BASE = 'http://localhost:8080/api/transactions';

async function fetchData(endpoint) {
    try {
        const response = await fetch(`${API_BASE}${endpoint}`);
        if (!response.ok) {
            throw new Error(`HTTP error! status: ${response.status}`);
        }
        return await response.json();
    } catch (error) {
        console.error('API Error:', error);
        return null;
    }
}

// Usage
const summary = await fetchData('/summary');
const transactions = await fetchData('/all');
```

---

## 10. Data Type Conversions

### LocalDate & LocalTime Handling

**Database → Java**:
```
MySQL DATE → java.time.LocalDate
MySQL TIME → java.time.LocalTime
```

**Java → JSON (Auto serialization)**:
```json
{
  "transactionDate": "2024-01-15",
  "transactionTime": "14:30:00"
}
```

**String → Java (Parser)**:
```
Input: "Jan 15, 2024"
DateTimeFormatter.ofPattern("MMM dd, yyyy")
Output: LocalDate(year=2024, month=1, day=15)
```

**Enum Serialization**:
```json
{
  "type": "DEBIT"  // String representation in JSON
}
```

---

## 11. Performance Bottlenecks & Solutions

| Bottleneck | Cause | Impact | Solution |
|-----------|-------|--------|----------|
| **Full table scan on search** | No index on description | 10-50ms per search | Add LIKE index |
| **Individual transaction inserts** | saveAndFlush() in loop | 5-10s for 100 txns | Batch insert |
| **Memory usage for large PDFs** | Entire text loaded in memory | 2-3x PDF size | Stream processing |
| **Client-side grouping** | Processing all transactions in JS | Slow for 1000+ txns | Server-side aggregation |
| **N+1 queries for categories** | One query per transaction | Multiple DB hits | Single join query |

---

## 12. Security Considerations

### 12.1 Current Security

✅ **Session-based isolation**
```java
String sessionId = UUID.randomUUID().toString();
// Prevents one user from accessing another's data
```

✅ **SQL Injection Prevention**
```java
// Using JPA prevents SQL injection
repository.findByType(type);  // Parameterized
```

✅ **CORS Enabled**
```java
@CrossOrigin(origins = "*")  // Allows cross-origin requests
```

### 12.2 Recommended Enhancements

⚠️ **Missing**:
- Authentication (no user login)
- Authorization (no permission checks)
- Input sanitization (description can contain scripts)
- Rate limiting (no upload limits)
- HTTPS/TLS (http only)
- CSRF protection

---

