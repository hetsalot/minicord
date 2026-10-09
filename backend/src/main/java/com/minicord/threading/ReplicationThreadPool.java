package com.minicord.threading;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Thread Pool dedicated to inter-node RMI replication.
 * DC Topic 2: Multithreading
 * - Fixed thread pool with custom NamedThreadFactory
 * - Semaphore rate-limiting (max 10 concurrent active RMI calls)
 * - Detailed statistics for /api/admin/threads endpoint
 */
@Component
public class ReplicationThreadPool {
    private static final Logger log = LoggerFactory.getLogger(ReplicationThreadPool.class);

    private final ThreadPoolExecutor executor;
    private final Semaphore rmiSemaphore = new Semaphore(10);
    private final AtomicLong submittedTasks = new AtomicLong(0);

    public ReplicationThreadPool() {
        int corePoolSize = 4;
        int maxPoolSize = 8;
        long keepAliveTime = 60L;

        this.executor = new ThreadPoolExecutor(
                corePoolSize,
                maxPoolSize,
                keepAliveTime,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(200),
                new ThreadFactory() {
                    private final AtomicLong count = new AtomicLong(1);
                    @Override
                    public Thread newThread(Runnable r) {
                        Thread t = new Thread(r, "replication-pool-" + count.getAndIncrement());
                        t.setDaemon(true);
                        return t;
                    }
                },
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }

    public Future<?> submit(Runnable task) {
        submittedTasks.incrementAndGet();
        return executor.submit(() -> {
            try {
                // Rate limit concurrent RMI tasks with Semaphore (Multithreading topic)
                rmiSemaphore.acquire();
                try {
                    task.run();
                } finally {
                    rmiSemaphore.release();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Replication task interrupted", e);
            }
        });
    }

    public int getCoreSize() { return executor.getCorePoolSize(); }
    public int getMaxSize() { return executor.getMaximumPoolSize(); }
    public int getActiveCount() { return executor.getActiveCount(); }
    public int getQueueSize() { return executor.getQueue().size(); }
    public long getCompletedTasks() { return executor.getCompletedTaskCount(); }
    public long getSubmittedTasks() { return submittedTasks.get(); }
    public int getAvailablePermits() { return rmiSemaphore.availablePermits(); }

    @PreDestroy
    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
