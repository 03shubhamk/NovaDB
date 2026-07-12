package com.novadb.storage;

import com.novadb.catalog.CatalogManager;
import com.novadb.catalog.Column;
import com.novadb.catalog.DataType;
import com.novadb.catalog.Schema;
import com.novadb.exception.NovaDBException;

import java.io.*;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Handles database file serialization and deserialization to custom binary (.ndb) files.
 */
public class PersistenceManager {
    private static final int METADATA_MAGIC = 0x4e4f5641; // "NOVA"
    private static final int DATA_MAGIC = 0x4e4f5644;     // "NOVD"
    private static final int FILE_VERSION = 1;

    private final Path dataDirectory;

    public PersistenceManager(Path dataDirectory) {
        this.dataDirectory = dataDirectory;
    }

    /**
     * Serializes all registered table schemas to metadata.ndb.
     */
    public synchronized void persistCatalog(CatalogManager catalog) {
        Path file = dataDirectory.resolve("metadata.ndb");
        try {
            Files.createDirectories(dataDirectory);
            writeAtomic(file, out -> {
                out.writeInt(METADATA_MAGIC);
                out.writeInt(FILE_VERSION);
                Set<String> tableNames = catalog.getTableNames();
                out.writeInt(tableNames.size());
                for (String name : tableNames) {
                    out.writeUTF(name);
                    Schema schema = catalog.getSchema(name);
                    out.writeInt(schema.getColumnCount());
                    for (int i = 0; i < schema.getColumnCount(); i++) {
                        Column col = schema.getColumn(i);
                        out.writeUTF(col.name());
                        out.writeUTF(col.type().name());
                        out.writeInt(col.length() == null ? -1 : col.length());
                    }
                }
            });
        } catch (IOException e) {
            throw new NovaDBException("Failed to persist database catalog metadata: " + e.getMessage(), e);
        }
    }

    /**
     * Parses metadata.ndb and loads registered table schemas.
     */
    public synchronized void loadCatalog(CatalogManager catalog) {
        Path file = dataDirectory.resolve("metadata.ndb");
        if (!Files.exists(file)) {
            return; // Fresh database setup
        }
        catalog.clear();
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(Files.newInputStream(file)))) {
            int magic = in.readInt();
            if (magic != METADATA_MAGIC) {
                throw new NovaDBException("Invalid metadata file magic number.");
            }
            int version = in.readInt();
            if (version != FILE_VERSION) {
                throw new NovaDBException("Unsupported metadata file version: " + version);
            }
            int tableCount = in.readInt();
            for (int t = 0; t < tableCount; t++) {
                String tableName = in.readUTF();
                int colCount = in.readInt();
                List<Column> columns = new ArrayList<>();
                for (int c = 0; c < colCount; c++) {
                    String colName = in.readUTF();
                    String typeName = in.readUTF();
                    int lengthVal = in.readInt();
                    Integer length = lengthVal == -1 ? null : lengthVal;
                    DataType type = DataType.valueOf(typeName);
                    columns.add(new Column(colName, type, length));
                }
                catalog.addTable(tableName, new Schema(columns));
            }
        } catch (IOException | IllegalArgumentException e) {
            throw new NovaDBException("Failed to load database catalog metadata: " + e.getMessage(), e);
        }
    }

    /**
     * Serializes raw table rows to <tableName>.ndb.
     */
    public synchronized void persistTable(String tableName, List<Row> rows, Schema schema) {
        Path file = dataDirectory.resolve(tableName.toUpperCase() + ".ndb");
        try {
            Files.createDirectories(dataDirectory);
            writeAtomic(file, out -> {
                out.writeInt(DATA_MAGIC);
                out.writeInt(FILE_VERSION);
                out.writeInt(rows.size());
                for (Row row : rows) {
                    for (int i = 0; i < schema.getColumnCount(); i++) {
                        Object val = row.getValue(i);
                        Column col = schema.getColumn(i);
                        if (val == null) {
                            out.writeByte(1); // 1 = NULL
                        } else {
                            out.writeByte(0); // 0 = NOT NULL
                            switch (col.type()) {
                                case INT -> out.writeInt((Integer) val);
                                case DOUBLE -> out.writeDouble((Double) val);
                                case BOOLEAN -> out.writeBoolean((Boolean) val);
                                case VARCHAR, TEXT -> out.writeUTF((String) val);
                            }
                        }
                    }
                }
            });
        } catch (IOException e) {
            throw new NovaDBException("Failed to persist table data for '" + tableName + "': " + e.getMessage(), e);
        }
    }

    /**
     * Deserializes table rows from <tableName>.ndb.
     */
    public synchronized List<Row> loadTable(String tableName, Schema schema) {
        Path file = dataDirectory.resolve(tableName.toUpperCase() + ".ndb");
        List<Row> rows = new ArrayList<>();
        if (!Files.exists(file)) {
            return rows;
        }
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(Files.newInputStream(file)))) {
            int magic = in.readInt();
            if (magic != DATA_MAGIC) {
                throw new NovaDBException("Invalid data file magic number for table '" + tableName + "'.");
            }
            int version = in.readInt();
            if (version != FILE_VERSION) {
                throw new NovaDBException("Unsupported data file version: " + version + " for table '" + tableName + "'.");
            }
            int rowCount = in.readInt();
            for (int r = 0; r < rowCount; r++) {
                List<Object> cellValues = new ArrayList<>();
                for (int i = 0; i < schema.getColumnCount(); i++) {
                    byte isNull = in.readByte();
                    if (isNull == 1) {
                        cellValues.add(null);
                    } else {
                        Column col = schema.getColumn(i);
                        Object val = switch (col.type()) {
                            case INT -> in.readInt();
                            case DOUBLE -> in.readDouble();
                            case BOOLEAN -> in.readBoolean();
                            case VARCHAR, TEXT -> in.readUTF();
                        };
                        cellValues.add(val);
                    }
                }
                rows.add(new Row(cellValues));
            }
        } catch (IOException e) {
            throw new NovaDBException("Failed to load table data for '" + tableName + "': " + e.getMessage(), e);
        }
        return rows;
    }

    /**
     * Deletes the table's persistent file from disk.
     */
    public synchronized void deleteTableFile(String tableName) {
        Path file = dataDirectory.resolve(tableName.toUpperCase() + ".ndb");
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            throw new NovaDBException("Failed to delete data file for table '" + tableName + "': " + e.getMessage(), e);
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
