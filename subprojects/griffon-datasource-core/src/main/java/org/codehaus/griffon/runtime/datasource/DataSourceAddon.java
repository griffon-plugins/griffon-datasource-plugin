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

import griffon.core.GriffonApplication;
import griffon.core.env.Metadata;
import griffon.plugins.datasource.ConnectionCallback;
import griffon.plugins.datasource.DataSourceFactory;
import griffon.plugins.datasource.DataSourceHandler;
import griffon.plugins.datasource.DataSourceStorage;
import griffon.plugins.monitor.MBeanManager;
import org.codehaus.griffon.runtime.core.addon.AbstractGriffonAddon;
import org.codehaus.griffon.runtime.jmx.DataSourceStorageMonitor;

import javax.annotation.Nonnull;
import javax.inject.Inject;
import javax.inject.Named;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;

import static griffon.util.ConfigUtils.getConfigValueAsBoolean;

/**
 * @author Andres Almiray
 * @since 1.1.0
 */
@Named("datasource")
public class DataSourceAddon extends AbstractGriffonAddon {
    @Inject
    private DataSourceHandler dataSourceHandler;

    @Inject
    private DataSourceFactory dataSourceFactory;

    @Inject
    private DataSourceStorage dataSourceStorage;

    @Inject
    private MBeanManager mbeanManager;

    @Inject
    private Metadata metadata;

    @Override
    public void init(@Nonnull GriffonApplication application) {
        mbeanManager.registerMBean(new DataSourceStorageMonitor(metadata, dataSourceStorage));
    }

    public void onStartupStart(@Nonnull GriffonApplication application) {
        for (String dataSourceName : dataSourceFactory.getDataSourceNames()) {
            Map<String, Object> config = dataSourceFactory.getConfigurationFor(dataSourceName);
            if (getConfigValueAsBoolean(config, "connect_on_startup", false)) {
                dataSourceHandler.withConnection(new ConnectionCallback<Void>() {
                    @Override
                    public Void handle(@Nonnull String dataSourceName, @Nonnull DataSource dataSource, @Nonnull Connection connection) throws SQLException {
                        return null;
                    }
                });
            }
        }
    }

    public void onShutdownStart(@Nonnull GriffonApplication application) {
        for (String dataSourceName : dataSourceFactory.getDataSourceNames()) {
            dataSourceHandler.closeDataSource(dataSourceName);
        }
    }
}
