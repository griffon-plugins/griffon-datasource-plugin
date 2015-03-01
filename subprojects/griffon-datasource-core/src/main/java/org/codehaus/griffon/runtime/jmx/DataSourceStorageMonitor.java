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
import griffon.plugins.datasource.DataSourceStorage;
import org.codehaus.griffon.runtime.monitor.AbstractObjectStorageMonitor;

import javax.annotation.Nonnull;
import javax.sql.DataSource;

/**
 * @author Andres Almiray
 * @since 1.2.0
 */
public class DataSourceStorageMonitor extends AbstractObjectStorageMonitor<DataSource> implements DataSourceStorageMonitorMXBean {
    public DataSourceStorageMonitor(@Nonnull Metadata metadata, @Nonnull DataSourceStorage delegate) {
        super(metadata, delegate);
    }

    @Override
    protected String getStorageName() {
        return "datasource";
    }
}
