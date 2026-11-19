package quizengine.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Immutable generic container holding successfully loaded domain records
 * alongside human-readable diagnostics for any malformed or skipped entries.
 *
 * @param <T> type of domain record loaded (e.g., {@link Question} or {@link Score})
 */
public class LoadResult<T> {

    private final List<T> records;
    private final List<String> diagnostics;

    public LoadResult(List<T> records, List<String> diagnostics) {
        this.records = Collections.unmodifiableList(
                records == null ? Collections.emptyList() : new ArrayList<>(records));
        this.diagnostics = Collections.unmodifiableList(
                diagnostics == null ? Collections.emptyList() : new ArrayList<>(diagnostics));
    }

    public List<T> getRecords() {
        return records;
    }

    public List<String> getDiagnostics() {
        return diagnostics;
    }

    public boolean hasDiagnostics() {
        return !diagnostics.isEmpty();
    }

    public boolean isEmpty() {
        return records.isEmpty();
    }
}
