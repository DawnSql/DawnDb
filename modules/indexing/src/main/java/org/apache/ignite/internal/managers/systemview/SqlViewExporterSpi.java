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

package org.apache.ignite.internal.managers.systemview;

import org.apache.ignite.IgniteCheckedException;
import org.apache.ignite.internal.GridKernalContext;
import org.apache.ignite.internal.IgniteEx;
import org.apache.ignite.internal.processors.query.h2.IgniteH2Indexing;
import org.apache.ignite.internal.processors.query.h2.SchemaManager;
import org.apache.ignite.spi.IgniteSpiContext;
import org.apache.ignite.spi.IgniteSpiException;
import org.apache.ignite.spi.systemview.view.FiltrableSystemView;
import org.apache.ignite.spi.systemview.view.SystemView;

import static org.apache.ignite.internal.processors.query.QueryUtils.sysSchemaName;

/**
 * This SPI implementation exports metrics as SQL views.
 *
 * Note, instance of this class created with reflection.
 * @see GridSystemViewManager#SYSTEM_VIEW_SQL_SPI
 */
class SqlViewExporterSpi extends AbstractSystemViewExporterSpi {
    /** Schema manager. */
    private SchemaManager mgr;

    /** {@inheritDoc} */
    @Override protected void onContextInitialized0(IgniteSpiContext spiCtx) throws IgniteSpiException {
        GridKernalContext ctx = ((IgniteEx)ignite()).context();

        if (ctx.query().getIndexing() instanceof IgniteH2Indexing) {
            mgr = ((IgniteH2Indexing)ctx.query().getIndexing()).schemaManager();

            sysViewReg.forEach(this::register);
            sysViewReg.addSystemViewCreationListener(this::register);
        }
    }

    /**
     * Registers system view as SQL View.
     *
     * @param sysView System view.
     */
    private void register(SystemView<?> sysView) {
        if (log.isDebugEnabled())
            log.debug("Found new system view [name=" + sysView.name() + ']');

        GridKernalContext ctx = ((IgniteEx)ignite()).context();

        SystemViewLocal<?> view = sysView instanceof FiltrableSystemView ?
            new FiltrableSystemViewLocal<>(ctx, sysView) : new SystemViewLocal<>(ctx, sysView);

        try {
            mgr.createSystemView(sysSchemaName(), view);
        }
        catch (IgniteCheckedException e) {
            throw new IgniteSpiException(e);
        }
    }
}
