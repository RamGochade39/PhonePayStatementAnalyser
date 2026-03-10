package in.insta.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import in.insta.entity.Transaction;
import in.insta.entity.TransactionCategory;
import in.insta.entity.TransactionType;
import in.insta.util.PdfUtil;
import in.insta.util.StatementParser;

@Service
public class TransactionService {

	// In-memory session storage - cleared on logout
	private final List<Transaction> sessionTransactions = Collections.synchronizedList(new ArrayList<>());
	private final Set<String> processedTransactionIds = Collections.synchronizedSet(new HashSet<>());

	public TransactionService() {
		// Constructor without FileBasedTransactionStore dependency
	}

	public String processPdf(MultipartFile file, String password) throws Exception {

		if (file == null || file.isEmpty()) {
			throw new IllegalArgumentException("PDF file is empty or null");
		}

		if (file.getSize() == 0) {
			throw new IllegalArgumentException("PDF file has no content");
		}

		try {
			String text = PdfUtil.extractText(file.getInputStream(), password);
			
			if (text == null || text.trim().isEmpty()) {
				throw new IllegalArgumentException("No text could be extracted from PDF. The file may be empty or image-based");
			}

			List<Transaction> transactions = StatementParser.parse(text);
			
			if (transactions == null || transactions.isEmpty()) {
				throw new IllegalArgumentException("No transactions found in the PDF. The file format may not be recognized");
			}
			
			int insertedCount = 0;

			for (Transaction t : transactions) {
				if (!processedTransactionIds.contains(t.getTransactionId())) {
					sessionTransactions.add(t);
					processedTransactionIds.add(t.getTransactionId());
					insertedCount++;
				}
			}

			return "✅ Parsed: " + transactions.size() + " transactions, Inserted: " + insertedCount + " new transactions";

		} catch (Exception e) {
			// Re-throw with more context
			if (e.getMessage().contains("password")) {
				throw new RuntimeException("Wrong password or password-protected PDF", e);
			} else if (e.getMessage().contains("PDF")) {
				throw new RuntimeException("Invalid or corrupted PDF file", e);
			} else {
				throw new RuntimeException("Error processing PDF: " + e.getMessage(), e);
			}
		}
	}

	public Map<String, Object> getInsights() {
	    Double totalSent = getTotalDebit();
	    Double totalReceived = getTotalCredit();

	    BigDecimal sent = BigDecimal.valueOf(
	            totalSent == null ? 0.0 : totalSent
	    ).setScale(2, RoundingMode.HALF_UP);

	    BigDecimal received = BigDecimal.valueOf(
	            totalReceived == null ? 0.0 : totalReceived
	    ).setScale(2, RoundingMode.HALF_UP);

	    BigDecimal netBalance = received.subtract(sent)
	            .setScale(2, RoundingMode.HALF_UP);

	    Map<String, Object> result = new HashMap<>();
	    result.put("totalSent", sent);
	    result.put("totalReceived", received);
	    result.put("netBalance", netBalance);

	    return result;
	}

	// Get all transactions
	public List<Transaction> getAllTransactions() {
		return new ArrayList<>(sessionTransactions);
	}

	// Get transactions by type (DEBIT or CREDIT)
	public List<Transaction> getTransactionsByType(TransactionType type) {
		return sessionTransactions.stream()
			.filter(t -> t.getType() == type)
			.collect(Collectors.toList());
	}

	// Get total debit amount
	public Double getTotalDebit() {
		return sessionTransactions.stream()
			.filter(t -> t.getType() == TransactionType.DEBIT)
			.mapToDouble(Transaction::getAmount)
			.sum();
	}

	// Get total credit amount
	public Double getTotalCredit() {
		return sessionTransactions.stream()
			.filter(t -> t.getType() == TransactionType.CREDIT)
			.mapToDouble(Transaction::getAmount)
			.sum();
	}

	// Get count of transactions by type
	public long getTransactionCountByType(TransactionType type) {
		return sessionTransactions.stream()
			.filter(t -> t.getType() == type)
			.count();
	}

	// Get average debit amount
	public Double getAverageDebit() {
		List<Transaction> debits = sessionTransactions.stream()
			.filter(t -> t.getType() == TransactionType.DEBIT)
			.collect(Collectors.toList());
		return debits.isEmpty() ? 0.0 : 
			debits.stream().mapToDouble(Transaction::getAmount).average().orElse(0.0);
	}

