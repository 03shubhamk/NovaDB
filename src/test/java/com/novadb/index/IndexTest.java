package com.novadb.index;

import com.novadb.DatabaseEngine;
import com.novadb.common.QueryResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class IndexTest {
    private DatabaseEngine engine;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        engine = new DatabaseEngine(tempDir.toString());
        engine.start();

        // Seed initial table
        engine.execute("CREATE TABLE users (id INT, email VARCHAR(30), score DOUBLE);");
        engine.execute("INSERT INTO users VALUES (1, 'alice@test.com', 95.0);");
        engine.execute("INSERT INTO users VALUES (2, 'bob@test.com', 85.5);");
        engine.execute("INSERT INTO users VALUES (3, 'charlie@test.com', 90.0);");
    }

    @AfterEach
    void tearDown() {
        engine.stop();
    }

    @Test
    void testCreateIndexAndLookup() {
        QueryResult idxCreate = engine.execute("CREATE INDEX idx_email ON users (email);");
        assertTrue(idxCreate.success());

        // Verify index in registry
        assertTrue(engine.getIndexManager().hasIndex("idx_email"));
        List<IndexInfo> indexes = engine.getIndexManager().getIndexesForTable("users");
        assertEquals(1, indexes.size());
        assertEquals("email", indexes.get(0).columnName());

        // Verify lookup on 'bob@test.com' returns row index 1
        List<Integer> matches = engine.getIndexManager().lookup("idx_email", "bob@test.com");
        assertEquals(1, matches.size());
        assertEquals(1, matches.get(0));

        // Lookup non-existent value
        List<Integer> nonExistent = engine.getIndexManager().lookup("idx_email", "guest@test.com");
        assertTrue(nonExistent.isEmpty());
    }

    @Test
    void testQueryOptimizationViaIndex() {
        engine.execute("CREATE INDEX idx_email ON users (email);");

        // SELECT query optimized by index
        QueryResult query = engine.execute("SELECT id FROM users WHERE email = 'charlie@test.com';");
        assertTrue(query.success());
        assertEquals(1, query.rows().size());
        assertEquals(3, query.rows().get(0).get(0)); // id is 3
    }

    @Test
    void testIndexRebuildOnInsert() {
        engine.execute("CREATE INDEX idx_score ON users (score);");

        // Insert new row
        engine.execute("INSERT INTO users VALUES (4, 'dave@test.com', 85.5);");

        // Verify the index now maps score 85.5 to multiple rows (row 1 (Bob) and row 3 (Dave))
        List<Integer> matches = engine.getIndexManager().lookup("idx_score", 85.5);
        assertEquals(2, matches.size());
        assertTrue(matches.contains(1)); // Bob
        assertTrue(matches.contains(3)); // Dave
    }

    @Test
    void testIndexRebuildOnUpdate() {
        engine.execute("CREATE INDEX idx_email ON users (email);");

        // Update email of Bob (id 2) to 'newbob@test.com'
        QueryResult update = engine.execute("UPDATE users SET email = 'newbob@test.com' WHERE id = 2;");
        assertTrue(update.success());

        // Verify old key lookup is empty
        List<Integer> oldMatches = engine.getIndexManager().lookup("idx_email", "bob@test.com");
        assertTrue(oldMatches.isEmpty());

        // Verify new key lookup matches Bob's row index 1
        List<Integer> newMatches = engine.getIndexManager().lookup("idx_email", "newbob@test.com");
        assertEquals(1, newMatches.size());
        assertEquals(1, newMatches.get(0));
    }

    @Test
    void testIndexRebuildOnDelete() {
        engine.execute("CREATE INDEX idx_email ON users (email);");

        // Delete Bob (id 2)
        QueryResult delete = engine.execute("DELETE FROM users WHERE id = 2;");
        assertTrue(delete.success());

        // Verify old key lookup is empty
        List<Integer> oldMatches = engine.getIndexManager().lookup("idx_email", "bob@test.com");
        assertTrue(oldMatches.isEmpty());

        // Verify that Charlie's row position has adjusted from index 2 to index 1 due to storage shift
        List<Integer> charlieMatches = engine.getIndexManager().lookup("idx_email", "charlie@test.com");
        assertEquals(1, charlieMatches.size());
        assertEquals(1, charlieMatches.get(0)); // Now index 1
    }

    @Test
    void testIndexPersistenceAcrossRestart() {
        Path dbPath = tempDir.resolve("restart_idx_db");

        // Set up first engine session
        try (DatabaseEngine session1 = new DatabaseEngine(dbPath.toString())) {
            session1.start();
            session1.execute("CREATE TABLE products (id INT, item VARCHAR(10));");
            session1.execute("INSERT INTO products VALUES (1, 'Disk');");
            session1.execute("INSERT INTO products VALUES (2, 'Flash');");
            
            // Create Index
            QueryResult idx = session1.execute("CREATE INDEX idx_item ON products (item);");
            assertTrue(idx.success());
            session1.stop();
        }

        // Verify index file exists on disk
        Path idxFile = dbPath.resolve("IDX_ITEM.idx");
        assertTrue(Files.exists(idxFile), "Index binary file should persist on disk.");

        // Set up second engine session and rehydrate
        try (DatabaseEngine session2 = new DatabaseEngine(dbPath.toString())) {
            session2.start();

            // Verify index loaded in manager
            assertTrue(session2.getIndexManager().hasIndex("idx_item"));

            // Verify index search lookup matches the record
            List<Integer> matches = session2.getIndexManager().lookup("idx_item", "Flash");
            assertEquals(1, matches.size());
            assertEquals(1, matches.get(0));

            session2.stop();
        }
    }

    @Test
    void testDropIndex() {
        engine.execute("CREATE INDEX idx_email ON users (email);");
        Path idxFile = tempDir.resolve("IDX_EMAIL.idx");

        QueryResult drop = engine.execute("DROP INDEX idx_email;");
        assertTrue(drop.success());

        assertFalse(engine.getIndexManager().hasIndex("idx_email"));
        assertFalse(Files.exists(idxFile), "Index file should be deleted on DROP INDEX.");
    }
}
