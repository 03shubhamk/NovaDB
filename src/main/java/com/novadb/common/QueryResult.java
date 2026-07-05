package com.novadb.common;

import java.util.List;

/**
 * Encapsulates the result of executing a SQL statement.
 * 
 * @param success Indicates if the command executed without errors.
 * @param message Feedback message (e.g., "Table created", "3 rows updated").
 * @param columns List of column names for SELECT queries; empty otherwise.
 * @param rows Double list representing retrieved data; empty otherwise.
 * @param executionTimeMs Time taken to execute the statement in milliseconds.
 */
public record QueryResult(
    boolean success,
    String message,
    List<String> columns,
    List<List<Object>> rows,
    long executionTimeMs
) {
    /**
     * Factory method for successful command executions (e.g., DDL, INSERT, UPDATE).
     */
    public static QueryResult success(String message, long executionTimeMs) {
        return new QueryResult(true, message, List.of(), List.of(), executionTimeMs);
    }

    /**
     * Factory method for successful SELECT queries.
     */
    public static QueryResult query(List<String> columns, List<List<Object>> rows, long executionTimeMs) {
        return new QueryResult(true, "", columns, rows, executionTimeMs);
    }

    /**
     * Factory method for failed executions.
     */
    public static QueryResult failure(String errorMessage, long executionTimeMs) {
        return new QueryResult(false, errorMessage, List.of(), List.of(), executionTimeMs);
    }
}
