package com.expensetracker;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * One income or expense record.
 * Private fields protect the data; methods provide controlled access to it.
 */
public class Transaction {
    private final long id;
    private final TransactionType type;
    private LocalDate date;
    private String description;
    private String category;
    private BigDecimal amount;

    /**
     * Creates a transaction and checks that its required values are valid.
     */
    public Transaction(long id, LocalDate date, TransactionType type,
                       String description, String category, BigDecimal amount) {
        if (id <= 0) {
            throw new IllegalArgumentException("Transaction ID must be positive.");
        }

        this.id = id;
        this.type = Objects.requireNonNull(type, "Transaction type is required.");
        this.date = requireDate(date);
        this.description = requireDescription(description);
        this.category = requireCategory(this.type, category);
        this.amount = requireAmount(amount);
    }

    public long getId() {
        return id;
    }

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = requireDate(date);
    }

    public TransactionType getType() {
        return type;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = requireDescription(description);
    }

    private static String requireDescription(String description) {
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("Description cannot be empty.");
        }
        return description.trim();
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = requireCategory(type, category);
    }

    private static String requireCategory(TransactionType type, String category) {
        if (type == TransactionType.EXPENSE
                && (category == null || category.isBlank())) {
            throw new IllegalArgumentException("Expense category cannot be empty.");
        }
        return category == null ? "" : category.trim();
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = requireAmount(amount);
    }

    private static BigDecimal requireAmount(BigDecimal amount) {
        Objects.requireNonNull(amount, "Transaction amount is required.");
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("Transaction amount must be greater than zero.");
        }
        return amount;
    }

    private static LocalDate requireDate(LocalDate date) {
        return Objects.requireNonNull(date, "Transaction date is required.");
    }
}
