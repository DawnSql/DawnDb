/*
 * Copyright 2019 GridGain Systems, Inc. and Contributors.
 *
 * Licensed under the GridGain Community Edition License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.gridgain.com/products/software/community-edition/gridgain-community-edition-license
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.ignite.internal.processors.schedule;

import it.sauronsoftware.cron4j.Scheduler;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.ignite.IgniteCheckedException;
import org.apache.ignite.internal.GridKernalContext;
import org.apache.ignite.internal.util.GridConcurrentHashSet;
import org.apache.ignite.internal.util.typedef.X;
import org.apache.ignite.lang.IgniteCallable;
import org.apache.ignite.scheduler.SchedulerFuture;
import org.jetbrains.annotations.Nullable;

/**
 * Schedules cron-based execution of grid tasks and closures.
 */
public class IgniteScheduleProcessor extends IgniteScheduleProcessorAdapter {
    /** Cron scheduler. */
    private Scheduler sched;

    /** Schedule futures. */
    //private Set<SchedulerFuture<?>> schedFuts = new GridConcurrentHashSet<>();
    private ConcurrentHashMap<String, SchedulerFuture<?>> schedFuts = new ConcurrentHashMap<String, SchedulerFuture<?>>();

    /**
     * @param ctx Kernal context.
     */
    public IgniteScheduleProcessor(GridKernalContext ctx) {
        super(ctx);
    }

    /** {@inheritDoc} */
    @Override public SchedulerFuture<?> schedule(final String name, final Runnable c, String ptrn) {
        assert c != null;
        assert ptrn != null;

        ScheduleFutureImpl<Object> fut = new ScheduleFutureImpl<>(name, sched, ctx, ptrn);

        fut.schedule(new IgniteCallable<Object>() {
            @Nullable @Override public Object call() {
                c.run();

                return null;
            }
        });

        return fut;
    }

    /** {@inheritDoc} */
    @Override public <R> SchedulerFuture<R> schedule(final String name, Callable<R> c, String pattern) {
        assert c != null;
        assert pattern != null;

        ScheduleFutureImpl<R> fut = new ScheduleFutureImpl<>(name, sched, ctx, pattern);

        fut.schedule(c);

        return fut;
    }

    /**
     *
     * @return Future objects of currently scheduled active(not finished) tasks.
     */
//    public Collection<SchedulerFuture<?>> getScheduledFutures() {
//        return Collections.unmodifiableList(new ArrayList<>(schedFuts));
//    }
    public ConcurrentHashMap<String, SchedulerFuture<?>> getScheduledFutures() {
        return this.schedFuts;
    }

    /**
     * Removes future object from the collection of scheduled futures.
     *
     * @param funName Future object.
     */
//    void onDescheduled(SchedulerFuture<?> fut) {
//        assert fut != null;
//
//        schedFuts.remove(fut);
//    }

    public void onDescheduled(final String funName) {
        schedFuts.remove(funName);
    }

    /**
     * Adds future object to the collection of scheduled futures.
     *
     * @param fut Future object.
     */
//    void onScheduled(SchedulerFuture<?> fut) {
//        assert fut != null;
//
//        schedFuts.add(fut);
//    }
    public void onScheduled(final String funName, final SchedulerFuture<?> fut) {
        assert fut != null;
        schedFuts.put(funName, fut);
    }

    /** {@inheritDoc} */
    @Override public void start() throws IgniteCheckedException {
        sched = new Scheduler();

        if (sched.isStarted() == false)
        {
            sched.start();
            X.println(">>>");
            X.println("定时任务启动！");
            X.println(">>>");
        }
    }

    /** {@inheritDoc} */
    @Override public void stop(boolean cancel) throws IgniteCheckedException {
        if (sched.isStarted())
            sched.stop();

        sched = null;
    }

    /** {@inheritDoc} */
    @Override public void printMemoryStats() {
        X.println(">>>");
        X.println(">>> Schedule processor memory stats [igniteInstanceName=" + ctx.igniteInstanceName() + ']');
        X.println(">>>   schedFutsSize: " + schedFuts.size());
    }
}