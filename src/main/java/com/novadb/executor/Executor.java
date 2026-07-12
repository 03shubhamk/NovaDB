package com.novadb.executor;

import com.novadb.catalog.*;
import com.novadb.common.QueryResult;
import com.novadb.exception.NovaDBException;
import com.novadb.parser.*;
import com.novadb.parser.expression.*;
import com.novadb.storage.Row;
import com.novadb.storage.StorageManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Executes parsed AST statements against the catalog and storage systems,
 * returning QueryResult instances.
 */
public class Executor {
    private final CatalogManager catalog;
    private final StorageManager storage;

    /**
     * Initializes the Executor with catalog and storage managers.
     */
    public Executor(CatalogManager catalog, StorageManager storage) {
        this.catalog = catalog;
        this.storage = storage;
    }

    /**
     * Executes the given SQL statement.
     * 
     * @param stmt The parsed AST statement.
     * @return Result of the statement execution.
     */
    public QueryResult execute(Statement stmt) {
        long startTime = System.nanoTime();

        try {
            if (stmt instanceof CreateTableStatement createStmt) {
                return executeCreate(createStmt, startTime);
            } else if (stmt instanceof DropTableStatement dropStmt) {
                return executeDrop(dropStmt, startTime);
            } else if (stmt instanceof InsertStatement insertStmt) {
                return executeInsert(insertStmt, startTime);
            } else if (stmt instanceof SelectStatement selectStmt) {
                return executeSelect(selectStmt, startTime);
            } else if (stmt instanceof TransactionStatement txnStmt) {
                // Transactions will be implemented in Phase 7. Return mock success for now.
                long duration = (System.nanoTime() - startTime) / 1_000_000;
                return QueryResult.success("Transaction command '" + txnStmt.type() + "' received (Transactions unimplemented).", duration);
            } else {
                throw new NovaDBException("Statement type execution not supported in Phase 3: " + stmt.getClass().getSimpleName());
            }
        } catch (Exception e) {
            long duration = (System.nanoTime() - startTime) / 1_000_000;
            if (e instanceof NovaDBException) {
                return QueryResult.failure(e.getMessage(), duration);
            }
            return QueryResult.failure("Execution error: " + e.getMessage(), duration);
        }
    }

    private QueryResult executeCreate(CreateTableStatement stmt, long startTime) {
        String tableName = stmt.tableName();
        
        List<Column> columns = new ArrayList<>();
        for (ColumnDefinition colDef : stmt.columns()) {
            DataType type = DataType.fromString(colDef.dataType());
            columns.add(new Column(colDef.name(), type, colDef.length()));
        }

        Schema schema = new Schema(columns);
        catalog.addTable(tableName, schema);
        storage.createTable(tableName);

        long duration = (System.nanoTime() - startTime) / 1_000_000;
        return QueryResult.success("Table '" + tableName + "' created successfully.", duration);
    }

    private QueryResult executeDrop(DropTableStatement stmt, long startTime) {
        String tableName = stmt.tableName();
        
        catalog.dropTable(tableName);
        storage.dropTable(tableName);

        long duration = (System.nanoTime() - startTime) / 1_000_000;
        return QueryResult.success("Table '" + tableName + "' dropped successfully.", duration);
    }

    private QueryResult executeInsert(InsertStatement stmt, long startTime) {
        String tableName = stmt.tableName();
        Schema schema = catalog.getSchema(tableName);

        List<Expression> exprValues = stmt.values();
        List<Object> rowValues = new ArrayList<>();

        if (stmt.columns() != null && !stmt.columns().isEmpty()) {
            List<String> insertCols = stmt.columns();
            if (insertCols.size() != exprValues.size()) {
                throw new NovaDBException("Column count does not match value count. Columns: " + insertCols.size() + ", Values: " + exprValues.size());
            }

            // Initialize row values with nulls
            for (int i = 0; i < schema.getColumnCount(); i++) {
                rowValues.add(null);
            }

            for (int i = 0; i < insertCols.size(); i++) {
                String colName = insertCols.get(i);
                int schemaIdx = schema.getColumnIndex(colName);
                if (schemaIdx == -1) {
                    throw new NovaDBException("Column '" + colName + "' does not exist in table '" + tableName + "'");
                }

                Object evaluatedVal = evaluateLiteral(exprValues.get(i));
                Object typeMatchedVal = validateAndCoerceType(schema.getColumn(schemaIdx), evaluatedVal);
                rowValues.set(schemaIdx, typeMatchedVal);
            }
        } else {
            // Positional mapping
            if (schema.getColumnCount() != exprValues.size()) {
                throw new NovaDBException("Column count does not match value count. Expected: " + schema.getColumnCount() + ", Received: " + exprValues.size());
            }

            for (int i = 0; i < schema.getColumnCount(); i++) {
                Object evaluatedVal = evaluateLiteral(exprValues.get(i));
                Object typeMatchedVal = validateAndCoerceType(schema.getColumn(i), evaluatedVal);
                rowValues.add(typeMatchedVal);
            }
        }

        Row newRow = new Row(rowValues);
        storage.insertRow(tableName, newRow);

        long duration = (System.nanoTime() - startTime) / 1_000_000;
        return QueryResult.success("1 row inserted.", duration);
    }

