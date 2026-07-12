package com.novadb.transaction;

import com.novadb.catalog.CatalogManager;
import com.novadb.catalog.Schema;
import com.novadb.exception.NovaDBException;
import com.novadb.index.IndexManager;
import com.novadb.storage.PersistenceManager;
import com.novadb.storage.Row;
import com.novadb.storage.StorageManager;

import java.util.List;
import java.util.Map;

/**
 * Manages the database session transaction context (BEGIN, COMMIT, ROLLBACK).
 */
public class TransactionManager {
    private boolean active = false;

    // Snapshots of the database state taken at BEGIN
    private Map<String, Schema> catalogSnapshot;
    private Map<String, List<Row>> storageSnapshot;
    private IndexManager.IndexSnapshot indexSnapshot;

    /**
     * Checks if a transaction is currently in progress.
     */
    public synchronized boolean isInTransaction() {
        return active;
    }

    /**
     * Starts a new transaction, taking snapshots of catalog, storage, and indexes.
     */
    public synchronized void begin(CatalogManager catalog, StorageManager storage, IndexManager indexManager) {
        if (active) {
            throw new NovaDBException("Transaction already active.");
        }
        this.catalogSnapshot = catalog.getSchemasSnapshot();
        this.storageSnapshot = storage.getTableDataSnapshot();
        this.indexSnapshot = indexManager.getSnapshot();
        this.active = true;
    }

    /**
     * Commits the transaction, discarding snapshots and writing all mutations to disk.
     */
    public synchronized void commit(CatalogManager catalog, StorageManager storage, IndexManager indexManager, PersistenceManager persistence) {
        if (!active) {
            throw new NovaDBException("No active transaction to commit.");
        }
        this.active = false;
        this.catalogSnapshot = null;
        this.storageSnapshot = null;
        this.indexSnapshot = null;

        // Atomically flush database catalog & schemas to disk
        persistence.persistCatalog(catalog, indexManager);

        // Atomically flush all table rows to disk
        for (String table : catalog.getTableNames()) {
            Schema schema = catalog.getSchema(table);
            persistence.persistTable(table, storage.getRows(table), schema);
        }

        // Rebuild and write active indexes to disk
        for (String table : catalog.getTableNames()) {
            Schema schema = catalog.getSchema(table);
            indexManager.rebuildTableIndexes(table, storage.getRows(table), schema);
        }
    }

    /**
     * Reverts all in-memory changes back to the snapshots taken at BEGIN.
     */
    public synchronized void rollback(CatalogManager catalog, StorageManager storage, IndexManager indexManager) {
        if (!active) {
            throw new NovaDBException("No active transaction to rollback.");
        }
        catalog.restoreSnapshot(catalogSnapshot);
        storage.restoreSnapshot(storageSnapshot);
        indexManager.restoreSnapshot(indexSnapshot);

        this.active = false;
        this.catalogSnapshot = null;
        this.storageSnapshot = null;
        this.indexSnapshot = null;
    }
}
