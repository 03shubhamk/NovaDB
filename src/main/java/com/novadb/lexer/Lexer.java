package com.novadb.lexer;

import com.novadb.exception.NovaDBException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * SQL Lexical Analyzer (Scanner) for NovaDB.
 * Tokenizes SQL queries, ignoring whitespace, tracking character positions,
 * and identifying SQL keywords, literals, and symbols.
 */
public class Lexer {
    private static final Map<String, TokenType> KEYWORDS = new HashMap<>();

    static {
        // Core SQL commands & syntax
        KEYWORDS.put("CREATE", TokenType.CREATE);
        KEYWORDS.put("TABLE", TokenType.TABLE);
        KEYWORDS.put("DROP", TokenType.DROP);
        KEYWORDS.put("INSERT", TokenType.INSERT);
        KEYWORDS.put("INTO", TokenType.INTO);
        KEYWORDS.put("VALUES", TokenType.VALUES);
        KEYWORDS.put("SELECT", TokenType.SELECT);
        KEYWORDS.put("FROM", TokenType.FROM);
        KEYWORDS.put("WHERE", TokenType.WHERE);
        KEYWORDS.put("UPDATE", TokenType.UPDATE);
        KEYWORDS.put("SET", TokenType.SET);
        KEYWORDS.put("DELETE", TokenType.DELETE);
        KEYWORDS.put("ORDER", TokenType.ORDER);
        KEYWORDS.put("BY", TokenType.BY);
        KEYWORDS.put("LIMIT", TokenType.LIMIT);
        KEYWORDS.put("ASC", TokenType.ASC);
        KEYWORDS.put("DESC", TokenType.DESC);
        KEYWORDS.put("AND", TokenType.AND);
        KEYWORDS.put("OR", TokenType.OR);

        KEYWORDS.put("BEGIN", TokenType.BEGIN);
        KEYWORDS.put("COMMIT", TokenType.COMMIT);
        KEYWORDS.put("ROLLBACK", TokenType.ROLLBACK);
        KEYWORDS.put("INDEX", TokenType.INDEX);
        KEYWORDS.put("ON", TokenType.ON);

        // Types
        KEYWORDS.put("INT", TokenType.INT);
        KEYWORDS.put("INTEGER", TokenType.INT); // alias
        KEYWORDS.put("DOUBLE", TokenType.DOUBLE);
        KEYWORDS.put("VARCHAR", TokenType.VARCHAR);
        KEYWORDS.put("TEXT", TokenType.TEXT);
        KEYWORDS.put("BOOLEAN", TokenType.BOOLEAN);
    }

    private final String source;
    private final List<Token> tokens = new ArrayList<>();
    private int start = 0;
    private int current = 0;

    /**
     * Initializes the Lexer with a target SQL query string.
     */
    public Lexer(String source) {
        this.source = source != null ? source : "";
    }

    /**
     * Scans the query and returns the generated tokens list.
     * 
     * @return List of matched Token instances ending with an EOF token.
     */
    public List<Token> scanTokens() {
        while (!isAtEnd()) {
            start = current;
            scanToken();
        }

        tokens.add(new Token(TokenType.EOF, "", null, current));
        return tokens;
    }

    private void scanToken() {
        char c = advance();
        switch (c) {
            case '(' -> addToken(TokenType.LPAREN);
            case ')' -> addToken(TokenType.RPAREN);
            case ',' -> addToken(TokenType.COMMA);
            case ';' -> addToken(TokenType.SEMICOLON);
            case '*' -> addToken(TokenType.ASTERISK);
            case '=' -> addToken(TokenType.EQUALS);
            case '!' -> {
                if (match('=')) {
                    addToken(TokenType.NOT_EQUALS);
                } else {
                    throw new NovaDBException("Unexpected character '!' at position " + (current - 1) + ". Expected '=' for '!='.");
                }
            }
            case '<' -> {
                if (match('=')) {
                    addToken(TokenType.LESS_EQUALS);
                } else if (match('>')) {
                    addToken(TokenType.NOT_EQUALS); // standard SQL <> operator
                } else {
                    addToken(TokenType.LESS);
                }
            }
            case '>' -> {
                if (match('=')) {
                    addToken(TokenType.GREATER_EQUALS);
                } else {
                    addToken(TokenType.GREATER);
                }
            }
            case '\'' -> stringLiteral();
            case ' ', '\r', '\t', '\n' -> {
                // Ignore whitespace
            }
            default -> {
                if (isDigit(c)) {
                    numberLiteral();
                } else if (isAlpha(c)) {
                    identifierOrKeyword();
                } else {
                    throw new NovaDBException("Invalid character '" + c + "' at position " + (current - 1) + ".");
                }
            }
        }
    }

