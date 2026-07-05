package com.novadb.parser;

/**
 * AST statement representing a transaction lifecycle query (BEGIN, COMMIT, ROLLBACK).
 */
public record TransactionStatement(TransactionType type) implements Statement {
    
    /**
     * Types of transaction control actions.
     */
    public enum TransactionType {
        BEGIN, COMMIT, ROLLBACK
    }

    @Override
    public String toString() {
        return type.name();
    }
}
