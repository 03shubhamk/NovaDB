package com.novadb.cli;

import com.novadb.DatabaseEngine;
import com.novadb.common.QueryResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class NovaDBConsoleTest {

    @TempDir
    Path tempDir;

    @Test
    void testEngineInitialization() {
        Path dbPath = tempDir.resolve("test_db");
        assertFalse(Files.exists(dbPath));

        try (DatabaseEngine engine = new DatabaseEngine(dbPath.toString())) {
            engine.start();
            assertTrue(engine.isActive());
            assertTrue(Files.exists(dbPath));
            assertEquals(dbPath.toAbsolutePath().normalize(), engine.getDataDirectory());

            engine.stop();
            assertFalse(engine.isActive());
        }
    }

    @Test
    void testReplExitCommand() {
        try (DatabaseEngine engine = new DatabaseEngine(tempDir.toString())) {
            engine.start();

            // Simulate typing "exit;"
            String input = "exit;\n";
            ByteArrayInputStream inStream = new ByteArrayInputStream(input.getBytes());
            ByteArrayOutputStream outStream = new ByteArrayOutputStream();
            PrintStream out = new PrintStream(outStream);

            Repl repl = new Repl(engine, inStream, out, out);
            repl.start();

            String output = outStream.toString();
            assertTrue(output.contains("Goodbye."));
        }
    }

    @Test
    void testReplDotExitCommand() {
        try (DatabaseEngine engine = new DatabaseEngine(tempDir.toString())) {
            engine.start();

            // Simulate typing ".exit"
            String input = ".exit\n";
            ByteArrayInputStream inStream = new ByteArrayInputStream(input.getBytes());
            ByteArrayOutputStream outStream = new ByteArrayOutputStream();
            PrintStream out = new PrintStream(outStream);

            Repl repl = new Repl(engine, inStream, out, out);
            repl.start();

            String output = outStream.toString();
            assertTrue(output.contains("Goodbye."));
        }
    }

    @Test
    void testReplHelpCommand() {
        try (DatabaseEngine engine = new DatabaseEngine(tempDir.toString())) {
            engine.start();

            // Simulate typing ".help" then ".exit"
            String input = ".help\n.exit\n";
            ByteArrayInputStream inStream = new ByteArrayInputStream(input.getBytes());
            ByteArrayOutputStream outStream = new ByteArrayOutputStream();
            PrintStream out = new PrintStream(outStream);

            Repl repl = new Repl(engine, inStream, out, out);
            repl.start();

            String output = outStream.toString();
            assertTrue(output.contains("Available commands:"));
            assertTrue(output.contains(".help"));
            assertTrue(output.contains(".exit"));
        }
    }

    @Test
    void testReplMultiLineQuery() {
        try (DatabaseEngine engine = new DatabaseEngine(tempDir.toString())) {
            engine.start();

            // Simulate typing a select query split across lines
            String input = "SELECT\n1\n;\nexit;\n";
            ByteArrayInputStream inStream = new ByteArrayInputStream(input.getBytes());
            ByteArrayOutputStream outStream = new ByteArrayOutputStream();
            PrintStream out = new PrintStream(outStream);

            Repl repl = new Repl(engine, inStream, out, out);
            repl.start();

            String output = outStream.toString();
            assertTrue(output.contains("Mock SELECT execution succeeded."));
        }
    }

    @Test
    void testReplInvalidCommand() {
        try (DatabaseEngine engine = new DatabaseEngine(tempDir.toString())) {
            engine.start();

            // Simulate typing a statement that fails execution
            String input = "CREATE TABLE users;\nexit;\n";
            ByteArrayInputStream inStream = new ByteArrayInputStream(input.getBytes());
            ByteArrayOutputStream outStream = new ByteArrayOutputStream();
            PrintStream out = new PrintStream(outStream);

            Repl repl = new Repl(engine, inStream, out, out);
            repl.start();

            String output = outStream.toString();
            assertTrue(output.contains("Expected '(' before column definitions."));
        }
    }

    @Test
    void testConsoleInteractiveModeExit() {
        // Run CommandLine console in interactive mode with direct standard input redirection
        String input = ".exit\n";
        System.setIn(new ByteArrayInputStream(input.getBytes()));
        
        ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        ByteArrayOutputStream errStream = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        PrintStream originalErr = System.err;
        
        try {
            System.setOut(new PrintStream(outStream));
            System.setErr(new PrintStream(errStream));

            NovaDBConsole console = new NovaDBConsole();
            int exitCode = new CommandLine(console).execute("-d", tempDir.resolve("console_db").toString());
            
            assertEquals(0, exitCode);
            assertTrue(outStream.toString().contains("NovaDB Shell"));
        } finally {
            System.setOut(originalOut);
            System.setErr(originalErr);
            System.setIn(System.in);
        }
    }

    @Test
    void testConsoleNonInteractiveModeSuccess() {
        NovaDBConsole console = new NovaDBConsole();
        CommandLine cmd = new CommandLine(console);
        
        ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        ByteArrayOutputStream errStream = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        PrintStream originalErr = System.err;

        try {
            System.setOut(new PrintStream(outStream));
            System.setErr(new PrintStream(errStream));

            int exitCode = cmd.execute("-d", tempDir.resolve("console_db_direct").toString(), "-q", "SELECT 1");
            
            assertEquals(0, exitCode);
            assertTrue(outStream.toString().contains("Mock SELECT execution succeeded."));
        } finally {
            System.setOut(originalOut);
            System.setErr(originalErr);
        }
    }

    @Test
    void testConsoleNonInteractiveModeFailure() {
        NovaDBConsole console = new NovaDBConsole();
        CommandLine cmd = new CommandLine(console);
        
        ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        ByteArrayOutputStream errStream = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        PrintStream originalErr = System.err;

        try {
            System.setOut(new PrintStream(outStream));
            System.setErr(new PrintStream(errStream));

            int exitCode = cmd.execute("-d", tempDir.resolve("console_db_direct_fail").toString(), "-q", "CREATE TABLE abc");
            
            assertEquals(1, exitCode);
            assertTrue(errStream.toString().contains("Error: Expected '(' before column definitions."));
        } finally {
            System.setOut(originalOut);
            System.setErr(originalErr);
        }
    }
}