	// Get average credit amount
	public Double getAverageCredit() {
		List<Transaction> credits = sessionTransactions.stream()
			.filter(t -> t.getType() == TransactionType.CREDIT)
			.collect(Collectors.toList());
		return credits.isEmpty() ? 0.0 : 
			credits.stream().mapToDouble(Transaction::getAmount).average().orElse(0.0);
	}

	// Get highest transaction
	public Transaction getHighestTransaction() {
		return sessionTransactions.stream()
			.max((t1, t2) -> Double.compare(t1.getAmount(), t2.getAmount()))
			.orElse(null);
	}

	// Get lowest transaction
	public Transaction getLowestTransaction() {
		return sessionTransactions.stream()
			.min((t1, t2) -> Double.compare(t1.getAmount(), t2.getAmount()))
			.orElse(null);
	}

	// Get recent transactions
	public List<Transaction> getRecentTransactions(int days) {
		LocalDate startDate = LocalDate.now().minusDays(days);
		return sessionTransactions.stream()
			.filter(t -> t.getTransactionDate().isAfter(startDate.minusDays(1)))
			.collect(Collectors.toList());
	}

	// Get transactions by date range
	public List<Transaction> getTransactionsByDateRange(LocalDate startDate, LocalDate endDate) {
		return sessionTransactions.stream()
			.filter(t -> !t.getTransactionDate().isBefore(startDate) && 
					!t.getTransactionDate().isAfter(endDate))
			.collect(Collectors.toList());
	}

	// Get top N transactions
	public List<Transaction> getTopNTransactions(int limit) {
		return sessionTransactions.stream()
			.sorted((t1, t2) -> Double.compare(t2.getAmount(), t1.getAmount()))
			.limit(limit)
			.collect(Collectors.toList());
	}

	// Get monthly summary
	public List<Map<String, Object>> getMonthlySummary() {
		List<Transaction> all = new ArrayList<>(sessionTransactions);
		Map<String, Map<String, Object>> monthly = new HashMap<>();
		
		for (Transaction t : all) {
			String monthKey = t.getTransactionDate().getYear() + "-" + 
				String.format("%02d", t.getTransactionDate().getMonthValue());
			
			monthly.putIfAbsent(monthKey, new HashMap<>());
			Map<String, Object> data = monthly.get(monthKey);
			
			double amount = t.getAmount() != null ? t.getAmount() : 0;
			if (t.getType() == TransactionType.CREDIT) {
				data.put("totalCredit", 
					(double) data.getOrDefault("totalCredit", 0.0) + amount);
			} else {
				data.put("totalDebit", 
					(double) data.getOrDefault("totalDebit", 0.0) + amount);
			}
			data.put("transactionCount", 
				(int) data.getOrDefault("transactionCount", 0) + 1);
		}
		
		return monthly.entrySet().stream()
			.map(e -> {
				Map<String, Object> item = new HashMap<>(e.getValue());
				item.put("month", e.getKey());
				double credit = (double) item.getOrDefault("totalCredit", 0.0);
				double debit = (double) item.getOrDefault("totalDebit", 0.0);
				item.put("balance", credit - debit);
				return item;
			})
			.sorted((a, b) -> ((String) b.get("month"))
				.compareTo((String) a.get("month")))
			.toList();
	}

	// Get daily summary
	public List<Map<String, Object>> getDailySummary() {
		List<Transaction> all = new ArrayList<>(sessionTransactions);
		Map<LocalDate, Map<String, Object>> daily = new HashMap<>();
		
		for (Transaction t : all) {
			LocalDate date = t.getTransactionDate();
			daily.putIfAbsent(date, new HashMap<>());
			Map<String, Object> data = daily.get(date);
			
			double amount = t.getAmount() != null ? t.getAmount() : 0;
			if (t.getType() == TransactionType.CREDIT) {
				data.put("totalCredit", 
					(double) data.getOrDefault("totalCredit", 0.0) + amount);
			} else {
				data.put("totalDebit", 
					(double) data.getOrDefault("totalDebit", 0.0) + amount);
			}
			data.put("transactionCount", 
				(int) data.getOrDefault("transactionCount", 0) + 1);
		}
		
		return daily.entrySet().stream()
			.map(e -> {
				Map<String, Object> item = new HashMap<>(e.getValue());
				item.put("date", e.getKey().toString());
				double credit = (double) item.getOrDefault("totalCredit", 0.0);
				double debit = (double) item.getOrDefault("totalDebit", 0.0);
				item.put("balance", credit - debit);
				return item;
			})
			.sorted((a, b) -> ((String) b.get("date"))
				.compareTo((String) a.get("date")))
			.toList();
	}

