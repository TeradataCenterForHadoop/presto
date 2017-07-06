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

import java.util.stream.Stream;

public class PlanNodeStatsEstimateMath
{
    private PlanNodeStatsEstimateMath()
    {}

    private interface SubtractRangeStrategy
    {
        StatisticRange range(StatisticRange leftRange, StatisticRange rightRange);
    }

    public static PlanNodeStatsEstimate differenceInStats(PlanNodeStatsEstimate left, PlanNodeStatsEstimate right)
    {
        return differenceInStatsWithRangeStrategy(left, right, StatisticRange::subtract);
    }

    public static PlanNodeStatsEstimate differenceInNonRangeStats(PlanNodeStatsEstimate left, PlanNodeStatsEstimate right)
    {
        return differenceInStatsWithRangeStrategy(left, right, ((leftRange, rightRange) -> leftRange));
    }

    private static PlanNodeStatsEstimate differenceInStatsWithRangeStrategy(PlanNodeStatsEstimate left, PlanNodeStatsEstimate right, SubtractRangeStrategy strategy)
    {
        PlanNodeStatsEstimate.Builder statsBuilder = PlanNodeStatsEstimate.builder();
        double newRowCount = left.getOutputRowCount() - right.getOutputRowCount();

        Stream.concat(left.getSymbolsWithKnownStatistics().stream(), right.getSymbolsWithKnownStatistics().stream())
                .forEach(symbol -> {
                    statsBuilder.addSymbolStatistics(symbol,
                            subtractColumnStats(left.getSymbolStatistics(symbol),
                                    left.getOutputRowCount(),
                                    right.getSymbolStatistics(symbol),
                                    right.getOutputRowCount(),
                                    newRowCount,
                                    strategy));
                });

        return statsBuilder.setOutputRowCount(newRowCount).build();
    }

    private static SymbolStatsEstimate subtractColumnStats(SymbolStatsEstimate leftStats,
            double leftRowCount,
            SymbolStatsEstimate rightStats,
            double rightRowCount,
            double newRowCount,
            SubtractRangeStrategy strategy)
    {
        StatisticRange leftRange = StatisticRange.from(leftStats);
        StatisticRange rightRange = StatisticRange.from(rightStats);

        double nullsCountLeft = leftStats.getNullsFraction() * leftRowCount;
        double nullsCountRight = rightStats.getNullsFraction() * rightRowCount;
        double totalSizeLeft = leftRowCount * leftStats.getAverageRowSize();
        double totalSizeRight = rightRowCount * rightStats.getAverageRowSize();
        StatisticRange range = strategy.range(leftRange, rightRange);

        return SymbolStatsEstimate.builder()
                .setDistinctValuesCount(leftStats.getDistinctValuesCount() - rightStats.getDistinctValuesCount())
                .setHighValue(range.getHigh())
                .setLowValue(range.getLow())
                .setAverageRowSize((totalSizeLeft - totalSizeRight) / newRowCount)
                .setNullsFraction((nullsCountLeft - nullsCountRight) / newRowCount)
                .build();
    }

    public static PlanNodeStatsEstimate addStats(PlanNodeStatsEstimate left, PlanNodeStatsEstimate right)
    {
        PlanNodeStatsEstimate.Builder statsBuilder = PlanNodeStatsEstimate.builder();
        double newRowCount = left.getOutputRowCount() + right.getOutputRowCount();

        Stream.concat(left.getSymbolsWithKnownStatistics().stream(), right.getSymbolsWithKnownStatistics().stream())
                .forEach(symbol -> {
                    statsBuilder.addSymbolStatistics(symbol,
                            addColumnStats(left.getSymbolStatistics(symbol),
                                    left.getOutputRowCount(),
                                    right.getSymbolStatistics(symbol),
                                    right.getOutputRowCount(), newRowCount));
                });

        return statsBuilder.setOutputRowCount(newRowCount).build();
    }

    private static SymbolStatsEstimate addColumnStats(SymbolStatsEstimate leftStats, double leftRows, SymbolStatsEstimate rightStats, double rightRows, double newRowCount)
    {
        StatisticRange leftRange = StatisticRange.from(leftStats);
        StatisticRange rightRange = StatisticRange.from(rightStats);

        StatisticRange sum = leftRange.add(rightRange);
        double nullsCountRight = rightStats.getNullsFraction() * rightRows;
        double nullsCountLeft = leftStats.getNullsFraction() * leftRows;
        double totalSizeLeft = leftRows * leftStats.getAverageRowSize();
        double totalSizeRight = rightRows * rightStats.getAverageRowSize();

        return SymbolStatsEstimate.builder()
                .setStatisticsRange(sum)
                .setAverageRowSize((totalSizeLeft + totalSizeRight) / newRowCount) // FIXME, weights to average. left and right should be equal in most cases anyway
                .setNullsFraction((nullsCountLeft + nullsCountRight) / newRowCount)
                .build();
    }
}
