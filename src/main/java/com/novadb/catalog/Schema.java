package com.novadb.catalog;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents the schema of a database table, holding column definitions
 * and providing fast lookups.
 */
public class Schema {
    private final List<Column> columns;
    private final Map<String, Integer> columnIndices;

    /**
     * Constructs a Schema with a list of columns.
     * 
     * @param columns List of columns defining the schema.
     */
    public Schema(List<Column> columns) {
        if (columns == null || columns.isEmpty()) {
            throw new IllegalArgumentException("Schema must contain at least one column.");
        }
        this.columns = List.copyOf(columns);
        
        Map<String, Integer> indices = new HashMap<>();
        for (int i = 0; i < this.columns.size(); i++) {
            String columnName = this.columns.get(i).name().toUpperCase();
            if (indices.containsKey(columnName)) {
                throw new IllegalArgumentException("Duplicate column name in schema: " + this.columns.get(i).name());
            }
            indices.put(columnName, i);
        }
        this.columnIndices = Collections.unmodifiableMap(indices);
    }

    /**
     * Gets the list of column definitions.
     */
    public List<Column> getColumns() {
        return columns;
    }

    /**
     * Resolves the index of a column by name.
     * 
     * @param columnName Case-insensitive column name.
     * @return 0-based column index, or -1 if not found.
     */
    public int getColumnIndex(String columnName) {
        if (columnName == null) return -1;
        return columnIndices.getOrDefault(columnName.toUpperCase(), -1);
    }

    /**
     * Checks if a column exists.
     */
    public boolean hasColumn(String columnName) {
        if (columnName == null) return false;
        return columnIndices.containsKey(columnName.toUpperCase());
    }

    /**
     * Retrieves column definition at specified index.
     */
    public Column getColumn(int index) {
        if (index < 0 || index >= columns.size()) {
            throw new IndexOutOfBoundsException("Column index out of bounds: " + index);
        }
        return columns.get(index);
    }

    /**
     * Gets the total number of columns in this schema.
     */
    public int getColumnCount() {
        return columns.size();
    }
}
