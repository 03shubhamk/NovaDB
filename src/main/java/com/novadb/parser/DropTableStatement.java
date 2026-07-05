package com.novadb.parser;

/**
 * AST statement representing a DROP TABLE query.
 */
public record DropTableStatement(String tableName) implements Statement {
    @Override
    public String toString() {
        return "DROP TABLE " + tableName;
    }
}
