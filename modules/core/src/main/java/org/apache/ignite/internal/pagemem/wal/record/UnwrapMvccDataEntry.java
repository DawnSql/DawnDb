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

package org.apache.ignite.internal.pagemem.wal.record;

import org.apache.ignite.binary.BinaryObject;
import org.apache.ignite.internal.processors.cache.CacheObject;
import org.apache.ignite.internal.processors.cache.CacheObjectValueContext;
import org.apache.ignite.internal.processors.cache.GridCacheOperation;
import org.apache.ignite.internal.processors.cache.KeyCacheObject;
import org.apache.ignite.internal.processors.cache.mvcc.MvccVersion;
import org.apache.ignite.internal.processors.cache.version.GridCacheVersion;
import org.apache.ignite.internal.util.IgniteUtils;
import org.apache.ignite.internal.util.tostring.GridToStringBuilder;
import org.apache.ignite.internal.util.typedef.internal.S;
import org.apache.ignite.internal.util.typedef.internal.SB;

import static org.apache.ignite.internal.util.tostring.GridToStringBuilder.SensitiveDataLogging.HASH;
import static org.apache.ignite.internal.util.tostring.GridToStringBuilder.SensitiveDataLogging.PLAIN;

/**
 * Data Entry for automatic unwrapping key and value from Mvcc Data Entry
 */
public class UnwrapMvccDataEntry extends MvccDataEntry implements UnwrappedDataEntry {
    /** Cache object value context. Context is used for unwrapping objects. */
    private final CacheObjectValueContext cacheObjValCtx;

    /** Keep binary. This flag disables converting of non primitive types (BinaryObjects). */
    private boolean keepBinary;

    /**
     * @param cacheId Cache ID.
     * @param key Key.
     * @param val Value or null for delete operation.
     * @param op Operation.
     * @param nearXidVer Near transaction version.
     * @param writeVer Write version.
     * @param expireTime Expire time.
     * @param partId Partition ID.
     * @param partCnt Partition counter.
     * @param mvccVer Mvcc version.
     * @param cacheObjValCtx cache object value context for unwrapping objects.
     * @param keepBinary disable unwrapping for non primitive objects, Binary Objects would be returned instead.
     */
    public UnwrapMvccDataEntry(
        final int cacheId,
        final KeyCacheObject key,
        final CacheObject val,
        final GridCacheOperation op,
        final GridCacheVersion nearXidVer,
        final GridCacheVersion writeVer,
        final long expireTime,
        final int partId,
        final long partCnt,
        MvccVersion mvccVer,
        final CacheObjectValueContext cacheObjValCtx,
        final boolean keepBinary) {
        super(cacheId, key, val, op, nearXidVer, writeVer, expireTime, partId, partCnt, mvccVer);

        this.cacheObjValCtx = cacheObjValCtx;
        this.keepBinary = keepBinary;
    }

    /** {@inheritDoc} */
    @Override public Object unwrappedKey() {
        try {
            if (keepBinary && key instanceof BinaryObject)
                return key;

            Object unwrapped = key.value(cacheObjValCtx, false);

            if (unwrapped instanceof BinaryObject) {
                if (keepBinary)
                    return unwrapped;
                unwrapped = ((BinaryObject)unwrapped).deserialize();
            }

            return unwrapped;
        }
        catch (Exception e) {
            cacheObjValCtx.kernalContext().log(UnwrapMvccDataEntry.class)
                .error("Unable to convert key [" + key + "]", e);

            return null;
        }
    }

    /** {@inheritDoc} */
    @Override public Object unwrappedValue() {
        try {
            if (val == null)
                return null;

            if (keepBinary && val instanceof BinaryObject)
                return val;

            return val.value(cacheObjValCtx, false);
        }
        catch (Exception e) {
            cacheObjValCtx.kernalContext().log(UnwrapMvccDataEntry.class)
                .error("Unable to convert value [" + value() + "]", e);
            return null;
        }
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        GridToStringBuilder.SensitiveDataLogging sensitiveDataLogging = S.getSensitiveDataLogging();

        SB sb = new SB();

        sb.a(getClass().getSimpleName()).a('[');

        if (sensitiveDataLogging == PLAIN)
            sb.a("k = ").a(unwrappedKey()).a(", v = [ ").a(unwrappedValue()).a("], ");
        else if (sensitiveDataLogging == HASH)
            sb.a("k = ").a(unwrappedKey() == null ? "null" : IgniteUtils.hash(unwrappedKey()))
                    .a(", v = [ ").a(unwrappedValue() == null ? "null" : IgniteUtils.hash(unwrappedValue())).a("], ");

        return sb.a("super = [").a(super.toString()).a("]]").toString();
    }
}
