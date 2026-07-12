package com.novadb.index;

import com.novadb.catalog.Column;
import com.novadb.catalog.Schema;
import com.novadb.exception.NovaDBException;
import com.novadb.storage.Row;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * Coordinates in-memory active Hash Indexes and their binary file persistence (.idx files).
 */
public class IndexManager {
    private static final int INDEX_MAGIC = 0x4e4f5649; // "NOVI"
    private static final int FILE_VERSION = 1;

    private final Path dataDirectory;

    // IndexName (Uppercase) -> Key -> List of 0-based matching row positions
    private final Map<String, Map<Object, List<Integer>>> indexes = new HashMap<>();
    
    // IndexName (Uppercase) -> Metadata details
    private final Map<String, IndexInfo> indexCatalog = new HashMap<>();

    // TableName (Uppercase) -> List of indexes registered on that table
    private final Map<String, List<IndexInfo>> tableIndexes = new HashMap<>();

    private com.novadb.transaction.TransactionManager transactionManager;

    public IndexManager(Path dataDirectory) {
        this.dataDirectory = dataDirectory;
    }

    public synchronized void setTransactionManager(com.novadb.transaction.TransactionManager transactionManager) {
        this.transactionManager = transactionManager;
    }

    /**
     * Creates and builds a new Hash Index on the given table and column.
     */
    public synchronized void createIndex(String indexName, String tableName, String columnName, List<Row> rows, Schema schema) {
        if (indexName == null || indexName.trim().isEmpty()) {
            throw new IllegalArgumentException("Index name cannot be null or empty.");
        }
        String idxKey = indexName.toUpperCase();
        if (indexCatalog.containsKey(idxKey)) {
            throw new NovaDBException("Index already exists: " + indexName);
        }

        int colIdx = schema.getColumnIndex(columnName);
        if (colIdx == -1) {
            throw new NovaDBException("Column '" + columnName + "' does not exist in table '" + tableName + "'.");
        }

        IndexInfo info = new IndexInfo(indexName, tableName, columnName);
        
        // Build in-memory map
        Map<Object, List<Integer>> indexMap = new HashMap<>();
        for (int i = 0; i < rows.size(); i++) {
            Object val = rows.get(i).getValue(colIdx);
            indexMap.computeIfAbsent(val, k -> new ArrayList<>()).add(i);
        }

        // Register in catalog
        indexCatalog.put(idxKey, info);
        indexes.put(idxKey, indexMap);
        tableIndexes.computeIfAbsent(tableName.toUpperCase(), k -> new ArrayList<>()).add(info);

        // Serialize to disk
        persistIndex(info, indexMap, schema.getColumn(colIdx));
    }

    /**
     * Looks up row indices matching a key value in the given index.
     */
    public synchronized List<Integer> lookup(String indexName, Object value) {
        if (indexName == null) return List.of();
        Map<Object, List<Integer>> indexMap = indexes.get(indexName.toUpperCase());
        if (indexMap == null) {
            throw new NovaDBException("Index not initialized: " + indexName);
        }
        
        // Handle numeric key coercion for lookups
        if (value instanceof Number numberVal) {
            // Find key match using double comparisons
            double lookupDouble = numberVal.doubleValue();
            for (Map.Entry<Object, List<Integer>> entry : indexMap.entrySet()) {
                if (entry.getKey() instanceof Number numKey) {
                    if (numKey.doubleValue() == lookupDouble) {
                        return Collections.unmodifiableList(entry.getValue());
                    }
                }
            }
        }

        List<Integer> matches = indexMap.get(value);
        if (matches == null) {
            return List.of();
        }
        return Collections.unmodifiableList(matches);
    }

    /**
     * Drops and cleans up the index.
     */
    public synchronized void dropIndex(String indexName) {
        if (indexName == null) return;
        String idxKey = indexName.toUpperCase();
        IndexInfo info = indexCatalog.remove(idxKey);
        if (info == null) {
            throw new NovaDBException("Index not found: " + indexName);
        }

        indexes.remove(idxKey);
        List<IndexInfo> tList = tableIndexes.get(info.tableName().toUpperCase());
        if (tList != null) {
            tList.removeIf(idx -> idx.indexName().equalsIgnoreCase(indexName));
        }

        // Delete from disk
        if (transactionManager == null || !transactionManager.isInTransaction()) {
            Path file = dataDirectory.resolve(idxKey + ".idx");
            try {
                Files.deleteIfExists(file);
            } catch (IOException e) {
                throw new NovaDBException("Failed to delete index file: " + file, e);
            }
        }
    }

