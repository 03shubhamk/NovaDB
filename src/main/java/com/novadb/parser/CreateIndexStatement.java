package com.novadb.parser;

/**
 * AST Node representing a CREATE INDEX statement.
 */
public record CreateIndexStatement(String indexName, String tableName, String columnName) implements Statement {
    @Override
    public String toString() {
        return "CREATE INDEX " + indexName + " ON " + tableName + " (" + columnName + ")";
    }
}
