package com.novadb.catalog;

/**
 * Represents a column definition in a table schema.
 * 
 * @param name The column name.
 * @param type The DataType of the column.
 * @param length The maximum length of the data value (used for VARCHAR, null otherwise).
 */
public record Column(String name, DataType type, Integer length) {
    
    public Column(String name, DataType type) {
        this(name, type, null);
    }

    @Override
    public String toString() {
        if (length != null) {
            return name + " " + type + "(" + length + ")";
        }
        return name + " " + type;
    }
}
