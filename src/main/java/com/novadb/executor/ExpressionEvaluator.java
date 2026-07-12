package com.novadb.executor;

import com.novadb.catalog.Schema;
import com.novadb.exception.NovaDBException;
import com.novadb.parser.expression.*;
import com.novadb.storage.Row;

/**
 * Utility class that evaluates AST Expressions against a specific database row and schema context.
 */
public class ExpressionEvaluator {

    /**
     * Evaluates an expression for a given row.
     * 
     * @param expr The AST expression to evaluate.
     * @param row The table row record containing cell values.
     * @param schema The schema defining column indexes.
     * @return The evaluated result object (Integer, Double, String, Boolean, or null).
     */
    public static Object evaluate(Expression expr, Row row, Schema schema) {
        if (expr instanceof LiteralExpression literalExpr) {
            return literalExpr.value();
        }

        if (expr instanceof ColumnExpression colExpr) {
            int idx = schema.getColumnIndex(colExpr.name());
            if (idx == -1) {
                throw new NovaDBException("Column not found in schema: " + colExpr.name());
            }
            return row.getValue(idx);
        }

        if (expr instanceof BinaryExpression binaryExpr) {
            String op = binaryExpr.operator();

            // Handle logical operators (AND, OR)
            if (op.equals("AND") || op.equals("OR")) {
                Object leftVal = evaluate(binaryExpr.left(), row, schema);
                
                // Shortcut evaluation if possible
                if (op.equals("AND") && Boolean.FALSE.equals(leftVal)) {
                    return false;
                }
                if (op.equals("OR") && Boolean.TRUE.equals(leftVal)) {
                    return true;
                }

                Object rightVal = evaluate(binaryExpr.right(), row, schema);

                if (!(leftVal instanceof Boolean) || !(rightVal instanceof Boolean)) {
                    throw new NovaDBException("Logical operators AND/OR require BOOLEAN operands. Found: " 
                        + (leftVal == null ? "null" : leftVal.getClass().getSimpleName()) + " and " 
                        + (rightVal == null ? "null" : rightVal.getClass().getSimpleName()));
                }

                if (op.equals("AND")) {
                    return (Boolean) leftVal && (Boolean) rightVal;
                } else {
                    return (Boolean) leftVal || (Boolean) rightVal;
                }
            }

            // Handle comparison operators (=, !=, <, <=, >, >=)
            Object leftVal = evaluate(binaryExpr.left(), row, schema);
            Object rightVal = evaluate(binaryExpr.right(), row, schema);

            // Null-safe checks
            if (leftVal == null || rightVal == null) {
                if (op.equals("=")) {
                    return leftVal == null && rightVal == null;
                }
                if (op.equals("!=") || op.equals("<>")) {
                    return !(leftVal == null && rightVal == null);
                }
                // Any other inequality check with a null operand is false in SQL
                return false;
            }

            // Numeric comparisons (with double coercion)
            if (leftVal instanceof Number && rightVal instanceof Number) {
                double leftNum = ((Number) leftVal).doubleValue();
                double rightNum = ((Number) rightVal).doubleValue();

                return switch (op) {
                    case "=" -> leftNum == rightNum;
                    case "!=", "<>" -> leftNum != rightNum;
                    case ">" -> leftNum > rightNum;
                    case ">=" -> leftNum >= rightNum;
                    case "<" -> leftNum < rightNum;
                    case "<=" -> leftNum <= rightNum;
                    default -> throw new NovaDBException("Invalid numeric comparison operator: " + op);
                };
            }

            // String comparisons
            if (leftVal instanceof String && rightVal instanceof String) {
                String leftStr = (String) leftVal;
                String rightStr = (String) rightVal;
                int cmp = leftStr.compareTo(rightStr);

                return switch (op) {
                    case "=" -> cmp == 0;
                    case "!=", "<>" -> cmp != 0;
                    case ">" -> cmp > 0;
                    case ">=" -> cmp >= 0;
                    case "<" -> cmp < 0;
                    case "<=" -> cmp <= 0;
                    default -> throw new NovaDBException("Invalid string comparison operator: " + op);
                };
            }

            // Boolean comparisons
            if (leftVal instanceof Boolean && rightVal instanceof Boolean) {
                boolean leftBool = (Boolean) leftVal;
                boolean rightBool = (Boolean) rightVal;

                return switch (op) {
                    case "=" -> leftBool == rightBool;
                    case "!=", "<>" -> leftBool != rightBool;
                    default -> throw new NovaDBException("Operator " + op + " is not supported for BOOLEAN type.");
                };
            }

            // Catch-all type mismatch
            throw new NovaDBException("Type mismatch: Cannot compare " 
                + leftVal.getClass().getSimpleName() + " with " 
                + rightVal.getClass().getSimpleName() + " using operator '" + op + "'.");
        }

        throw new NovaDBException("Unsupported expression type: " + expr.getClass().getSimpleName());
    }
}
