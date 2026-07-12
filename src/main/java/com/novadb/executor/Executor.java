package com.novadb.executor;

import com.novadb.catalog.*;
import com.novadb.common.QueryResult;
import com.novadb.exception.NovaDBException;
import com.novadb.parser.*;
import com.novadb.parser.expression.*;
import com.novadb.index.IndexInfo;
import com.novadb.index.IndexManager;
import com.novadb.parser.CreateIndexStatement;
import com.novadb.parser.DropIndexStatement;
import com.novadb.storage.PersistenceManager;
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
    private final PersistenceManager persistence;
    private final IndexManager indexManager;
    private final com.novadb.transaction.TransactionManager transactionManager;

    /**
     * Initializes the Executor with catalog, storage, persistence, index, and transaction managers.
     */
    public Executor(CatalogManager catalog, StorageManager storage, PersistenceManager persistence, IndexManager indexManager, com.novadb.transaction.TransactionManager transactionManager) {
        this.catalog = catalog;
        this.storage = storage;
        this.persistence = persistence;
        this.indexManager = indexManager;
        this.transactionManager = transactionManager;
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
            } else if (stmt instanceof UpdateStatement updateStmt) {
                return executeUpdate(updateStmt, startTime);
            } else if (stmt instanceof DeleteStatement deleteStmt) {
                return executeDelete(deleteStmt, startTime);
            } else if (stmt instanceof CreateIndexStatement createIdxStmt) {
                return executeCreateIndex(createIdxStmt, startTime);
            } else if (stmt instanceof DropIndexStatement dropIdxStmt) {
                return executeDropIndex(dropIdxStmt, startTime);
            } else if (stmt instanceof TransactionStatement txnStmt) {
                return executeTransaction(txnStmt, startTime);
            } else {
                throw new NovaDBException("Statement type execution not supported: " + stmt.getClass().getSimpleName());
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

        if (!transactionManager.isInTransaction()) {
            persistence.persistCatalog(catalog, indexManager);
            persistence.persistTable(tableName, List.of(), schema);
        }

        long duration = (System.nanoTime() - startTime) / 1_000_000;
        return QueryResult.success("Table '" + tableName + "' created successfully.", duration);
    }

    private QueryResult executeDrop(DropTableStatement stmt, long startTime) {
        String tableName = stmt.tableName();
        
        catalog.dropTable(tableName);
        storage.dropTable(tableName);
        indexManager.deleteTableIndexes(tableName);

        if (!transactionManager.isInTransaction()) {
            persistence.persistCatalog(catalog, indexManager);
            persistence.deleteTableFile(tableName);
        }

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

        indexManager.rebuildTableIndexes(tableName, storage.getRows(tableName), schema);
        if (!transactionManager.isInTransaction()) {
            persistence.persistTable(tableName, storage.getRows(tableName), schema);
        }

        long duration = (System.nanoTime() - startTime) / 1_000_000;
        return QueryResult.success("1 row inserted.", duration);
    }

    private QueryResult executeSelect(SelectStatement stmt, long startTime) {
        String tableName = stmt.tableName();
        Schema schema = catalog.getSchema(tableName);
        List<Row> allRows = storage.getRows(tableName);

        List<Row> filteredRows = new ArrayList<>();
        List<Integer> indexMatches = getIndexMatchRows(stmt.whereClause(), tableName, schema);

        if (indexMatches != null) {
            // Index lookup matches. Fetch only matched row positions
            for (int pos : indexMatches) {
                if (pos >= 0 && pos < allRows.size()) {
                    filteredRows.add(allRows.get(pos));
                }
            }
        } else {
            // Fallback: Full Scan
            for (Row row : allRows) {
                if (stmt.whereClause() != null) {
                    Object condition = ExpressionEvaluator.evaluate(stmt.whereClause(), row, schema);
                    if (Boolean.TRUE.equals(condition)) {
                        filteredRows.add(row);
                    }
                } else {
                    filteredRows.add(row);
                }
            }
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
        for (Row row : filteredRows) {
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

    private QueryResult executeUpdate(UpdateStatement stmt, long startTime) {
        String tableName = stmt.tableName();
        Schema schema = catalog.getSchema(tableName);
        List<Row> allRows = storage.getRows(tableName);

        List<Row> updatedRows = new ArrayList<>();
        int updatedCount = 0;

        for (Row row : allRows) {
            boolean matches = false;
            if (stmt.whereClause() != null) {
                Object condition = ExpressionEvaluator.evaluate(stmt.whereClause(), row, schema);
                matches = Boolean.TRUE.equals(condition);
            } else {
                matches = true;
            }

            if (matches) {
                updatedCount++;
                List<Object> newCellValues = new ArrayList<>(row.values());

                // Evaluate SET assignments using the ORIGINAL row values
                List<Object> evaluatedSetValues = new ArrayList<>();
                for (int i = 0; i < stmt.targetColumns().size(); i++) {
                    Expression setExpr = stmt.values().get(i);
                    Object val = ExpressionEvaluator.evaluate(setExpr, row, schema);
                    evaluatedSetValues.add(val);
                }

                // Apply assignments, validating types
                for (int i = 0; i < stmt.targetColumns().size(); i++) {
                    String colName = stmt.targetColumns().get(i);
                    int colIdx = schema.getColumnIndex(colName);
                    if (colIdx == -1) {
                        throw new NovaDBException("Column '" + colName + "' does not exist in table '" + tableName + "'.");
                    }

                    Object coercedVal = validateAndCoerceType(schema.getColumn(colIdx), evaluatedSetValues.get(i));
                    newCellValues.set(colIdx, coercedVal);
                }

                updatedRows.add(new Row(newCellValues));
            } else {
                updatedRows.add(row);
            }
        }

        // Rewrite updated rows to storage
        storage.dropTable(tableName);
        storage.createTable(tableName);
        for (Row r : updatedRows) {
            storage.insertRow(tableName, r);
        }

        if (!transactionManager.isInTransaction()) {
            persistence.persistTable(tableName, updatedRows, schema);
        }
        indexManager.rebuildTableIndexes(tableName, updatedRows, schema);

        long duration = (System.nanoTime() - startTime) / 1_000_000;
        return QueryResult.success(updatedCount + " rows updated.", duration);
    }

    private QueryResult executeDelete(DeleteStatement stmt, long startTime) {
        String tableName = stmt.tableName();
        Schema schema = catalog.getSchema(tableName);
        List<Row> allRows = storage.getRows(tableName);

        List<Row> remainingRows = new ArrayList<>();
        int deletedCount = 0;

        for (Row row : allRows) {
            boolean matches = false;
            if (stmt.whereClause() != null) {
                Object condition = ExpressionEvaluator.evaluate(stmt.whereClause(), row, schema);
                matches = Boolean.TRUE.equals(condition);
            } else {
                matches = true;
            }

            if (matches) {
                deletedCount++;
            } else {
                remainingRows.add(row);
            }
        }

        // Rewrite remaining rows to storage
        storage.dropTable(tableName);
        storage.createTable(tableName);
        for (Row r : remainingRows) {
            storage.insertRow(tableName, r);
        }

        if (!transactionManager.isInTransaction()) {
            persistence.persistTable(tableName, remainingRows, schema);
        }
        indexManager.rebuildTableIndexes(tableName, remainingRows, schema);

        long duration = (System.nanoTime() - startTime) / 1_000_000;
        return QueryResult.success(deletedCount + " rows deleted.", duration);
    }

    private Object evaluateLiteral(Expression expr) {
        if (!(expr instanceof LiteralExpression)) {
            throw new NovaDBException("Only literal values are supported in Phase 3 INSERT operations.");
        }
        return ((LiteralExpression) expr).value();
    }

    private QueryResult executeCreateIndex(CreateIndexStatement stmt, long startTime) {
        String indexName = stmt.indexName();
        String tableName = stmt.tableName();
        String columnName = stmt.columnName();

        Schema schema = catalog.getSchema(tableName);
        List<Row> rows = storage.getRows(tableName);

        indexManager.createIndex(indexName, tableName, columnName, rows, schema);
        if (!transactionManager.isInTransaction()) {
            persistence.persistCatalog(catalog, indexManager);
        }

        long duration = (System.nanoTime() - startTime) / 1_000_000;
        return QueryResult.success("Index '" + indexName + "' created successfully.", duration);
    }

    private QueryResult executeDropIndex(DropIndexStatement stmt, long startTime) {
        String indexName = stmt.indexName();
        indexManager.dropIndex(indexName);
        if (!transactionManager.isInTransaction()) {
            persistence.persistCatalog(catalog, indexManager);
        }

        long duration = (System.nanoTime() - startTime) / 1_000_000;
        return QueryResult.success("Index '" + indexName + "' dropped successfully.", duration);
    }

    private List<Integer> getIndexMatchRows(Expression whereClause, String tableName, Schema schema) {
        if (!(whereClause instanceof BinaryExpression binaryExpr)) {
            return null;
        }
        if (!binaryExpr.operator().equals("=")) {
            return null;
        }

        String colName = null;
        Object literalVal = null;

        if (binaryExpr.left() instanceof ColumnExpression colExpr && binaryExpr.right() instanceof LiteralExpression litExpr) {
            colName = colExpr.name();
            literalVal = litExpr.value();
        } else if (binaryExpr.right() instanceof ColumnExpression colExpr && binaryExpr.left() instanceof LiteralExpression litExpr) {
            colName = colExpr.name();
            literalVal = litExpr.value();
        }

        if (colName == null) {
            return null;
        }

        List<IndexInfo> idxList = indexManager.getIndexesForTable(tableName);
        for (IndexInfo idx : idxList) {
            if (idx.columnName().equalsIgnoreCase(colName)) {
                return indexManager.lookup(idx.indexName(), literalVal);
            }
        }

        return null;
    }

    private QueryResult executeTransaction(TransactionStatement stmt, long startTime) {
        switch (stmt.type()) {
            case BEGIN -> transactionManager.begin(catalog, storage, indexManager);
            case COMMIT -> transactionManager.commit(catalog, storage, indexManager, persistence);
            case ROLLBACK -> transactionManager.rollback(catalog, storage, indexManager);
        }
        long duration = (System.nanoTime() - startTime) / 1_000_000;
        return QueryResult.success("Transaction command '" + stmt.type() + "' executed successfully.", duration);
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
