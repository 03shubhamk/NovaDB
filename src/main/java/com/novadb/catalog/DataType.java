package com.novadb.catalog;

/**
 * Supported data types in NovaDB schemas.
 */
public enum DataType {
    INT,
    DOUBLE,
    VARCHAR,
    TEXT,
    BOOLEAN;

    /**
     * Resolves DataType enum from string representation.
     */
    public static DataType fromString(String typeStr) {
        try {
            return DataType.valueOf(typeStr.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown data type: " + typeStr);
        }
    }
}
