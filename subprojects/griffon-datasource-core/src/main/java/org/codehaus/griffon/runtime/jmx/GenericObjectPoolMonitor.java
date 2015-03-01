/*
 * Copyright 2014-2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.codehaus.griffon.runtime.jmx;

import griffon.core.env.Metadata;
import griffon.exceptions.GriffonException;
import org.apache.commons.pool.impl.GenericObjectPool;
import org.codehaus.griffon.runtime.monitor.AbstractMBeanRegistration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.management.MBeanServer;
import javax.management.ObjectName;

import static java.util.Objects.requireNonNull;

/**
 * @author Andres Almiray
 * @since 1.2.0
 */
public class GenericObjectPoolMonitor extends AbstractMBeanRegistration implements GenericObjectPoolMonitorMXBean {
    private static final Logger LOG = LoggerFactory.getLogger(GenericObjectPoolMonitor.class);

    private GenericObjectPool delegate;
    private final String name;

    public GenericObjectPoolMonitor(@Nonnull Metadata metadata, @Nonnull GenericObjectPool delegate, @Nonnull String name) {
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
    public int getMaxActive() {
        return delegate.getMaxActive();
    }

    @Override
    public void setMaxActive(int m) {
        LOG.trace("Changing maxActive from={} to={}", getMaxActive(), m);
        delegate.setMaxActive(m);
    }

    @Override
    public long getMaxWait() {
        return delegate.getMaxWait();
    }

    @Override
    public void setMaxWait(long m) {
        LOG.trace("Changing maxWait from={} to={}" + getMaxWait(), m);
        delegate.setMaxWait(m);
    }

    @Override
    public int getMaxIdle() {
        return delegate.getMaxIdle();
    }

    @Override
    public void setMaxIdle(int m) {
        LOG.trace("Changing maxIdle from={} to={}" + getMaxIdle(), m);
        delegate.setMaxIdle(m);
    }

    @Override
    public void setMinIdle(int m) {
        LOG.trace("Changing minIdle from={} to={}" + getMinIdle(), +m);
        delegate.setMinIdle(m);
    }

    @Override
    public int getMinIdle() {
        return delegate.getMinIdle();
    }

    @Override
    public int getNumActive() {
        return delegate.getNumActive();
    }

    @Override
    public int getNumIdle() {
        return delegate.getNumIdle();
    }

    @Override
    public void clear() {
        LOG.trace("Clearing the {} connection pool", name);
        delegate.clear();
    }

    @Override
    public void evict() {
        try {
            LOG.trace("Evicting from the {} connection pool", name);
            delegate.evict();
        } catch (Exception e) {
            throw new GriffonException(e);
        }
    }
}
