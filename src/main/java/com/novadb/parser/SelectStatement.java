package com.novadb.parser;

import com.novadb.parser.expression.Expression;

import java.util.List;
import java.util.stream.Collectors;

/**
 * AST statement representing a SELECT query.
 */
public record SelectStatement(
    List<String> projection,
    String tableName,
    Expression whereClause,
    List<OrderByTerm> orderBy,
    Integer limit
) implements Statement {
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("SELECT ");
        sb.append(String.join(", ", projection));
        sb.append(" FROM ").append(tableName);

        if (whereClause != null) {
            sb.append(" WHERE ").append(whereClause);
        }

        if (orderBy != null && !orderBy.isEmpty()) {
            String orderStr = orderBy.stream()
                    .map(OrderByTerm::toString)
                    .collect(Collectors.joining(", "));
            sb.append(" ORDER BY ").append(orderStr);
        }

        if (limit != null) {
            sb.append(" LIMIT ").append(limit);
        }

        return sb.toString();
    }
}
