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

package org.apache.ignite.internal.processors.query.h2.twostep.msg;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import org.apache.ignite.internal.GridKernalContext;
import org.apache.ignite.plugin.extensions.communication.MessageReader;
import org.apache.ignite.plugin.extensions.communication.MessageWriter;
import org.gridgain.internal.h2.value.Value;
import org.gridgain.internal.h2.value.ValueDecimal;

import static org.gridgain.internal.h2.util.StringUtils.convertBytesToHex;

/**
 * H2 Decimal.
 */
public class GridH2Decimal extends GridH2ValueMessage {
    /** */
    private int scale;

    /** */
    private byte[] b;

    /**
     *
     */
    public GridH2Decimal() {
        // No-op.
    }

    /**
     * @param val Value.
     */
    public GridH2Decimal(Value val) {
        assert val.getType().getValueType() == Value.DECIMAL : val.getType();

        BigDecimal x = val.getBigDecimal();

        scale = x.scale();
        b = x.unscaledValue().toByteArray();
    }

    /** {@inheritDoc} */
    @Override public Value value(GridKernalContext ctx) {
        return ValueDecimal.get(new BigDecimal(new BigInteger(b), scale));
    }

    /** {@inheritDoc} */
    @Override public boolean writeTo(ByteBuffer buf, MessageWriter writer) {
        writer.setBuffer(buf);

        if (!super.writeTo(buf, writer))
            return false;

        if (!writer.isHeaderWritten()) {
            if (!writer.writeHeader(directType(), fieldsCount()))
                return false;

            writer.onHeaderWritten();
        }

        switch (writer.state()) {
            case 0:
                if (!writer.writeByteArray("b", b))
                    return false;

                writer.incrementState();

            case 1:
                if (!writer.writeInt("scale", scale))
                    return false;

                writer.incrementState();

        }

        return true;
    }

    /** {@inheritDoc} */
    @Override public boolean readFrom(ByteBuffer buf, MessageReader reader) {
        reader.setBuffer(buf);

        if (!reader.beforeMessageRead())
            return false;

        if (!super.readFrom(buf, reader))
            return false;

        switch (reader.state()) {
            case 0:
                b = reader.readByteArray("b");

                if (!reader.isLastRead())
                    return false;

                reader.incrementState();

            case 1:
                scale = reader.readInt("scale");

                if (!reader.isLastRead())
                    return false;

                reader.incrementState();

        }

        return reader.afterMessageRead(GridH2Decimal.class);
    }

    /** {@inheritDoc} */
    @Override public short directType() {
        return -10;
    }

    /** {@inheritDoc} */
    @Override public byte fieldsCount() {
        return 2;
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return scale + "_" + convertBytesToHex(b);
    }
}
