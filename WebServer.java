package com.expensetracker;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executors;

/**
 * Serves the browser page and a small HTTP API for tracker operations.
 */
public class WebServer {
    private static final int MAX_FORM_BYTES = 16 * 1024;
    private static final String TRANSACTIONS_PATH = "/api/transactions";

    private final ExpenseTracker tracker;
    private final FileStorage fileStorage;
    private final Path indexFile;
    private final HttpServer server;

    public WebServer(Path dataFile, Path indexFile, int port) throws IOException {
        this.fileStorage = new FileStorage(dataFile);
        this.tracker = new ExpenseTracker(fileStorage.loadTransactions());
        this.indexFile = indexFile.toAbsolutePath().normalize();
        this.server = HttpServer.create(
                new InetSocketAddress("127.0.0.1", port), 0);
        this.server.setExecutor(Executors.newSingleThreadExecutor());
        this.server.createContext("/", this::handle);
    }

    public void start() {
        server.start();
        System.out.printf("Loaded %d transaction(s).%n",
                tracker.getTransactions().size());
        System.out.println("Personal Expense Tracker is running at "
                + "http://127.0.0.1:" + server.getAddress().getPort());
        System.out.println("Keep this window open while using the application.");
    }

    private void handle(HttpExchange exchange) throws IOException {
        try {
            route(exchange);
        } catch (IllegalArgumentException exception) {
            sendJson(exchange, 400, errorJson(exception.getMessage()));
        } catch (StorageException exception) {
            sendJson(exchange, 500, errorJson(exception.getMessage()));
        } finally {
            exchange.close();
        }
    }

    private void route(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();

        if (path.equals("/") || path.equals("/index.html")) {
            if (!method.equals("GET")) {
                methodNotAllowed(exchange, "GET");
                return;
            }
            sendResponse(exchange, 200, "text/html; charset=utf-8",
                    Files.readString(indexFile, StandardCharsets.UTF_8));
            return;
        }

        if (path.equals(TRANSACTIONS_PATH)) {
            if (method.equals("GET")) {
                sendJson(exchange, 200, transactionsJson(tracker.getTransactions()));
            } else if (method.equals("POST")) {
                verifySameOrigin(exchange);
                addTransaction(exchange);
            } else {
                methodNotAllowed(exchange, "GET, POST");
            }
            return;
        }

        if (path.startsWith(TRANSACTIONS_PATH + "/")) {
            routeOneTransaction(exchange, path.substring(TRANSACTIONS_PATH.length() + 1));
            return;
        }

        if (path.equals("/api/search")) {
            if (!method.equals("GET")) {
                methodNotAllowed(exchange, "GET");
                return;
            }
            searchTransactions(exchange);
            return;
        }

        if (path.equals("/api/categories")) {
            if (!method.equals("GET")) {
                methodNotAllowed(exchange, "GET");
                return;
            }
            sendJson(exchange, 200, stringListJson(
                    ExpenseTracker.getExpenseCategories()));
            return;
        }

        if (path.equals("/api/summary")) {
            if (!method.equals("GET")) {
                methodNotAllowed(exchange, "GET");
                return;
            }
            sendJson(exchange, 200, summaryJson());
            return;
        }

        if (path.equals("/api/save")) {
            if (!method.equals("POST")) {
                methodNotAllowed(exchange, "POST");
                return;
            }
            verifySameOrigin(exchange);
            saveCurrentTransactions();
            sendJson(exchange, 200, "{\"saved\":true}");
            return;
        }

        sendJson(exchange, 404, errorJson("Page or API route not found."));
    }

    private void routeOneTransaction(HttpExchange exchange, String idText)
            throws IOException {
        if (idText.isBlank() || idText.contains("/")) {
            sendJson(exchange, 404, errorJson("Transaction not found."));
            return;
        }

        long id = parseId(idText);
        String method = exchange.getRequestMethod();
        Transaction transaction = tracker.findById(id);

        if (transaction == null) {
            sendJson(exchange, 404, errorJson("Transaction not found."));
            return;
        }

        if (method.equals("GET")) {
            sendJson(exchange, 200, transactionJson(transaction));
        } else if (method.equals("PUT")) {
            verifySameOrigin(exchange);
            updateTransaction(exchange, transaction);
        } else if (method.equals("DELETE")) {
            verifySameOrigin(exchange);
            tracker.delete(id);
            saveCurrentTransactions();
            sendJson(exchange, 200, "{\"deleted\":true}");
        } else {
            methodNotAllowed(exchange, "GET, PUT, DELETE");
        }
    }

