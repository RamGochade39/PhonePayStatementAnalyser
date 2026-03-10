package in.insta.controller;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import jakarta.servlet.http.HttpSession;

import in.insta.entity.Transaction;
import in.insta.entity.TransactionType;
import in.insta.service.TransactionService;
import in.insta.service.TransactionDataManager;

@RestController
@RequestMapping("/api/transactions")
@CrossOrigin(origins = "*", allowedHeaders = "*", methods = {RequestMethod.GET, RequestMethod.POST, RequestMethod.OPTIONS})
public class TransactionController {

	private final TransactionService service;
	private final TransactionDataManager dataManager;

	public TransactionController(TransactionService service, TransactionDataManager dataManager) {
		this.service = service;
		this.dataManager = dataManager;
	}

	@PostMapping("/upload")
	public ResponseEntity<?> uploadPdf(@RequestParam("file") MultipartFile file,
			@RequestParam(value = "password", required = false) String password,
			HttpSession session) {

		try {
			// Validate file existence
			if (file == null || file.isEmpty()) {
				return ResponseEntity.badRequest().body("❌ No file selected. Please upload a PDF");
			}

			// Validate file name
			String fileName = file.getOriginalFilename();
			if (fileName == null || fileName.isEmpty()) {
				return ResponseEntity.badRequest().body("❌ Invalid file name");
			}

			// Validate file type
			if (!fileName.toLowerCase().endsWith(".pdf")) {
				return ResponseEntity.badRequest().body("❌ Invalid file type. Only PDF files are allowed");
			}

			// Validate file size (max 50MB)
			long maxFileSize = 50 * 1024 * 1024;
			if (file.getSize() > maxFileSize) {
				return ResponseEntity.status(413).body("❌ File too large. Maximum file size is 50MB. Your file is " + 
					String.format("%.2f MB", file.getSize() / (1024.0 * 1024.0)));
			}

			// Validate file size not too small
			if (file.getSize() < 1024) {
				return ResponseEntity.badRequest().body("❌ File is too small. Please check if the PDF is valid");
			}

			// Validate file content
			byte[] content = file.getBytes();
			if (content.length == 0 || !isPdfFile(content)) {
				return ResponseEntity.badRequest().body("❌ Invalid PDF file. The file appears to be corrupted or not a valid PDF");
			}

			// Validate password
			if (password == null || password.trim().isEmpty()) {
				return ResponseEntity.badRequest().body("🔐 Password is required. Enter password or type 'none' if not encrypted");
			}

			password = password.trim();
			if (password.length() < 2 && !password.equalsIgnoreCase("none")) {
				return ResponseEntity.badRequest().body("🔐 Invalid password format. Enter a valid password or 'none'");
			}

			// Process PDF
			String sessionId = (String) session.getAttribute("sessionId");
			if (sessionId == null) {
				sessionId = UUID.randomUUID().toString();
				session.setAttribute("sessionId", sessionId);
			}

			String result = service.processPdf(file, password.equalsIgnoreCase("none") ? "" : password);
			
			// Store processed transactions in session
			List<Transaction> transactions = service.getAllTransactions();
			if (transactions == null || transactions.isEmpty()) {
				return ResponseEntity.badRequest().body("❌ No transactions found in the PDF. The file may be empty or invalid");
			}

			dataManager.storeTransactions(sessionId, transactions);
			session.setAttribute("transactionsLoaded", true);
			
			return ResponseEntity.ok(result);

		} catch (IllegalArgumentException e) {
			return ResponseEntity.badRequest().body("❌ Invalid request: " + e.getMessage());
		} catch (IOException e) {
			return ResponseEntity.badRequest().body("❌ File read error: " + e.getMessage() + ". Please check if the file is valid");
		} catch (Exception e) {
			// Log exception for debugging
			e.printStackTrace();
			
			// Check for common error messages
			String errorMsg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
			
			if (errorMsg.contains("password") || errorMsg.contains("decrypt")) {
				return ResponseEntity.status(401).body("🔐 Wrong password: The password entered is incorrect for this PDF");
			} else if (errorMsg.contains("timeout")) {
				return ResponseEntity.status(408).body("⏱️ Request timeout: The file is taking too long to process. Try a smaller file");
			} else if (errorMsg.contains("out of memory")) {
				return ResponseEntity.status(500).body("❌ File too complex: The PDF is too complex to process. Try a simpler file");
			} else if (errorMsg.contains("pdf") || errorMsg.contains("corrupt")) {
				return ResponseEntity.badRequest().body("❌ Corrupted PDF: The PDF file appears to be corrupted or invalid");
			} else {
				return ResponseEntity.status(500).body("❌ Error processing PDF: " + e.getMessage() + ". Please check the file and try again");
			}
		}
	}

	// Helper method to validate PDF file
	private boolean isPdfFile(byte[] content) {
		if (content.length < 5) return false;
		// Check for PDF magic bytes (25 50 44 46 = %PDF)
		return content[0] == (byte) 0x25 && 
		       content[1] == (byte) 0x50 && 
		       content[2] == (byte) 0x44 && 
		       content[3] == (byte) 0x46;
	}

