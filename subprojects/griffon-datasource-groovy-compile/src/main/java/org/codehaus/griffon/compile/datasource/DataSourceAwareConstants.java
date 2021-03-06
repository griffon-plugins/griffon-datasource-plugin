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
package org.codehaus.griffon.compile.datasource;

import org.codehaus.griffon.compile.core.BaseConstants;
import org.codehaus.griffon.compile.core.MethodDescriptor;

import static org.codehaus.griffon.compile.core.MethodDescriptor.annotatedMethod;
import static org.codehaus.griffon.compile.core.MethodDescriptor.annotatedType;
import static org.codehaus.griffon.compile.core.MethodDescriptor.annotations;
import static org.codehaus.griffon.compile.core.MethodDescriptor.args;
import static org.codehaus.griffon.compile.core.MethodDescriptor.method;
import static org.codehaus.griffon.compile.core.MethodDescriptor.throwing;
import static org.codehaus.griffon.compile.core.MethodDescriptor.type;
import static org.codehaus.griffon.compile.core.MethodDescriptor.typeParams;
import static org.codehaus.griffon.compile.core.MethodDescriptor.types;

/**
 * @author Andres Almiray
 */
public interface DataSourceAwareConstants extends BaseConstants {
    String DATA_SOURCE_TYPE = "java.sql.DataSource";
    String DATA_SOURCE_HANDLER_TYPE = "griffon.plugins.datasource.DataSourceHandler";
    String DATA_SOURCE_CALLBACK_TYPE = "griffon.plugins.datasource.DataSourceCallback";
    String CONNECTION_CALLBACK_TYPE = "griffon.plugins.datasource.ConnectionCallback";
    String RUNTIME_SQL_EXCEPTION_TYPE = "griffon.plugins.datasource.exceptions.RuntimeSQLException";
    String DATA_SOURCE_HANDLER_PROPERTY = "dataSourceHandler";
    String DATA_SOURCE_HANDLER_FIELD_NAME = "this$" + DATA_SOURCE_HANDLER_PROPERTY;

    String METHOD_WITH_DATA_SOURCE = "withDataSource";
    String METHOD_WITH_CONNECTION = "withConnection";
    String METHOD_CLOSE_DATA_SOURCE = "closeDataSource";
    String DATA_SOURCE_NAME = "dataSourceName";
    String CALLBACK = "callback";
    String CONNECTION = "connection";

    MethodDescriptor[] METHODS = new MethodDescriptor[]{
        method(
            type(VOID),
            METHOD_CLOSE_DATA_SOURCE
        ),
        method(
            type(VOID),
            METHOD_CLOSE_DATA_SOURCE,
            args(annotatedType(types(type(ANNOTATION_NONNULL)), JAVA_LANG_STRING))
        ),

        annotatedMethod(
            annotations(ANNOTATION_NONNULL),
            type(R),
            typeParams(R),
            METHOD_WITH_DATA_SOURCE,
            args(annotatedType(annotations(ANNOTATION_NONNULL), DATA_SOURCE_CALLBACK_TYPE, R)),
            throwing(type(RUNTIME_SQL_EXCEPTION_TYPE))
        ),
        annotatedMethod(
            types(type(ANNOTATION_NONNULL)),
            type(R),
            typeParams(R),
            METHOD_WITH_DATA_SOURCE,
            args(
                annotatedType(annotations(ANNOTATION_NONNULL), JAVA_LANG_STRING),
                annotatedType(annotations(ANNOTATION_NONNULL), DATA_SOURCE_CALLBACK_TYPE, R)),
            throwing(type(RUNTIME_SQL_EXCEPTION_TYPE))
        ),

        annotatedMethod(
            annotations(ANNOTATION_NONNULL),
            type(R),
            typeParams(R),
            METHOD_WITH_CONNECTION,
            args(annotatedType(annotations(ANNOTATION_NONNULL), CONNECTION_CALLBACK_TYPE, R)),
            throwing(type(RUNTIME_SQL_EXCEPTION_TYPE))
        ),
        annotatedMethod(
            annotations(ANNOTATION_NONNULL),
            type(R),
            typeParams(R),
            METHOD_WITH_CONNECTION,
            args(
                annotatedType(annotations(ANNOTATION_NONNULL), JAVA_LANG_STRING),
                annotatedType(annotations(ANNOTATION_NONNULL), CONNECTION_CALLBACK_TYPE, R)),
            throwing(type(RUNTIME_SQL_EXCEPTION_TYPE))
        )
    };
}
