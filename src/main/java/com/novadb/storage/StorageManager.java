package com.novadb.storage;

import com.novadb.exception.NovaDBException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Manages the raw data storage (rows) of database tables.
 * For Phase 3, storage is maintained purely in-memory.
 */
public class StorageManager {
    private final Map<String, List<Row>> tableData = new HashMap<>();

    /**
     * Allocates storage space for a new table.
     * 
     * @param tableName Name of the table (case-insensitive key).
     */
    public synchronized void createTable(String tableName) {
        if (tableName == null || tableName.trim().isEmpty()) {
            throw new IllegalArgumentException("Table name cannot be null or empty.");
        }
        String key = tableName.toUpperCase();
        if (tableData.containsKey(key)) {
            throw new NovaDBException("Table storage space already exists: " + tableName);
        }
        tableData.put(key, new ArrayList<>());
    }

    /**
     * Appends a record row to a table.
     * 
     * @param tableName Table name.
     * @param row Row record to append.
     */
    public synchronized void insertRow(String tableName, Row row) {
        if (tableName == null) {
            throw new IllegalArgumentException("Table name cannot be null.");
        }
        if (row == null) {
            throw new IllegalArgumentException("Row cannot be null.");
        }

        List<Row> rows = tableData.get(tableName.toUpperCase());
        if (rows == null) {
            throw new NovaDBException("Table storage not initialized: " + tableName);
        }
        rows.add(row);
    }

    /**
     * Retrieves all rows stored for a table.
     * 
     * @param tableName Table name.
     * @return Read-only list of rows.
     */
    public synchronized List<Row> getRows(String tableName) {
        if (tableName == null) return List.of();
        List<Row> rows = tableData.get(tableName.toUpperCase());
        if (rows == null) {
            throw new NovaDBException("Table storage not initialized: " + tableName);
        }
        return Collections.unmodifiableList(rows);
    }

    /**
     * Drops table data rows from storage.
     */
    public synchronized void dropTable(String tableName) {
        if (tableName == null) return;
        String key = tableName.toUpperCase();
        if (!tableData.containsKey(key)) {
            throw new NovaDBException("Table storage not initialized: " + tableName);
        }
        tableData.remove(key);
    }

    /**
     * Resets the storage engine.
     */
    public synchronized void clear() {
        tableData.clear();
    }

    public synchronized Map<String, List<Row>> getTableDataSnapshot() {
        Map<String, List<Row>> snapshot = new HashMap<>();
        for (Map.Entry<String, List<Row>> entry : tableData.entrySet()) {
            snapshot.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        }
        return snapshot;
    }

    public synchronized void restoreSnapshot(Map<String, List<Row>> snapshot) {
        tableData.clear();
        for (Map.Entry<String, List<Row>> entry : snapshot.entrySet()) {
            tableData.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        }
    }
}
