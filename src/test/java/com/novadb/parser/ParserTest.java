package com.novadb.parser;

import com.novadb.exception.NovaDBException;
import com.novadb.lexer.Lexer;
import com.novadb.lexer.Token;
import com.novadb.parser.expression.*;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ParserTest {

    private Statement parse(String sql) {
        Lexer lexer = new Lexer(sql);
        List<Token> tokens = lexer.scanTokens();
        Parser parser = new Parser(tokens);
        return parser.parse();
    }

    @Test
    void testCreateTable() {
        Statement stmt = parse("CREATE TABLE users (id INT, name VARCHAR(100), active BOOLEAN);");
        assertTrue(stmt instanceof CreateTableStatement);
        CreateTableStatement create = (CreateTableStatement) stmt;
        assertEquals("users", create.tableName());
        assertEquals(3, create.columns().size());
        
        ColumnDefinition col1 = create.columns().get(0);
        assertEquals("id", col1.name());
        assertEquals("INT", col1.dataType());
        assertNull(col1.length());

        ColumnDefinition col2 = create.columns().get(1);
        assertEquals("name", col2.name());
        assertEquals("VARCHAR", col2.dataType());
        assertEquals(100, col2.length());
    }

    @Test
    void testDropTable() {
        Statement stmt = parse("DROP TABLE users;");
        assertTrue(stmt instanceof DropTableStatement);
        DropTableStatement drop = (DropTableStatement) stmt;
        assertEquals("users", drop.tableName());
    }

    @Test
    void testInsert() {
        Statement stmt = parse("INSERT INTO users (id, name) VALUES (1, 'John');");
        assertTrue(stmt instanceof InsertStatement);
        InsertStatement insert = (InsertStatement) stmt;
        assertEquals("users", insert.tableName());
        assertEquals(List.of("id", "name"), insert.columns());
        assertEquals(2, insert.values().size());
        
        assertTrue(insert.values().get(0) instanceof LiteralExpression);
        assertEquals(1, ((LiteralExpression) insert.values().get(0)).value());

        assertTrue(insert.values().get(1) instanceof LiteralExpression);
        assertEquals("John", ((LiteralExpression) insert.values().get(1)).value());
    }

    @Test
    void testSelectBasic() {
        Statement stmt = parse("SELECT name, age FROM users;");
        assertTrue(stmt instanceof SelectStatement);
        SelectStatement select = (SelectStatement) stmt;
        assertEquals("users", select.tableName());
        assertEquals(List.of("name", "age"), select.projection());
        assertNull(select.whereClause());
        assertTrue(select.orderBy().isEmpty());
        assertNull(select.limit());
    }

    @Test
    void testSelectComplex() {
        Statement stmt = parse("SELECT * FROM users WHERE age >= 21 AND active = TRUE ORDER BY name DESC, id ASC LIMIT 10;");
        assertTrue(stmt instanceof SelectStatement);
        SelectStatement select = (SelectStatement) stmt;
        assertEquals("users", select.tableName());
        assertEquals(List.of("*"), select.projection());
        
        // Expression hierarchy check: WHERE age >= 21 AND active = TRUE
        // AND should be top-level BinaryExpression
        assertTrue(select.whereClause() instanceof BinaryExpression);
        BinaryExpression andExpr = (BinaryExpression) select.whereClause();
        assertEquals("AND", andExpr.operator());

        assertTrue(andExpr.left() instanceof BinaryExpression);
        BinaryExpression left = (BinaryExpression) andExpr.left();
        assertEquals(">=", left.operator());
        assertEquals("age", ((ColumnExpression) left.left()).name());
        assertEquals(21, ((LiteralExpression) left.right()).value());

        assertTrue(andExpr.right() instanceof BinaryExpression);
        BinaryExpression right = (BinaryExpression) andExpr.right();
        assertEquals("=", right.operator());
        assertEquals("active", ((ColumnExpression) right.left()).name());
        assertEquals(true, ((LiteralExpression) right.right()).value());

        // Order by check
        assertEquals(2, select.orderBy().size());
        assertEquals("name", select.orderBy().get(0).columnName());
        assertFalse(select.orderBy().get(0).asc()); // DESC
        assertEquals("id", select.orderBy().get(1).columnName());
        assertTrue(select.orderBy().get(1).asc()); // ASC (default)

        // Limit check
        assertEquals(10, select.limit());
    }

    @Test
    void testUpdate() {
        Statement stmt = parse("UPDATE users SET name = 'Bob', age = 30 WHERE id = 5;");
        assertTrue(stmt instanceof UpdateStatement);
        UpdateStatement update = (UpdateStatement) stmt;
        assertEquals("users", update.tableName());
        assertEquals(List.of("name", "age"), update.targetColumns());
        assertEquals(2, update.values().size());

        assertTrue(update.values().get(0) instanceof LiteralExpression);
        assertEquals("Bob", ((LiteralExpression) update.values().get(0)).value());

        assertTrue(update.values().get(1) instanceof LiteralExpression);
        assertEquals(30, ((LiteralExpression) update.values().get(1)).value());
        
        assertTrue(update.whereClause() instanceof BinaryExpression);
    }

    @Test
    void testDelete() {
        Statement stmt = parse("DELETE FROM users WHERE active = FALSE;");
        assertTrue(stmt instanceof DeleteStatement);
        DeleteStatement delete = (DeleteStatement) stmt;
        assertEquals("users", delete.tableName());
        assertTrue(delete.whereClause() instanceof BinaryExpression);
    }

    @Test
    void testTransactions() {
        Statement begin = parse("BEGIN;");
        assertTrue(begin instanceof TransactionStatement);
        assertEquals(TransactionStatement.TransactionType.BEGIN, ((TransactionStatement) begin).type());

        Statement commit = parse("COMMIT;");
        assertTrue(commit instanceof TransactionStatement);
        assertEquals(TransactionStatement.TransactionType.COMMIT, ((TransactionStatement) commit).type());

        Statement rollback = parse("ROLLBACK;");
        assertTrue(rollback instanceof TransactionStatement);
        assertEquals(TransactionStatement.TransactionType.ROLLBACK, ((TransactionStatement) rollback).type());
    }

    @Test
    void testExpressionPrecedenceAndParentheses() {
        // age > 10 OR status = 'VIP' AND active = TRUE
        // Precedence says AND has higher precedence, so it should parse as:
        // age > 10 OR (status = 'VIP' AND active = TRUE)
        Statement stmt = parse("SELECT * FROM users WHERE age > 10 OR status = 'VIP' AND active = TRUE;");
        assertTrue(stmt instanceof SelectStatement);
        SelectStatement select = (SelectStatement) stmt;
        
        assertTrue(select.whereClause() instanceof BinaryExpression);
        BinaryExpression orExpr = (BinaryExpression) select.whereClause();
        assertEquals("OR", orExpr.operator());
        
        // Left child should be age > 10
        assertEquals(">", ((BinaryExpression) orExpr.left()).operator());
        
        // Right child should be status = 'VIP' AND active = TRUE
        assertEquals("AND", ((BinaryExpression) orExpr.right()).operator());

        // With explicit parenthesis: (age > 10 OR status = 'VIP') AND active = TRUE
        Statement stmt2 = parse("SELECT * FROM users WHERE (age > 10 OR status = 'VIP') AND active = TRUE;");
        SelectStatement select2 = (SelectStatement) stmt2;
        
        assertTrue(select2.whereClause() instanceof BinaryExpression);
        BinaryExpression andExpr = (BinaryExpression) select2.whereClause();
        assertEquals("AND", andExpr.operator());
        
        // Left child should be age > 10 OR status = 'VIP'
        assertEquals("OR", ((BinaryExpression) andExpr.left()).operator());
        // Right child should be active = TRUE
        assertEquals("=", ((BinaryExpression) andExpr.right()).operator());
    }

    @Test
    void testParserExceptions() {
        // Missing closing paren
        assertThrows(NovaDBException.class, () -> parse("CREATE TABLE users (id INT;"));
        
        // Missing table name
        assertThrows(NovaDBException.class, () -> parse("DROP TABLE;"));
        
        // Invalid limit value (must be integer, not string)
        assertThrows(NovaDBException.class, () -> parse("SELECT * FROM users LIMIT 'ten';"));
        
        // Unexpected token after query
        assertThrows(NovaDBException.class, () -> parse("SELECT * FROM users; SELECT * FROM profiles;"));
    }
}