    private QueryResult executeSelect(SelectStatement stmt, long startTime) {
        String tableName = stmt.tableName();
        Schema schema = catalog.getSchema(tableName);
        List<Row> allRows = storage.getRows(tableName);

        if (stmt.whereClause() != null) {
            throw new NovaDBException("WHERE clause filter execution is not implemented in Phase 3 (scheduled for Phase 4).");
        }

        // Resolve projection columns and indices
        List<String> projCols = stmt.projection();
        List<String> outputHeaders = new ArrayList<>();
        List<Integer> targetIndices = new ArrayList<>();

        if (projCols.size() == 1 && projCols.get(0).equals("*")) {
            for (int i = 0; i < schema.getColumnCount(); i++) {
                outputHeaders.add(schema.getColumn(i).name());
                targetIndices.add(i);
            }
        } else {
            for (String colName : projCols) {
                int schemaIdx = schema.getColumnIndex(colName);
                if (schemaIdx == -1) {
                    throw new NovaDBException("Column '" + colName + "' does not exist in table '" + tableName + "'");
                }
                outputHeaders.add(schema.getColumn(schemaIdx).name());
                targetIndices.add(schemaIdx);
            }
        }

        // Map data rows
        List<List<Object>> resultRows = new ArrayList<>();
        for (Row row : allRows) {
            List<Object> projectedCells = new ArrayList<>();
            for (int idx : targetIndices) {
                projectedCells.add(row.getValue(idx));
            }
            resultRows.add(projectedCells);
        }

        // Apply LIMIT slicing if specified
        if (stmt.limit() != null) {
            int limitCount = stmt.limit();
            if (limitCount < 0) {
                throw new NovaDBException("LIMIT count cannot be negative.");
            }
            int sliceEnd = Math.min(limitCount, resultRows.size());
            resultRows = resultRows.subList(0, sliceEnd);
        }

        long duration = (System.nanoTime() - startTime) / 1_000_000;
        return QueryResult.query(outputHeaders, resultRows, duration);
    }

    private Object evaluateLiteral(Expression expr) {
        if (!(expr instanceof LiteralExpression)) {
            throw new NovaDBException("Only literal values are supported in Phase 3 INSERT operations.");
        }
        return ((LiteralExpression) expr).value();
    }

    private Object validateAndCoerceType(Column column, Object value) {
        if (value == null) {
            return null; // All columns support null in this version
        }

        switch (column.type()) {
            case INT -> {
                if (value instanceof Integer) {
                    return value;
                }
                throw new NovaDBException("Type mismatch: Expected INT value for column '" + column.name() + "', found " + value.getClass().getSimpleName() + ".");
            }
            case DOUBLE -> {
                if (value instanceof Double) {
                    return value;
                }
                if (value instanceof Integer) {
                    return ((Integer) value).doubleValue(); // Coerce Integer to Double
                }
                throw new NovaDBException("Type mismatch: Expected DOUBLE value for column '" + column.name() + "', found " + value.getClass().getSimpleName() + ".");
            }
            case VARCHAR -> {
                if (value instanceof String str) {
                    if (column.length() != null && str.length() > column.length()) {
                        throw new NovaDBException("Value exceeds length constraint of column '" + column.name() + "' (max length: " + column.length() + ").");
                    }
                    return value;
                }
                throw new NovaDBException("Type mismatch: Expected VARCHAR value for column '" + column.name() + "', found " + value.getClass().getSimpleName() + ".");
            }
            case TEXT -> {
                if (value instanceof String) {
                    return value;
                }
                throw new NovaDBException("Type mismatch: Expected TEXT value for column '" + column.name() + "', found " + value.getClass().getSimpleName() + ".");
            }
            case BOOLEAN -> {
                if (value instanceof Boolean) {
                    return value;
                }
                throw new NovaDBException("Type mismatch: Expected BOOLEAN value for column '" + column.name() + "', found " + value.getClass().getSimpleName() + ".");
            }
            default -> throw new NovaDBException("Unsupported column type: " + column.type());
        }
    }
}
