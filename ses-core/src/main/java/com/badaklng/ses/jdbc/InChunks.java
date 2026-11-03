package com.badaklng.ses.jdbc;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.function.Function;

/**
 * Splits an id collection into Oracle-safe {@code IN} chunks.
 *
 * <p>Oracle rejects an expression list longer than 1000 entries with
 * ORA-01795, and Spring Data turns {@code findAllById(ids)} /
 * {@code ...In(ids)} into exactly such a list. Two repositories in this
 * codebase already carry a comment warning about the cap
 * ({@code SesApprovalTaskAssigneeRepository}, {@code
 * SesApprovalTaskSignoffRepository}) and leave the chunking to the caller;
 * this is that chunking, in one place, so the warning does not have to be
 * re-implemented per call site.
 *
 * <p>Also de-duplicates and drops nulls, because a list page can easily
 * hold several payments pointing at the same business record.
 */
public final class InChunks {

    /**
     * 1000 is Oracle's hard ceiling; 900 leaves room for the other bind
     * parameters a query may carry alongside the {@code IN} list.
     */
    public static final int MAX = 900;

    private InChunks() {
    }

    /** Distinct, non-null ids in encounter order, split into chunks of at most {@link #MAX}. */
    public static <T> List<List<T>> of(Collection<T> ids) {
        List<List<T>> chunks = new ArrayList<>();
        if (ids == null || ids.isEmpty()) {
            return chunks;
        }
        List<T> distinct = new ArrayList<>(new LinkedHashSet<>(ids));
        distinct.removeIf(id -> id == null);
        for (int i = 0; i < distinct.size(); i += MAX) {
            chunks.add(distinct.subList(i, Math.min(i + MAX, distinct.size())));
        }
        return chunks;
    }

    /**
     * Runs {@code query} once per chunk and concatenates the results — the
     * shape nearly every caller wants.
     */
    public static <T, R> List<R> flatMap(Collection<T> ids, Function<List<T>, ? extends Collection<R>> query) {
        List<R> out = new ArrayList<>();
        for (List<T> chunk : of(ids)) {
            out.addAll(query.apply(chunk));
        }
        return out;
    }

    /** Self-check: run with {@code java InChunks} after changing the bounds above. */
    public static void main(String[] args) {
        assert of(null).isEmpty() : "null -> no chunks";
        assert of(new ArrayList<Long>()).isEmpty() : "empty -> no chunks";

        List<Long> dupes = new ArrayList<>();
        dupes.add(7L);
        dupes.add(7L);
        dupes.add(null);
        dupes.add(9L);
        List<List<Long>> one = of(dupes);
        assert one.size() == 1 : "small input -> single chunk";
        assert one.get(0).size() == 2 : "deduped and null-stripped, got " + one.get(0);

        List<Long> many = new ArrayList<>();
        for (long i = 0; i < MAX * 2 + 1; i++) {
            many.add(i);
        }
        List<List<Long>> split = of(many);
        assert split.size() == 3 : "expected 3 chunks, got " + split.size();
        assert split.get(0).size() == MAX && split.get(2).size() == 1 : "chunk boundaries wrong";
        int total = 0;
        for (List<Long> c : split) {
            assert c.size() <= MAX : "chunk over Oracle's limit";
            total += c.size();
        }
        assert total == many.size() : "lost ids while chunking";

        List<Long> flat = flatMap(many, chunk -> chunk);
        assert flat.size() == many.size() : "flatMap dropped ids";

        System.out.println("InChunks self-check OK (MAX=" + MAX + ")");
    }
}
