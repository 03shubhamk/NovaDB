package com.novadb.transaction;

import com.novadb.DatabaseEngine;
import com.novadb.common.QueryResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class TransactionTest {
    private DatabaseEngine engine;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        engine = new DatabaseEngine(tempDir.toString());
        engine.start();

        // Seed base table
        engine.execute("CREATE TABLE accounts (id INT, holder VARCHAR(10), balance DOUBLE);");
        engine.execute("INSERT INTO accounts VALUES (1, 'Alice', 100.0);");
        engine.execute("INSERT INTO accounts VALUES (2, 'Bob', 150.0);");
    }

    @AfterEach
    void tearDown() {
        engine.stop();
    }

    @Test
    void testBasicRollbackDML() {
        engine.execute("BEGIN;");
        engine.execute("INSERT INTO accounts VALUES (3, 'Charlie', 200.0);");
        engine.execute("UPDATE accounts SET balance = 50.0 WHERE id = 1;");
        engine.execute("DELETE FROM accounts WHERE id = 2;");

        // Verify changes are visible inside the transaction
        QueryResult selectInside = engine.execute("SELECT * FROM accounts;");
        assertEquals(2, selectInside.rows().size());
        assertEquals("Charlie", selectInside.rows().get(1).get(1)); // Alice & Charlie (Bob deleted)
        assertEquals(50.0, selectInside.rows().get(0).get(2)); // Alice updated to 50.0

        // Execute Rollback
        QueryResult rollback = engine.execute("ROLLBACK;");
        assertTrue(rollback.success());

        // Verify state is restored to original seed
        QueryResult selectAfter = engine.execute("SELECT * FROM accounts ORDER BY id ASC;");
        assertEquals(2, selectAfter.rows().size());
        assertEquals("Alice", selectAfter.rows().get(0).get(1));
        assertEquals(100.0, selectAfter.rows().get(0).get(2)); // Reverted to 100.0
        assertEquals("Bob", selectAfter.rows().get(1).get(1)); // Restored
    }

    @Test
    void testBasicCommitDML() {
        engine.execute("BEGIN;");
        engine.execute("INSERT INTO accounts VALUES (3, 'Charlie', 200.0);");
        engine.execute("UPDATE accounts SET balance = 300.0 WHERE id = 1;");
        
        QueryResult commit = engine.execute("COMMIT;");
        assertTrue(commit.success());

        // Verify changes persist
        QueryResult select = engine.execute("SELECT * FROM accounts ORDER BY id ASC;");
        assertEquals(3, select.rows().size());
        assertEquals(300.0, select.rows().get(0).get(2)); // Alice is 300.0
        assertEquals("Charlie", select.rows().get(2).get(1));
    }

    @Test
    void testRollbackDDL() {
        engine.execute("BEGIN;");
        engine.execute("CREATE TABLE orders (order_id INT, amount DOUBLE);");
        assertTrue(engine.getCatalog().hasTable("orders"));

        engine.execute("ROLLBACK;");
        assertFalse(engine.getCatalog().hasTable("orders"));
    }

    @Test
    void testRollbackDropTable() {
        engine.execute("BEGIN;");
        engine.execute("DROP TABLE accounts;");
        assertFalse(engine.getCatalog().hasTable("accounts"));

        engine.execute("ROLLBACK;");
        assertTrue(engine.getCatalog().hasTable("accounts"));
        QueryResult select = engine.execute("SELECT * FROM accounts;");
        assertEquals(2, select.rows().size());
    }

    @Test
    void testRollbackIndexOperations() {
        // Create an index inside transaction, then roll back
        engine.execute("BEGIN;");
        engine.execute("CREATE INDEX idx_holder ON accounts (holder);");
        assertTrue(engine.getIndexManager().hasIndex("idx_holder"));

        engine.execute("ROLLBACK;");
        assertFalse(engine.getIndexManager().hasIndex("idx_holder"));
        assertFalse(Files.exists(tempDir.resolve("IDX_HOLDER.idx")), "Index file should not have written to disk.");
    }

    @Test
    void testIsolationOnFilesystem() {
        Path tableFile = tempDir.resolve("ACCOUNTS.ndb");

        long originalSize = 0;
        try {
            originalSize = Files.size(tableFile);
        } catch (Exception e) {
            fail("Accounts table file should exist on disk.");
        }

        // Perform inserts inside transaction
        engine.execute("BEGIN;");
        engine.execute("INSERT INTO accounts VALUES (3, 'Dave', 400.0);");
        engine.execute("INSERT INTO accounts VALUES (4, 'Eve', 500.0);");

        // Verify that even though rows exist in memory, the file size on disk is unchanged (no write flushes occurred!)
        try {
            assertEquals(originalSize, Files.size(tableFile), "Data file should not update on disk during active transaction.");
        } catch (Exception e) {
            fail("Failed to read file size.");
        }

        engine.execute("COMMIT;");

        // Verify files have updated on commit
        try {
            assertTrue(Files.size(tableFile) > originalSize, "Data file should expand on disk after commit flush.");
        } catch (Exception e) {
            fail("Failed to verify file size post commit.");
        }
    }

    @Test
    void testNestedTransactionErrors() {
        QueryResult r1 = engine.execute("BEGIN;");
        assertTrue(r1.success());

        // Nested BEGIN should fail
        QueryResult r2 = engine.execute("BEGIN;");
        assertFalse(r2.success());
        assertTrue(r2.message().contains("already active"));

        engine.execute("ROLLBACK;");
    }

    @Test
    void testInvalidTransactionCommands() {
        // COMMIT without active transaction should fail
        QueryResult r1 = engine.execute("COMMIT;");
        assertFalse(r1.success());
        assertTrue(r1.message().contains("No active transaction"));

        // ROLLBACK without active transaction should fail
        QueryResult r2 = engine.execute("ROLLBACK;");
        assertFalse(r2.success());
        assertTrue(r2.message().contains("No active transaction"));
    }

    @Test
    void testCommitPersistenceAcrossRestart() {
        Path dbPath = tempDir.resolve("restart_txn_db");

        try (DatabaseEngine session1 = new DatabaseEngine(dbPath.toString())) {
            session1.start();
            session1.execute("CREATE TABLE logs (msg VARCHAR(100));");
            
            session1.execute("BEGIN;");
            session1.execute("INSERT INTO logs VALUES ('Txn test message');");
            session1.execute("COMMIT;");
            session1.stop();
        }

        // Restart and verify load
        try (DatabaseEngine session2 = new DatabaseEngine(dbPath.toString())) {
            session2.start();
            QueryResult select = session2.execute("SELECT * FROM logs;");
            assertTrue(select.success());
            assertEquals(1, select.rows().size());
            assertEquals("Txn test message", select.rows().get(0).get(0));
            session2.stop();
        }
    }
}
