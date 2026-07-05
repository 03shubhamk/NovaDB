package com.novadb.parser;

/**
 * Represents a column definition inside a CREATE TABLE statement.
 * 
 * @param name Name of the column.
 * @param dataType Data type of the column (e.g. INT, VARCHAR).
 * @param length Size modifier (e.g. varchar length), or null if not applicable.
 */
public record ColumnDefinition(String name, String dataType, Integer length) {
    @Override
    public String toString() {
        if (length != null) {
            return name + " " + dataType + "(" + length + ")";
        }
        return name + " " + dataType;
    }
}