	@GetMapping("/insights")
	public Map<String, Object> getInsights() {
		return service.getInsights();
	}

	// Get all transactions
	@GetMapping("/all")
	public List<Transaction> getAllTransactions() {
		return service.getAllTransactions();
	}

	// Get all debited transactions
	@GetMapping("/debited")
	public List<Transaction> getAllDebitedTransactions() {
		return service.getTransactionsByType(TransactionType.DEBIT);
	}

	// Get all credited transactions
	@GetMapping("/credited")
	public List<Transaction> getAllCreditedTransactions() {
		return service.getTransactionsByType(TransactionType.CREDIT);
	}

	// Get total debit amount
	@GetMapping("/total-debit")
	public Map<String, Object> getTotalDebit() {
		Double totalDebit = service.getTotalDebit();
		return Map.of(
			"totalDebit", totalDebit != null ? totalDebit : 0.0,
			"currency", "INR"
		);
	}

	// Get total credit amount
	@GetMapping("/total-credit")
	public Map<String, Object> getTotalCredit() {
		Double totalCredit = service.getTotalCredit();
		return Map.of(
			"totalCredit", totalCredit != null ? totalCredit : 0.0,
			"currency", "INR"
		);
	}

	// Get account balance (total credit - total debit)
	@GetMapping("/balance")
	public Map<String, Object> getBalance() {
		Double totalCredit = service.getTotalCredit();
		Double totalDebit = service.getTotalDebit();
		
		double credit = totalCredit != null ? totalCredit : 0.0;
		double debit = totalDebit != null ? totalDebit : 0.0;
		double balance = credit - debit;
		
		return Map.of(
			"totalCredit", credit,
			"totalDebit", debit,
			"balance", balance,
			"currency", "INR"
		);
	}

	// Get debit count
	@GetMapping("/debit-count")
	public Map<String, Object> getDebitCount() {
		long count = service.getTransactionCountByType(TransactionType.DEBIT);
		return Map.of(
			"debitTransactions", count
		);
	}

	// Get credit count
	@GetMapping("/credit-count")
	public Map<String, Object> getCreditCount() {
		long count = service.getTransactionCountByType(TransactionType.CREDIT);
		return Map.of(
			"creditTransactions", count
		);
	}

	// Get transaction summary
	@GetMapping("/summary")
	public Map<String, Object> getTransactionSummary() {
		Double totalCredit = service.getTotalCredit();
		Double totalDebit = service.getTotalDebit();
		long creditCount = service.getTransactionCountByType(TransactionType.CREDIT);
		long debitCount = service.getTransactionCountByType(TransactionType.DEBIT);
		
		double credit = totalCredit != null ? totalCredit : 0.0;
		double debit = totalDebit != null ? totalDebit : 0.0;
		double balance = credit - debit;
		
		return Map.of(
			"totalCredit", credit,
			"totalDebit", debit,
			"balance", balance,
			"creditCount", creditCount,
			"debitCount", debitCount,
			"totalTransactions", creditCount + debitCount,
			"currency", "INR"
		);
	}

	// ========== NEW INSIGHTS ENDPOINTS ==========

	// Get average debit amount
	@GetMapping("/average-debit")
	public Map<String, Object> getAverageDebit() {
		Double avg = service.getAverageDebit();
		return Map.of(
			"averageDebit", avg != null ? avg : 0.0,
			"currency", "INR"
		);
	}

	// Get average credit amount
	@GetMapping("/average-credit")
	public Map<String, Object> getAverageCredit() {
		Double avg = service.getAverageCredit();
		return Map.of(
			"averageCredit", avg != null ? avg : 0.0,
			"currency", "INR"
		);
	}

	// Get highest transaction
	@GetMapping("/highest")
	public Map<String, Object> getHighestTransaction() {
		Transaction transaction = service.getHighestTransaction();
		if (transaction == null) {
			return Map.of("message", "No transactions found");
		}
		return Map.of(
			"amount", transaction.getAmount(),
			"type", transaction.getType(),
			"description", transaction.getDescription(),
			"date", transaction.getTransactionDate(),
			"time", transaction.getTransactionTime(),
			"currency", "INR"
		);
	}

	// Get lowest transaction
	@GetMapping("/lowest")
	public Map<String, Object> getLowestTransaction() {
		Transaction transaction = service.getLowestTransaction();
		if (transaction == null) {
			return Map.of("message", "No transactions found");
		}
		return Map.of(
			"amount", transaction.getAmount(),
			"type", transaction.getType(),
			"description", transaction.getDescription(),
			"date", transaction.getTransactionDate(),
			"time", transaction.getTransactionTime(),
			"currency", "INR"
		);
	}

	// Get recent transactions (last N days)
	@GetMapping("/recent")
	public Map<String, Object> getRecentTransactions(
			@RequestParam(value = "days", defaultValue = "7") int days) {
		List<Transaction> transactions = service.getRecentTransactions(days);
		return Map.of(
			"days", days,
			"count", transactions.size(),
			"transactions", transactions
		);
	}

