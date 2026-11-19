package com.expensetracker;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Reads and writes transactions in a simple UTF-8 CSV file.
 */
public class FileStorage {
    private static final String HEADER =
            "id,date,type,description,category,amount";

    private final Path filePath;

    public FileStorage(Path filePath) {
        this.filePath = Objects.requireNonNull(filePath, "File path is required.");
    }

    /**
     * Loads valid rows. Missing or empty files represent an empty history.
     * Invalid rows are reported and skipped so other records can still load.
     */
    public List<Transaction> loadTransactions() throws IOException {
        List<Transaction> transactions = new ArrayList<>();
        if (!Files.exists(filePath)) {
            return transactions;
        }

        Set<Long> loadedIds = new HashSet<>();
        try (BufferedReader reader =
                     Files.newBufferedReader(filePath, StandardCharsets.UTF_8)) {
            String line = reader.readLine();
            if (line == null) {
                return transactions;
            }

            if (line.startsWith("\uFEFF")) {
                line = line.substring(1);
            }
            if (!HEADER.equalsIgnoreCase(line.trim())) {
                addIfValid(line, 1, loadedIds, transactions);
            }

            int lineNumber = 1;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                addIfValid(line, lineNumber, loadedIds, transactions);
            }
        }
        return transactions;
    }

    /**
     * Replaces the CSV contents with the supplied current transaction list.
     */
    public void saveTransactions(List<Transaction> transactions) throws IOException {
        Objects.requireNonNull(transactions, "Transaction list is required.");
        Path absolutePath = filePath.toAbsolutePath();
        Path parent = absolutePath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        try (BufferedWriter writer =
                     Files.newBufferedWriter(absolutePath, StandardCharsets.UTF_8)) {
            writer.write(HEADER);
            writer.newLine();

            for (Transaction transaction : transactions) {
                writer.write(toCsvRow(transaction));
                writer.newLine();
            }
        }
    }

    private void addIfValid(String line, int lineNumber, Set<Long> loadedIds,
                            List<Transaction> transactions) {
        if (line.isBlank()) {
            return;
        }

        try {
            Transaction transaction = parseTransaction(line);
            if (!loadedIds.add(transaction.getId())) {
                System.err.printf(
                        "Warning: skipped duplicate transaction ID at CSV line %d.%n",
                        lineNumber);
                return;
            }
            transactions.add(transaction);
        } catch (IllegalArgumentException | DateTimeParseException
                 | ArithmeticException exception) {
            System.err.printf(
                    "Warning: skipped invalid transaction at CSV line %d (%s).%n",
                    lineNumber, exception.getMessage());
        }
    }

    private Transaction parseTransaction(String line) {
        List<String> fields = parseCsvLine(line);
        if (fields.size() != 6) {
            throw new IllegalArgumentException("Expected 6 fields.");
        }

        long id = Long.parseLong(fields.get(0).trim());
        LocalDate date = LocalDate.parse(fields.get(1).trim());
        TransactionType type = TransactionType.valueOf(
                fields.get(2).trim().toUpperCase(Locale.ROOT));
        String description = fields.get(3);
        String category = fields.get(4);
        BigDecimal amount = new BigDecimal(fields.get(5).trim())
                .setScale(2, RoundingMode.UNNECESSARY);

        if (type == TransactionType.EXPENSE
                && !isValidExpenseCategory(category)) {
            throw new IllegalArgumentException("Unknown expense category.");
        }

        return new Transaction(id, date, type, description, category, amount);
    }

    private boolean isValidExpenseCategory(String category) {
        for (String validCategory : ExpenseTracker.getExpenseCategories()) {
            if (validCategory.equalsIgnoreCase(category.trim())) {
                return true;
            }
        }
        return false;
    }

    private List<String> parseCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean insideQuotes = false;
        boolean quoteClosed = false;

        for (int index = 0; index < line.length(); index++) {
            char character = line.charAt(index);

            if (insideQuotes) {
                if (character == '"') {
                    if (index + 1 < line.length() && line.charAt(index + 1) == '"') {
                        field.append('"');
                        index++;
                    } else {
                        insideQuotes = false;
                        quoteClosed = true;
                    }
                } else {
                    field.append(character);
                }
            } else if (character == ',') {
                fields.add(field.toString());
                field.setLength(0);
                quoteClosed = false;
            } else if (character == '"') {
                if (field.length() != 0 || quoteClosed) {
                    throw new IllegalArgumentException("Unexpected quote in CSV field.");
                }
                insideQuotes = true;
            } else if (quoteClosed) {
                if (!Character.isWhitespace(character)) {
                    throw new IllegalArgumentException(
                            "Unexpected text after a quoted CSV field.");
                }
            } else {
                field.append(character);
            }
        }

        if (insideQuotes) {
            throw new IllegalArgumentException("Unclosed quoted CSV field.");
        }
        fields.add(field.toString());
        return fields;
    }

    private String toCsvRow(Transaction transaction) {
        Objects.requireNonNull(transaction, "Transaction cannot be null.");
        return transaction.getId()
                + "," + transaction.getDate()
                + "," + transaction.getType()
                + "," + quoteCsvField(transaction.getDescription())
                + "," + quoteCsvField(transaction.getCategory())
                + "," + transaction.getAmount().setScale(2, RoundingMode.UNNECESSARY);
    }

    private String quoteCsvField(String value) {
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }
}