    /**
     * Rebuilds all indexes active on the table after a table data update/delete rewrite.
     */
    public synchronized void rebuildTableIndexes(String tableName, List<Row> rows, Schema schema) {
        List<IndexInfo> idxList = tableIndexes.get(tableName.toUpperCase());
        if (idxList == null || idxList.isEmpty()) {
            return;
        }

        for (IndexInfo info : idxList) {
            String idxKey = info.indexName().toUpperCase();
            int colIdx = schema.getColumnIndex(info.columnName());
            if (colIdx == -1) continue;

            Map<Object, List<Integer>> indexMap = new HashMap<>();
            for (int i = 0; i < rows.size(); i++) {
                Object val = rows.get(i).getValue(colIdx);
                indexMap.computeIfAbsent(val, k -> new ArrayList<>()).add(i);
            }

            indexes.put(idxKey, indexMap);
            persistIndex(info, indexMap, schema.getColumn(colIdx));
        }
    }

    /**
     * Loads index from disk, or rebuilds it if file does not exist.
     */
    public synchronized void loadOrRebuildIndex(IndexInfo info, List<Row> rows, Schema schema) {
        String idxKey = info.indexName().toUpperCase();
        int colIdx = schema.getColumnIndex(info.columnName());
        if (colIdx == -1) {
            throw new NovaDBException("Failed to load index '" + info.indexName() + "': column '" + info.columnName() + "' not found.");
        }

        Path file = dataDirectory.resolve(idxKey + ".idx");
        Map<Object, List<Integer>> indexMap = new HashMap<>();

        if (Files.exists(file)) {
            // Read from disk
            try (DataInputStream in = new DataInputStream(new BufferedInputStream(Files.newInputStream(file)))) {
                int magic = in.readInt();
                if (magic != INDEX_MAGIC) {
                    throw new NovaDBException("Invalid index magic number.");
                }
                int version = in.readInt();
                if (version != FILE_VERSION) {
                    throw new NovaDBException("Unsupported index file version: " + version);
                }
                in.readUTF(); // Skip table name in check
                in.readUTF(); // Skip column name in check
                
                int entryCount = in.readInt();
                for (int e = 0; e < entryCount; e++) {
                    byte isNull = in.readByte();
                    Object key = null;
                    if (isNull == 0) {
                        key = switch (schema.getColumn(colIdx).type()) {
                            case INT -> in.readInt();
                            case DOUBLE -> in.readDouble();
                            case BOOLEAN -> in.readBoolean();
                            case VARCHAR, TEXT -> in.readUTF();
                        };
                    }
                    int valCount = in.readInt();
                    List<Integer> positions = new ArrayList<>();
                    for (int v = 0; v < valCount; v++) {
                        positions.add(in.readInt());
                    }
                    indexMap.put(key, positions);
                }
            } catch (IOException ex) {
                // Fail-safe: Rebuild if file read fails
                indexMap = buildIndexMap(rows, colIdx);
                persistIndex(info, indexMap, schema.getColumn(colIdx));
            }
        } else {
            // File does not exist, build and write
            indexMap = buildIndexMap(rows, colIdx);
            persistIndex(info, indexMap, schema.getColumn(colIdx));
        }

        // Register
        indexCatalog.put(idxKey, info);
        indexes.put(idxKey, indexMap);
        List<IndexInfo> currentList = tableIndexes.computeIfAbsent(info.tableName().toUpperCase(), k -> new ArrayList<>());
        boolean exists = false;
        for (IndexInfo registered : currentList) {
            if (registered.indexName().equalsIgnoreCase(info.indexName())) {
                exists = true;
                break;
            }
        }
        if (!exists) {
            currentList.add(info);
        }
    }

    public synchronized boolean hasIndex(String indexName) {
        if (indexName == null) return false;
        return indexCatalog.containsKey(indexName.toUpperCase());
    }

    public synchronized List<IndexInfo> getAllIndexes() {
        return new ArrayList<>(indexCatalog.values());
    }

    public synchronized List<IndexInfo> getIndexesForTable(String tableName) {
        List<IndexInfo> list = tableIndexes.get(tableName.toUpperCase());
        return list == null ? List.of() : Collections.unmodifiableList(list);
    }

    public synchronized void deleteTableIndexes(String tableName) {
        List<IndexInfo> list = tableIndexes.remove(tableName.toUpperCase());
        if (list != null) {
            for (IndexInfo info : list) {
                indexCatalog.remove(info.indexName().toUpperCase());
                indexes.remove(info.indexName().toUpperCase());
                if (transactionManager == null || !transactionManager.isInTransaction()) {
                    Path file = dataDirectory.resolve(info.indexName().toUpperCase() + ".idx");
                    try {
                        Files.deleteIfExists(file);
                    } catch (IOException e) {
                        // Ignore, drop table cleanup
                    }
                }
            }
        }
    }

