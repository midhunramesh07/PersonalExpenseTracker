package com.expensetracker;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Starts the local web application.
 */
public class Main {
    public static void main(String[] args) {
        try {
            WebServer server = new WebServer(
                    Path.of("data", "transactions.csv"),
                    Path.of("web", "index.html"),
                    8080);
            server.start();
        } catch (IOException exception) {
            System.err.println("Could not start the expense tracker: "
                    + exception.getMessage());
        }
    }
}
