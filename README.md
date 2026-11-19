# Personal Expense Tracker

A Java 21 web application for recording and reviewing personal income and expenses. It serves a browser interface from a small local Java HTTP server and stores transactions in a CSV file.

## Objectives

- Record income and expenses with dates, descriptions, categories, and unique IDs.
- Review, search, update, and delete transactions from a browser.
- Calculate total income, total expenses, and the remaining balance.
- Keep transaction history between runs using local CSV storage.

## Features

- Add income and categorized expenses.
- View transactions in a table and see live income, expense, and balance totals.
- Browse the eight expense categories.
- Search by ID, description, category, date, or type.
- Edit description, amount, category, or date.
- Delete a transaction after browser confirmation.
- Save automatically after changes or manually using **Save history**.
- Validate submitted data on the Java server as well as in the browser.

Invalid or duplicate CSV records are reported and skipped while other valid rows load. Amounts must be positive and have no more than two decimal places.

## Technologies

- Java 21 (Eclipse Temurin 21 recommended)
- Visual Studio Code
- JDK `HttpServer` module (`jdk.httpserver`)
- HTML, CSS, and browser JavaScript
- Java standard library: `ArrayList`, `BigDecimal`, `LocalDate`, and file I/O
- UTF-8 CSV storage; no database, web framework, or external library

## Java concepts used

- **Classes and objects:** `Transaction` models a record; the other classes organize the application.
- **Encapsulation:** transaction fields are private and accessed through methods.
- **Enum:** `TransactionType` limits types to `INCOME` and `EXPENSE`.
- **Collections and loops:** an `ArrayList` holds transactions; loops display and search them.
- **Conditionals and `switch`:** validate values and route search choices.
- **Exception handling:** reject invalid web form data and report file/server errors.
- **`BigDecimal`:** performs accurate monetary arithmetic.
- **`LocalDate`:** represents transaction dates.
- **HTTP:** the built-in JDK server serves the page and handles same-origin API requests.
- **File handling:** Java NIO reads and writes CSV records.

## Project structure

```text
PersonalExpenseTracker/
├── src/
│   └── com/expensetracker/
│       ├── Main.java
│       ├── WebServer.java
│       ├── Transaction.java
│       ├── TransactionType.java
│       ├── ExpenseTracker.java
│       └── FileStorage.java
├── web/
│   └── index.html
├── data/
│   └── transactions.csv
├── docs/
│   └── project-documentation.md
├── README.md
└── .gitignore
```

`Main` starts the local server. `WebServer` serves the page and API, `ExpenseTracker` manages records in memory, and `FileStorage` reads and writes CSV. `Transaction` and `TransactionType` model transaction data. See [the class and method guide](docs/project-documentation.md) for details.

The CSV columns are `id,date,type,description,category,amount`. Data is saved in `data/transactions.csv` relative to the project directory. The CSV is ignored by Git because it may contain private financial information.

## Install and run

1. Install a JDK 21 distribution, such as Eclipse Temurin 21, and make `java` and `javac` available on your `PATH`.
2. Verify the tools in PowerShell:

   ```powershell
   java -version
   javac -version
   ```

3. Open the project folder in VS Code. In a PowerShell terminal, change to the project root and compile:

   ```powershell
   Set-Location "C:\Users\local disk\Documents\PersonalExpenseTracker"
   New-Item -ItemType Directory -Path .\out -Force | Out-Null
   $sources = Get-ChildItem .\src\com\expensetracker -Filter *.java |
       ForEach-Object { $_.FullName }
   javac --add-modules jdk.httpserver -d .\out $sources
   ```

4. Start the server from the project root:

   ```powershell
   java --add-modules jdk.httpserver -cp .\out com.expensetracker.Main
   ```

5. Open **http://127.0.0.1:8080** in a browser. Keep the terminal running while you use the app; press **Ctrl+C** there to stop it.

The server binds only to `127.0.0.1`, so it is available on this computer and is not exposed to other devices on the network. The `out/` directory contains compiled `.class` files and can be recreated by recompiling.

## HTTP API

The browser uses these same-origin routes:

| Method | Route | Purpose |
|---|---|---|
| `GET` | `/api/transactions` | List transactions |
| `POST` | `/api/transactions` | Add an income or expense |
| `GET` | `/api/transactions/{id}` | Get one transaction |
| `PUT` | `/api/transactions/{id}` | Update a transaction |
| `DELETE` | `/api/transactions/{id}` | Delete a transaction |
| `GET` | `/api/search?field=...&q=...` | Search transactions |
| `GET` | `/api/categories` | List expense categories |
| `GET` | `/api/summary` | Get totals and balance |
| `POST` | `/api/save` | Save the current history |

Form requests use URL-encoded fields; responses are JSON. The app binds to loopback and does not enable cross-origin requests.

## Verification

The project was compiled with Java 21 and exercised through isolated HTTP requests using temporary CSV files. Checks covered page loading, categories, income and expense creation, searches, summaries, updating, invalid-amount rejection, manual saving, CSV reload after server restart, and deletion. No external test framework is included.

## Possible future enhancements

- Monthly summaries and category-based spending reports.
- Optional budgets and budget warnings.
- Improved browser accessibility and additional visual themes.
- Add a small automated test suite using the Java testing tools of choice.
