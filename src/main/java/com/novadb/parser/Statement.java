package com.novadb.parser;

/**
 * Base interface for all Abstract Syntax Tree (AST) statements in NovaDB.
 */
public interface Statement {
    /**
     * Textual representation of the parsed SQL command (useful for debug outputs).
     */
    @Override
    String toString();
}
