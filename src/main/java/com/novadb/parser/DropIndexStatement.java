package com.novadb.parser;

/**
 * AST Node representing a DROP INDEX statement.
 */
public record DropIndexStatement(String indexName) implements Statement {
    @Override
    public String toString() {
        return "DROP INDEX " + indexName;
    }
}
