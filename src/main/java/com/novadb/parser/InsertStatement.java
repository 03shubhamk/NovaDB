package com.novadb.parser;

import com.novadb.parser.expression.Expression;

import java.util.List;
import java.util.stream.Collectors;

/**
 * AST statement representing an INSERT INTO query.
 */
public record InsertStatement(
    String tableName,
    List<String> columns,
    List<Expression> values
) implements Statement {
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("INSERT INTO ");
        sb.append(tableName);
        
        if (columns != null && !columns.isEmpty()) {
            sb.append(" (").append(String.join(", ", columns)).append(")");
        }
        
        String valsStr = values.stream()
                .map(Expression::toString)
                .collect(Collectors.joining(", "));
        sb.append(" VALUES (").append(valsStr).append(")");
        
        return sb.toString();
    }
}
