package com.novadb.executor;

import com.novadb.DatabaseEngine;
import com.novadb.common.QueryResult;
import com.novadb.exception.NovaDBException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ExecutionTest {
    private DatabaseEngine engine;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        engine = new DatabaseEngine(tempDir.toString());
        engine.start();
    }

    @AfterEach
    void tearDown() {
        engine.stop();
    }

    @Test
    void testCreateTableAndSelectEmpty() {
        QueryResult createResult = engine.execute("CREATE TABLE students (id INT, name VARCHAR(50), active BOOLEAN);");
        assertTrue(createResult.success());
        assertTrue(createResult.message().contains("created successfully"));

        // Select from empty table
        QueryResult selectResult = engine.execute("SELECT * FROM students;");
        assertTrue(selectResult.success());
        assertEquals(List.of("id", "name", "active"), selectResult.columns());
        assertTrue(selectResult.rows().isEmpty());
    }

    @Test
    void testDuplicateTableCreate() {
        engine.execute("CREATE TABLE dummy (id INT);");
        QueryResult duplicateResult = engine.execute("CREATE TABLE dummy (id INT);");
        assertFalse(duplicateResult.success());
        assertTrue(duplicateResult.message().contains("Table already exists"));
    }

    @Test
    void testInsertPositionalSuccess() {
        engine.execute("CREATE TABLE employees (id INT, name VARCHAR(20), rate DOUBLE);");

        QueryResult insertResult = engine.execute("INSERT INTO employees VALUES (101, 'Alice', 45.50);");
        assertTrue(insertResult.success());
        assertEquals("1 row inserted.", insertResult.message());

        QueryResult selectResult = engine.execute("SELECT * FROM employees;");
        assertTrue(selectResult.success());
        assertEquals(1, selectResult.rows().size());
        assertEquals(101, selectResult.rows().get(0).get(0));
        assertEquals("Alice", selectResult.rows().get(0).get(1));
        assertEquals(45.50, selectResult.rows().get(0).get(2));
    }

    @Test
    void testInsertNamedColumnsSuccess() {
        engine.execute("CREATE TABLE employees (id INT, name VARCHAR(20), rate DOUBLE);");

        // Insert only ID and Rate, Name remains null
        QueryResult insertResult = engine.execute("INSERT INTO employees (rate, id) VALUES (75.0, 102);");
        assertTrue(insertResult.success());

        QueryResult selectResult = engine.execute("SELECT id, name, rate FROM employees;");
        assertTrue(selectResult.success());
        assertEquals(1, selectResult.rows().size());
        assertEquals(102, selectResult.rows().get(0).get(0));
        assertNull(selectResult.rows().get(0).get(1));
        assertEquals(75.0, selectResult.rows().get(0).get(2));
    }

    @Test
    void testInsertImplicitNumericCoercion() {
        engine.execute("CREATE TABLE items (id INT, price DOUBLE);");

        // Insert integer (100) into DOUBLE column
        QueryResult insertResult = engine.execute("INSERT INTO items VALUES (1, 100);");
        assertTrue(insertResult.success());

        QueryResult selectResult = engine.execute("SELECT price FROM items;");
        assertTrue(selectResult.success());
        assertEquals(1, selectResult.rows().size());
        assertEquals(100.0, selectResult.rows().get(0).get(0)); // Coerced to Double
    }

    @Test
    void testInsertVarcharLengthConstraintExceeded() {
        engine.execute("CREATE TABLE users (id INT, username VARCHAR(5));");

        // "abcdef" has length 6, constraint is VARCHAR(5)
        QueryResult insertResult = engine.execute("INSERT INTO users VALUES (1, 'abcdef');");
        assertFalse(insertResult.success());
        assertTrue(insertResult.message().contains("exceeds length constraint"));
    }

    @Test
    void testInsertTypeMismatch() {
        engine.execute("CREATE TABLE users (id INT, is_admin BOOLEAN);");

        // Try inserting String into INT
        QueryResult failInt = engine.execute("INSERT INTO users VALUES ('one', TRUE);");
        assertFalse(failInt.success());
        assertTrue(failInt.message().contains("Type mismatch: Expected INT"));

        // Try inserting Int into BOOLEAN
        QueryResult failBool = engine.execute("INSERT INTO users VALUES (1, 0);");
        assertFalse(failBool.success());
        assertTrue(failBool.message().contains("Type mismatch: Expected BOOLEAN"));
    }

    @Test
    void testSelectProjectionsAndLimit() {
        engine.execute("CREATE TABLE test_table (id INT, val VARCHAR(10));");
        engine.execute("INSERT INTO test_table VALUES (10, 'A');");
        engine.execute("INSERT INTO test_table VALUES (20, 'B');");
        engine.execute("INSERT INTO test_table VALUES (30, 'C');");

        // Specific projection
        QueryResult projResult = engine.execute("SELECT val FROM test_table;");
        assertTrue(projResult.success());
        assertEquals(List.of("val"), projResult.columns());
        assertEquals(3, projResult.rows().size());
        assertEquals("A", projResult.rows().get(0).get(0));

        // Limit count
        QueryResult limitResult = engine.execute("SELECT * FROM test_table LIMIT 2;");
        assertTrue(limitResult.success());
        assertEquals(2, limitResult.rows().size());
        assertEquals(10, limitResult.rows().get(0).get(0));
        assertEquals(20, limitResult.rows().get(1).get(0));
    }

    @Test
    void testSelectInvalidTableOrColumn() {
        QueryResult selectFailTable = engine.execute("SELECT * FROM non_existent;");
        assertFalse(selectFailTable.success());
        assertTrue(selectFailTable.message().contains("Table not found"));

        engine.execute("CREATE TABLE sample (id INT);");
        QueryResult selectFailColumn = engine.execute("SELECT name FROM sample;");
        assertFalse(selectFailColumn.success());
        assertTrue(selectFailColumn.message().contains("Column 'name' does not exist"));
    }
}
