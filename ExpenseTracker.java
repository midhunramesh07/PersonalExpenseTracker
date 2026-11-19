package com.expensetracker;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Holds transactions in memory and provides the tracker's core operations.
 */
public class ExpenseTracker {
    private static final List<String> EXPENSE_CATEGORIES = List.of(
            "Food",
            "Transportation",
            "Shopping",
            "Education",
            "Entertainment",
            "Healthcare",
            "Bills",
            "Others");

    private final List<Transaction> transactions = new ArrayList<>();
    private long nextId = 1;
    private boolean idSpaceExhausted;

    public ExpenseTracker() {
    }

    /**
     * Initializes the tracker with records loaded from storage.
     */
    public ExpenseTracker(List<Transaction> savedTransactions) {
        Objects.requireNonNull(savedTransactions, "Saved transactions are required.");
        for (Transaction transaction : savedTransactions) {
            Objects.requireNonNull(transaction, "Saved transaction cannot be null.");
            for (Transaction existing : transactions) {
                if (existing.getId() == transaction.getId()) {
                    throw new IllegalArgumentException("Duplicate transaction ID.");
                }
            }
            transactions.add(transaction);
            updateNextId(transaction.getId());
        }
    }

    public static List<String> getExpenseCategories() {
        return EXPENSE_CATEGORIES;
    }

    public List<Transaction> getTransactions() {
        return List.copyOf(transactions);
    }

    public Transaction addIncome(BigDecimal amount, String description, LocalDate date) {
        return addTransaction(
                TransactionType.INCOME, amount, description, "", date);
    }

    public Transaction addExpense(BigDecimal amount, String description,
                                  String category, LocalDate date) {
        return addTransaction(
                TransactionType.EXPENSE, amount, description,
                normalizeCategory(category), date);
    }

    private Transaction addTransaction(TransactionType type, BigDecimal amount,
                                        String description, String category,
                                        LocalDate date) {
        if (idSpaceExhausted) {
            throw new IllegalStateException("No transaction IDs are available.");
        }

        Transaction transaction = new Transaction(
                nextId, date, type, description, category, amount);
        transactions.add(transaction);
        updateNextId(transaction.getId());
        return transaction;
    }

    public Transaction findById(long id) {
        for (Transaction transaction : transactions) {
            if (transaction.getId() == id) {
                return transaction;
            }
        }
        return null;
    }

    public List<Transaction> searchById(long id) {
        Transaction transaction = findById(id);
        if (transaction == null) {
            return List.of();
        }
        return List.of(transaction);
    }

    public List<Transaction> searchByDescription(String query) {
        return searchText(query, true);
    }

    public List<Transaction> searchByCategory(String query) {
        return searchText(query, false);
    }

    public List<Transaction> searchByDate(LocalDate date) {
        List<Transaction> matches = new ArrayList<>();
        for (Transaction transaction : transactions) {
            if (transaction.getDate().equals(date)) {
                matches.add(transaction);
            }
        }
        return matches;
    }

    public List<Transaction> searchByType(TransactionType type) {
        List<Transaction> matches = new ArrayList<>();
        for (Transaction transaction : transactions) {
            if (transaction.getType() == type) {
                matches.add(transaction);
            }
        }
        return matches;
    }

    public List<Transaction> search(String query) {
        String normalizedQuery = query == null
                ? ""
                : query.trim().toLowerCase(Locale.ROOT);
        List<Transaction> matches = new ArrayList<>();

        if (normalizedQuery.isEmpty()) {
            return matches;
        }

        for (Transaction transaction : transactions) {
            if (matchesQuery(transaction, normalizedQuery)) {
                matches.add(transaction);
            }
        }
        return matches;
    }

    public BigDecimal getTotalIncome() {
        return totalForType(TransactionType.INCOME);
    }

    public BigDecimal getTotalExpenses() {
        return totalForType(TransactionType.EXPENSE);
    }

    public BigDecimal getBalance() {
        return getTotalIncome().subtract(getTotalExpenses());
    }

    public boolean updateDescription(long id, String description) {
        Transaction transaction = findById(id);
        if (transaction == null) {
            return false;
        }
        transaction.setDescription(description);
        return true;
    }

    public boolean updateAmount(long id, BigDecimal amount) {
        Transaction transaction = findById(id);
        if (transaction == null) {
            return false;
        }
        transaction.setAmount(amount);
        return true;
    }

    public boolean updateCategory(long id, String category) {
        Transaction transaction = findById(id);
        if (transaction == null) {
            return false;
        }
        if (transaction.getType() != TransactionType.EXPENSE) {
            return false;
        }
        transaction.setCategory(normalizeCategory(category));
        return true;
    }

    public boolean updateDate(long id, LocalDate date) {
        Transaction transaction = findById(id);
        if (transaction == null) {
            return false;
        }
        transaction.setDate(date);
        return true;
    }

    public boolean delete(long id) {
        Transaction transaction = findById(id);
        if (transaction == null) {
            return false;
        }
        transactions.remove(transaction);
        return true;
    }

    private List<Transaction> searchText(String query, boolean descriptionField) {
        String normalizedQuery = query == null
                ? ""
                : query.trim().toLowerCase(Locale.ROOT);
        List<Transaction> matches = new ArrayList<>();

        if (normalizedQuery.isEmpty()) {
            return matches;
        }

        for (Transaction transaction : transactions) {
            String value = descriptionField
                    ? transaction.getDescription()
                    : transaction.getCategory();
            if (value.toLowerCase(Locale.ROOT).contains(normalizedQuery)) {
                matches.add(transaction);
            }
        }
        return matches;
    }

    private BigDecimal totalForType(TransactionType type) {
        BigDecimal total = BigDecimal.ZERO;
        for (Transaction transaction : transactions) {
            if (transaction.getType() == type) {
                total = total.add(transaction.getAmount());
            }
        }
        return total;
    }

    private boolean matchesQuery(Transaction transaction, String query) {
        return Long.toString(transaction.getId()).contains(query)
                || transaction.getDescription().toLowerCase(Locale.ROOT).contains(query)
                || transaction.getCategory().toLowerCase(Locale.ROOT).contains(query)
                || transaction.getDate().toString().contains(query)
                || transaction.getType().name().toLowerCase(Locale.ROOT).contains(query);
    }

    private String normalizeCategory(String category) {
        if (category == null) {
            throw new IllegalArgumentException("Expense category is required.");
        }

        String normalized = category.trim().toLowerCase(Locale.ROOT);
        for (String validCategory : EXPENSE_CATEGORIES) {
            if (validCategory.toLowerCase(Locale.ROOT).equals(normalized)) {
                return validCategory;
            }
        }
        throw new IllegalArgumentException("Choose a valid expense category.");
    }

    private void updateNextId(long usedId) {
        if (usedId == Long.MAX_VALUE) {
            idSpaceExhausted = true;
        } else if (usedId >= nextId) {
            nextId = usedId + 1;
        }
    }
}
