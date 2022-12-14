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

package org.apache.ignite.internal.processors.query.h2.opt;

import org.gridgain.internal.h2.api.TableEngine;
import org.gridgain.internal.h2.command.ddl.CreateTableData;
import org.gridgain.internal.h2.table.PageStoreTable;
import org.gridgain.internal.h2.table.Table;

/**
 * Default table engine.
 */
public class GridH2DefaultTableEngine implements TableEngine {
    /** {@inheritDoc} */
    @Override public Table createTable(CreateTableData data) {
        // Used to create shadow table view used by CTE.
        data.persistData = false;
        data.persistIndexes = false;

        if (data.isHidden && data.id == 0 && "SYS".equals(data.tableName))
            return new GridH2MetaTable(data);

        return new PageStoreTable(data);
    }
}
