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

package org.apache.ignite.internal.processors.query.h2.sys.view;

import java.util.Iterator;
import org.gridgain.internal.h2.engine.Session;
import org.gridgain.internal.h2.result.Row;
import org.gridgain.internal.h2.result.SearchRow;
import org.gridgain.internal.h2.table.Column;

/**
 * SQL system view.
 */
public interface SqlSystemView {
    /**
     * Gets table name.
     */
    public String getTableName();

    /**
     * Gets description.
     */
    public String getDescription();

    /**
     * Gets columns.
     */
    public Column[] getColumns();

    /**
     * Gets indexed column names.
     */
    public String[] getIndexes();

    /**
     * Gets view content.
     *
     * @param ses Session.
     * @param first First.
     * @param last Last.
     */
    public Iterator<Row> getRows(Session ses, SearchRow first, SearchRow last);

    /**
     * Gets row count for this view (or approximated row count, if real value can't be calculated quickly).
     */
    public long getRowCount();

    /**
     * Gets approximated row count (required to build execution plan).
     */
    public long getRowCountApproximation();

    /**
     * Check if the row count can be retrieved quickly.
     *
     * @return true if it can
     */
    public boolean canGetRowCount();

    /**
     * Gets SQL script for creating table.
     */
    public String getCreateSQL();

    /**
     * @return {@code True} if view is distributed.
     */
    public boolean isDistributed();
}
