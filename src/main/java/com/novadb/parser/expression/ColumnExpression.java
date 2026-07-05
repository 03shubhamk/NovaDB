package com.novadb.parser.expression;

/**
 * AST Expression representing a column reference (e.g. name, age, id).
 */
public record ColumnExpression(String name) implements Expression {
    @Override
    public String toString() {
        return name;
    }
}