	// Get transactions by date range
	@GetMapping("/date-range")
	public Map<String, Object> getTransactionsByDateRange(
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
		List<Transaction> transactions = service.getTransactionsByDateRange(startDate, endDate);
		return Map.of(
			"startDate", startDate,
			"endDate", endDate,
			"count", transactions.size(),
			"transactions", transactions
		);
	}

	// Get top N transactions
	@GetMapping("/top-transactions")
	public Map<String, Object> getTopTransactions(
			@RequestParam(value = "limit", defaultValue = "10") int limit) {
		List<Transaction> transactions = service.getTopNTransactions(limit);
		return Map.of(
			"limit", limit,
			"count", transactions.size(),
			"transactions", transactions
		);
	}

	// Get monthly summary
	@GetMapping("/monthly-summary")
	public Map<String, Object> getMonthlySummary() {
		List<Map<String, Object>> monthlySummary = service.getMonthlySummary();
		return Map.of(
			"monthlySummary", monthlySummary
		);
	}

	// Get daily summary
	@GetMapping("/daily-summary")
	public Map<String, Object> getDailySummary() {
		List<Map<String, Object>> dailySummary = service.getDailySummary();
		return Map.of(
			"dailySummary", dailySummary
		);
	}

	// Search transactions by description/keyword
	@GetMapping("/search")
	public Map<String, Object> searchTransactions(
			@RequestParam String keyword) {
		List<Transaction> transactions = service.searchByDescription(keyword);
		return Map.of(
			"keyword", keyword,
			"count", transactions.size(),
			"transactions", transactions
		);
	}

	// Get spending insights
	@GetMapping("/spending-insights")
	public Map<String, Object> getSpendingInsights() {
		Double totalCredit = service.getTotalCredit();
		Double totalDebit = service.getTotalDebit();
		Double avgCredit = service.getAverageCredit();
		Double avgDebit = service.getAverageDebit();
		long creditCount = service.getTransactionCountByType(TransactionType.CREDIT);
		long debitCount = service.getTransactionCountByType(TransactionType.DEBIT);

		double credit = totalCredit != null ? totalCredit : 0.0;
		double debit = totalDebit != null ? totalDebit : 0.0;
		double avgCred = avgCredit != null ? avgCredit : 0.0;
		double avgDeb = avgDebit != null ? avgDebit : 0.0;
		double balance = credit - debit;

		return Map.of(
			"totalIncome", credit,
			"totalExpense", debit,
			"netBalance", balance,
			"averageIncome", avgCred,
			"averageExpense", avgDeb,
			"incomeTransactions", creditCount,
			"expenseTransactions", debitCount,
			"savingsRate", creditCount > 0 ? (balance / credit) * 100 : 0,
			"currency", "INR"
		);
	}

   	// ========== CATEGORY ENDPOINTS ==========

	// Get spending by category
	@GetMapping("/category-wise-spending-old")
	public Map<String, Object> getCategoryWiseSpendingOld() {
		return service.getCategoryWiseSpending();
	}

	// Get category details with percentage breakdown
	@GetMapping("/category-breakdown-old")
	public Map<String, Object> getCategoryBreakdownOld() {
		return service.getCategoryBreakdown();
	}

	// Get top spending categories
	@GetMapping("/top-categories-old")
	public Map<String, Object> getTopCategoriesOld(
			@RequestParam(value = "limit", defaultValue = "5") int limit) {
		return Map.of(
			"topCategories", service.getTopCategories(limit),
			"limit", limit
		);
	}

	// Get category-wise spending details
	@GetMapping("/category-wise-spending")
	public Map<String, Object> getCategoryWiseSpending() {
		return service.getCategoryWiseSpending();
	}

	// Get category breakdown with percentages
	@GetMapping("/category-breakdown")
	public Map<String, Object> getCategoryBreakdown() {
		return service.getCategoryBreakdown();
	}

	// Get top spending categories
	@GetMapping("/top-categories")
	public Map<String, Object> getTopCategories(
			@RequestParam(value = "limit", defaultValue = "5") int limit) {
		return Map.of(
			"topCategories", service.getTopCategories(limit),
			"limit", limit
		);
	}

	// Get transactions by specific category
	@GetMapping("/transactions-by-category")
	public Map<String, Object> getTransactionsByCategory(
			@RequestParam String category) {
		List<Transaction> transactions = service.getTransactionsByCategory(category);
		return Map.of(
			"category", category,
			"count", transactions.size(),
			"transactions", transactions
		);
	}

	// Get category-wise spending details with type breakdown
	@GetMapping("/category-type-breakdown")
	public Map<String, Object> getCategoryTypeBreakdown() {
		return service.getCategoryBreakdown();
	}

	// Get all available categories
	@GetMapping("/categories")
	public Map<String, Object> getAvailableCategories() {
		return Map.of(
			"categories", new String[]{
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
			},
			"message", "All available transaction categories with smart pattern matching"
		);
	}

	// Logout - Clear all session data
	@PostMapping("/logout")
	public Map<String, Object> logout() {
		service.clearSessionData();
		return Map.of(
			"status", "success",
			"message", "Session cleared successfully. All data has been removed."
		);
	}

}