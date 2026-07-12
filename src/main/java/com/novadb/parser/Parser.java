package com.novadb.parser;

import com.novadb.exception.NovaDBException;
import com.novadb.lexer.Token;
import com.novadb.lexer.TokenType;
import com.novadb.parser.TransactionStatement.TransactionType;
import com.novadb.parser.expression.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Custom recursive-descent parser for NovaDB SQL grammar.
 * Parses a list of tokens into Statement abstract syntax tree nodes.
 */
public class Parser {
    private final List<Token> tokens;
    private int current = 0;

    public Parser(List<Token> tokens) {
        this.tokens = tokens != null ? tokens : List.of();
    }

    /**
     * Entry point to parse a single complete SQL statement.
     * Consumes trailing semicolons and verifies that no trailing input exists.
     * 
     * @return Parsed Statement node.
     */
    public Statement parse() {
        if (isAtEnd()) {
            throw new NovaDBException("Empty SQL statement.");
        }
        Statement stmt = statement();
        
        // Consume optional/required trailing semicolon
        if (match(TokenType.SEMICOLON)) {
            // Semicolon consumed
        }
        
        if (!isAtEnd()) {
            throw new NovaDBException("Syntax error: Unexpected tokens after statement at position " + peek().position() + ".");
        }
        
        return stmt;
    }

    private Statement statement() {
        if (check(TokenType.CREATE)) {
            advance();
            if (match(TokenType.TABLE)) return createTableStatementRest();
            if (match(TokenType.INDEX)) return createIndexStatementRest();
            throw new NovaDBException("Expected 'TABLE' or 'INDEX' keyword after 'CREATE' at position " + peek().position() + ".");
        }
        if (check(TokenType.DROP)) {
            advance();
            if (match(TokenType.TABLE)) return dropTableStatementRest();
            if (match(TokenType.INDEX)) return dropIndexStatementRest();
            throw new NovaDBException("Expected 'TABLE' or 'INDEX' keyword after 'DROP' at position " + peek().position() + ".");
        }
        if (match(TokenType.INSERT)) return insertStatement();
        if (match(TokenType.SELECT)) return selectStatement();
        if (match(TokenType.UPDATE)) return updateStatement();
        if (match(TokenType.DELETE)) return deleteStatement();
        
        // Transactions
        if (match(TokenType.BEGIN)) return new TransactionStatement(TransactionType.BEGIN);
        if (match(TokenType.COMMIT)) return new TransactionStatement(TransactionType.COMMIT);
        if (match(TokenType.ROLLBACK)) return new TransactionStatement(TransactionType.ROLLBACK);

        throw new NovaDBException("Syntax error: Expected SQL command (SELECT, INSERT, UPDATE, DELETE, CREATE, DROP, BEGIN, COMMIT, ROLLBACK) at position " + peek().position() + ".");
    }

    private Statement createTableStatementRest() {
        Token tableName = consume(TokenType.IDENTIFIER, "Expected table name.");
        consume(TokenType.LPAREN, "Expected '(' before column definitions.");

        List<ColumnDefinition> columns = new ArrayList<>();
        do {
            Token columnName = consume(TokenType.IDENTIFIER, "Expected column name.");
            
            // Parse data type
            Token typeToken = advance();
            if (typeToken.type() != TokenType.INT &&
                typeToken.type() != TokenType.DOUBLE &&
                typeToken.type() != TokenType.VARCHAR &&
                typeToken.type() != TokenType.TEXT &&
                typeToken.type() != TokenType.BOOLEAN) {
                throw new NovaDBException("Expected valid data type (INT, DOUBLE, VARCHAR, TEXT, BOOLEAN) at position " + typeToken.position() + ".");
            }

            Integer varcharLength = null;
            if (typeToken.type() == TokenType.VARCHAR) {
                consume(TokenType.LPAREN, "Expected '(' after VARCHAR type.");
                Token lengthToken = consume(TokenType.NUMBER_LITERAL, "Expected length value for VARCHAR.");
                if (!(lengthToken.literal() instanceof Integer)) {
                    throw new NovaDBException("VARCHAR length must be an integer at position " + lengthToken.position() + ".");
                }
                varcharLength = (Integer) lengthToken.literal();
                consume(TokenType.RPAREN, "Expected ')' after VARCHAR length.");
            }

            columns.add(new ColumnDefinition(columnName.lexeme(), typeToken.type().name(), varcharLength));
        } while (match(TokenType.COMMA));

        consume(TokenType.RPAREN, "Expected ')' after column definitions.");
        return new CreateTableStatement(tableName.lexeme(), columns);
    }

