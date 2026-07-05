package com.novadb.cli;

import com.novadb.DatabaseEngine;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.concurrent.Callable;

/**
 * Main command-line wrapper for NovaDB using Picocli.
 * Supports starting an interactive REPL session or executing statements directly.
 */
@Command(
    name = "novadb",
    mixinStandardHelpOptions = true,
    version = "NovaDB 1.0",
    description = "NovaDB - A Lightweight SQL Database Engine Built from Scratch."
)
public class NovaDBConsole implements Callable<Integer> {

    @Option(
        names = {"-d", "--dir"},
        description = "Target storage directory for database files (default: data)"
    )
    private String dataDir = "data";

    @Option(
        names = {"-q", "--query"},
        description = "Execute a single SQL query in non-interactive mode and exit"
    )
    private String query;

    /**
     * Application entry point.
     * 
     * @param args Command-line arguments.
     */
    public static void main(String[] args) {
        int exitCode = new CommandLine(new NovaDBConsole()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() {
        try (DatabaseEngine engine = new DatabaseEngine(dataDir)) {
            engine.start();

            if (query != null && !query.trim().isEmpty()) {
                String sql = query.trim();
                // SQL statements must end with a semicolon in our engine.
                if (!sql.endsWith(";")) {
                    sql += ";";
                }
                
                Repl repl = new Repl(engine, System.in, System.out, System.err);
                boolean success = repl.executeSql(sql);
                return success ? 0 : 1;
            } else {
                // Interactive REPL loop
                Repl repl = new Repl(engine, System.in, System.out, System.err);
                repl.start();
                return 0;
            }
        } catch (Exception e) {
            System.err.println("Database startup failed: " + e.getMessage());
            return 1;
        }
    }
}
