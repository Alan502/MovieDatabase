package edu.macalester.tagrelatedness;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Shilad Sen
 * Utilities to run for each loops in parallel.
 * Slightly modified for this project's purposes.
 */


public class ParallelForEach {
    public static final Logger LOG = Logger.getLogger(ParallelForEach.class.getName());

    /**
     * Construct a parallel loop on [from, to).
     *
     * @param from bottom of range (inclusive)
     * @param to top of range (exclusive)
     * @param numThreads
     * @param fn callback
     */
    public static void range(int from, int to, int numThreads, final Procedure<Integer> fn) {
        List<Integer> range = new ArrayList<Integer>();
        for (int i = from; i < to; i++) { range.add(i); }
        loop(range, numThreads, fn);
    }
    public static <T,R> List<R> range(int from, int to, int numThreads, final Function<Integer, R> fn) {
        List<Integer> range = new ArrayList<Integer>();
        for (int i = from; i < to; i++) { range.add(i); }
        return loop(range, numThreads, fn);
    }

    public static <T,R> List<R> loop(
            Collection<T> collection,
            int numThreads,
            final Function<T,R> fn) {
        return loop(collection, numThreads, fn, 50);
    }

    public static <T> void loop(
            Collection<T> collection,
            int numThreads,
            final Procedure<T> fn) {
        loop(collection, numThreads, fn, 50);
    }
    public static <T> void loop(
            Collection<T> collection,
            int numThreads,
            final Procedure<T> fn,
            final int logModulo) {
        loop(collection, numThreads, new Function<T, Object> () {
            public Object call(T arg) throws Exception {
                fn.call(arg);
                return null;
            }
        }, logModulo);
    }

    public static <T,R> List<R> loop(
            Collection<T> collection,
            int numThreads,
            final Function<T,R> fn,
            final int logModulo) {

        final List<R> result = new ArrayList<R>();
        for (int i = 0; i < collection.size(); i++) result.add(null);
        final ExecutorService exec = new ThreadPoolErrors(numThreads);
        final CountDownLatch latch = new CountDownLatch(collection.size());
        try {
            // create a copy so that modifications to original list are safe
            final List<T> asList = new ArrayList<T>(collection);
            for (int i = 0; i < asList.size(); i++) {
                final int finalI = i;
                exec.submit(new Runnable() {
                    public void run() {
                        T obj = asList.get(finalI);                       
                        try {
                            if (finalI % logModulo == 0) {
                                LOG.info("processing list element " + (finalI+1) + " of " + asList.size());
                            }
                            R r = fn.call(obj);
                            result.set(finalI, r);
                        } catch (Exception e) {
                            LOG.log(Level.SEVERE, "error processing list element " + obj, e);
                        } finally {
                            latch.countDown();
                        }
                    }});
            }
            latch.await();
            return result;
        } catch (InterruptedException e) {
            LOG.log(Level.SEVERE, "Interrupted parallel for each", e);
            throw new RuntimeException(e);
        } finally {
            exec.shutdown();
        }
    }

    public static <T> void iterate(
            Iterator<T> iterator,
            int numThreads,
            int queueSize,
            final Procedure<T> fn,
            final int logModulo) {

        final ExecutorService exec = new ThreadPoolErrors(numThreads);
        BoundedExecutor boundedExec = new BoundedExecutor(exec, queueSize);
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicInteger elemsToGo = new AtomicInteger(0);

        int i = 0;
        try {
            // create a copy so that modifications to original list are safe
            elemsToGo.incrementAndGet();
            while (iterator.hasNext()) {
                if (i++ % logModulo == 0) {
                    LOG.info("processing iterable " + i);
                }
                final T obj = iterator.next();
                elemsToGo.incrementAndGet();
                boundedExec.submitTask(new Runnable() {
                    public void run() {
                        try {
                            fn.call(obj);
                        } catch (Exception e) {
                            LOG.log(Level.SEVERE, "error processing list element " + obj, e);
                        } finally {
                            if (elemsToGo.decrementAndGet() == 0) {
                                latch.countDown();
                            }
                        }
                    }
                });
            }
            if (elemsToGo.decrementAndGet() > 0) {
                latch.await();
            }
        } catch (InterruptedException e) {
            LOG.log(Level.SEVERE, "Interrupted parallel for each", e);
            throw new RuntimeException(e);
        } finally {
            exec.shutdown();
        }
    }



    /**
     * This code adapted from:
     * http://stackoverflow.com/questions/2248131/handling-exceptions-from-java-executorservice-tasks
     */
    private static class ThreadPoolErrors extends ThreadPoolExecutor {
        public ThreadPoolErrors(int threads) {
            super(  threads, // core threads
                    threads, // max threads
                    0, // timeout
                    TimeUnit.MILLISECONDS, // timeout units
                    new LinkedBlockingQueue<Runnable>() // work queue
            );
        }

        protected void afterExecute(Runnable r, Throwable t) {
            super.afterExecute(r, t);
            if (t == null && r instanceof Future<?>) {
                try {
                    Future<?> future = (Future<?>) r;
                    if (future.isDone()) {
                        future.get();
                    }
                } catch (CancellationException ce) {
                    t = ce;
                } catch (ExecutionException ee) {
                    t = ee.getCause();
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt(); // ignore/reset
                }
            }
            if (t != null) {
                LOG.log(Level.SEVERE, "Uncaught Exception: ", t);
            }
        }
    }

    /**
     * From
     */
    public static class BoundedExecutor {
        private final Executor exec;
        private final Semaphore semaphore;

        public BoundedExecutor(Executor exec, int bound) {
            this.exec = exec;
            this.semaphore = new Semaphore(bound);
        }

        public void submitTask(final Runnable command)
                throws InterruptedException, RejectedExecutionException {
            semaphore.acquire();
            try {
                exec.execute(new Runnable() {
                    public void run() {
                        try {
                            command.run();
                        } finally {
                            semaphore.release();
                        }
                    }
                });
            } catch (RejectedExecutionException e) {
                semaphore.release();
                throw e;
            }
        }
    }
}