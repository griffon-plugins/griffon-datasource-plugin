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
package org.codehaus.griffon.runtime.datasource;

import griffon.core.Configuration;
import griffon.core.GriffonApplication;
import griffon.core.env.Metadata;
import griffon.exceptions.GriffonException;
import griffon.plugins.datasource.ConnectionCallback;
import griffon.plugins.datasource.DataSourceFactory;
import griffon.util.GriffonClassUtils;
import org.apache.commons.dbcp.ConnectionFactory;
import org.apache.commons.dbcp.DriverManagerConnectionFactory;
import org.apache.commons.dbcp.PoolableConnectionFactory;
import org.apache.commons.dbcp.PoolingDataSource;
import org.apache.commons.pool.ObjectPool;
import org.apache.commons.pool.impl.GenericObjectPool;
import org.codehaus.griffon.runtime.core.storage.AbstractObjectFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.inject.Inject;
import javax.inject.Named;
import javax.management.InstanceAlreadyExistsException;
import javax.management.InstanceNotFoundException;
import javax.management.MBeanRegistrationException;
import javax.management.MalformedObjectNameException;
import javax.management.NotCompliantMBeanException;
import javax.management.ObjectName;
import javax.sql.DataSource;
import java.io.IOException;
import java.net.URL;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;

import static griffon.core.GriffonExceptionHandler.sanitize;
import static griffon.core.env.Environment.getEnvironmentShortName;
import static griffon.util.ConfigUtils.getConfigValue;
import static griffon.util.ConfigUtils.getConfigValueAsBoolean;
import static griffon.util.ConfigUtils.getConfigValueAsString;
import static griffon.util.GriffonNameUtils.isBlank;
import static griffon.util.GriffonNameUtils.requireNonBlank;
import static java.lang.management.ManagementFactory.getPlatformMBeanServer;
import static java.util.Arrays.asList;
import static java.util.Objects.requireNonNull;

/**
 * @author Andres Almiray
 */
public class DefaultDataSourceFactory extends AbstractObjectFactory<DataSource> implements DataSourceFactory {
    private static final Logger LOG = LoggerFactory.getLogger(DefaultDataSourceFactory.class);
    private static final String ERROR_DATASOURCE_BLANK = "Argument 'dataSourceName' must not be blank";
    private final Set<String> dataSourceNames = new LinkedHashSet<>();

    @Inject
    public DefaultDataSourceFactory(@Nonnull @Named("datasource") Configuration configuration, @Nonnull GriffonApplication application) {
        super(configuration, application);
        dataSourceNames.add(KEY_DEFAULT);

        if (configuration.containsKey(getPluralKey())) {
            Map<String, Object> datasources = (Map<String, Object>) configuration.get(getPluralKey());
            dataSourceNames.addAll(datasources.keySet());
        }
    }

    @Nonnull
    @Override
    public Set<String> getDataSourceNames() {
        return dataSourceNames;
    }

    @Nonnull
    @Override
    public Map<String, Object> getConfigurationFor(@Nonnull String dataSourceName) {
        requireNonBlank(dataSourceName, ERROR_DATASOURCE_BLANK);
        return narrowConfig(dataSourceName);
    }

    @Nonnull
    @Override
    protected String getSingleKey() {
        return "dataSource";
    }

    @Nonnull
    @Override
    protected String getPluralKey() {
        return "dataSources";
    }

    @Nonnull
    @Override
    public DataSource create(@Nonnull String name) {
        requireNonBlank(name, ERROR_DATASOURCE_BLANK);
        Map<String, Object> config = narrowConfig(name);

        event("DataSourceConnectStart", asList(name, config));

        OpenPoolingDataSource dataSource = createDataSource(config, name);
        boolean skipSchema = getConfigValueAsBoolean(config, "schema", false);
        if (!skipSchema) {
            processSchema(config, name, dataSource);
        }

        if (getConfigValueAsBoolean(config, "jmx", true)) {
            registerMBean(name, dataSource);
        }

        event("DataSourceConnectEnd", asList(name, config, dataSource));

        return dataSource;
    }

    @Override
    public void destroy(@Nonnull String name, @Nonnull DataSource instance) {
        requireNonBlank(name, ERROR_DATASOURCE_BLANK);
        requireNonNull(instance, "Argument 'instance' must not be null");
        Map<String, Object> config = narrowConfig(name);

        event("DataSourceDisconnectStart", asList(name, config, instance));

        if (getConfigValueAsBoolean(config, "jmx", true)) {
            unregisterMBean(name);
        }

        event("DataSourceDisconnectEnd", asList(name, config));
    }