    public synchronized void registerIndexInfo(IndexInfo info) {
        String idxKey = info.indexName().toUpperCase();
        indexCatalog.put(idxKey, info);
        if (!tableIndexes.containsKey(info.tableName().toUpperCase())) {
            tableIndexes.put(info.tableName().toUpperCase(), new ArrayList<>());
        }
        // Avoid duplicate metadata registrations
        List<IndexInfo> currentList = tableIndexes.get(info.tableName().toUpperCase());
        boolean exists = false;
        for (IndexInfo registered : currentList) {
            if (registered.indexName().equalsIgnoreCase(info.indexName())) {
                exists = true;
                break;
            }
        }
        if (!exists) {
            currentList.add(info);
        }
    }

    public synchronized void clear() {
        indexes.clear();
        indexCatalog.clear();
        tableIndexes.clear();
    }

    public static class IndexSnapshot {
        public final Map<String, Map<Object, List<Integer>>> indexes;
        public final Map<String, IndexInfo> indexCatalog;
        public final Map<String, List<IndexInfo>> tableIndexes;

        public IndexSnapshot(
            Map<String, Map<Object, List<Integer>>> indexes,
            Map<String, IndexInfo> indexCatalog,
            Map<String, List<IndexInfo>> tableIndexes
        ) {
            this.indexes = indexes;
            this.indexCatalog = indexCatalog;
            this.tableIndexes = tableIndexes;
        }
    }

    public synchronized IndexSnapshot getSnapshot() {
        Map<String, Map<Object, List<Integer>>> indexesSnap = new HashMap<>();
        for (Map.Entry<String, Map<Object, List<Integer>>> entry : indexes.entrySet()) {
            Map<Object, List<Integer>> mapCopy = new HashMap<>();
            for (Map.Entry<Object, List<Integer>> subEntry : entry.getValue().entrySet()) {
                mapCopy.put(subEntry.getKey(), new ArrayList<>(subEntry.getValue()));
            }
            indexesSnap.put(entry.getKey(), mapCopy);
        }

        Map<String, IndexInfo> catalogSnap = new HashMap<>(indexCatalog);
        
        Map<String, List<IndexInfo>> tableIndexesSnap = new HashMap<>();
        for (Map.Entry<String, List<IndexInfo>> entry : tableIndexes.entrySet()) {
            tableIndexesSnap.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        }

        return new IndexSnapshot(indexesSnap, catalogSnap, tableIndexesSnap);
    }

    public synchronized void restoreSnapshot(IndexSnapshot snap) {
        indexes.clear();
        indexes.putAll(snap.indexes);
        indexCatalog.clear();
        indexCatalog.putAll(snap.indexCatalog);
        tableIndexes.clear();
        tableIndexes.putAll(snap.tableIndexes);
    }

    private Map<Object, List<Integer>> buildIndexMap(List<Row> rows, int colIdx) {
        Map<Object, List<Integer>> map = new HashMap<>();
        for (int i = 0; i < rows.size(); i++) {
            Object val = rows.get(i).getValue(colIdx);
            map.computeIfAbsent(val, k -> new ArrayList<>()).add(i);
        }
        return map;
    }

    private void persistIndex(IndexInfo info, Map<Object, List<Integer>> indexMap, Column col) {
        if (transactionManager != null && transactionManager.isInTransaction()) {
            return; // Skip writing index files to disk during transaction
        }
        Path file = dataDirectory.resolve(info.indexName().toUpperCase() + ".idx");
        try {
            Files.createDirectories(dataDirectory);
            writeAtomic(file, out -> {
                out.writeInt(INDEX_MAGIC);
                out.writeInt(FILE_VERSION);
                out.writeUTF(info.tableName());
                out.writeUTF(info.columnName());
                out.writeInt(indexMap.size());
                for (Map.Entry<Object, List<Integer>> entry : indexMap.entrySet()) {
                    Object key = entry.getKey();
                    if (key == null) {
                        out.writeByte(1); // 1 = NULL key
                    } else {
                        out.writeByte(0); // 0 = NOT NULL key
                        switch (col.type()) {
                            case INT -> out.writeInt((Integer) key);
                            case DOUBLE -> out.writeDouble((Double) key);
                            case BOOLEAN -> out.writeBoolean((Boolean) key);
                            case VARCHAR, TEXT -> out.writeUTF((String) key);
                        }
                    }
                    List<Integer> positions = entry.getValue();
                    out.writeInt(positions.size());
                    for (int pos : positions) {
                        out.writeInt(pos);
                    }
                }
            });
        } catch (IOException e) {
            throw new NovaDBException("Failed to persist index file for '" + info.indexName() + "': " + e.getMessage(), e);
        }
    }

    private void writeAtomic(Path file, WriteOperation op) throws IOException {
        Path tmp = file.resolveSibling(file.getFileName().toString() + ".tmp");
        try {
            try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(tmp)))) {
                op.write(out);
            }
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    interface WriteOperation {
        void write(DataOutputStream out) throws IOException;
    }
}
