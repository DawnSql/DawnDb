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

package org.apache.ignite.internal.processors;

import org.apache.ignite.IgniteCheckedException;
import org.apache.ignite.IgniteLogger;
import org.apache.ignite.cluster.ClusterNode;
import org.apache.ignite.internal.GridKernalContext;
import org.apache.ignite.internal.IgniteInternalFuture;
import org.apache.ignite.internal.util.tostring.GridToStringExclude;
import org.apache.ignite.internal.util.typedef.internal.S;
import org.apache.ignite.lang.IgniteFuture;
import org.apache.ignite.spi.IgniteNodeValidationResult;
import org.apache.ignite.spi.discovery.DiscoveryDataBag;
import org.apache.ignite.spi.discovery.DiscoveryDataBag.GridDiscoveryData;
import org.apache.ignite.spi.discovery.DiscoveryDataBag.JoiningNodeDiscoveryData;
import org.jetbrains.annotations.Nullable;

/**
 * Advanced parent adapter for all processor.
 */
public abstract class GridProcessorAdapter implements GridProcessor {
    /** */
    private static final String DIAGNOSTIC_LOG_CATEGORY = "org.apache.ignite.internal.diagnostic";

    /** Kernal context. */
    @GridToStringExclude
    protected final GridKernalContext ctx;

    /** Grid logger. */
    @GridToStringExclude
    protected final IgniteLogger log;

    /** Diagnostic logger. */
    @GridToStringExclude
    protected final IgniteLogger diagnosticLog;

    /**
     * @param ctx Kernal context.
     */
    protected GridProcessorAdapter(GridKernalContext ctx) {
        assert ctx != null;

        this.ctx = ctx;

        log = ctx.log(getClass());

        diagnosticLog = ctx.log(DIAGNOSTIC_LOG_CATEGORY);
    }

    /** {@inheritDoc} */
    @Override public void onKernalStart(boolean active) throws IgniteCheckedException {
        // No-op.
    }

    /** {@inheritDoc} */
    @Override public void onKernalStop(boolean cancel) {
        // No-op.
    }

    /** {@inheritDoc} */
    @Override public void onDisconnected(IgniteFuture<?> reconnectFut) throws IgniteCheckedException {
        // No-op.
    }

    /** {@inheritDoc} */
    @Override public IgniteInternalFuture<?> onReconnected(boolean clusterRestarted) throws IgniteCheckedException {
        return null;
    }

    /** {@inheritDoc} */
    @Nullable @Override public DiscoveryDataExchangeType discoveryDataType() {
        return null;
    }

    /** {@inheritDoc} */
    @Override public void collectJoiningNodeData(DiscoveryDataBag dataBag) {
        // No-op.
    }

    /** {@inheritDoc} */
    @Override public void collectGridNodeData(DiscoveryDataBag dataBag) {
        // No-op.
    }

    /** {@inheritDoc} */
    @Override public void onGridDataReceived(GridDiscoveryData data) {
        // No-op.
    }

    /** {@inheritDoc} */
    @Override public void onJoiningNodeDataReceived(JoiningNodeDiscoveryData data) {
        // No-op.
    }

    /** {@inheritDoc} */
    @Override public void start() throws IgniteCheckedException {
        // No-op.
    }

    /** {@inheritDoc} */
    @Override public void stop(boolean cancel) throws IgniteCheckedException {
        // No-op.
    }

    /** {@inheritDoc} */
    @Override public void printMemoryStats() {
        // No-op.
    }

    /** {@inheritDoc} */
    @Nullable @Override public IgniteNodeValidationResult validateNode(ClusterNode node) {
        return null;
    }

    /** {@inheritDoc} */
    @Nullable @Override public IgniteNodeValidationResult validateNode(ClusterNode node, DiscoveryDataBag.JoiningNodeDiscoveryData discoData) {
        return null;
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridProcessorAdapter.class, this);
    }
}