    private void addTransaction(HttpExchange exchange) throws IOException {
        Map<String, String> form = readForm(exchange);
        TransactionType type = parseType(required(form, "type"));
        BigDecimal amount = parseAmount(required(form, "amount"));
        String description = required(form, "description");
        LocalDate date = optionalDate(form.get("date"));

        Transaction transaction;
        if (type == TransactionType.INCOME) {
            transaction = tracker.addIncome(amount, description, date);
        } else {
            String category = normalizeCategory(required(form, "category"));
            transaction = tracker.addExpense(amount, description, category, date);
        }

        saveCurrentTransactions();
        sendJson(exchange, 201, transactionJson(transaction));
    }

    private void updateTransaction(HttpExchange exchange, Transaction transaction)
            throws IOException {
        Map<String, String> form = readForm(exchange);
        String description = required(form, "description");
        BigDecimal amount = parseAmount(required(form, "amount"));
        LocalDate date = parseDate(required(form, "date"));
        String category = transaction.getCategory();

        if (transaction.getType() == TransactionType.EXPENSE) {
            category = normalizeCategory(required(form, "category"));
        }

        tracker.updateDescription(transaction.getId(), description);
        tracker.updateAmount(transaction.getId(), amount);
        tracker.updateDate(transaction.getId(), date);
        if (transaction.getType() == TransactionType.EXPENSE) {
            tracker.updateCategory(transaction.getId(), category);
        }

        saveCurrentTransactions();
        sendJson(exchange, 200, transactionJson(transaction));
    }

    private void searchTransactions(HttpExchange exchange) throws IOException {
        Map<String, String> query = parseParameters(
                exchange.getRequestURI().getRawQuery());
        String field = required(query, "field").toLowerCase(Locale.ROOT);
        String value = required(query, "q");
        List<Transaction> matches;

        switch (field) {
            case "id":
                matches = tracker.searchById(parseId(value));
                break;
            case "description":
                matches = tracker.searchByDescription(value);
                break;
            case "category":
                matches = tracker.searchByCategory(value);
                break;
            case "date":
                matches = tracker.searchByDate(parseDate(value));
                break;
            case "type":
                matches = tracker.searchByType(parseType(value));
                break;
            default:
                throw new IllegalArgumentException(
                        "Search field must be id, description, category, date, or type.");
        }

        sendJson(exchange, 200, transactionsJson(matches));
    }

    private Map<String, String> readForm(HttpExchange exchange) throws IOException {
        byte[] body;
        try (InputStream input = exchange.getRequestBody()) {
            body = input.readNBytes(MAX_FORM_BYTES + 1);
        }
        if (body.length > MAX_FORM_BYTES) {
            throw new IllegalArgumentException("Form data is too large.");
        }
        return parseParameters(new String(body, StandardCharsets.UTF_8));
    }

    private Map<String, String> parseParameters(String encoded) {
        Map<String, String> parameters = new LinkedHashMap<>();
        if (encoded == null || encoded.isBlank()) {
            return parameters;
        }

        for (String part : encoded.split("&")) {
            if (part.isEmpty()) {
                continue;
            }
            String[] pair = part.split("=", 2);
            String key = URLDecoder.decode(pair[0], StandardCharsets.UTF_8);
            String value = pair.length == 1
                    ? ""
                    : URLDecoder.decode(pair[1], StandardCharsets.UTF_8);
            if (key.isBlank() || parameters.putIfAbsent(key, value) != null) {
                throw new IllegalArgumentException(
                        "Form data contains a missing or repeated field.");
            }
        }
        return parameters;
    }

    private void verifySameOrigin(HttpExchange exchange) {
        String origin = exchange.getRequestHeaders().getFirst("Origin");
        if (origin == null) {
            return;
        }

        String expectedOrigin = "http://" + exchange.getLocalAddress().getHostString()
                + ":" + exchange.getLocalAddress().getPort();
        if (!expectedOrigin.equalsIgnoreCase(origin)) {
            throw new IllegalArgumentException(
                    "Cross-origin changes are not allowed.");
        }
    }

    private String required(Map<String, String> values, String name) {
        String value = values.get(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required.");
        }
        return value.trim();
    }

    private long parseId(String text) {
        try {
            long id = Long.parseLong(text.trim());
            if (id > 0) {
                return id;
            }
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Transaction ID must be a positive whole number.");
        }
        throw new IllegalArgumentException("Transaction ID must be a positive whole number.");
    }

