package com.novadb.parser;

import java.util.List;
import java.util.stream.Collectors;

/**
 * AST statement representing a CREATE TABLE query.
 */
public record CreateTableStatement(String tableName, List<ColumnDefinition> columns) implements Statement {
    @Override
    public String toString() {
        String colsStr = columns.stream()
                .map(ColumnDefinition::toString)
                .collect(Collectors.joining(", "));
        return "CREATE TABLE " + tableName + " (" + colsStr + ")";
    }
}
