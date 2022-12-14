/*
 * Copyright 2020 GridGain Systems, Inc. and Contributors.
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

package org.apache.ignite.internal.processors.cache.persistence.diagnostic.pagelocktracker;

/**
 * Implementation of {@link PageLockTrackerMXBean}.
 */
public class PageLockTrackerMXBeanImpl implements PageLockTrackerMXBean {
    /** */
    private static final long OVERHEAD_SIZE = 16 + 8;

    /** Page lock tracker manager */
    private final PageLockTrackerManager mgr;

    /**
     * @param mgr Page lock tracker manager.
     */
    public PageLockTrackerMXBeanImpl(PageLockTrackerManager mgr, MemoryCalculator memoryCalculator) {
        this.mgr = mgr;

        memoryCalculator.onHeapAllocated(OVERHEAD_SIZE);
    }

    /** {@inheritDoc} */
    @Override public String dumpLocks() {
        return mgr.dumpLocksToString();
    }

    /** {@inheritDoc} */
    @Override public void dumpLocksToLog() {
        mgr.dumpLocksToLog();
    }

    /** {@inheritDoc} */
    @Override public String dumpLocksToFile() {
        return mgr.dumpLocksToFile();
    }

    /** {@inheritDoc} */
    @Override public String dumpLocksToFile(String path) {
        return mgr.dumpLocksToFile(path);
    }
}
