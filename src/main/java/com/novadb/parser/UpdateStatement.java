package com.novadb.parser;

import com.novadb.parser.expression.Expression;

import java.util.List;

/**
 * AST statement representing an UPDATE query.
 */
public record UpdateStatement(
    String tableName,
    List<String> targetColumns,
    List<Expression> values,
    Expression whereClause
) implements Statement {
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("UPDATE ");
        sb.append(tableName).append(" SET ");

        for (int i = 0; i < targetColumns.size(); i++) {
            sb.append(targetColumns.get(i)).append(" = ").append(values.get(i));
            if (i < targetColumns.size() - 1) {
                sb.append(", ");
            }
        }

        if (whereClause != null) {
            sb.append(" WHERE ").append(whereClause);
        }

        return sb.toString();
    }
}
