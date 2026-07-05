package com.novadb.parser.expression;

/**
 * AST Expression representing a binary operation (e.g. A = B, age > 18, active AND verified).
 */
public record BinaryExpression(String operator, Expression left, Expression right) implements Expression {
    @Override
    public String toString() {
        return "(" + left + " " + operator + " " + right + ")";
    }
}
