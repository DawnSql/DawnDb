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

package org.apache.ignite.internal.processors.cluster.baseline.autoadjust;

import org.apache.ignite.IgniteLogger;
import org.apache.ignite.internal.GridKernalContext;
import org.apache.ignite.internal.IgniteFeatures;
import org.apache.ignite.internal.cluster.DistributedBaselineConfiguration;
import org.apache.ignite.internal.cluster.IgniteClusterImpl;
import org.apache.ignite.internal.managers.discovery.GridDiscoveryManager;
import org.apache.ignite.internal.processors.affinity.AffinityTopologyVersion;
import org.apache.ignite.internal.processors.cache.GridCachePartitionExchangeManager;
import org.apache.ignite.internal.processors.cluster.GridClusterStateProcessor;
import org.apache.ignite.internal.util.typedef.internal.CU;

import static org.apache.ignite.internal.IgniteFeatures.BASELINE_AUTO_ADJUSTMENT;
import static org.apache.ignite.internal.processors.cluster.baseline.autoadjust.BaselineAutoAdjustData.NULL_BASELINE_DATA;
import static org.apache.ignite.internal.util.IgniteUtils.isLocalNodeCoordinator;

/**
 * Baseline topology updater with ability to watch of topology changes.
 * It initiates update to set new baseline after some timeout.
 */
public class BaselineTopologyUpdater {
    /** */
    private final IgniteLogger log;

    /** */
    private final IgniteClusterImpl cluster;

    /** */
    private final GridCachePartitionExchangeManager<?, ?> exchangeManager;

    /** Configuration of baseline. */
    private final DistributedBaselineConfiguration baselineConfiguration;

    /** Discovery manager. */
    private final GridDiscoveryManager discoveryMgr;

    /** */
    private final GridClusterStateProcessor stateProcessor;

    /** Scheduler of specific task of baseline changing. */
    private final BaselineAutoAdjustScheduler baselineAutoAdjustScheduler;

    /** Context.*/
    private final GridKernalContext ctx;

    /** Last data for set new baseline. */
    private BaselineAutoAdjustData lastBaselineData = NULL_BASELINE_DATA;

    /**
     * @param ctx Context.
     */
    public BaselineTopologyUpdater(GridKernalContext ctx) {
        this.log = ctx.log(BaselineTopologyUpdater.class);
        this.cluster = ctx.cluster().get();
        this.baselineConfiguration = ctx.state().baselineConfiguration();
        this.exchangeManager = ctx.cache().context().exchange();
        this.stateProcessor = ctx.state();
        this.baselineAutoAdjustScheduler = new BaselineAutoAdjustScheduler(ctx.timeout(), new BaselineAutoAdjustExecutor(
            ctx.log(BaselineAutoAdjustExecutor.class),
            cluster,
            ctx.pools().getSystemExecutorService(),
            this::isTopologyWatcherEnabled
        ), ctx.log(BaselineAutoAdjustScheduler.class));
        this.discoveryMgr = ctx.discovery();
        this.ctx = ctx;
    }

    /**
     * Schedule update of the baseline topology
     * @param topologyVersion version of topology
     */
    public void triggerBaselineUpdate(long topologyVersion) {
        if (!isTopologyWatcherEnabled()) {
            synchronized (this) {
                lastBaselineData = NULL_BASELINE_DATA;
            }

            return;
        }

        synchronized (this) {
            lastBaselineData = lastBaselineData.next(topologyVersion);

            final BaselineAutoAdjustData baselineData = lastBaselineData;

            if (isLocalNodeCoordinator(discoveryMgr)) {
                exchangeManager.affinityReadyFuture(new AffinityTopologyVersion(topologyVersion))
                    .listen(future -> {
                        if (future.error() != null)
                            return;

                        if (exchangeManager.lastFinishedFuture().hasLostPartitions()) {
                            log.warning("Baseline won't be changed cause lost partitions were detected");

                            return;
                        }

                        long timeout = baselineConfiguration.getBaselineAutoAdjustTimeout();

                        // In case of merging exchanges the baseline data can be already expired
                        // and so it should be rejected by scheduler.
                        if (baselineAutoAdjustScheduler.schedule(baselineData, timeout))
                            log.warning("Baseline auto-adjust will be executed in '" + timeout + "' ms");
                    });
            }
        }
    }

    /**
     * @return {@code true} if auto-adjust baseline enabled.
     */
    private boolean isTopologyWatcherEnabled() {
        return isSupported(ctx)
            && !ctx.clientNode()
            && stateProcessor.clusterState().active()
            && baselineConfiguration.isBaselineAutoAdjustEnabled()
            && (CU.isPersistenceEnabled(cluster.ignite().configuration()) || cluster.baselineAutoAdjustTimeout() != 0L);
    }

    /**
     * @return {@code True} if all nodes in the cluster support auto-adjust baseline.
     * @see IgniteFeatures#BASELINE_AUTO_ADJUSTMENT
     */
    public static boolean isSupported(GridKernalContext ctx) {
        return IgniteFeatures.allNodesSupport(ctx, BASELINE_AUTO_ADJUSTMENT);
    }

    /**
     * @return Statistic of baseline auto-adjust.
     */
    public BaselineAutoAdjustStatus getStatus() {
        synchronized (this) {
            if (lastBaselineData.isAdjusted() || baselineAutoAdjustScheduler.isExecutionExpired(lastBaselineData))
                return BaselineAutoAdjustStatus.notScheduled();

            long timeToLastTask = baselineAutoAdjustScheduler.lastScheduledTaskTime();

            if (timeToLastTask <= 0)
                return BaselineAutoAdjustStatus.inProgress();

            return BaselineAutoAdjustStatus.scheduled(timeToLastTask);
        }
    }
}
