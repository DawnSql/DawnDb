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

package org.apache.ignite.internal.processors.query.h2.sql;

import java.util.Collections;
import java.util.List;
import org.apache.ignite.internal.processors.query.h2.opt.GridH2Table;
import org.apache.ignite.internal.util.typedef.internal.SB;
import org.gridgain.internal.h2.command.Parser;
import org.gridgain.internal.h2.table.Table;
import org.jetbrains.annotations.Nullable;

import javax.cache.CacheException;

/**
 * Table with optional schema.
 */
public class GridSqlTable extends GridSqlElement {
    /** */
    private final String schema;

    /** */
    private final String tblName;

    /** */
    private final GridH2Table tbl;

    /** */
    private List<String> useIndexes;

    /**
     * @param schema Schema.
     * @param tblName Table name.
     */
    public GridSqlTable(@Nullable String schema, String tblName) {
        this(schema, tblName, null);
    }

    /**
     * @param tbl Table.
     */
    public GridSqlTable(Table tbl) {
        this(tbl.getSchema().getName(), tbl.getName(), tbl);
    }

    /**
     * @param schema Schema.
     * @param tblName Table name.
     * @param tbl H2 Table.
     */
    private GridSqlTable(String schema, String tblName, @Nullable Table tbl) {
        super(Collections.<GridSqlAst>emptyList());

        if (schema == null || tblName == null)
            throw new CacheException("Table not found.");

        this.schema = schema;
        this.tblName = tblName;

        this.tbl = tbl instanceof GridH2Table ? (GridH2Table)tbl : null;
    }

    /** {@inheritDoc}  */
    @Override public String getSQL() {
        return getBeforeAliasSql(true) + getAfterAliasSQL(true);
    }

    /**
     * @return SQL for the table before alias.
     */
    public String getBeforeAliasSql(boolean alwaysQuote) {
        if (schema == null)
            return Parser.quoteIdentifier(tblName, alwaysQuote);

        return Parser.quoteIdentifier(schema, alwaysQuote) + '.' + Parser.quoteIdentifier(tblName, alwaysQuote);
    }

    /**
     * @return SQL for the table after alias.
     */
    public String getAfterAliasSQL(boolean alwaysQuote) {
        if (useIndexes == null)
            return "";

        SB b = new SB();

        b.a(" USE INDEX (");

        boolean first = true;

        for (String idx : useIndexes) {
            if (first)
                first = false;
            else
                b.a(", ");

            b.a(Parser.quoteIdentifier(idx, alwaysQuote));
        }

        b.a(')');

        return b.toString();
    }

    /**
     * @param useIndexes List of indexes.
     */
    public void useIndexes(List<String> useIndexes) {
        this.useIndexes = useIndexes;
    }

    /**
     * @return Schema.
     */
    public String schema() {
        return schema;
    }

    /**
     * @return Table name.
     */
    public String tableName() {
        return tblName;
    }

    /**
     * @return Referenced data table.
     */
    public GridH2Table dataTable() {
        return tbl;
    }

    /** {@inheritDoc} */
    @Override public boolean equals(Object o) {
        if (!super.equals(o))
            return false;

        GridSqlTable that = (GridSqlTable)o;

        return schema.equals(that.schema) && tblName.equals(that.tblName);
    }

    /** {@inheritDoc} */
    @Override public int hashCode() {
        int result = 1;

        result = 31 * result + schema.hashCode();
        result = 31 * result + tblName.hashCode();

        return result;
    }
}
