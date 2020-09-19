/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * Copyright 2014-2020 The author and/or original authors.
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
package org.codehaus.griffon.runtime.datasource;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import griffon.annotations.core.Nonnull;
import griffon.core.Configuration;
import griffon.core.GriffonApplication;
import griffon.core.env.Environment;
import griffon.core.env.Metadata;
import griffon.exceptions.GriffonException;
import griffon.plugins.datasource.DataSourceFactory;
import griffon.plugins.datasource.events.DataSourceConnectEndEvent;
import griffon.plugins.datasource.events.DataSourceConnectStartEvent;
import griffon.plugins.datasource.events.DataSourceDisconnectEndEvent;
import griffon.plugins.datasource.events.DataSourceDisconnectStartEvent;
import griffon.plugins.monitor.MBeanManager;
import griffon.util.GriffonClassUtils;
import org.codehaus.griffon.runtime.core.storage.AbstractObjectFactory;
import org.codehaus.griffon.runtime.datasource.monitor.HikariPoolMonitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import javax.sql.DataSource;
import java.io.IOException;
import java.net.URL;
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
import static griffon.util.GriffonNameUtils.requireNonBlank;
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
    private MBeanManager mBeanManager;

    @Inject
    private Metadata metadata;

    @Inject
    private Environment environment;

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

        event(DataSourceConnectStartEvent.of(name, config));

        DataSource dataSource = createDataSource(config, name);
        boolean skipSchema = getConfigValueAsBoolean(config, "schema", false);
        if (!skipSchema) {
            processSchema(config, name, dataSource);
        }

        if (getConfigValueAsBoolean(config, "jmx", true)) {
            dataSource = new JMXAwareDataSource(dataSource);
            registerMBeans(name, (JMXAwareDataSource) dataSource);
        }

        event(DataSourceConnectEndEvent.of(name, config, dataSource));

        return dataSource;
    }

    @Override
    public void destroy(@Nonnull String name, @Nonnull DataSource instance) {
        requireNonBlank(name, ERROR_DATASOURCE_BLANK);
        requireNonNull(instance, "Argument 'instance' must not be null");
        Map<String, Object> config = narrowConfig(name);

        event(DataSourceDisconnectStartEvent.of(name, config, instance));

        if (getConfigValueAsBoolean(config, "jmx", true)) {
            unregisterMBeans((JMXAwareDataSource) instance);
        }

        event(DataSourceDisconnectEndEvent.of(name, config));
    }

    private void registerMBeans(@Nonnull String name, @Nonnull JMXAwareDataSource dataSource) {
        HikariPoolMonitor poolMonitor = new HikariPoolMonitor(metadata, ((HikariDataSource) dataSource.getDelegate()).getHikariPoolMXBean(), name);
        dataSource.addObjectName(mBeanManager.registerMBean(poolMonitor, true).getCanonicalName());
    }

    private void unregisterMBeans(@Nonnull JMXAwareDataSource dataSource) {
        for (String objectName : dataSource.getObjectNames()) {
            mBeanManager.unregisterMBean(objectName);
        }
        dataSource.clearObjectNames();
    }

    @Nonnull
    @SuppressWarnings("ConstantConditions")
    private DataSource createDataSource(@Nonnull Map<String, Object> config, @Nonnull String name) {
        String driverClassName = getConfigValueAsString(config, "driverClassName", "");
        requireNonBlank(driverClassName, "Configuration for " + name + ".driverClassName must not be blank");
        String url = getConfigValueAsString(config, "url", "");
        requireNonBlank(url, "Configuration for " + name + ".url must not be blank");

        try {
            getApplication().getApplicationClassLoader().get().loadClass(driverClassName);
        } catch (ClassNotFoundException e) {
            throw new GriffonException(e);
        }

        String username = getConfigValueAsString(config, "username", "");
        String password = getConfigValueAsString(config, "password", "");

        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl(url);
        hikariConfig.setDriverClassName(driverClassName);
        hikariConfig.setUsername(username);
        hikariConfig.setPassword(password);
        Map<String, Object> pool = getConfigValue(config, "pool", Collections.<String, Object>emptyMap());
        GriffonClassUtils.setPropertiesNoException(hikariConfig, pool);

        return new HikariDataSource(hikariConfig);
    }

    private void processSchema(@Nonnull Map<String, Object> config, @Nonnull String name, @Nonnull DataSource dataSource) {
        String dbCreate = getConfigValueAsString(config, "dbCreate", "skip");
        if (!"create".equals(dbCreate)) {
            return;
        }

        String env = getEnvironmentShortName(environment);
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

        DefaultDataSourceHandler.doWithConnection(name, dataSource, (dataSourceName, ds, connection) -> {
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
        });
    }
}
