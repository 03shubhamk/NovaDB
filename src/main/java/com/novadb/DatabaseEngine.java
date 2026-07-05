package com.novadb;

import com.novadb.common.QueryResult;
import com.novadb.exception.NovaDBException;
import com.novadb.lexer.Lexer;
import com.novadb.lexer.Token;
import com.novadb.parser.Parser;
import com.novadb.parser.Statement;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Main coordinator for NovaDB database. Manages storage path setup, engine lifecycle,
 * and entry point for executing statements.
 */
public class DatabaseEngine implements AutoCloseable {
    private static final Logger LOGGER = Logger.getLogger(DatabaseEngine.class.getName());
    
    private final Path dataDirectory;
    private boolean active;

    /**
     * Initializes the database engine using the default directory 'data'.
     */
    public DatabaseEngine() {
        this("data");
    }

    /**
     * Initializes the database engine with a custom base directory.
     * 
     * @param dataDir Directory path for data storage.
     */
    public DatabaseEngine(String dataDir) {
        if (dataDir == null || dataDir.trim().isEmpty()) {
            throw new IllegalArgumentException("Data directory path cannot be null or empty.");
        }
        this.dataDirectory = Paths.get(dataDir).toAbsolutePath().normalize();
    }

    /**
     * Starts the database engine, setting up the required directories.
     */
    public synchronized void start() {
        if (active) {
            return;
        }
        
        try {
            Files.createDirectories(dataDirectory);
            LOGGER.log(Level.INFO, "NovaDB starting up. Data storage path: {0}", dataDirectory);
            active = true;
        } catch (IOException e) {
            throw new NovaDBException("Failed to initialize data directories: " + dataDirectory, e);
        }
    }

    /**
     * Stops the engine and releases any locks or resources.
     */
    public synchronized void stop() {
        if (!active) {
            return;
        }
        LOGGER.info("NovaDB shutting down.");
        active = false;
    }

    /**
     * Checks if the engine is running.
     */
    public boolean isActive() {
        return active;
    }

    /**
     * Returns the configured storage directory path.
     */
    public Path getDataDirectory() {
        return dataDirectory;
    }

    /**
     * Entry point for query execution.
     * 
     * @param sql The SQL statement string.
     * @return Execution result summary.
     */
    public QueryResult execute(String sql) {
        if (!active) {
            throw new NovaDBException("Database engine is not running.");
        }
        if (sql == null || sql.trim().isEmpty()) {
            throw new IllegalArgumentException("SQL statement cannot be null or empty.");
        }

        long startTime = System.nanoTime();
        String normalizedSql = sql.trim().replaceAll("\\s+", " ").toUpperCase();

        try {
            String cleanedSql = normalizedSql.replaceAll("\\s*;\\s*$", "");
            if (cleanedSql.equals("SELECT 1")) {
                long durationMs = (System.nanoTime() - startTime) / 1_000_000;
                return QueryResult.success("Mock SELECT execution succeeded.", durationMs);
            }

            Lexer lexer = new Lexer(sql);
            List<Token> tokens = lexer.scanTokens();
            Parser parser = new Parser(tokens);
            Statement stmt = parser.parse();

            long durationMs = (System.nanoTime() - startTime) / 1_000_000;
            return QueryResult.success("Parsed AST: " + stmt.toString(), durationMs);
        } catch (Exception e) {
            long durationMs = (System.nanoTime() - startTime) / 1_000_000;
            return QueryResult.failure(e.getMessage(), durationMs);
        }
    }

    @Override
    public void close() {
        stop();
    }
}
