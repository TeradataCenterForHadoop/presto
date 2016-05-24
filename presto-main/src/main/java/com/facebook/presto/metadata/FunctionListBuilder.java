/*
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
package com.facebook.presto.metadata;

import com.facebook.presto.operator.Description;
import com.facebook.presto.operator.aggregation.GenericAggregationFunctionFactory;
import com.facebook.presto.operator.aggregation.InternalAggregationFunction;
import com.facebook.presto.operator.scalar.JsonPath;
import com.facebook.presto.operator.scalar.ScalarFunction;
import com.facebook.presto.operator.scalar.ScalarOperator;
import com.facebook.presto.operator.scalar.annotations.ScalarFromAnnotationsParser;
import com.facebook.presto.operator.window.ReflectionWindowFunctionSupplier;
import com.facebook.presto.operator.window.SqlWindowFunction;
import com.facebook.presto.operator.window.ValueWindowFunction;
import com.facebook.presto.operator.window.WindowFunction;
import com.facebook.presto.operator.window.WindowFunctionSupplier;
import com.facebook.presto.spi.ConnectorSession;
import com.facebook.presto.spi.PrestoException;
import com.facebook.presto.spi.type.Type;
import com.facebook.presto.spi.type.TypeManager;
import com.facebook.presto.spi.type.TypeSignature;
import com.facebook.presto.type.Constraint;
import com.facebook.presto.type.LiteralParameters;
import com.facebook.presto.type.SqlType;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.primitives.Primitives;
import io.airlift.joni.Regex;

import javax.annotation.Nullable;

import java.lang.annotation.Annotation;

import java.lang.invoke.MethodHandle;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static com.facebook.presto.metadata.FunctionKind.SCALAR;
import static com.facebook.presto.metadata.FunctionKind.WINDOW;
import static com.facebook.presto.metadata.Signature.longVariableExpression;
import static com.facebook.presto.metadata.Signature.typeVariable;
import static com.facebook.presto.spi.StandardErrorCode.FUNCTION_IMPLEMENTATION_ERROR;
import static com.facebook.presto.spi.type.BigintType.BIGINT;
import static com.facebook.presto.spi.type.TypeSignature.parseTypeSignature;
import static com.facebook.presto.util.ImmutableCollectors.toImmutableList;
import static com.google.common.base.CaseFormat.LOWER_CAMEL;
import static com.google.common.base.CaseFormat.LOWER_UNDERSCORE;
import static com.google.common.base.Preconditions.checkArgument;
import static java.lang.String.format;
import static java.lang.invoke.MethodHandles.lookup;
import static java.util.Locale.ENGLISH;
import static java.util.Objects.requireNonNull;

public class FunctionListBuilder
{
    private static final Set<Class<?>> NON_NULLABLE_ARGUMENT_TYPES = ImmutableSet.of(
            long.class,
            double.class,
            boolean.class,
            Regex.class,
            JsonPath.class);

    private final List<SqlFunction> functions = new ArrayList<>();
    private final TypeManager typeManager;

    public FunctionListBuilder(TypeManager typeManager)
    {
        this.typeManager = requireNonNull(typeManager, "typeManager is null");
    }

    public FunctionListBuilder window(String name, Type returnType, List<? extends Type> argumentTypes, Class<? extends WindowFunction> functionClass)
    {
        WindowFunctionSupplier windowFunctionSupplier = new ReflectionWindowFunctionSupplier<>(
                new Signature(name, WINDOW, returnType.getTypeSignature(), Lists.transform(ImmutableList.copyOf(argumentTypes), Type::getTypeSignature)),
                functionClass);

        functions.add(new SqlWindowFunction(windowFunctionSupplier));
        return this;
    }

    public FunctionListBuilder window(String name, Class<? extends ValueWindowFunction> clazz, String typeVariable, String... argumentTypes)
    {
        Signature signature = new Signature(
                name,
                WINDOW,
                ImmutableList.of(typeVariable(typeVariable)),
                ImmutableList.of(),
                parseTypeSignature(typeVariable),
                Arrays.asList(argumentTypes).stream().map(TypeSignature::parseTypeSignature).collect(toImmutableList()),
                false);
        functions.add(new SqlWindowFunction(new ReflectionWindowFunctionSupplier<>(signature, clazz)));
        return this;
    }

    public FunctionListBuilder aggregate(List<InternalAggregationFunction> functions)
    {
        for (InternalAggregationFunction function : functions) {
            aggregate(function);
        }
        return this;
    }

    public FunctionListBuilder aggregate(InternalAggregationFunction function)
    {
        String name = function.name();
        name = name.toLowerCase(ENGLISH);

        String description = getDescription(function.getClass());
        functions.add(SqlAggregationFunction.create(name, description, function));
        return this;
    }

    public FunctionListBuilder aggregate(Class<?> aggregationDefinition)
    {
        functions.addAll(GenericAggregationFunctionFactory.fromAggregationDefinition(aggregationDefinition, typeManager).listFunctions());
        return this;
    }

    public FunctionListBuilder scalar(Class<?> clazz)
    {
        functions.addAll(ScalarFromAnnotationsParser.parseFunctionDefinitionClass(clazz));
        return this;
    }

    public FunctionListBuilder scalars(Class<?> clazz)
    {
        functions.addAll(ScalarFromAnnotationsParser.parseFunctionSetClass(clazz));
        return this;
    }

    public FunctionListBuilder functions(SqlFunction... sqlFunctions)
    {
        for (SqlFunction sqlFunction : sqlFunctions) {
            function(sqlFunction);
        }
        return this;
    }

    public FunctionListBuilder function(SqlFunction sqlFunction)
    {
        requireNonNull(sqlFunction, "parametricFunction is null");
        functions.add(sqlFunction);
        return this;
    }

    @Deprecated
    private static String getDescription(AnnotatedElement annotatedElement)
    {
        Description description = annotatedElement.getAnnotation(Description.class);
        return (description == null) ? null : description.value();
    }

    public List<SqlFunction> getFunctions()
    {
        return ImmutableList.copyOf(functions);
    }
}
