package uk.gov.hmcts.cp.bulkresulting.search;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Semaphore;
import java.util.function.Function;

/**
 * Runs one call per item with at most a fixed number in flight, and returns the results in the
 * items' order. The first failure is rethrown unchanged.
 */
public class BoundedParallel {

    private final ExecutorService executor;
    private final int parallelism;

    /**
     * Creates the runner.
     *
     * @param executor    the executor the calls run on
     * @param parallelism the most calls in flight at once
     */
    public BoundedParallel(final ExecutorService executor, final int parallelism) {
        this.executor = executor;
        this.parallelism = parallelism;
    }

    /**
     * Maps every item.
     *
     * @param items the items
     * @param call  the call for one item
     * @param <T>   the item type
     * @param <R>   the result type
     * @return the results, in the items' order
     */
    public <T, R> List<R> map(final List<T> items, final Function<T, R> call) {
        final Semaphore permits = new Semaphore(parallelism);
        final List<CompletableFuture<R>> futures = new ArrayList<>(items.size());
        for (final T item : items) {
            futures.add(CompletableFuture.supplyAsync(() -> withPermit(permits, () -> call.apply(item)), executor));
        }
        final List<R> results = new ArrayList<>(futures.size());
        try {
            for (final CompletableFuture<R> future : futures) {
                results.add(future.join());
            }
        } catch (final CompletionException failure) {
            futures.forEach(future -> future.cancel(true));
            throw unwrap(failure);
        }
        return results;
    }

    private static <R> R withPermit(final Semaphore permits, final java.util.function.Supplier<R> call) {
        try {
            permits.acquire();
        } catch (final InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while waiting to call a dependency", interrupted);
        }
        try {
            return call.get();
        } finally {
            permits.release();
        }
    }

    private static RuntimeException unwrap(final CompletionException failure) {
        final Throwable cause = failure.getCause();
        return cause instanceof RuntimeException runtime ? runtime : failure;
    }
}
