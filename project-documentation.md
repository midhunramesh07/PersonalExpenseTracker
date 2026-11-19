# Project Documentation

This guide explains how the Java classes work together in the browser version. The Java package is `com.expensetracker`.

## Application flow

`Main` creates and starts `WebServer`. The server loads CSV records through `FileStorage`, creates an `ExpenseTracker`, serves `web/index.html`, and handles browser API requests. The page uses JavaScript `fetch` calls to submit forms and display JSON responses. Successful additions, updates, and deletions are saved to the CSV file.

The HTTP server listens only on `127.0.0.1` and uses a single request thread, keeping access to the in-memory `ArrayList` sequential.

## `TransactionType.java`

Defines the two permitted transaction types: `INCOME` and `EXPENSE`. An enum is a fixed set of named values, which prevents misspelled types in normal application code.

## `Transaction.java`

Represents one transaction. Its fields are private for encapsulation. The ID and type are final; the date, description, category, and amount have getters and validated update methods.

The constructor checks that the ID and amount are positive, the date and type are present, the description is not blank, and expenses have a category. `BigDecimal` represents money and `LocalDate` represents dates.

## `ExpenseTracker.java`

Stores transactions in an `ArrayList` and implements the business operations used by both the HTTP server and the browser UI.

- Constructors create an empty tracker or initialize one from loaded records.
- `getExpenseCategories()` and `getTransactions()` provide the allowed categories and a read-only record-list snapshot.
- `addIncome(...)` and `addExpense(...)` validate and add records with IDs.
- `findById(...)` and `searchById(...)` look up records.
- `searchByDescription(...)`, `searchByCategory(...)`, `searchByDate(...)`, `searchByType(...)`, and `search(...)` provide the search options.
- `getTotalIncome()`, `getTotalExpenses()`, and `getBalance()` calculate totals using `BigDecimal`.
- `updateDescription(...)`, `updateAmount(...)`, `updateCategory(...)`, and `updateDate(...)` update supported fields without changing the ID.
- `delete(...)` removes a record.

## `FileStorage.java`

Reads and writes UTF-8 CSV using Java NIO. The columns are:

```text
id,date,type,description,category,amount
```

- `FileStorage(Path filePath)` selects the data file.
- `loadTransactions()` treats a missing or empty file as an empty history. It warns and skips invalid or duplicate rows while keeping other valid records.
- `saveTransactions(List<Transaction>)` creates the parent directory if necessary and writes the header and current transaction list.
- Private parsing and formatting methods handle quoted fields, commas, and embedded double quotes.

File access problems are reported as `IOException`; they are not silently ignored.

## `WebServer.java`

Uses the JDK's `com.sun.net.httpserver.HttpServer` module; it does not require Spring Boot or other dependencies.

- The constructor loads the CSV, prepares the fixed HTML file, and binds the HTTP server to `127.0.0.1`.
- `start()` begins listening and prints the URL.
- `route(...)` dispatches requests to the page and API routes.
- `addTransaction(...)`, `updateTransaction(...)`, and the delete route validate and perform changes, then save them.
- `searchTransactions(...)` calls the tracker's search methods.
- `readForm(...)` and parsing helpers validate URL-encoded browser form values, dates, types, IDs, and amounts.
- JSON formatting methods return transactions, categories, totals, and errors without an external JSON library.
- `saveCurrentTransactions()` reports storage failures as HTTP errors.

The API uses `GET /api/transactions`, `POST /api/transactions`, `GET/PUT/DELETE /api/transactions/{id}`, `GET /api/search`, `GET /api/categories`, `GET /api/summary`, and `POST /api/save`.

## `Main.java`

The application entry point. It configures the relative CSV and HTML paths, uses port `8080`, starts `WebServer`, and reports startup errors. Run it from the project root so `data/transactions.csv` and `web/index.html` resolve correctly.

## `web/index.html`

Contains the browser interface, responsive CSS, and JavaScript. The page renders totals, categories, and transactions; submits income/expense forms; searches; updates records in a dialog; confirms deletion; and requests manual saves. User-provided text is inserted with `textContent` rather than interpreted as HTML.

## Important Java and web concepts

- **`BigDecimal`:** avoids binary floating-point rounding problems in money calculations.
- **`LocalDate`:** stores a date without an unrelated time or time zone.
- **`ArrayList`:** provides a simple growable collection for current records.
- **HTTP methods:** `GET` reads data, `POST` creates/saves, `PUT` updates, and `DELETE` removes.
- **URL-encoded forms and JSON:** the browser sends form values; the server returns JSON.
- **Exceptions and validation:** invalid values receive a clear HTTP 400 response; file failures receive an HTTP 500 response.
- **CSV escaping:** commas and quotes inside descriptions are preserved in saved data.
