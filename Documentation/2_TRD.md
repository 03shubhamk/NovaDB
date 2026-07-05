# NovaDB – Technical Requirements Document (TRD)

## Tech Stack
- Java 21
- Maven
- JUnit 5
- Picocli

## Modules
- Lexer
- SQL Parser
- Query Executor
- Storage Engine
- Catalog Manager
- Index Manager
- Transaction Manager
- CLI

## Storage
Custom binary .ndb files with metadata and table files.

## Indexing
Version 1: Hash Index
Version 2: B+ Tree

## Error Handling
Custom exceptions with descriptive messages.

## Testing
Unit tests for every module.
Integration tests for SQL execution.
