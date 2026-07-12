package com.novadb.lexer;

/**
 * Types of tokens recognized by the NovaDB SQL Lexer.
 */
public enum TokenType {
    // Single-character tokens
    LPAREN, RPAREN, COMMA, SEMICOLON, ASTERISK,

    // Operators
    EQUALS,         // =
    NOT_EQUALS,     // != or <>
    GREATER,        // >
    GREATER_EQUALS, // >=
    LESS,           // <
    LESS_EQUALS,    // <=

    // Literals
    IDENTIFIER,
    STRING_LITERAL,
    NUMBER_LITERAL,
    BOOLEAN_LITERAL,
    NULL_LITERAL,

    // Keywords
    CREATE, TABLE, DROP,
    INSERT, INTO, VALUES,
    SELECT, FROM, WHERE,
    UPDATE, SET, DELETE,
    ORDER, BY, LIMIT,
    ASC, DESC,
    AND, OR,
    BEGIN, COMMIT, ROLLBACK,
    INDEX, ON,
    
    // Data Types
    INT, DOUBLE, VARCHAR, TEXT, BOOLEAN,

    // End of file
    EOF
}
