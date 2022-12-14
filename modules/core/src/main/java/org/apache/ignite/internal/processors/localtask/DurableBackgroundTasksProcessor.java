/*
 * Copyright 2022 GridGain Systems, Inc. and Contributors.
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

package org.apache.ignite.internal.processors.localtask;

import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import org.apache.ignite.IgniteCheckedException;
import org.apache.ignite.IgniteException;
import org.apache.ignite.cluster.ClusterState;
import org.apache.ignite.configuration.CacheConfiguration;
import org.apache.ignite.internal.GridKernalContext;
import org.apache.ignite.internal.IgniteInternalFuture;
import org.apache.ignite.internal.processors.GridProcessorAdapter;
import org.apache.ignite.internal.processors.cache.persistence.GridCacheDatabaseSharedManager;
import org.apache.ignite.internal.processors.cache.persistence.IgniteCacheDatabaseSharedManager;
import org.apache.ignite.internal.processors.cache.persistence.checkpoint.CheckpointListener;
import org.apache.ignite.internal.processors.cache.persistence.metastorage.MetaStorage;
import org.apache.ignite.internal.processors.cache.persistence.metastorage.MetastorageLifecycleListener;
import org.apache.ignite.internal.processors.cache.persistence.metastorage.ReadOnlyMetastorage;
import org.apache.ignite.internal.processors.cache.persistence.metastorage.ReadWriteMetastorage;
import org.apache.ignite.internal.processors.cache.persistence.metastorage.pendingtask.DurableBackgroundTask;
import org.apache.ignite.internal.processors.cache.persistence.metastorage.pendingtask.DurableBackgroundTaskResult;
import org.apache.ignite.internal.processors.cluster.ChangeGlobalStateFinishMessage;
import org.apache.ignite.internal.processors.cluster.ChangeGlobalStateMessage;
import org.apache.ignite.internal.util.GridBusyLock;
import org.apache.ignite.internal.util.future.GridFutureAdapter;
import org.apache.ignite.internal.util.lang.IgniteThrowableConsumer;
import org.apache.ignite.internal.util.typedef.internal.CU;

import static org.apache.ignite.IgniteSystemProperties.IGNITE_EXECUTE_DURABLE_BACKGROUND_TASKS_ON_NODE_START_OR_ACTIVATE;
import static org.apache.ignite.IgniteSystemProperties.getBoolean;
import static org.apache.ignite.internal.processors.localtask.DurableBackgroundTaskState.State.COMPLETED;
import static org.apache.ignite.internal.processors.localtask.DurableBackgroundTaskState.State.INIT;
import static org.apache.ignite.internal.processors.localtask.DurableBackgroundTaskState.State.PREPARE;
import static org.apache.ignite.internal.processors.localtask.DurableBackgroundTaskState.State.STARTED;

/**
 * Processor that is responsible for durable background tasks that are executed on local node.
 */
