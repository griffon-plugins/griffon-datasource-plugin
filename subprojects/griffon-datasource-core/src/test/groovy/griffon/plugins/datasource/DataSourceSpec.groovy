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
package griffon.plugins.datasource

import griffon.core.CallableWithArgs
import griffon.core.GriffonApplication
import griffon.plugins.datasource.events.DataSourceConnectEndEvent
import griffon.plugins.datasource.events.DataSourceConnectStartEvent
import griffon.plugins.datasource.events.DataSourceDisconnectEndEvent
import griffon.plugins.datasource.events.DataSourceDisconnectStartEvent
import griffon.plugins.datasource.exceptions.RuntimeSQLException
import griffon.test.core.GriffonUnitRule
import groovy.sql.Sql
import org.junit.Rule
import spock.lang.Specification
import spock.lang.Unroll

import javax.application.event.EventHandler
import javax.inject.Inject
import javax.sql.DataSource
import java.sql.Connection

@Unroll
class DataSourceSpec extends Specification {
    static {
        System.setProperty('org.slf4j.simpleLogger.defaultLogLevel', 'trace')
    }

    @Rule
    public final GriffonUnitRule griffon = new GriffonUnitRule()

    @Inject
    private DataSourceHandler dataSourceHandler

    @Inject
    private GriffonApplication application

    private class TestEventHandler {
        List<String> events = []

        @EventHandler
        void handleDataSourceConnectStartEvent(DataSourceConnectStartEvent event) {
            events << event.class.simpleName
        }

        @EventHandler
        void handleDataSourceConnectEndEvent(DataSourceConnectEndEvent event) {
            events << event.class.simpleName
        }

        @EventHandler
        void handleDataSourceDisconnectStartEvent(DataSourceDisconnectStartEvent event) {
            events << event.class.simpleName
        }

        @EventHandler
        void handleDataSourceDisconnectEndEvent(DataSourceDisconnectEndEvent event) {
            events << event.class.simpleName
        }
    }

    void 'Open and close default dataSource'() {
        given:
        List eventNames = [
            'DataSourceConnectStartEvent', 'DataSourceConnectEndEvent',
            'DataSourceDisconnectStartEvent', 'DataSourceDisconnectEndEvent'
        ]
        TestEventHandler testEventHandler = new TestEventHandler()
        application.eventRouter.subscribe(testEventHandler)

        when:
        dataSourceHandler.withDataSource { String dataSourceName, DataSource dataSource ->
            true
        }
        dataSourceHandler.closeDataSource()
        // second call should be a NOOP
        dataSourceHandler.closeDataSource()

        then:
        testEventHandler.events.size() == 4
        testEventHandler.events == eventNames
    }

    void 'Connect to default dataSource'() {
        expect:
        dataSourceHandler.withDataSource { String dataSourceName, DataSource dataSource ->
            dataSourceName == 'default' && dataSource
        }
    }

    void 'Open a connection to default dataSource'() {
        expect:
        dataSourceHandler.withConnection { String dataSourceName, DataSource dataSource, Connection connection ->
            dataSourceName == 'default' && dataSource && connection
        }
    }

    void 'Can connect to #name dataSource'() {
        expect:
        dataSourceHandler.withDataSource(name) { String dataSourceName, DataSource dataSource ->
            dataSourceName == name && dataSource
        }

        where:
        name       | _
        'default'  | _
        'internal' | _
        'people'   | _
    }

    void 'Can open a connection to #name dataSource'() {
        expect:
        dataSourceHandler.withConnection(name) { String dataSourceName, DataSource dataSource, Connection connection ->
            dataSourceName == name && dataSource && connection
        }

        where:
        name       | _
        'default'  | _
        'internal' | _
        'people'   | _
    }

    void 'Bogus dataSource name (#name) results in error'() {
        when:
        dataSourceHandler.withDataSource(name) { String dataSourceName, DataSource dataSource ->
            true
        }

        then:
        thrown(IllegalArgumentException)

        where:
        name    | _
        null    | _
        ''      | _
        'bogus' | _
    }

    void 'Execute statements on people dataSource'() {
        when:
        List peopleIn = dataSourceHandler.withDataSource('people') { String dataSourceName, DataSource dataSource ->
            Sql sql = new Sql(dataSource)
            def people = sql.dataSet('people')
            [[id: 1, name: 'Danno', lastname: 'Ferrin'],
             [id: 2, name: 'Andres', lastname: 'Almiray'],
             [id: 3, name: 'James', lastname: 'Williams'],
             [id: 4, name: 'Guillaume', lastname: 'Laforge'],
             [id: 5, name: 'Jim', lastname: 'Shingler'],
             [id: 6, name: 'Alexander', lastname: 'Klein'],
             [id: 7, name: 'Rene', lastname: 'Groeschke']].each { data ->
                people.add(data)
            }
        }

        List peopleOut = dataSourceHandler.withDataSource('people') { String dataSourceName, DataSource dataSource ->
            Sql sql = new Sql(dataSource)
            List list = []
            sql.eachRow('SELECT * FROM people') {
                list << [id      : it.id,
                         name    : it.name,
                         lastname: it.lastname]
            }
            list
        }

        then:
        peopleIn == peopleOut
    }

    void 'A runtime SQLException is thrown within dataSource handling'() {
        when:
        dataSourceHandler.withDataSource { String dataSourceName, DataSource dataSource ->
            Sql sql = new Sql(dataSource)
            sql.dataSet('people').add(id: 0)
        }

        then:
        thrown(RuntimeSQLException)
    }

    void 'A runtime SQLException is thrown within connection handling'() {
        when:
        dataSourceHandler.withConnection { String dataSourceName, DataSource dataSource, Connection connection ->
            Sql sql = new Sql(connection)
            sql.dataSet('people').add(id: 0)
        }

        then:
        thrown(RuntimeSQLException)
    }
}