    private Statement dropTableStatementRest() {
        Token tableName = consume(TokenType.IDENTIFIER, "Expected table name.");
        return new DropTableStatement(tableName.lexeme());
    }

    private Statement createIndexStatementRest() {
        Token indexName = consume(TokenType.IDENTIFIER, "Expected index name.");
        consume(TokenType.ON, "Expected 'ON' keyword.");
        Token tableName = consume(TokenType.IDENTIFIER, "Expected table name.");
        consume(TokenType.LPAREN, "Expected '(' before column name.");
        Token columnName = consume(TokenType.IDENTIFIER, "Expected indexed column name.");
        consume(TokenType.RPAREN, "Expected ')' after column name.");
        return new CreateIndexStatement(indexName.lexeme(), tableName.lexeme(), columnName.lexeme());
    }

    private Statement dropIndexStatementRest() {
        Token indexName = consume(TokenType.IDENTIFIER, "Expected index name.");
        return new DropIndexStatement(indexName.lexeme());
    }

    private Statement insertStatement() {
        consume(TokenType.INTO, "Expected 'INTO' keyword after 'INSERT'.");
        Token tableName = consume(TokenType.IDENTIFIER, "Expected table name.");

        List<String> targetColumns = new ArrayList<>();
        if (match(TokenType.LPAREN)) {
            do {
                Token col = consume(TokenType.IDENTIFIER, "Expected column name identifier.");
                targetColumns.add(col.lexeme());
            } while (match(TokenType.COMMA));
            consume(TokenType.RPAREN, "Expected ')' after target columns list.");
        }

        consume(TokenType.VALUES, "Expected 'VALUES' keyword.");
        consume(TokenType.LPAREN, "Expected '(' before values list.");

        List<Expression> values = new ArrayList<>();
        do {
            values.add(expression());
        } while (match(TokenType.COMMA));

        consume(TokenType.RPAREN, "Expected ')' after values list.");
        return new InsertStatement(tableName.lexeme(), targetColumns, values);
    }

    private Statement selectStatement() {
        List<String> projection = new ArrayList<>();
        if (match(TokenType.ASTERISK)) {
            projection.add("*");
        } else {
            do {
                Token col = consume(TokenType.IDENTIFIER, "Expected column name for projection.");
                projection.add(col.lexeme());
            } while (match(TokenType.COMMA));
        }

        consume(TokenType.FROM, "Expected 'FROM' keyword.");
        Token tableName = consume(TokenType.IDENTIFIER, "Expected table name.");

        Expression whereClause = null;
        if (match(TokenType.WHERE)) {
            whereClause = expression();
        }

        List<OrderByTerm> orderBy = new ArrayList<>();
        if (match(TokenType.ORDER)) {
            consume(TokenType.BY, "Expected 'BY' keyword after 'ORDER'.");
            do {
                Token col = consume(TokenType.IDENTIFIER, "Expected column name in ORDER BY.");
                boolean asc = true;
                if (match(TokenType.ASC)) {
                    asc = true;
                } else if (match(TokenType.DESC)) {
                    asc = false;
                }
                orderBy.add(new OrderByTerm(col.lexeme(), asc));
            } while (match(TokenType.COMMA));
        }

        Integer limitValue = null;
        if (match(TokenType.LIMIT)) {
            Token limitToken = consume(TokenType.NUMBER_LITERAL, "Expected limit count.");
            if (!(limitToken.literal() instanceof Integer)) {
                throw new NovaDBException("LIMIT value must be an integer at position " + limitToken.position() + ".");
            }
            limitValue = (Integer) limitToken.literal();
        }

        return new SelectStatement(projection, tableName.lexeme(), whereClause, orderBy, limitValue);
    }

