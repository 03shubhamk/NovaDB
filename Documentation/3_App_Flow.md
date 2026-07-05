# NovaDB – Application Flow

User
→ CLI
→ Lexer
→ SQL Parser
→ Query Planner
→ Query Executor
→ Storage Engine
→ Response

Example:
INSERT
→ Validate
→ Parse
→ Locate Table
→ Write Record
→ Update Index
→ Success

SELECT
→ Parse
→ Apply WHERE
→ Use Index (if available)
→ Read Records
→ Return Results
