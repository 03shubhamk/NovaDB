package com.novadb.catalog;

import com.novadb.exception.NovaDBException;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Manages database catalog metadata, registering schemas and table definitions
 * in memory.
 */
public class CatalogManager {
    private final Map<String, Schema> schemas = new HashMap<>();

    /**
     * Registers a new table with its associated schema.
     * 
     * @param tableName The table name (case-insensitive key).
     * @param schema The table schema definition.
     */
    public synchronized void addTable(String tableName, Schema schema) {
        if (tableName == null || tableName.trim().isEmpty()) {
            throw new IllegalArgumentException("Table name cannot be null or empty.");
        }
        if (schema == null) {
            throw new IllegalArgumentException("Table schema cannot be null.");
        }

        String key = tableName.toUpperCase();
        if (schemas.containsKey(key)) {
            throw new NovaDBException("Table already exists: " + tableName);
        }
        schemas.put(key, schema);
    }

    /**
     * Retrieves the schema of a table.
     * 
     * @param tableName The table name (case-insensitive lookup).
     * @return Schema definition of the table.
     */
    public synchronized Schema getSchema(String tableName) {
        if (tableName == null) return null;
        Schema schema = schemas.get(tableName.toUpperCase());
        if (schema == null) {
            throw new NovaDBException("Table not found: " + tableName);
        }
        return schema;
    }

    /**
     * Deletes table schema metadata from catalog.
     */
    public synchronized void dropTable(String tableName) {
        if (tableName == null) return;
        String key = tableName.toUpperCase();
        if (!schemas.containsKey(key)) {
            throw new NovaDBException("Table not found: " + tableName);
        }
        schemas.remove(key);
    }

    /**
     * Checks if table metadata exists in catalog.
     */
    public synchronized boolean hasTable(String tableName) {
        if (tableName == null) return false;
        return schemas.containsKey(tableName.toUpperCase());
    }

    /**
     * Gets names of all tables currently defined in catalog.
     */
    public synchronized Set<String> getTableNames() {
        return Collections.unmodifiableSet(schemas.keySet());
    }

    /**
     * Resets the catalog metadata.
     */
    public synchronized void clear() {
        schemas.clear();
    }
}