    private void registerMBean(@Nonnull String name, @Nonnull OpenPoolingDataSource dataSource) {
        try {
            ObjectName objectName = objectNameFor(name);
            getPlatformMBeanServer().registerMBean(new DataSourceMonitor(dataSource.getPool(), name), objectName);
        } catch (MalformedObjectNameException | NotCompliantMBeanException | MBeanRegistrationException | InstanceAlreadyExistsException e) {
            throw new GriffonException("An unexpected error occurred when registering a JMX bean", e);
        }
    }

    private void unregisterMBean(@Nonnull String name) {
        try {
            ObjectName objectName = objectNameFor(name);
            if (getPlatformMBeanServer().isRegistered(objectName)) {
                getPlatformMBeanServer().unregisterMBean(objectName);
            }
        } catch (MalformedObjectNameException | InstanceNotFoundException | MBeanRegistrationException e) {
            throw new GriffonException("An unexpected error occurred when unregistering a JMX bean", e);
        }
    }

    private ObjectName objectNameFor(String name) throws MalformedObjectNameException {
        String applicationName = Metadata.getCurrent().getApplicationName();
        return new ObjectName("griffon.plugins.datasource:type=ConnectionPool,application=" + applicationName + ",name=" + name);
    }

    @Nonnull
    @SuppressWarnings("ConstantConditions")
    private OpenPoolingDataSource createDataSource(@Nonnull Map<String, Object> config, @Nonnull String name) {
        String driverClassName = getConfigValueAsString(config, "driverClassName", "");
        requireNonBlank(driverClassName, "Configuration for " + name + ".driverClassName must not be blank");
        String url = getConfigValueAsString(config, "url", "");
        requireNonBlank(url, "Configuration for " + name + ".url must not be blank");

        try {
            getApplication().getApplicationClassLoader().get().loadClass(driverClassName);
        } catch (ClassNotFoundException e) {
            throw new GriffonException(e);
        }

        GenericObjectPool<DataSource> connectionPool = new GenericObjectPool<>(null);
        Map<String, Object> pool = getConfigValue(config, "pool", Collections.<String, Object>emptyMap());
        GriffonClassUtils.setPropertiesNoException(connectionPool, pool);

        String username = getConfigValueAsString(config, "username", "");
        String password = getConfigValueAsString(config, "password", "");
        ConnectionFactory connectionFactory = null;
        if (isBlank(username)) {
            connectionFactory = new DriverManagerConnectionFactory(url, null);
        } else {
            connectionFactory = new DriverManagerConnectionFactory(url, username, password);
        }

        new PoolableConnectionFactory(connectionFactory, connectionPool, null, null, false, true);
        return new OpenPoolingDataSource(connectionPool);
    }

    private void processSchema(@Nonnull Map<String, Object> config, @Nonnull String name, @Nonnull DataSource dataSource) {
        String dbCreate = getConfigValueAsString(config, "dbCreate", "skip");
        if (!"create".equals(dbCreate)) {
            return;
        }

        String env = getEnvironmentShortName();
        URL ddl = null;
        for (String schemaName : asList(name + "-schema-" + env + ".ddl", name + "-schema.ddl", "schema-" + env + ".ddl", "schema.ddl")) {
            ddl = getApplication().getResourceHandler().getResourceAsURL(schemaName);
            if (ddl == null) {
                LOG.warn("DataSource[{}].dbCreate was set to 'create' but {} was not found in classpath.", name, schemaName);
            } else {
                break;
            }
        }
        if (ddl == null) {
            LOG.error("DataSource[{}].dbCreate was set to 'create' but no suitable schema was found in classpath.", name);
            return;
        }
        final URL url = ddl;

        LOG.info("Initializing schema on '{}'", name);

        boolean autoclose = getConfigValueAsBoolean(config, "autoclose", true);
        DefaultDataSourceHandler.doWithConnection(name, dataSource, autoclose, new ConnectionCallback<Object>() {
            @Override
            public Object handle(@Nonnull String dataSourceName, @Nonnull DataSource ds, @Nonnull Connection connection) {
                try (Scanner sc = new Scanner(url.openStream()); Statement statement = connection.createStatement()) {
                    sc.useDelimiter(";");
                    while (sc.hasNext()) {
                        String line = sc.next().trim();
                        statement.execute(line);
                    }
                } catch (IOException | SQLException e) {
                    LOG.error("An error occurred when reading schema DDL from " + url, sanitize(e));
                    return null;
                }

                return null;
            }
        });
    }

    private static class OpenPoolingDataSource extends PoolingDataSource {
        public OpenPoolingDataSource() {
        }

        public OpenPoolingDataSource(ObjectPool pool) {
            super(pool);
        }

        public GenericObjectPool getPool() {
            return (GenericObjectPool) _pool;
        }
    }
}