    private void stringLiteral() {
        StringBuilder val = new StringBuilder();
        while (!isAtEnd()) {
            if (peek() == '\'') {
                // Check if it's an escaped single quote (e.g. '')
                if (peekNext() == '\'') {
                    val.append('\'');
                    advance(); // consume first quote
                    advance(); // consume second quote
                } else {
                    break; // Closing quote reached
                }
            } else {
                val.append(advance());
            }
        }

        if (isAtEnd()) {
            throw new NovaDBException("Unterminated string literal starting at position " + start + ".");
        }

        // Consume the closing single quote
        advance();

        String lexeme = source.substring(start, current);
        tokens.add(new Token(TokenType.STRING_LITERAL, lexeme, val.toString(), start));
    }

    private void numberLiteral() {
        boolean isDouble = false;
        while (isDigit(peek())) {
            advance();
        }

        // Look for floating-point dot
        if (peek() == '.' && isDigit(peekNext())) {
            isDouble = true;
            advance(); // consume the '.'

            while (isDigit(peek())) {
                advance();
            }
        }

        String lexeme = source.substring(start, current);
        Object value;
        if (isDouble) {
            value = Double.parseDouble(lexeme);
            tokens.add(new Token(TokenType.NUMBER_LITERAL, lexeme, value, start));
        } else {
            value = Integer.parseInt(lexeme);
            tokens.add(new Token(TokenType.NUMBER_LITERAL, lexeme, value, start));
        }
    }

    private void identifierOrKeyword() {
        while (isAlphaNumeric(peek())) {
            advance();
        }

        String lexeme = source.substring(start, current);
        String upperLexeme = lexeme.toUpperCase();

        TokenType type = KEYWORDS.get(upperLexeme);
        if (type != null) {
            // It is a reserved keyword
            addToken(type);
        } else if (upperLexeme.equals("TRUE")) {
            tokens.add(new Token(TokenType.BOOLEAN_LITERAL, lexeme, Boolean.TRUE, start));
        } else if (upperLexeme.equals("FALSE")) {
            tokens.add(new Token(TokenType.BOOLEAN_LITERAL, lexeme, Boolean.FALSE, start));
        } else if (upperLexeme.equals("NULL")) {
            tokens.add(new Token(TokenType.NULL_LITERAL, lexeme, null, start));
        } else {
            // Regular identifier (table name, column name)
            addToken(TokenType.IDENTIFIER);
        }
    }

    private boolean isAtEnd() {
        return current >= source.length();
    }

    private char advance() {
        return source.charAt(current++);
    }

    private boolean match(char expected) {
        if (isAtEnd()) return false;
        if (source.charAt(current) != expected) return false;
        current++;
        return true;
    }

    private char peek() {
        if (isAtEnd()) return '\0';
        return source.charAt(current);
    }

    private char peekNext() {
        if (current + 1 >= source.length()) return '\0';
        return source.charAt(current + 1);
    }

    private boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    private boolean isAlpha(char c) {
        return (c >= 'a' && c <= 'z') ||
               (c >= 'A' && c <= 'Z') ||
               c == '_';
    }

    private boolean isAlphaNumeric(char c) {
        return isAlpha(c) || isDigit(c);
    }

    private void addToken(TokenType type) {
        String lexeme = source.substring(start, current);
        tokens.add(new Token(type, lexeme, null, start));
    }
}