    private Statement updateStatement() {
        Token tableName = consume(TokenType.IDENTIFIER, "Expected table name.");
        consume(TokenType.SET, "Expected 'SET' keyword after table name.");

        List<String> targetColumns = new ArrayList<>();
        List<Expression> values = new ArrayList<>();

        do {
            Token col = consume(TokenType.IDENTIFIER, "Expected column name.");
            consume(TokenType.EQUALS, "Expected '=' assignment operator.");
            Expression valExpr = expression();

            targetColumns.add(col.lexeme());
            values.add(valExpr);
        } while (match(TokenType.COMMA));

        Expression whereClause = null;
        if (match(TokenType.WHERE)) {
            whereClause = expression();
        }

        return new UpdateStatement(tableName.lexeme(), targetColumns, values, whereClause);
    }

    private Statement deleteStatement() {
        consume(TokenType.FROM, "Expected 'FROM' keyword after 'DELETE'.");
        Token tableName = consume(TokenType.IDENTIFIER, "Expected table name.");

        Expression whereClause = null;
        if (match(TokenType.WHERE)) {
            whereClause = expression();
        }

        return new DeleteStatement(tableName.lexeme(), whereClause);
    }

    // --- Expression Parsing (Recursive Descent with Precedence) ---

    private Expression expression() {
        return logicalOr();
    }

    private Expression logicalOr() {
        Expression expr = logicalAnd();
        while (match(TokenType.OR)) {
            String op = previous().lexeme().toUpperCase();
            Expression right = logicalAnd();
            expr = new BinaryExpression(op, expr, right);
        }
        return expr;
    }

    private Expression logicalAnd() {
        Expression expr = equality();
        while (match(TokenType.AND)) {
            String op = previous().lexeme().toUpperCase();
            Expression right = equality();
            expr = new BinaryExpression(op, expr, right);
        }
        return expr;
    }

    private Expression equality() {
        Expression expr = comparison();
        while (match(TokenType.EQUALS, TokenType.NOT_EQUALS)) {
            String op = previous().lexeme().toUpperCase();
            Expression right = comparison();
            expr = new BinaryExpression(op, expr, right);
        }
        return expr;
    }

    private Expression comparison() {
        Expression expr = primary();
        while (match(TokenType.GREATER, TokenType.GREATER_EQUALS, TokenType.LESS, TokenType.LESS_EQUALS)) {
            String op = previous().lexeme().toUpperCase();
            Expression right = primary();
            expr = new BinaryExpression(op, expr, right);
        }
        return expr;
    }

    private Expression primary() {
        if (match(TokenType.BOOLEAN_LITERAL, TokenType.NUMBER_LITERAL, TokenType.STRING_LITERAL, TokenType.NULL_LITERAL)) {
            return new LiteralExpression(previous().literal());
        }

        if (match(TokenType.IDENTIFIER)) {
            return new ColumnExpression(previous().lexeme());
        }

        if (match(TokenType.LPAREN)) {
            Expression expr = expression();
            consume(TokenType.RPAREN, "Expected ')' after expression.");
            return expr;
        }

        throw new NovaDBException("Syntax error: Expected expression at position " + peek().position() + ".");
    }

    // --- Parser Helpers ---

    private boolean isAtEnd() {
        return peek().type() == TokenType.EOF;
    }

    private Token peek() {
        return tokens.get(current);
    }

    private Token previous() {
        return tokens.get(current - 1);
    }

    private Token advance() {
        if (!isAtEnd()) current++;
        return previous();
    }

    private boolean match(TokenType... types) {
        for (TokenType type : types) {
            if (check(type)) {
                advance();
                return true;
            }
        }
        return false;
    }

    private boolean check(TokenType type) {
        if (isAtEnd()) return false;
        return peek().type() == type;
    }

    private Token consume(TokenType type, String errorMessage) {
        if (check(type)) return advance();
        throw new NovaDBException(errorMessage + " Found: " + peek().lexeme() + " at position " + peek().position() + ".");
    }
}
