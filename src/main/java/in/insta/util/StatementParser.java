package in.insta.util;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import in.insta.entity.Transaction;
import in.insta.entity.TransactionType;

public class StatementParser {

	private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("MMM dd, yyyy", Locale.ENGLISH);

	private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("hh:mm a", Locale.ENGLISH);
	
	// Store extracted candidate name
	private static String extractedCandidateName = "Unknown";

	public static List<Transaction> parse(String text) {

		List<Transaction> transactions = new ArrayList<>();

		// Validate input
		if (text == null || text.trim().isEmpty()) {
			System.out.println("⚠️ Warning: Empty or null text provided to parser");
			return transactions;
		}

		text = cleanText(text);
		
		// Extract candidate name from first line
		String[] allLines = text.split("\\r?\\n");
		if (allLines.length > 0) {
			String firstLine = allLines[0].trim();
			// Look for patterns like "Account of XYZ" or "Name: XYZ" or just the name
			if (!firstLine.isEmpty() && !firstLine.matches("\\d.*")) {
				extractedCandidateName = firstLine;
				System.out.println("📝 Extracted Candidate: " + extractedCandidateName);
			}
		}

		String[] lines = text.split("\\r?\\n");

		for (int i = 0; i < lines.length; i++) {

			try {

				// Detect date line
				if (lines[i].matches("[A-Za-z]{3} \\d{2}, \\d{4}")) {

					Transaction t = new Transaction();

					// Date
					t.setTransactionDate(LocalDate.parse(lines[i].trim(), DATE_FORMAT));

					// Time
					i++;
					t.setTransactionTime(LocalTime.parse(lines[i].trim(), TIME_FORMAT));

					// Description
					i++;
					t.setDescription(lines[i].trim());

					// Transaction ID
					i++;
					t.setTransactionId(lines[i].replace("Transaction ID :", "").trim());

					// UTR
					i++;
					t.setUtr(lines[i].replace("UTR No :", "").trim());

					// Skip "Debited from" or "Credited to"
					i++;

					// Credit / Debit + Amount
					i++;
					String amountLine = lines[i].trim(); // Example: Credit INR 10.00

					if (amountLine.startsWith("Credit")) {
						t.setType(TransactionType.CREDIT);
					} else {
						t.setType(TransactionType.DEBIT);
					}

					String amount = amountLine.replaceAll("[^0-9.]", "");
					t.setAmount(Double.parseDouble(amount));

					transactions.add(t);
				}

			} catch (Exception e) {
				System.out.println("Skipping corrupted block...");
			}
		}

		System.out.println("TOTAL PARSED = " + transactions.size());

		return transactions;
	}
	
	/**
	 * Get the extracted candidate name from the last parsed PDF
	 */
	public static String getExtractedCandidateName() {
		return extractedCandidateName;
	}

	private static String cleanText(String text) {

		text = text.replaceAll("Page \\d+ of \\d+", "");
		text = text.replaceAll("This is a system generated statement.*", "");
		text = text.replaceAll("Date\\s+Transaction Details\\s+Type\\s+Amount", "");
		text = text.replaceAll("Transaction Statement for.*", "");
		text = text.replaceAll("Oct .* - .*", "");

		System.out.println("===================================================");
		System.out.println(text);
		System.out.println("===================================================");

		return text;
	}
}