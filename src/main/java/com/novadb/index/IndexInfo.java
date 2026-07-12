package com.novadb.index;

/**
 * Metadata record representing a database index.
 */
public record IndexInfo(String indexName, String tableName, String columnName) {}
