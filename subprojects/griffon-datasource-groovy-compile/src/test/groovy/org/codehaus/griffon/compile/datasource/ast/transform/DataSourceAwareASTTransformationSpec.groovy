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
package org.codehaus.griffon.compile.datasource.ast.transform

import griffon.plugins.datasource.DataSourceHandler
import spock.lang.Specification

import java.lang.reflect.Method

/**
 * @author Andres Almiray
 */
class DataSourceAwareASTTransformationSpec extends Specification {
    def 'DataSourceAwareASTTransformation is applied to a bean via @DataSourceAware'() {
        given:
        GroovyShell shell = new GroovyShell()

        when:
        def bean = shell.evaluate('''
        @griffon.transform.DataSourceAware
        class Bean { }
        new Bean()
        ''')

        then:
        bean instanceof DataSourceHandler
        DataSourceHandler.methods.every { Method target ->
            bean.class.declaredMethods.find { Method candidate ->
                candidate.name == target.name &&
                candidate.returnType == target.returnType &&
                candidate.parameterTypes == target.parameterTypes &&
                candidate.exceptionTypes == target.exceptionTypes
            }
        }
    }

    def 'DataSourceAwareASTTransformation is not applied to a DataSourceHandler subclass via @DataSourceAware'() {
        given:
        GroovyShell shell = new GroovyShell()

        when:
        def bean = shell.evaluate('''
        import griffon.plugins.datasource.ConnectionCallback
        import griffon.plugins.datasource.DataSourceCallback
        import griffon.plugins.datasource.exceptions.RuntimeSQLException
        import griffon.plugins.datasource.DataSourceHandler

        import javax.annotation.Nonnull
        @griffon.transform.DataSourceAware
        class DataSourceHandlerBean implements DataSourceHandler {
            @Override
            public <R> R withDataSource(@Nonnull DataSourceCallback<R> callback) throws RuntimeSQLException {
                return null
            }
            @Override
            public <R> R withDataSource(@Nonnull String dataSourceName, @Nonnull DataSourceCallback<R> callback) throws RuntimeSQLException {
                return null
            }
            @Override
            public <R> R withConnection(@Nonnull ConnectionCallback<R> callback) throws RuntimeSQLException {
                return null
            }
            @Override
            public <R> R withConnection(@Nonnull String dataSourceName, @Nonnull ConnectionCallback<R> callback) throws RuntimeSQLException {
                 return null
            }
            @Override
            public <R> R withConnection(boolean autoClose, @Nonnull ConnectionCallback<R> callback) throws RuntimeSQLException {
                return null
            }
            @Override
            public <R> R withConnection(@Nonnull String dataSourceName, boolean autoClose, @Nonnull ConnectionCallback<R> callback) throws RuntimeSQLException {
                 return null
            }
            @Override
            void closeDataSource(){}
            @Override
            void closeDataSource(@Nonnull String dataSourceName){}
        }
        new DataSourceHandlerBean()
        ''')

        then:
        bean instanceof DataSourceHandler
        DataSourceHandler.methods.every { Method target ->
            bean.class.declaredMethods.find { Method candidate ->
                candidate.name == target.name &&
                    candidate.returnType == target.returnType &&
                    candidate.parameterTypes == target.parameterTypes &&
                    candidate.exceptionTypes == target.exceptionTypes
            }
        }
    }
}
