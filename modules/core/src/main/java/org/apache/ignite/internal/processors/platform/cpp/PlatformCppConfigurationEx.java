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

package org.apache.ignite.internal.processors.platform.cpp;

import org.apache.ignite.internal.logger.platform.PlatformLogger;
import org.apache.ignite.internal.processors.platform.PlatformConfigurationEx;
import org.apache.ignite.internal.processors.platform.cache.PlatformCacheExtension;
import org.apache.ignite.internal.processors.platform.callback.PlatformCallbackGateway;
import org.apache.ignite.internal.processors.platform.memory.PlatformMemoryManagerImpl;
import org.apache.ignite.internal.processors.platform.utils.PlatformUtils;
import org.apache.ignite.platform.cpp.PlatformCppConfiguration;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * Internal interop CPP configuration.
 */
public class PlatformCppConfigurationEx extends PlatformCppConfiguration implements PlatformConfigurationEx {
    /** Native gateway. */
    private final PlatformCallbackGateway gate;

    /** Memory manager. */
    private final PlatformMemoryManagerImpl memMgr;

    /** Logger. */
    private final PlatformLogger logger;

    /** Warnings */
    private Collection<String> warns;

    /**
     * Copy constructor.
     *
     * @param cfg Configuration to copy.
     * @param gate Native gateway.
     * @param memMgr Memory manager.
     * @param logger Logger to use.
     */
    public PlatformCppConfigurationEx(PlatformCppConfiguration cfg, PlatformCallbackGateway gate,
        PlatformMemoryManagerImpl memMgr, PlatformLogger logger) {
        super(cfg);

        this.gate = gate;
        this.memMgr = memMgr;
        this.logger = logger;
    }

    /** {@inheritDoc} */
    @Override public PlatformCallbackGateway gate() {
        return gate;
    }

    /** {@inheritDoc} */
    @Override public PlatformMemoryManagerImpl memory() {
        return memMgr;
    }

    /** {@inheritDoc} */
    @Override public String platform() {
        return PlatformUtils.PLATFORM_CPP;
    }

    /** {@inheritDoc} */
    @Override public Collection<String> warnings() {
        return warns;
    }

    /** {@inheritDoc} */
    @Override public PlatformLogger logger() {
        return logger;
    }

    /** {@inheritDoc} */
    @Override @Nullable public Collection<PlatformCacheExtension> cacheExtensions() {
        return null;
    }

    /**
     * @param warnings Warnings.
     */
    public void warnings(Collection<String> warnings) {
        this.warns = warnings;
    }
}