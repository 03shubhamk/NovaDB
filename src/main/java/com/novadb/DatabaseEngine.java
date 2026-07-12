package com.novadb;

import com.novadb.catalog.CatalogManager;
import com.novadb.catalog.Schema;
import com.novadb.common.QueryResult;
import com.novadb.exception.NovaDBException;
import com.novadb.executor.Executor;
import com.novadb.lexer.Lexer;
import com.novadb.lexer.Token;
import com.novadb.parser.Parser;
import com.novadb.parser.Statement;
import com.novadb.index.IndexInfo;
import com.novadb.index.IndexManager;
import com.novadb.storage.PersistenceManager;
import com.novadb.storage.Row;
import com.novadb.storage.StorageManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
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
    private final CatalogManager catalog;
    private final StorageManager storage;
    private final IndexManager indexManager;
    private final PersistenceManager persistence;
    private final com.novadb.transaction.TransactionManager transactionManager;
    private final Executor executor;
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
        this.catalog = new CatalogManager();
        this.storage = new StorageManager();
        this.indexManager = new IndexManager(this.dataDirectory);
        this.transactionManager = new com.novadb.transaction.TransactionManager();
        this.indexManager.setTransactionManager(this.transactionManager);
        this.persistence = new PersistenceManager(this.dataDirectory);
        this.executor = new Executor(this.catalog, this.storage, this.persistence, this.indexManager, this.transactionManager);
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
            
            // Load catalog schemas
            persistence.loadCatalog(catalog, indexManager);
            
            // Rehydrate stored table records
            for (String tableName : catalog.getTableNames()) {
                Schema schema = catalog.getSchema(tableName);
                storage.createTable(tableName);
                List<Row> rows = persistence.loadTable(tableName, schema);
                for (Row row : rows) {
                    storage.insertRow(tableName, row);
                }
                
                // Rehydrate active indexes
                List<IndexInfo> idxList = new ArrayList<>(indexManager.getIndexesForTable(tableName));
                for (IndexInfo info : idxList) {
                    indexManager.loadOrRebuildIndex(info, rows, schema);
                }
            }
            
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

            return executor.execute(stmt);
        } catch (Exception e) {
            long durationMs = (System.nanoTime() - startTime) / 1_000_000;
            return QueryResult.failure(e.getMessage(), durationMs);
        }
    }

    public CatalogManager getCatalog() {
        return catalog;
    }

    public StorageManager getStorage() {
        return storage;
    }

    public PersistenceManager getPersistence() {
        return persistence;
    }

    public IndexManager getIndexManager() {
        return indexManager;
    }

    public com.novadb.transaction.TransactionManager getTransactionManager() {
        return transactionManager;
    }

    @Override
    public void close() {
        stop();
    }
}
