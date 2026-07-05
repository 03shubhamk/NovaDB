package com.novadb.parser;

import com.novadb.parser.expression.Expression;

/**
 * AST statement representing a DELETE FROM query.
 */
public record DeleteStatement(String tableName, Expression whereClause) implements Statement {
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("DELETE FROM ");
        sb.append(tableName);

        if (whereClause != null) {
            sb.append(" WHERE ").append(whereClause);
        }

        return sb.toString();
    }
}
