package com.novadb.storage;

import com.novadb.DatabaseEngine;
import com.novadb.common.QueryResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.DataInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PersistenceTest {

    @TempDir
    Path tempDir;

    @Test
    void testCatalogAndTablePersistenceAcrossRestarts() {
        Path dbPath = tempDir.resolve("sandbox_db");

        // --- STEP 1: Initialize database, create schema, and insert records ---
        try (DatabaseEngine engine = new DatabaseEngine(dbPath.toString())) {
            engine.start();

            // Create table
            QueryResult create = engine.execute("CREATE TABLE products (id INT, name VARCHAR(20), active BOOLEAN, price DOUBLE);");
            assertTrue(create.success());

            // Insert records (including a NULL name to verify null safety serialization)
            QueryResult ins1 = engine.execute("INSERT INTO products VALUES (10, 'Smartphone', TRUE, 799.99);");
            QueryResult ins2 = engine.execute("INSERT INTO products VALUES (20, NULL, FALSE, 0.00);");
            assertTrue(ins1.success());
            assertTrue(ins2.success());

            // Check in-memory select before shutdown
            QueryResult selectBefore = engine.execute("SELECT id, name, active, price FROM products;");
            assertTrue(selectBefore.success());
            assertEquals(2, selectBefore.rows().size());
            assertEquals(10, selectBefore.rows().get(0).get(0));
            assertEquals("Smartphone", selectBefore.rows().get(0).get(1));
            assertEquals(true, selectBefore.rows().get(0).get(2));
            assertEquals(799.99, selectBefore.rows().get(0).get(3));

            engine.stop();
        }

        // --- STEP 2: Verify binary files were created correctly ---
        Path metadataFile = dbPath.resolve("metadata.ndb");
        Path tableFile = dbPath.resolve("PRODUCTS.ndb");
        assertTrue(Files.exists(metadataFile), "Metadata file should exist.");
        assertTrue(Files.exists(tableFile), "Data file for PRODUCTS should exist.");

        // Read magic numbers to verify format
        try (InputStream in = Files.newInputStream(metadataFile);
             DataInputStream dataIn = new DataInputStream(in)) {
            int magic = dataIn.readInt();
            assertEquals(0x4e4f5641, magic, "Metadata file magic number should match 'NOVA'.");
        } catch (Exception e) {
            fail("Failed to verify metadata magic number: " + e.getMessage());
        }

        try (InputStream in = Files.newInputStream(tableFile);
             DataInputStream dataIn = new DataInputStream(in)) {
            int magic = dataIn.readInt();
            assertEquals(0x4e4f5644, magic, "Data file magic number should match 'NOVD'.");
        } catch (Exception e) {
            fail("Failed to verify table data magic number: " + e.getMessage());
        }

        // --- STEP 3: Rehydrate database from files and verify state ---
        try (DatabaseEngine engine = new DatabaseEngine(dbPath.toString())) {
            engine.start();

            // Verify table and schema exist in catalog
            assertTrue(engine.getCatalog().hasTable("products"));
            assertNotNull(engine.getCatalog().getSchema("products"));

            // Verify records loaded correctly from disk
            QueryResult selectAfter = engine.execute("SELECT id, name, active, price FROM products;");
            assertTrue(selectAfter.success());
            assertEquals(2, selectAfter.rows().size());

            // Row 1
            assertEquals(10, selectAfter.rows().get(0).get(0));
            assertEquals("Smartphone", selectAfter.rows().get(0).get(1));
            assertEquals(true, selectAfter.rows().get(0).get(2));
            assertEquals(799.99, selectAfter.rows().get(0).get(3));

            // Row 2
            assertEquals(20, selectAfter.rows().get(1).get(0));
            assertNull(selectAfter.rows().get(1).get(1)); // Null resolved correctly
            assertEquals(false, selectAfter.rows().get(1).get(2));
            assertEquals(0.00, selectAfter.rows().get(1).get(3));

            engine.stop();
        }
    }

    @Test
    void testFileCleanupOnTableDrop() {
        Path dbPath = tempDir.resolve("drop_db");

        try (DatabaseEngine engine = new DatabaseEngine(dbPath.toString())) {
            engine.start();

            engine.execute("CREATE TABLE temp_table (id INT);");
            engine.execute("INSERT INTO temp_table VALUES (100);");

            Path tableFile = dbPath.resolve("TEMP_TABLE.ndb");
            assertTrue(Files.exists(tableFile));

            // Drop table
            QueryResult drop = engine.execute("DROP TABLE temp_table;");
            assertTrue(drop.success());

            // File should be deleted immediately
            assertFalse(Files.exists(tableFile), "Data file should be deleted on DROP TABLE.");

            engine.stop();
        }
    }
}