	// Search transactions by description
	public List<Transaction> searchByDescription(String keyword) {
		return sessionTransactions.stream()
			.filter(t -> t.getDescription().toLowerCase().contains(keyword.toLowerCase()))
			.collect(Collectors.toList());
	}

	// Get category for a transaction based on description
	public TransactionCategory getCategoryForTransaction(Transaction transaction) {
		return TransactionCategory.categorize(transaction.getDescription());
	}

	// Get all transactions grouped by category
	public Map<String, Object> getCategoryWiseSpending() {
		List<Transaction> allTransactions = new ArrayList<>(sessionTransactions);
		Map<String, Object> categoryMap = new HashMap<>();
		Map<String, Double> categoryTotals = new HashMap<>();
		Map<String, Integer> categoryCounts = new HashMap<>();

		for (Transaction transaction : allTransactions) {
			TransactionCategory category = TransactionCategory.categorize(transaction.getDescription());
			String categoryName = category.getDisplayName();
			
			double amount = transaction.getAmount() != null ? transaction.getAmount() : 0;
			categoryTotals.put(categoryName, categoryTotals.getOrDefault(categoryName, 0.0) + amount);
			categoryCounts.put(categoryName, categoryCounts.getOrDefault(categoryName, 0) + 1);
		}

		categoryMap.put("categoryTotals", categoryTotals);
		categoryMap.put("categoryCounts", categoryCounts);
		return categoryMap;
	}

	// Get category breakdown with percentages
	public Map<String, Object> getCategoryBreakdown() {
		List<Transaction> allTransactions = new ArrayList<>(sessionTransactions);
		Double totalDebit = getTotalDebit();
		double total = totalDebit != null ? totalDebit : 0.0;

		Map<String, Map<String, Object>> categoryBreakdown = new HashMap<>();

		for (Transaction transaction : allTransactions) {
			if (transaction.getType() == TransactionType.DEBIT) {
				TransactionCategory category = TransactionCategory.categorize(transaction.getDescription());
				String categoryName = category.getDisplayName();
				
				categoryBreakdown.putIfAbsent(categoryName, new HashMap<>());
				Map<String, Object> catData = categoryBreakdown.get(categoryName);
				
				double amount = transaction.getAmount() != null ? transaction.getAmount() : 0;
				double currentTotal = (double) catData.getOrDefault("total", 0.0);
				int count = (int) catData.getOrDefault("count", 0);
				
				catData.put("total", currentTotal + amount);
				catData.put("count", count + 1);
			}
		}

		// Calculate percentages
		Map<String, Object> result = new HashMap<>();
		for (String category : categoryBreakdown.keySet()) {
			Map<String, Object> data = categoryBreakdown.get(category);
			double categoryTotal = (double) data.get("total");
			double percentage = total > 0 ? (categoryTotal / total) * 100 : 0;
			data.put("percentage", Math.round(percentage * 100.0) / 100.0);
			result.put(category, data);
		}

		return result;
	}

	// Clear session data (on logout)
	public void clearSessionData() {
		sessionTransactions.clear();
		processedTransactionIds.clear();
	}


	// Get top spending categories
	public List<Map<String, Object>> getTopCategories(int limit) {
		Map<String, Object> breakdown = getCategoryBreakdown();
		return breakdown.entrySet().stream()
			.map(entry -> {
				Map<String, Object> item = new HashMap<>();
				item.put("category", entry.getKey());
				@SuppressWarnings("unchecked")
				Map<String, Object> data = (Map<String, Object>) entry.getValue();
				item.put("total", data.get("total"));
				item.put("count", data.get("count"));
				item.put("percentage", data.get("percentage"));
				return item;
			})
			.sorted((a, b) -> Double.compare(
				(double) ((Map<String, Object>) b).get("total"),
				(double) ((Map<String, Object>) a).get("total")
			))
			.limit(limit)
			.toList();
	}

	// Get transactions by category
	public List<Transaction> getTransactionsByCategory(String categoryName) {
		return sessionTransactions.stream()
			.filter(t -> TransactionCategory.categorize(t.getDescription()).getDisplayName().equals(categoryName))
			.toList();
	}
}