    private BigDecimal parseAmount(String text) {
        try {
            BigDecimal amount = new BigDecimal(text.trim())
                    .setScale(2, RoundingMode.UNNECESSARY);
            if (amount.signum() > 0) {
                return amount;
            }
        } catch (NumberFormatException | ArithmeticException exception) {
            throw new IllegalArgumentException(
                    "Amount must be positive and have at most two decimal places.");
        }
        throw new IllegalArgumentException(
                "Amount must be positive and have at most two decimal places.");
    }

    private LocalDate optionalDate(String text) {
        if (text == null || text.isBlank()) {
            return LocalDate.now();
        }
        return parseDate(text);
    }

    private LocalDate parseDate(String text) {
        try {
            return LocalDate.parse(text.trim());
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("Date must use yyyy-MM-dd format.");
        }
    }

    private TransactionType parseType(String text) {
        try {
            return TransactionType.valueOf(text.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Transaction type must be income or expense.");
        }
    }

    private String normalizeCategory(String category) {
        for (String validCategory : ExpenseTracker.getExpenseCategories()) {
            if (validCategory.equalsIgnoreCase(category.trim())) {
                return validCategory;
            }
        }
        throw new IllegalArgumentException("Choose a valid expense category.");
    }

    private void saveCurrentTransactions() {
        try {
            fileStorage.saveTransactions(tracker.getTransactions());
        } catch (IOException exception) {
            throw new StorageException(
                    "Change is in memory, but the CSV file could not be saved: "
                            + exception.getMessage(),
                    exception);
        }
    }

    private String transactionsJson(List<Transaction> transactions) {
        StringBuilder json = new StringBuilder("[");
        for (int index = 0; index < transactions.size(); index++) {
            if (index > 0) {
                json.append(',');
            }
            json.append(transactionJson(transactions.get(index)));
        }
        return json.append(']').toString();
    }

    private String transactionJson(Transaction transaction) {
        return "{\"id\":" + transaction.getId()
                + ",\"date\":" + jsonString(transaction.getDate().toString())
                + ",\"type\":" + jsonString(transaction.getType().name())
                + ",\"description\":" + jsonString(transaction.getDescription())
                + ",\"category\":" + jsonString(transaction.getCategory())
                + ",\"amount\":" + transaction.getAmount().toPlainString()
                + "}";
    }

    private String summaryJson() {
        return "{\"totalIncome\":" + tracker.getTotalIncome().toPlainString()
                + ",\"totalExpenses\":" + tracker.getTotalExpenses().toPlainString()
                + ",\"balance\":" + tracker.getBalance().toPlainString()
                + "}";
    }

    private String stringListJson(List<String> values) {
        List<String> encoded = new ArrayList<>();
        for (String value : values) {
            encoded.add(jsonString(value));
        }
        return "[" + String.join(",", encoded) + "]";
    }

    private String errorJson(String message) {
        return "{\"error\":" + jsonString(message == null
                ? "Request could not be completed."
                : message) + "}";
    }

    private String jsonString(String value) {
        StringBuilder json = new StringBuilder("\"");
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '"':
                    json.append("\\\"");
                    break;
                case '\\':
                    json.append("\\\\");
                    break;
                case '\b':
                    json.append("\\b");
                    break;
                case '\f':
                    json.append("\\f");
                    break;
                case '\n':
                    json.append("\\n");
                    break;
                case '\r':
                    json.append("\\r");
                    break;
                case '\t':
                    json.append("\\t");
                    break;
                default:
                    if (character < 0x20) {
                        json.append(String.format("\\u%04x", (int) character));
                    } else {
                        json.append(character);
                    }
            }
        }
        return json.append('"').toString();
    }

    private void methodNotAllowed(HttpExchange exchange, String allowed)
            throws IOException {
        exchange.getResponseHeaders().set("Allow", allowed);
        sendJson(exchange, 405, errorJson("HTTP method is not allowed for this route."));
    }

    private void sendJson(HttpExchange exchange, int status, String json)
            throws IOException {
        sendResponse(exchange, status, "application/json; charset=utf-8", json);
    }

    private void sendResponse(HttpExchange exchange, int status, String contentType,
                              String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        Headers headers = exchange.getResponseHeaders();
        headers.set("Content-Type", contentType);
        headers.set("X-Content-Type-Options", "nosniff");
        headers.set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
    }

    private static final class StorageException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        private StorageException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
