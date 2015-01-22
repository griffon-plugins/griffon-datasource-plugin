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
import griffon.core.addon.GriffonAddon;
import griffon.core.injection.Module;
import griffon.plugins.datasource.DataSourceFactory;
import griffon.plugins.datasource.DataSourceHandler;
import griffon.plugins.datasource.DataSourceStorage;
import org.codehaus.griffon.runtime.core.injection.AbstractModule;
import org.codehaus.griffon.runtime.util.ResourceBundleProvider;
import org.kordamp.jipsy.ServiceProviderFor;

import javax.inject.Named;
import java.util.ResourceBundle;

import static griffon.util.AnnotationUtils.named;

/**
 * @author Andres Almiray
 */
@Named("datasource")
@ServiceProviderFor(Module.class)
public class DataSourceModule extends AbstractModule {
    @Override
    protected void doConfigure() {
        // tag::bindings[]
        bind(ResourceBundle.class)
            .withClassifier(named("datasource"))
            .toProvider(new ResourceBundleProvider("DataSource"))
            .asSingleton();

        bind(Configuration.class)
            .withClassifier(named("datasource"))
            .to(DefaultDataSourceConfiguration.class)
            .asSingleton();

        bind(DataSourceStorage.class)
            .to(DefaultDataSourceStorage.class)
            .asSingleton();

        bind(DataSourceFactory.class)
            .to(DefaultDataSourceFactory.class)
            .asSingleton();

        bind(DataSourceHandler.class)
            .to(DefaultDataSourceHandler.class)
            .asSingleton();

        bind(GriffonAddon.class)
            .to(DataSourceAddon.class)
            .asSingleton();
        // end::bindings[]
    }
}
