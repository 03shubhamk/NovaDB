package com.novadb.cli;

import com.novadb.DatabaseEngine;
import com.novadb.common.QueryResult;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.util.List;

/**
 * Handles the Read-Eval-Print Loop (REPL) interactive shell for NovaDB.
 * Supports multi-line input ending with a semicolon, dot commands, and
 * pretty printing of query results.
 */
public class Repl {
    private final DatabaseEngine engine;
    private final BufferedReader reader;
    private final PrintStream out;
    private final PrintStream err;

    /**
     * Creates a REPL session attached to standard input and output streams.
     * 
     * @param engine The database engine instance.
     */
    public Repl(DatabaseEngine engine) {
        this(engine, System.in, System.out, System.err);
    }

    /**
     * Constructor allowing stream redirection (useful for unit testing).
     */
    public Repl(DatabaseEngine engine, InputStream in, PrintStream out, PrintStream err) {
        this.engine = engine;
        this.reader = new BufferedReader(new InputStreamReader(in));
        this.out = out;
        this.err = err;
    }

    /**
     * Starts the interactive shell loop.
     */
    public void start() {
        out.println("NovaDB Shell - Version 1.0");
        out.println("Type '.help' for usage instructions or '.exit' to quit.");
        out.println();

        StringBuilder queryBuffer = new StringBuilder();
        boolean running = true;

        while (running) {
            try {
                // Determine prompt based on whether we are in the middle of a multi-line query
                if (queryBuffer.isEmpty()) {
                    out.print("NovaDB> ");
                } else {
                    out.print("     -> ");
                }
                out.flush();

                String line = reader.readLine();
                if (line == null) {
                    // End of stream
                    break;
                }

                String trimmedLine = line.trim();
                if (trimmedLine.isEmpty()) {
                    continue;
                }

                // Check for dot commands (only when starting a new command)
                if (queryBuffer.isEmpty() && trimmedLine.startsWith(".")) {
                    running = handleDotCommand(trimmedLine);
                    continue;
                }

                // Append line to current statement buffer
                queryBuffer.append(" ").append(trimmedLine);

                // If statement ends with semicolon, execute it
                if (trimmedLine.endsWith(";")) {
                    String sql = queryBuffer.toString().trim();
                    queryBuffer.setLength(0); // Reset buffer

                    // Standard exits via SQL style
                    if (sql.equalsIgnoreCase("exit;") || sql.equalsIgnoreCase("quit;")) {
                        running = false;
                        continue;
                    }

                    executeSql(sql);
                }
            } catch (Exception e) {
                err.println("Fatal error in REPL shell: " + e.getMessage());
                running = false;
            }
        }

        out.println("Goodbye.");
    }

    /**
     * Executes the SQL statement and formats the output.
     * 
     * @param sql The SQL statement string.
     * @return true if successful, false otherwise.
     */
    public boolean executeSql(String sql) {
        boolean success = false;
        try {
            QueryResult result = engine.execute(sql);
            success = result.success();
            if (success) {
                if (!result.columns().isEmpty()) {
                    printTable(result.columns(), result.rows());
                    out.printf("(%d rows, execution time: %d ms)%n", result.rows().size(), result.executionTimeMs());
                } else {
                    out.printf("%s (execution time: %d ms)%n", 
                        result.message().isEmpty() ? "Command executed successfully." : result.message(), 
                        result.executionTimeMs());
                }
            } else {
                err.println("Error: " + result.message());
            }
        } catch (Exception e) {
            err.println("Error: " + e.getMessage());
        }
        out.println();
        return success;
    }

    /**
     * Handles metadata/client dot commands.
     * 
     * @return true to continue REPL, false to exit.
     */
    private boolean handleDotCommand(String command) {
        String[] parts = command.split("\\s+");
        String cmd = parts[0].toLowerCase();

        switch (cmd) {
            case ".exit", ".quit" -> {
                return false;
            }
            case ".help" -> {
                out.println("Available commands:");
                out.println("  .help             Show this help message");
                out.println("  .exit / .quit     Exit NovaDB interactive shell");
                out.println("  exit; / quit;     Exit NovaDB interactive shell");
                out.println("  [SQL Statement];  Execute SQL command (must end with a semicolon ';')");
                out.println();
                return true;
            }
            default -> {
                err.println("Unknown command: " + cmd + ". Type '.help' for list of commands.");
                out.println();
                return true;
            }
        }
    }

    /**
     * Formats and prints query results in a clean tabular view.
     */
    private void printTable(List<String> columns, List<List<Object>> rows) {
        if (columns == null || columns.isEmpty()) {
            return;
        }

        // Calculate maximum width of columns
        int[] widths = new int[columns.size()];
        for (int i = 0; i < columns.size(); i++) {
            widths[i] = columns.get(i).length();
        }

        for (List<Object> row : rows) {
            for (int i = 0; i < row.size() && i < widths.length; i++) {
                Object val = row.get(i);
                String strVal = (val == null) ? "NULL" : val.toString();
                if (strVal.length() > widths[i]) {
                    widths[i] = strVal.length();
                }
            }
        }

        // Build horizontal divider
        StringBuilder divider = new StringBuilder("+");
        for (int w : widths) {
            divider.append("-".repeat(w + 2)).append("+");
        }

        // Print header
        out.println(divider);
        out.print("|");
        for (int i = 0; i < columns.size(); i++) {
            out.printf(" %-" + widths[i] + "s |", columns.get(i));
        }
        out.println();
        out.println(divider);

        // Print rows
        for (List<Object> row : rows) {
            out.print("|");
            for (int i = 0; i < columns.size(); i++) {
                Object val = (i < row.size()) ? row.get(i) : null;
                String strVal = (val == null) ? "NULL" : val.toString();
                out.printf(" %-" + widths[i] + "s |", strVal);
            }
            out.println();
        }

        out.println(divider);
    }
}
