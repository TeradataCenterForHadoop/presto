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
package com.facebook.presto.cost;

import com.facebook.presto.sql.planner.Symbol;
import com.facebook.presto.sql.tree.ComparisonExpressionType;

import java.util.Optional;
import java.util.OptionalDouble;

import static com.facebook.presto.cost.FilterStatsCalculator.filterStatsForUnknownExpression;
import static com.facebook.presto.cost.SymbolStatsEstimate.buildFrom;
import static java.lang.Double.NEGATIVE_INFINITY;
import static java.lang.Double.NaN;
import static java.lang.Double.POSITIVE_INFINITY;
import static java.lang.Double.isNaN;
import static java.lang.Math.max;

public class ComparisonStatsCalculator
{
    private ComparisonStatsCalculator()
    {}

    public static PlanNodeStatsEstimate comparisonSymbolToLiteralStats(PlanNodeStatsEstimate inputStatistics,
            Symbol symbol,
            OptionalDouble doubleLiteral,
            ComparisonExpressionType type)
    {
        return comparisonExpressionToLiteralStats(inputStatistics, Optional.of(symbol), inputStatistics.getSymbolStatistics(symbol), doubleLiteral, type);
    }

    public static PlanNodeStatsEstimate comparisonSymbolToLiteralStats(
            PlanNodeStatsEstimate inputStatistics,
            Optional<Symbol> symbol,
            SymbolStatsEstimate symbolStats,
            OptionalDouble doubleLiteral,
            ComparisonExpressionType type)
    {
        switch (type) {
            case EQUAL:
                return symbolToLiteralEquality(inputStatistics, symbol, symbolStats, doubleLiteral);
            case NOT_EQUAL:
                return symbolToLiteralNonEquality(inputStatistics, symbol, symbolStats, doubleLiteral);
            case LESS_THAN:
            case LESS_THAN_OR_EQUAL:
                return symbolToLiteralLessThan(inputStatistics, symbol, symbolStats, doubleLiteral);
            case GREATER_THAN:
            case GREATER_THAN_OR_EQUAL:
                return symbolToLiteralGreaterThan(inputStatistics, symbol, symbolStats, doubleLiteral);
            case IS_DISTINCT_FROM:
            default:
                return filterStatsForUnknownExpression(inputStatistics);
        }
    }

    private static PlanNodeStatsEstimate symbolToLiteralRangeComparison(
            PlanNodeStatsEstimate inputStatistics,
            Optional<Symbol> symbol,
            SymbolStatsEstimate symbolStats,
            StatisticRange literalRange)
    {
        StatisticRange range = StatisticRange.from(symbolStats);
        StatisticRange intersectRange = range.intersect(literalRange);

        double filterFactor = range.overlapPercentWith(intersectRange);

        PlanNodeStatsEstimate estimate = inputStatistics.mapOutputRowCount(rowCount -> filterFactor * (1 - symbolStats.getNullsFraction()) * rowCount);
        if (symbol.isPresent()) {
            SymbolStatsEstimate symbolNewEstimate =
                    SymbolStatsEstimate.builder()
                            .setAverageRowSize(symbolStats.getAverageRowSize())
                            .setStatisticsRange(intersectRange)
                            .setNullsFraction(0.0).build();
            estimate = estimate.mapSymbolColumnStatistics(symbol.get(), oldStats -> symbolNewEstimate);
        }
        return estimate;
    }

    private static PlanNodeStatsEstimate symbolToLiteralEquality(
            PlanNodeStatsEstimate inputStatistics,
            Optional<Symbol> symbol,
            SymbolStatsEstimate symbolStats,
            OptionalDouble literal)
    {
        StatisticRange literalRange;
        if (literal.isPresent()) {
            literalRange = new StatisticRange(literal.getAsDouble(), literal.getAsDouble(), 1);
        }
        else {
            literalRange = new StatisticRange(NEGATIVE_INFINITY, POSITIVE_INFINITY, 1);
        }
        return symbolToLiteralRangeComparison(inputStatistics, symbol, symbolStats, literalRange);
    }

