/*
 * Copyright (c) 2008-2020, Hazelcast, Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hazelcast.jet.sql.impl.validate;

import com.hazelcast.jet.sql.impl.schema.JetTableFunctionParameter;
import com.hazelcast.sql.impl.QueryException;
import com.hazelcast.sql.impl.calcite.validate.operators.HazelcastSqlCastFunction;
import com.hazelcast.sql.impl.calcite.validate.types.HazelcastObjectType;
import com.hazelcast.sql.impl.calcite.validate.types.HazelcastTypeFactory;
import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import org.apache.calcite.rel.type.RelDataType;
import org.apache.calcite.rel.type.RelDataTypeFactory;
import org.apache.calcite.schema.FunctionParameter;
import org.apache.calcite.schema.TableFunction;
import org.apache.calcite.sql.SqlBasicCall;
import org.apache.calcite.sql.SqlBasicTypeNameSpec;
import org.apache.calcite.sql.SqlDataTypeSpec;
import org.apache.calcite.sql.SqlLiteral;
import org.apache.calcite.sql.SqlNode;
import org.apache.calcite.sql.fun.SqlArrayValueConstructor;
import org.apache.calcite.sql.fun.SqlMapValueConstructor;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.sql.type.SqlTypeName;
import org.apache.calcite.sql.validate.SqlUserDefinedTableFunction;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static java.util.Collections.singletonList;
import static org.apache.calcite.sql.parser.SqlParserPos.ZERO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@RunWith(JUnitParamsRunner.class)
public class JetSqlOperatorTableTest {

    private static final RelDataTypeFactory TYPE_FACTORY = HazelcastTypeFactory.INSTANCE;

    @Mock
    private TableFunction tableFunction;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    @SuppressWarnings({"unused", "checkstyle:LineLength"})
    private Object[] invalidNodes() {
        return new Object[]{
                new Object[]{new SqlBasicCall(new SqlMapValueConstructor(), new SqlNode[]{literal("key"), literal("value")}, ZERO)},
                new Object[]{new SqlBasicCall(new SqlArrayValueConstructor(), new SqlNode[]{literal("value")}, ZERO)},
                new Object[]{new SqlBasicCall(new HazelcastSqlCastFunction(), new SqlNode[]{literal("true"), new SqlDataTypeSpec(new SqlBasicTypeNameSpec(SqlTypeName.BOOLEAN, ZERO), ZERO)}, ZERO)},
                new Object[]{SqlLiteral.createBoolean(true, ZERO)},
                new Object[]{SqlLiteral.createExactNumeric("127", ZERO)},
                new Object[]{SqlLiteral.createExactNumeric("32767", ZERO)},
                new Object[]{SqlLiteral.createExactNumeric("2147483647", ZERO)},
                new Object[]{SqlLiteral.createExactNumeric("9223372036854775807", ZERO)},
                new Object[]{SqlLiteral.createApproxNumeric("1234567890.1", ZERO)},
                new Object[]{SqlLiteral.createApproxNumeric("123451234567890.1", ZERO)},
                new Object[]{SqlLiteral.createApproxNumeric("9223372036854775.123", ZERO)}
        };
    }

    @Test
    @Parameters(method = "invalidNodes")
    public void when_getRowTypeWithInvalidNode_then_throws(SqlNode node) {
        SqlUserDefinedTableFunction sqlFunction = function("invalid");

        assertThatThrownBy(() -> sqlFunction.getRowType(TYPE_FACTORY, singletonList(node)))
                .isInstanceOf(QueryException.class)
                .hasMessageContaining("All arguments of call to function test_function should be VARCHAR literals");
    }

    @SuppressWarnings("unused")
    private Object[] validNodes() {
        return new Object[]{
                new Object[]{new SqlBasicCall(SqlStdOperatorTable.DEFAULT, new SqlNode[0], ZERO), null},
                new Object[]{SqlLiteral.createNull(ZERO), null},
                new Object[]{SqlLiteral.createCharString("string", ZERO), "string"},
        };
    }

    @Test
    @Parameters(method = "validNodes")
    public void when_getRowTypeWithValidNode_then_returnsValue(SqlNode node, Object expected) {
        SqlUserDefinedTableFunction sqlFunction = function("valid");
        given(tableFunction.getRowType(TYPE_FACTORY, singletonList(expected))).willReturn(HazelcastObjectType.INSTANCE);

        RelDataType rowType = sqlFunction.getRowType(TYPE_FACTORY, singletonList(node));

        assertThat(rowType).isEqualTo(HazelcastObjectType.INSTANCE);
    }

    private SqlUserDefinedTableFunction function(String parameterName) {
        FunctionParameter parameter = new JetTableFunctionParameter(0, parameterName, true);
        given(tableFunction.getParameters()).willReturn(singletonList(parameter));
        return JetSqlOperatorTable.from(tableFunction, "test_function");
    }

    private static SqlNode literal(String value) {
        return SqlLiteral.createCharString(value, ZERO);
    }
}