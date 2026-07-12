package com.novadb.executor;

import com.novadb.DatabaseEngine;
import com.novadb.common.QueryResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ConditionalExecutionTest {
    private DatabaseEngine engine;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        engine = new DatabaseEngine(tempDir.toString());
        engine.start();
        
        // Seed data
        engine.execute("CREATE TABLE users (id INT, name VARCHAR(20), active BOOLEAN, balance DOUBLE);");
        engine.execute("INSERT INTO users VALUES (1, 'Alice', TRUE, 1500.0);");
        engine.execute("INSERT INTO users VALUES (2, 'Bob', FALSE, 2500.0);");
        engine.execute("INSERT INTO users VALUES (3, 'Charlie', TRUE, 500.0);");
        engine.execute("INSERT INTO users VALUES (4, 'Dave', NULL, 1200.0);");
    }

    @AfterEach
    void tearDown() {
        engine.stop();
    }

    @Test
    void testConditionalSelectEquals() {
        QueryResult select = engine.execute("SELECT name FROM users WHERE active = TRUE;");
        assertTrue(select.success());
        assertEquals(2, select.rows().size());
        assertEquals("Alice", select.rows().get(0).get(0));
        assertEquals("Charlie", select.rows().get(1).get(0));
    }

    @Test
    void testConditionalSelectInequality() {
        QueryResult select = engine.execute("SELECT name FROM users WHERE balance > 1000;");
        assertTrue(select.success());
        assertEquals(3, select.rows().size());
        assertEquals("Alice", select.rows().get(0).get(0));
        assertEquals("Bob", select.rows().get(1).get(0));
        assertEquals("Dave", select.rows().get(2).get(0));
    }

    @Test
    void testConditionalSelectAndOr() {
        // AND expression
        QueryResult selectAnd = engine.execute("SELECT name FROM users WHERE active = TRUE AND balance > 1000;");
        assertTrue(selectAnd.success());
        assertEquals(1, selectAnd.rows().size());
        assertEquals("Alice", selectAnd.rows().get(0).get(0));

        // OR expression
        QueryResult selectOr = engine.execute("SELECT name FROM users WHERE balance < 1000 OR name = 'Bob';");
        assertTrue(selectOr.success());
        assertEquals(2, selectOr.rows().size());
        assertEquals("Bob", selectOr.rows().get(0).get(0));
        assertEquals("Charlie", selectOr.rows().get(1).get(0));
    }

    @Test
    void testConditionalSelectNulls() {
        // Match null
        QueryResult selectNull = engine.execute("SELECT name FROM users WHERE active = NULL;");
        assertTrue(selectNull.success());
        assertEquals(1, selectNull.rows().size());
        assertEquals("Dave", selectNull.rows().get(0).get(0));

        // Match not null
        QueryResult selectNotNull = engine.execute("SELECT name FROM users WHERE active != NULL;");
        assertTrue(selectNotNull.success());
        assertEquals(3, selectNotNull.rows().size());
    }

    @Test
    void testConditionalUpdate() {
        QueryResult update = engine.execute("UPDATE users SET balance = 3000.0 WHERE active = TRUE;");
        assertTrue(update.success());
        assertEquals("2 rows updated.", update.message());

        // Verify changes
        QueryResult select = engine.execute("SELECT name, balance FROM users WHERE balance = 3000.0;");
        assertTrue(select.success());
        assertEquals(2, select.rows().size());
        assertEquals("Alice", select.rows().get(0).get(0));
        assertEquals("Charlie", select.rows().get(1).get(0));
    }

    @Test
    void testConditionalDelete() {
        QueryResult delete = engine.execute("DELETE FROM users WHERE active = FALSE;");
        assertTrue(delete.success());
        assertEquals("1 rows deleted.", delete.message());

        // Verify Bob is deleted
        QueryResult select = engine.execute("SELECT name FROM users;");
        assertTrue(select.success());
        assertEquals(3, select.rows().size());
        for (List<Object> row : select.rows()) {
            assertNotEquals("Bob", row.get(0));
        }
    }

    @Test
    void testFullDelete() {
        QueryResult delete = engine.execute("DELETE FROM users;");
        assertTrue(delete.success());
        assertEquals("4 rows deleted.", delete.message());

        QueryResult select = engine.execute("SELECT * FROM users;");
        assertTrue(select.success());
        assertTrue(select.rows().isEmpty());
    }

    @Test
    void testLogicalEvaluationErrors() {
        // AND/OR with non-boolean (string/numeric) throws error
        QueryResult failAnd = engine.execute("SELECT * FROM users WHERE active AND 'yes';");
        assertFalse(failAnd.success());
        assertTrue(failAnd.message().contains("Logical operators AND/OR require BOOLEAN"));
    }

    @Test
    void testComparisonTypeMismatchError() {
        // Compare double with string
        QueryResult failCompare = engine.execute("SELECT * FROM users WHERE balance = 'rich';");
        assertFalse(failCompare.success());
        assertTrue(failCompare.message().contains("Type mismatch: Cannot compare Double with String"));
    }
}
