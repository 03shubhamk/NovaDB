package com.novadb.storage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Represents a single record row inside a database table.
 * 
 * @param values Ordered list of column values.
 */
public record Row(List<Object> values) {
    public Row {
        if (values == null) {
            throw new IllegalArgumentException("Row values cannot be null.");
        }
        // Create an unmodifiable copy allowing null values
        values = Collections.unmodifiableList(new ArrayList<>(values));
    }

    /**
     * Gets the value of the cell at the specified column index.
     */
    public Object getValue(int index) {
        return values.get(index);
    }
}
