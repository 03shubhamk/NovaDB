package com.novadb.parser;

/**
 * Represents a term in the ORDER BY clause.
 * 
 * @param columnName The name of the column to sort on.
 * @param asc True for ASC sorting, false for DESC sorting.
 */
public record OrderByTerm(String columnName, boolean asc) {
    @Override
    public String toString() {
        return columnName + " " + (asc ? "ASC" : "DESC");
    }
}
