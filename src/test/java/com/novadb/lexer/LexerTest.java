package com.novadb.lexer;

import com.novadb.exception.NovaDBException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LexerTest {

    @Test
    void testBasicSelectQuery() {
        String sql = "SELECT id, name, age FROM users WHERE age >= 18 AND status = 'Active';";
        Lexer lexer = new Lexer(sql);
        List<Token> tokens = lexer.scanTokens();

        // 18 tokens expected (including EOF)
        assertEquals(18, tokens.size());

        assertEquals(TokenType.SELECT, tokens.get(0).type());
        
        assertEquals(TokenType.IDENTIFIER, tokens.get(1).type());
        assertEquals("id", tokens.get(1).lexeme());
        
        assertEquals(TokenType.COMMA, tokens.get(2).type());
        
        assertEquals(TokenType.IDENTIFIER, tokens.get(3).type());
        assertEquals("name", tokens.get(3).lexeme());
        
        assertEquals(TokenType.COMMA, tokens.get(4).type());
        
        assertEquals(TokenType.IDENTIFIER, tokens.get(5).type());
        assertEquals("age", tokens.get(5).lexeme());
        
        assertEquals(TokenType.FROM, tokens.get(6).type());
        
        assertEquals(TokenType.IDENTIFIER, tokens.get(7).type());
        assertEquals("users", tokens.get(7).lexeme());
        
        assertEquals(TokenType.WHERE, tokens.get(8).type());
        
        assertEquals(TokenType.IDENTIFIER, tokens.get(9).type());
        assertEquals("age", tokens.get(9).lexeme());
        
        assertEquals(TokenType.GREATER_EQUALS, tokens.get(10).type());
        
        assertEquals(TokenType.NUMBER_LITERAL, tokens.get(11).type());
        assertEquals(18, tokens.get(11).literal());
        
        assertEquals(TokenType.AND, tokens.get(12).type());
        
        assertEquals(TokenType.IDENTIFIER, tokens.get(13).type());
        assertEquals("status", tokens.get(13).lexeme());
        
        assertEquals(TokenType.EQUALS, tokens.get(14).type());
        
        assertEquals(TokenType.STRING_LITERAL, tokens.get(15).type());
        assertEquals("Active", tokens.get(15).literal());
        
        assertEquals(TokenType.SEMICOLON, tokens.get(16).type());
        assertEquals(TokenType.EOF, tokens.get(17).type());
    }

    @Test
    void testNumericTypes() {
        Lexer lexer = new Lexer("123 45.67");
        List<Token> tokens = lexer.scanTokens();
        
        assertEquals(3, tokens.size()); // 123, 45.67, EOF
        
        assertEquals(TokenType.NUMBER_LITERAL, tokens.get(0).type());
        assertEquals(123, tokens.get(0).literal());
        
        assertEquals(TokenType.NUMBER_LITERAL, tokens.get(1).type());
        assertEquals(45.67, tokens.get(1).literal());
    }

    @Test
    void testStringEscaping() {
        Lexer lexer = new Lexer("'O''Connor'");
        List<Token> tokens = lexer.scanTokens();
        
        assertEquals(2, tokens.size()); // string, EOF
        assertEquals(TokenType.STRING_LITERAL, tokens.get(0).type());
        assertEquals("O'Connor", tokens.get(0).literal());
        assertEquals("'O''Connor'", tokens.get(0).lexeme());
    }

    @Test
    void testUnterminatedString() {
        Lexer lexer = new Lexer("'Unterminated String");
        assertThrows(NovaDBException.class, lexer::scanTokens);
    }

    @Test
    void testInvalidCharacters() {
        Lexer lexer = new Lexer("SELECT @ FROM users");
        assertThrows(NovaDBException.class, lexer::scanTokens);
    }

    @Test
    void testLogicalAndComparisonOperators() {
        Lexer lexer = new Lexer("= != <> < <= > >= AND OR");
        List<Token> tokens = lexer.scanTokens();
        
        assertEquals(10, tokens.size());
        assertEquals(TokenType.EQUALS, tokens.get(0).type());
        assertEquals(TokenType.NOT_EQUALS, tokens.get(1).type());
        assertEquals(TokenType.NOT_EQUALS, tokens.get(2).type()); // <> is NOT_EQUALS
        assertEquals(TokenType.LESS, tokens.get(3).type());
        assertEquals(TokenType.LESS_EQUALS, tokens.get(4).type());
        assertEquals(TokenType.GREATER, tokens.get(5).type());
        assertEquals(TokenType.GREATER_EQUALS, tokens.get(6).type());
        assertEquals(TokenType.AND, tokens.get(7).type());
        assertEquals(TokenType.OR, tokens.get(8).type());
    }

    @Test
    void testKeywordsCaseInsensitivity() {
        Lexer lexer = new Lexer("cReAtE TaBlE SeLeCt");
        List<Token> tokens = lexer.scanTokens();
        
        assertEquals(4, tokens.size());
        assertEquals(TokenType.CREATE, tokens.get(0).type());
        assertEquals(TokenType.TABLE, tokens.get(1).type());
        assertEquals(TokenType.SELECT, tokens.get(2).type());
    }

    @Test
    void testLiteralsAndTypes() {
        Lexer lexer = new Lexer("TRUE FALSE NULL INT VARCHAR BOOLEAN");
        List<Token> tokens = lexer.scanTokens();
        
        assertEquals(7, tokens.size());
        assertEquals(TokenType.BOOLEAN_LITERAL, tokens.get(0).type());
        assertEquals(true, tokens.get(0).literal());
        
        assertEquals(TokenType.BOOLEAN_LITERAL, tokens.get(1).type());
        assertEquals(false, tokens.get(1).literal());
        
        assertEquals(TokenType.NULL_LITERAL, tokens.get(2).type());
        assertNull(tokens.get(2).literal());
        
        assertEquals(TokenType.INT, tokens.get(3).type());
        assertEquals(TokenType.VARCHAR, tokens.get(4).type());
        assertEquals(TokenType.BOOLEAN, tokens.get(5).type());
    }
}
