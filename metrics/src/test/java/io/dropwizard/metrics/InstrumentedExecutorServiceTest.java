/*
 * Copyright 2010-2013 Coda Hale and Yammer, Inc., 2014-2017 Dropwizard Team
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * You may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.dropwizard.metrics;

import org.junit.After;
import org.junit.Test;

import io.dropwizard.metrics.Counter;
import io.dropwizard.metrics.InstrumentedExecutorService;
import io.dropwizard.metrics.Meter;
import io.dropwizard.metrics.MetricRegistry;
import io.dropwizard.metrics.Timer;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.failBecauseExceptionWasNotThrown;

public class InstrumentedExecutorServiceTest {
    private ExecutorService executor;
    private final MetricRegistry registry = new MetricRegistry();

    @Test
    public void reportsTasksInformation() throws Exception {
        this.executor = Executors.newCachedThreadPool();
        final InstrumentedExecutorService instrumentedExecutorService = new InstrumentedExecutorService(executor, registry, "xs");
        final Meter submitted = registry.meter("xs.submitted");
        final Counter running = registry.counter("xs.running");
        final Meter completed = registry.meter("xs.completed");
        final Timer duration = registry.timer("xs.duration");
        final Meter rejected = registry.meter("xs.rejected");

        assertThat(submitted.getCount()).isEqualTo(0);
        assertThat(running.getCount()).isEqualTo(0);
        assertThat(completed.getCount()).isEqualTo(0);
        assertThat(duration.getCount()).isEqualTo(0);
        assertThat(rejected.getCount()).isEqualTo(0);

        Future<?> theFuture = instrumentedExecutorService.submit(() -> {
            assertThat(submitted.getCount()).isEqualTo(1);
            assertThat(running.getCount()).isEqualTo(1);
            assertThat(completed.getCount()).isEqualTo(0);
            assertThat(duration.getCount()).isEqualTo(0);
            assertThat(rejected.getCount()).isEqualTo(0);
    });

        theFuture.get();

        assertThat(submitted.getCount()).isEqualTo(1);
        assertThat(running.getCount()).isEqualTo(0);
        assertThat(completed.getCount()).isEqualTo(1);
        assertThat(duration.getCount()).isEqualTo(1);
        assertThat(duration.getSnapshot().size()).isEqualTo(1);
        assertThat(rejected.getCount()).isEqualTo(0);
    }

    @Test
    public void reportsRejected() throws Exception {
        final BlockingQueue<Runnable> queueCapacityOne = new LinkedBlockingQueue<>(1);
        this.executor = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, queueCapacityOne);
        final InstrumentedExecutorService instrumented = new InstrumentedExecutorService(executor, registry, "r");
        final CountDownLatch finish = new CountDownLatch(1);

        final Meter submitted = registry.meter("r.submitted");
        final Counter running = registry.counter("r.running");
        final Meter completed = registry.meter("r.completed");
        final Timer duration = registry.timer("r.duration");
        final Meter rejected = registry.meter("r.rejected");

        assertThat(submitted.getCount()).isEqualTo(0);
        assertThat(running.getCount()).isEqualTo(0);
        assertThat(completed.getCount()).isEqualTo(0);
        assertThat(duration.getCount()).isEqualTo(0);
        assertThat(rejected.getCount()).isEqualTo(0);

        final List<Future<Object>> futures = new ArrayList<>();
        // Start two callables - one to run on thread and one to be added to queue
        for (int i = 0; i < 2; i++) {
            futures.add(instrumented.submit(() -> {
                finish.await();
                return null;
            }));
        }
        try {
            // Attempt to submit third callable - should fail
            instrumented.submit(() -> {
                throw new IllegalStateException("Shouldn't run this task");
            });
            failBecauseExceptionWasNotThrown(RejectedExecutionException.class);
        } catch (RejectedExecutionException e) {
            // Expected
        } finally {
            finish.countDown();
            for (Future future : futures) {
                future.get();
            }
        }

        assertThat(submitted.getCount()).isEqualTo(3);
        assertThat(running.getCount()).isEqualTo(0);
        assertThat(completed.getCount()).isEqualTo(2);
        assertThat(duration.getCount()).isEqualTo(2);
        assertThat(rejected.getCount()).isEqualTo(1);
    }

    @After
    public void tearDown() throws Exception {
        if (executor != null) {
            executor.shutdown();
            if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
                System.err.println("InstrumentedExecutorService did not terminate.");
            }
        }
    }

}
