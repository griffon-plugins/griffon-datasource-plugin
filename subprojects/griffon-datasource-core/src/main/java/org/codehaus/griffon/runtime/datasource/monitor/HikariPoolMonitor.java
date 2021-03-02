/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * Copyright 2014-2021 The author and/or original authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.codehaus.griffon.runtime.datasource.monitor;

import com.zaxxer.hikari.HikariPoolMXBean;
import griffon.annotations.core.Nonnull;
import griffon.core.env.Metadata;
import org.codehaus.griffon.runtime.monitor.AbstractMBeanRegistration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.management.MBeanServer;
import javax.management.ObjectName;

import static java.util.Objects.requireNonNull;

/**
 * @author Andres Almiray
 * @since 2.0.0
 */
public class HikariPoolMonitor extends AbstractMBeanRegistration implements HikariPoolMonitorMXBean {
    private static final Logger LOG = LoggerFactory.getLogger(HikariPoolMonitor.class);
    private final String name;
    private HikariPoolMXBean delegate;

    public HikariPoolMonitor(@Nonnull Metadata metadata, @Nonnull HikariPoolMXBean delegate, @Nonnull String name) {
        super(metadata);
        this.delegate = requireNonNull(delegate, "Argument 'delegate' must not be null");
        this.name = name;
    }

    @Override
    public ObjectName preRegister(MBeanServer server, ObjectName name) throws Exception {
        return new ObjectName("griffon.plugins.datasource:type=ConnectionPool,application=" + metadata.getApplicationName() + ",name=" + this.name);
    }

    @Override
    public void postDeregister() {
        delegate = null;
        super.postDeregister();
    }

    @Override
    public int getActiveConnections() {
        return delegate.getActiveConnections();
    }

    @Override
    public int getIdleConnections() {
        return delegate.getIdleConnections();
    }

    @Override
    public int getTotalConnections() {
        return delegate.getTotalConnections();
    }

    @Override
    public int getThreadsAwaitingConnection() {
        return delegate.getThreadsAwaitingConnection();
    }

    @Override
    public void softEvictConnections() {
        LOG.trace("Evicting connections in the {} connection pool", name);
        delegate.softEvictConnections();
    }

    @Override
    public void suspendPool() {
        LOG.trace("Suspending the {} connection pool", name);
        delegate.suspendPool();
    }

    @Override
    public void resumePool() {
        LOG.trace("Resuming the {} connection pool", name);
        delegate.resumePool();
    }
}