public class DurableBackgroundTasksProcessor extends GridProcessorAdapter implements MetastorageLifecycleListener,
    CheckpointListener {
    /** Prefix for metastorage keys for durable background tasks. */
    private static final String TASK_PREFIX = "durable-background-task-";

    /** MetaStorage synchronization mutex. */
    private final Object metaStorageMux = new Object();

    /** Current tasks. Mapping: {@link DurableBackgroundTask#name task name} -> task state. */
    private final ConcurrentMap<String, DurableBackgroundTaskState<?>> tasks = new ConcurrentHashMap<>();

    /** Lock for canceling tasks. */
    private final ReadWriteLock cancelLock = new ReentrantReadWriteLock(true);

    /**
     * Tasks to be removed from the MetaStorage after the end of a checkpoint.
     * Mapping: {@link DurableBackgroundTask#name task name} -> task.
     */
    private final ConcurrentMap<String, DurableBackgroundTask<?>> toRmv = new ConcurrentHashMap<>();

    /** Prohibiting the execution of tasks. Guarded by {@link #cancelLock}. */
    private boolean prohibitionExecTasks = true;

    /** Node stop lock. */
    private final GridBusyLock stopLock = new GridBusyLock();

    /**
     * Constructor.
     *
     * @param ctx Kernal context.
     */
    public DurableBackgroundTasksProcessor(GridKernalContext ctx) {
        super(ctx);
    }

    /** {@inheritDoc} */
    @Override public void start() throws IgniteCheckedException {
        ctx.internalSubscriptionProcessor().registerMetastorageListener(this);
    }

    /** {@inheritDoc} */
    @Override public void onKernalStop(boolean cancel) {
        cancelTasks();

        stopLock.block();
    }

    /** {@inheritDoc} */
    @Override public void onReadyForRead(ReadOnlyMetastorage metastorage) {
        if (!stopLock.enterBusy())
            return;

        try {
            metaStorageOperation(metaStorage -> {
                assert metaStorage != null;

                metaStorage.iterate(
                    TASK_PREFIX,
                    (k, v) -> {
                        DurableBackgroundTask task = ((DurableBackgroundTask<?>)v);
                        DurableBackgroundTask convertedTask = task.convertAfterRestoreIfNeeded();

                        boolean converted = false;

                        if (task != convertedTask) {
                            assert !task.name().equals(convertedTask.name()) :
                                "Duplicate task names [original=" + task.name() +
                                    ", converted=" + convertedTask.name() + ']';

                            GridFutureAdapter<?> outFut = new GridFutureAdapter<>();
                            outFut.onDone();

                            DurableBackgroundTaskState<?> state =
                                new DurableBackgroundTaskState<>(task, outFut, true, false);

                            state.state(COMPLETED);

                            tasks.put(task.name(), state);

                            task = convertedTask;
                            converted = true;
                        }

                        tasks.put(
                            task.name(),
                            new DurableBackgroundTaskState<>(task, new GridFutureAdapter<>(), true, converted)
                        );
                    },
                    true
                );
            });
        }
        finally {
            stopLock.leaveBusy();
        }
    }

    /** {@inheritDoc} */
    @Override public void onReadyForReadWrite(ReadWriteMetastorage metastorage) {
        if (!stopLock.enterBusy())
            return;

        try {
            for (DurableBackgroundTaskState<?> state : tasks.values()) {
                if (state.converted()) {
                    metaStorageOperation(metaStorage -> {
                        assert metaStorage != null;

                        DurableBackgroundTask<?> task = state.task();

                        metaStorage.write(metaStorageKey(state.task()), task);
                    });
                }
            }

            ((GridCacheDatabaseSharedManager)ctx.cache().context().database()).addCheckpointListener(this);
        }
        finally {
            stopLock.leaveBusy();
        }
    }

    /** {@inheritDoc} */
    @Override public void beforeCheckpointBegin(Context ctx) {
        /* No op. */
    }

    /** {@inheritDoc} */
    @Override public void onMarkCheckpointBegin(Context ctx) {
        for (Iterator<DurableBackgroundTaskState<?>> it = tasks.values().iterator(); it.hasNext(); ) {
            DurableBackgroundTaskState<?> taskState = it.next();

            if (taskState.state() == COMPLETED) {
                assert taskState.saved();

                DurableBackgroundTask<?> t = taskState.task();

                toRmv.put(t.name(), t);

                it.remove();
            }
        }
    }

    /** {@inheritDoc} */
    @Override public void onCheckpointBegin(Context ctx) {
        /* No op. */
    }

    /** {@inheritDoc} */
    @Override public void afterCheckpointEnd(Context ctx) {
        if (!stopLock.enterBusy())
            return;

        try {
            for (Iterator<DurableBackgroundTask<?>> it = toRmv.values().iterator(); it.hasNext(); ) {
                DurableBackgroundTask<?> t = it.next();

                metaStorageOperation(metaStorage -> {
                    if (metaStorage != null) {
                        if (!tasks.containsKey(t.name()))
                            metaStorage.remove(metaStorageKey(t));

                        it.remove();
                    }
                });
            }
        }
        finally {
            stopLock.leaveBusy();
        }
    }

    /**
     * Callback at the start of a global state change.
     *
     * @param msg Message for change cluster global state.
     */
    public void onStateChangeStarted(ChangeGlobalStateMessage msg) {
        if (!ClusterState.active(msg.state()))
            cancelTasks();
    }

    /**
     * Callback on finish of a global state change.
     *
     * @param msg Finish message for change cluster global state.
     */
    public void onStateChangeFinish(ChangeGlobalStateFinishMessage msg) {
        if (ClusterState.active(msg.state()))
            activateTasks();
    }

    /** {@inheritDoc} */
    @Override public void onKernalStart(boolean active) throws IgniteCheckedException {
        if (active)
            activateTasks();
    }

    /**
     * Asynchronous execution of a durable background task.
     *
     * A new task will be added for execution either if there is no task with
     * the same {@link DurableBackgroundTask#name name} or it (previous) will be completed.
     *
     * If the task is required to be completed after restarting the node,
     * then it must be saved to the MetaStorage.
     *
     * If the task is saved to the Metastorage, then it will be deleted from it
     * only after its completion and at the end of the checkpoint. Otherwise, it
     * will be removed as soon as it is completed.
     *
     * @param task Durable background task.
     * @param save Save task to MetaStorage.
     * @return Futures that will complete when the task is completed.
     */
    public <R> IgniteInternalFuture<R> executeAsync(DurableBackgroundTask<R> task, boolean save) {
        if (!stopLock.enterBusy())
            throw new IgniteException("Node is stopping.");

        try {
            DurableBackgroundTaskState<?> taskState = tasks.compute(
                task.name(),
                (taskName, prev) -> {
                    if (prev != null && prev.state() != COMPLETED) {
                        throw new IllegalArgumentException("Task is already present and has not been completed: " +
                            taskName);
                    }

                    return new DurableBackgroundTaskState<>(task, new GridFutureAdapter<>(), save, false);
                });

            if (save) {
                metaStorageOperation(metaStorage -> {
                    if (metaStorage != null)
                        metaStorage.write(metaStorageKey(task), task);
                });
            }

            executeAsync0(task);

            return (IgniteInternalFuture<R>)taskState.outFuture();
        }
        finally {
            stopLock.leaveBusy();
        }
    }

    /**
     * Overloading the {@link #executeAsync(DurableBackgroundTask, boolean)}.
     * If task is applied to persistent cache, saves it to MetaStorage.
     *
     * @param t Durable background task.
     * @param cacheCfg Cache configuration.
     * @return Futures that will complete when the task is completed.
     */
    public <R> IgniteInternalFuture<R> executeAsync(DurableBackgroundTask<R> t, CacheConfiguration cacheCfg) {
        return executeAsync(t, CU.isPersistentCache(cacheCfg, ctx.config().getDataStorageConfiguration()));
    }

    /**
     * Asynchronous execution of a durable background task.
     *
     * @param t Durable background task.
     */
    private void executeAsync0(DurableBackgroundTask<?> t) {
        cancelLock.readLock().lock();

        try {
            if (!prohibitionExecTasks) {
                DurableBackgroundTaskState<?> taskState = tasks.get(t.name());

                if (taskState != null && taskState.state(INIT, PREPARE)) {
                    if (log.isInfoEnabled())
                        log.info("Executing durable background task: " + t.name());

                    t.executeAsync(ctx).listen(f -> {
                        DurableBackgroundTaskResult<?> res = null;

                        try {
                            res = f.get();
                        }
                        catch (Throwable e) {
                            log.error("Task completed with an error: " + t.name(), e);
                        }

                        assert res != null;

                        if (res.error() != null)
                            log.error("Could not execute durable background task: " + t.name(), res.error());

                        if (res.completed()) {
                            if (res.error() == null && log.isInfoEnabled())
                                log.info("Execution of durable background task completed: " + t.name());

                            if (taskState.saved())
                                taskState.state(COMPLETED);
                            else
                                tasks.remove(t.name());

                            GridFutureAdapter<Object> outFut = (GridFutureAdapter<Object>)taskState.outFuture();
                            outFut.onDone(res.result(), res.error());
                        }
                        else {
                            assert res.restart();

                            if (log.isInfoEnabled())
                                log.info("Execution of durable background task will be restarted: " + t.name());

                            taskState.state(INIT);
                        }
                    });

                    taskState.state(PREPARE, STARTED);
                }
            }
        }
        finally {
            cancelLock.readLock().unlock();
        }
    }

    /**
     * Prohibit the execution of tasks and cancel tasks.
     */
    private void cancelTasks() {
        cancelLock.writeLock().lock();

        try {
            prohibitionExecTasks = true;

            if (executeTasksOnNodeStartOrActivate()) {
                for (DurableBackgroundTaskState<?> taskState : tasks.values())
                    taskState.task().cancel();
            }
        }
        finally {
            cancelLock.writeLock().unlock();
        }
    }

    /**
     * Allow the execution of tasks and activate tasks.
     */
    private void activateTasks() {
        cancelLock.writeLock().lock();

        try {
            prohibitionExecTasks = false;

            for (DurableBackgroundTaskState<?> taskState : tasks.values())
                executeAsync0(taskState.task());
        }
        finally {
            cancelLock.writeLock().unlock();
        }
    }

    /**
     * Performing an operation on a {@link MetaStorage}.
     * Guarded by {@link #metaStorageMux}.
     *
     * @param consumer MetaStorage operation, argument can be {@code null}.
     * @throws IgniteException If an exception is thrown from the {@code consumer}.
     */
    private void metaStorageOperation(IgniteThrowableConsumer<MetaStorage> consumer) throws IgniteException {
        synchronized (metaStorageMux) {
            IgniteCacheDatabaseSharedManager dbMgr = ctx.cache().context().database();

            dbMgr.checkpointReadLock();

            try {
                MetaStorage metaStorage = dbMgr.metaStorage();

                consumer.accept(metaStorage);
            }
            catch (IgniteCheckedException e) {
                throw new IgniteException(e);
            }
            finally {
                dbMgr.checkpointReadUnlock();
            }
        }
    }

    /**
     * Getting the task key for the MetaStorage.
     *
     * @param t Durable background task.
     * @return MetaStorage {@code t} key.
     */
    static String metaStorageKey(DurableBackgroundTask<?> t) {
        return TASK_PREFIX + t.name();
    }

    /**
     * @return Whether execute background tasks on node start or cluster activate, {@code true} by default.
     */
    private static boolean executeTasksOnNodeStartOrActivate() {
        return getBoolean(IGNITE_EXECUTE_DURABLE_BACKGROUND_TASKS_ON_NODE_START_OR_ACTIVATE, true);
    }
}