    private static PlanNodeStatsEstimate symbolToLiteralNonEquality(
            PlanNodeStatsEstimate inputStatistics,
            Optional<Symbol> symbol,
            SymbolStatsEstimate symbolStats,
            OptionalDouble literal)
    {
        StatisticRange range = StatisticRange.from(symbolStats);

        StatisticRange literalRange;
        if (literal.isPresent()) {
            literalRange = new StatisticRange(literal.getAsDouble(), literal.getAsDouble(), 1);
        }
        else {
            literalRange = new StatisticRange(NEGATIVE_INFINITY, POSITIVE_INFINITY, 1);
        }
        StatisticRange intersectRange = range.intersect(literalRange);
        double filterFactor = 1 - range.overlapPercentWith(intersectRange);

        PlanNodeStatsEstimate estimate = inputStatistics.mapOutputRowCount(rowCount -> filterFactor * (1 - symbolStats.getNullsFraction()) * rowCount);
        if (symbol.isPresent()) {
            SymbolStatsEstimate symbolNewEstimate = buildFrom(symbolStats)
                    .setNullsFraction(0.0)
                    .setDistinctValuesCount(max(symbolStats.getDistinctValuesCount() - 1, 0))
                    .build();
            estimate = estimate.mapSymbolColumnStatistics(symbol.get(), oldStats -> symbolNewEstimate);
        }
        return estimate;
    }

    private static PlanNodeStatsEstimate symbolToLiteralLessThan(
            PlanNodeStatsEstimate inputStatistics,
            Optional<Symbol> symbol,
            SymbolStatsEstimate symbolStats,
            OptionalDouble literal)
    {
        return symbolToLiteralRangeComparison(inputStatistics, symbol, symbolStats, new StatisticRange(NEGATIVE_INFINITY, literal.orElse(POSITIVE_INFINITY), NaN));
    }

    private static PlanNodeStatsEstimate symbolToLiteralGreaterThan(
            PlanNodeStatsEstimate inputStatistics,
            Optional<Symbol> symbol,
            SymbolStatsEstimate symbolStats,
            OptionalDouble literal)
    {
        return symbolToLiteralRangeComparison(inputStatistics, symbol, symbolStats, new StatisticRange(literal.orElse(NEGATIVE_INFINITY), POSITIVE_INFINITY, NaN));
    }

    public static PlanNodeStatsEstimate comparisonSymbolToSymbolStats(PlanNodeStatsEstimate inputStatistics,
            Symbol left,
            Symbol right,
            ComparisonExpressionType type)
    {
        switch (type) {
            case EQUAL:
                return symbolToSymbolEquality(inputStatistics, left, right);
            case NOT_EQUAL:
                return symbolToSymbolNonEquality(inputStatistics, left, right);
            case LESS_THAN:
            case LESS_THAN_OR_EQUAL:
            case GREATER_THAN:
            case GREATER_THAN_OR_EQUAL:
            case IS_DISTINCT_FROM:
            default:
                return filterStatsForUnknownExpression(inputStatistics);
        }
    }

    private static PlanNodeStatsEstimate symbolToSymbolEquality(PlanNodeStatsEstimate inputStatistics,
            Symbol left,
            Symbol right)
    {
        SymbolStatsEstimate leftStats = inputStatistics.getSymbolStatistics(left);
        SymbolStatsEstimate rightStats = inputStatistics.getSymbolStatistics(right);

        if (isNaN(leftStats.getDistinctValuesCount()) || isNaN(rightStats.getDistinctValuesCount())) {
            filterStatsForUnknownExpression(inputStatistics);
        }

        StatisticRange leftRange = StatisticRange.from(leftStats);
        StatisticRange rightRange = StatisticRange.from(rightStats);

        StatisticRange intersect = leftRange.intersect(rightRange);

        SymbolStatsEstimate newRightStats = buildFrom(rightStats)
                .setNullsFraction(0)
                .setStatisticsRange(intersect)
                .build();
        SymbolStatsEstimate newLeftStats = buildFrom(leftStats)
                .setNullsFraction(0)
                .setStatisticsRange(intersect)
                .build();

        double nullsFilterFactor = (1 - leftStats.getNullsFraction()) * (1 - rightStats.getNullsFraction());
        double filterFactor = 1 / max(leftRange.getDistinctValuesCount(), rightRange.getDistinctValuesCount());

        return inputStatistics.mapOutputRowCount(size -> size * filterFactor * nullsFilterFactor)
                .mapSymbolColumnStatistics(left, oldLeftStats -> newLeftStats)
                .mapSymbolColumnStatistics(right, oldRightStats -> newRightStats);
    }

    private static PlanNodeStatsEstimate symbolToSymbolNonEquality(PlanNodeStatsEstimate inputStatistics,
            Symbol left,
            Symbol right)
    {
        return PlanNodeStatsEstimateMath.differenceInStats(inputStatistics, symbolToSymbolEquality(inputStatistics, left, right));
    }
}
