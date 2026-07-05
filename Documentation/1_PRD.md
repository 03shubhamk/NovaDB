# NovaDB – Product Requirements Document (PRD)

## Vision
NovaDB is a lightweight SQL database engine built from scratch for learning how relational databases work internally.

## Goals
- Execute basic SQL commands.
- Persist data to disk.
- Demonstrate parser, storage engine, indexing, and transactions.

## Target Users
- Students
- Recruiters reviewing portfolio projects
- Developers learning DB internals

## MVP Features
- CREATE TABLE
- DROP TABLE
- INSERT
- SELECT
- UPDATE
- DELETE
- WHERE
- ORDER BY
- LIMIT
- Transactions (BEGIN, COMMIT, ROLLBACK)
- Hash Index
- CLI

## Non-functional Requirements
- Java 21
- Modular architecture
- Unit tested
- Cross-platform
- Persistent storage

## Success Criteria
- SQL execution works correctly.
- Data persists after restart.
- Clean architecture and documentation.
