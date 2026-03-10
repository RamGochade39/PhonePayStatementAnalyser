package in.insta.entity;

import java.time.LocalDate;
import java.time.LocalTime;
import com.fasterxml.jackson.annotation.JsonFormat;

public class Transaction {

	@JsonFormat(pattern = "yyyy-MM-dd")
	private LocalDate transactionDate;
	
	@JsonFormat(pattern = "HH:mm:ss")
	private LocalTime transactionTime;

	private String description;

	private String transactionId;

	private String utr;

	private TransactionType type;

	private Double amount;

	// Constructors
	public Transaction() {
	}

	public Transaction(LocalDate transactionDate, LocalTime transactionTime, String description, 
			String transactionId, String utr, TransactionType type, Double amount) {
		this.transactionDate = transactionDate;
		this.transactionTime = transactionTime;
		this.description = description;
		this.transactionId = transactionId;
		this.utr = utr;
		this.type = type;
		this.amount = amount;
	}

	// Getters and Setters
	public LocalDate getTransactionDate() {
		return transactionDate;
	}

	public void setTransactionDate(LocalDate transactionDate) {
		this.transactionDate = transactionDate;
	}

	public LocalTime getTransactionTime() {
		return transactionTime;
	}

	public void setTransactionTime(LocalTime transactionTime) {
		this.transactionTime = transactionTime;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public String getTransactionId() {
		return transactionId;
	}

	public void setTransactionId(String transactionId) {
		this.transactionId = transactionId;
	}

	public String getUtr() {
		return utr;
	}

	public void setUtr(String utr) {
		this.utr = utr;
	}

	public TransactionType getType() {
		return type;
	}

	public void setType(TransactionType type) {
		this.type = type;
	}

	public Double getAmount() {
		return amount;
	}

	public void setAmount(Double amount) {
		this.amount = amount;
	}
}
