package uk.gov.hmcts.cp.bulkresulting.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class BoundedParallelTest {

    private static final Integer FAILING_ITEM = 2;

    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    @AfterEach
    void close() {
        executor.close();
    }

    @Test
    void map_should_keep_order_and_never_exceed_the_bound() {
        final AtomicInteger inFlight = new AtomicInteger();
        final AtomicInteger peak = new AtomicInteger();
        final BoundedParallel parallel = new BoundedParallel(executor, 2);

        final List<Integer> results = parallel.map(List.of(1, 2, 3, 4, 5, 6), item -> {
            peak.accumulateAndGet(inFlight.incrementAndGet(), Math::max);
            try {
                Thread.sleep(20);
            } catch (final InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
            inFlight.decrementAndGet();
            return item * 10;
        });

        assertThat(results).containsExactly(10, 20, 30, 40, 50, 60);
        assertThat(peak.get()).isLessThanOrEqualTo(2);
    }

    @Test
    void map_should_rethrow_the_failure_unchanged() {
        final BoundedParallel parallel = new BoundedParallel(executor, 3);

        assertThatThrownBy(() -> parallel.map(List.of(1, 2), item -> {
            if (FAILING_ITEM.equals(item)) {
                throw new InvalidFilterException("boom");
            }
            return item;
        })).isInstanceOf(InvalidFilterException.class).hasMessage("boom");
    }
}
