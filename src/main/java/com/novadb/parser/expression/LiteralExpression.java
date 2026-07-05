package com.novadb.parser.expression;

/**
 * AST Expression representing a constant value (e.g. 42, 'Alice', TRUE, NULL).
 */
public record LiteralExpression(Object value) implements Expression {
    @Override
    public String toString() {
        if (value == null) {
            return "NULL";
        }
        if (value instanceof String) {
            return "'" + value + "'";
        }
        return value.toString();
    }
}
