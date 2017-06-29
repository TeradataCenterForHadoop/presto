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

import com.facebook.presto.Session;
import com.facebook.presto.metadata.MetadataManager;
import com.facebook.presto.spi.type.DoubleType;
import com.facebook.presto.spi.type.Type;
import com.facebook.presto.sql.planner.Symbol;
import com.facebook.presto.sql.tree.BetweenPredicate;
import com.facebook.presto.sql.tree.DoubleLiteral;
import com.facebook.presto.sql.tree.Expression;
import com.facebook.presto.sql.tree.IsNotNullPredicate;
import com.facebook.presto.sql.tree.IsNullPredicate;
import com.facebook.presto.sql.tree.SymbolReference;
import com.google.common.collect.ImmutableMap;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.Map;

import static com.facebook.presto.testing.TestingSession.testSessionBuilder;
import static java.lang.Double.NEGATIVE_INFINITY;
import static java.lang.Double.NaN;
import static java.lang.Double.POSITIVE_INFINITY;

@Test(singleThreaded = true)
public class TestFilterStatsCalculator
{
    private FilterStatsCalculator statsCalculator;
    private PlanNodeStatsEstimate standardInputStatistics;
    private Map<Symbol, Type> standardTypes;
    private Session session;

    @BeforeMethod
    public void setUp()
            throws Exception
    {
        SymbolStatsEstimate xStats = SymbolStatsEstimate.builder()
                .setAverageRowSize(4.0)
                .setDistinctValuesCount(40.0)
                .setLowValue(-10.0)
                .setHighValue(10.0)
                .setNullsFraction(0.25)
                .build();
        SymbolStatsEstimate yStats = SymbolStatsEstimate.builder()
                .setAverageRowSize(4.0)
                .setDistinctValuesCount(20.0)
                .setLowValue(0.0)
                .setHighValue(5.0)
                .setNullsFraction(0.5)
                .build();
        SymbolStatsEstimate zStats = SymbolStatsEstimate.builder()
                .setAverageRowSize(4.0)
                .setDistinctValuesCount(5.0)
                .setLowValue(-100.0)
                .setHighValue(100.0)
                .setNullsFraction(0.1)
                .build();
        SymbolStatsEstimate leftOpenStats = SymbolStatsEstimate.builder()
                .setAverageRowSize(4.0)
                .setDistinctValuesCount(50.0)
                .setLowValue(NEGATIVE_INFINITY)
                .setHighValue(15.0)
                .setNullsFraction(0.1)
                .build();
        SymbolStatsEstimate rightOpenStats = SymbolStatsEstimate.builder()
                .setAverageRowSize(4.0)
                .setDistinctValuesCount(50.0)
                .setLowValue(-15.0)
                .setHighValue(POSITIVE_INFINITY)
                .setNullsFraction(0.1)
                .build();
        SymbolStatsEstimate unknownRangeStats = SymbolStatsEstimate.builder()
                .setAverageRowSize(4.0)
                .setDistinctValuesCount(50.0)
                .setLowValue(NEGATIVE_INFINITY)
                .setHighValue(POSITIVE_INFINITY)
                .setNullsFraction(0.1)
                .build();
        SymbolStatsEstimate emptyRangeStats = SymbolStatsEstimate.builder()
                .setAverageRowSize(4.0)
                .setDistinctValuesCount(0.0)
                .setLowValue(NaN)
                .setHighValue(NaN)
                .setNullsFraction(NaN)
                .build();
        standardInputStatistics = PlanNodeStatsEstimate.builder()
                .addSymbolStatistics(new Symbol("x"), xStats)
                .addSymbolStatistics(new Symbol("y"), yStats)
                .addSymbolStatistics(new Symbol("z"), zStats)
                .addSymbolStatistics(new Symbol("leftOpen"), leftOpenStats)
                .addSymbolStatistics(new Symbol("rightOpen"), rightOpenStats)
                .addSymbolStatistics(new Symbol("unknownRange"), unknownRangeStats)
                .addSymbolStatistics(new Symbol("emptyRange"), emptyRangeStats)
                .setOutputRowCount(1000.0)
                .build();

        standardTypes = ImmutableMap.<Symbol, Type>builder()
                .put(new Symbol("x"), DoubleType.DOUBLE)
                .put(new Symbol("y"), DoubleType.DOUBLE)
                .put(new Symbol("z"), DoubleType.DOUBLE)
                .put(new Symbol("leftOpen"), DoubleType.DOUBLE)
                .put(new Symbol("rightOpen"), DoubleType.DOUBLE)
                .put(new Symbol("unknownRange"), DoubleType.DOUBLE)
                .put(new Symbol("emptyRange"), DoubleType.DOUBLE).build();

        session = testSessionBuilder().build();
        statsCalculator = new FilterStatsCalculator(MetadataManager.createTestMetadataManager());
    }

    public PlanNodeStatsAssertion assertExpression(Expression expression) {
        return PlanNodeStatsAssertion.assertThat(statsCalculator.filterStats(standardInputStatistics,
                expression,
                session,
                standardTypes));
    }

    @Test
    public void testIsNullFilter() {
        Expression isNullPredicate = new IsNullPredicate(new SymbolReference("x"));
        assertExpression(isNullPredicate)
                .outputRowsCount(250.0)
                .symbolStats(new Symbol("x"), symbolStats -> {
                   symbolStats.distinctValuesCount(0)
                           .emptyRange()
                           .nullsFraction(1.0);
                });

        Expression isNullEmptyRangePredicate = new IsNullPredicate(new SymbolReference("emptyRange"));
        assertExpression(isNullEmptyRangePredicate)
                .outputRowsCount(1000.0)
                .symbolStats(new Symbol("emptyRange"), symbolStats -> {
                    symbolStats.distinctValuesCount(0.0)
                            .emptyRange()
                            .nullsFraction(1.0);
                });
    }

    @Test
    public void testIsNotNullFilter() {
        Expression isNotNullPredicate = new IsNotNullPredicate(new SymbolReference("x"));
        assertExpression(isNotNullPredicate)
                .outputRowsCount(750.0)
                .symbolStats(new Symbol("x"), symbolStats -> {
                    symbolStats.distinctValuesCount(40.0)
                            .lowValue(-10.0)
                            .highValue(10.0)
                            .nullsFraction(0.0);
                });

        Expression isNotNullEmptyRangePredicate = new IsNotNullPredicate(new SymbolReference("emptyRange"));
        assertExpression(isNotNullEmptyRangePredicate)
                .outputRowsCount(0.0)
                .symbolStats(new Symbol("emptyRange"), symbolStats -> {
                    symbolStats.distinctValuesCount(0.0)
                            .emptyRange()
                            .nullsFraction(0.0);
                });
    }

    @Test
    public void testBetweenOperatorFilter() {
        // Only right side cut
        Expression betweenPredicateRightCut = new BetweenPredicate(new SymbolReference("x"), new DoubleLiteral("7.5"), new DoubleLiteral("12.0"));
        assertExpression(betweenPredicateRightCut)
                .outputRowsCount(93.75)
                .symbolStats(new Symbol("x"), symbolStats -> {
                    symbolStats.distinctValuesCount(5.0)
                            .lowValue(7.5)
                            .highValue(10.0)
                            .nullsFraction(0.0);
                });

        // Only left side cut
        Expression betweenPredicateLeftCut = new BetweenPredicate(new SymbolReference("x"), new DoubleLiteral("-12.0"), new DoubleLiteral("-7.5"));
        assertExpression(betweenPredicateLeftCut)
                .outputRowsCount(93.75)
                .symbolStats(new Symbol("x"), symbolStats -> {
                    symbolStats.distinctValuesCount(5.0)
                            .lowValue(-10)
                            .highValue(-7.5)
                            .nullsFraction(0.0);
                });

        // Both sides cut
        Expression betweenPredicateBothSidesCut = new BetweenPredicate(new SymbolReference("x"), new DoubleLiteral("-2.5"), new DoubleLiteral("2.5"));
        assertExpression(betweenPredicateBothSidesCut)
                .outputRowsCount(187.5)
                .symbolStats(new Symbol("x"), symbolStats -> {
                    symbolStats.distinctValuesCount(10.0)
                            .lowValue(-2.5)
                            .highValue(2.5)
                            .nullsFraction(0.0);
                });

        // Both sides cut unknownRange
        Expression betweenPredicateBothSidesCutUnknownRange = new BetweenPredicate(new SymbolReference("unknownRange"), new DoubleLiteral("2.72"), new DoubleLiteral("3.14"));
        assertExpression(betweenPredicateBothSidesCutUnknownRange)
                .outputRowsCount(112.5)
                .symbolStats(new Symbol("unknownRange"), symbolStats -> {
                    symbolStats.distinctValuesCount(6.25)
                            .lowValue(2.72)
                            .highValue(3.14)
                            .nullsFraction(0.0);
                });

        // Left side open, cut on open side
        Expression betweenPredicateCutOnLeftOpenSide = new BetweenPredicate(new SymbolReference("leftOpen"), new DoubleLiteral("-10.0"), new DoubleLiteral("10.0"));
        assertExpression(betweenPredicateCutOnLeftOpenSide)
                .outputRowsCount(180.0)
                .symbolStats(new Symbol("leftOpen"), symbolStats -> {
                    symbolStats.distinctValuesCount(10.0)
                            .lowValue(-10.0)
                            .highValue(10.0)
                            .nullsFraction(0.0);
                });

        // Right side open, cut on open side
        Expression betweenPredicateCutOnRightOpenSide = new BetweenPredicate(new SymbolReference("rightOpen"), new DoubleLiteral("-10.0"), new DoubleLiteral("10.0"));
        assertExpression(betweenPredicateCutOnRightOpenSide)
                .outputRowsCount(180.0)
                .symbolStats(new Symbol("rightOpen"), symbolStats -> {
                    symbolStats.distinctValuesCount(10.0)
                            .lowValue(-10.0)
                            .highValue(10.0)
                            .nullsFraction(0.0);
                });

        // Filter all
        Expression betweenPredicateFilterAll = new BetweenPredicate(new SymbolReference("y"), new DoubleLiteral("27.5"), new DoubleLiteral("107.0"));
        assertExpression(betweenPredicateFilterAll)
                .outputRowsCount(0.0)
                .symbolStats(new Symbol("y"), symbolStats -> {
                    symbolStats.distinctValuesCount(0.0)
                            .emptyRange()
                            .nullsFraction(0.0);
                });

        // Filter nothing
        Expression betweenPredicateFilterNothing = new BetweenPredicate(new SymbolReference("y"), new DoubleLiteral("-100.0"), new DoubleLiteral("100.0"));
        assertExpression(betweenPredicateFilterNothing)
                .outputRowsCount(500.0)
                .symbolStats(new Symbol("y"), symbolStats -> {
                    symbolStats.distinctValuesCount(20.0)
                            .lowValue(0.0)
                            .highValue(5.0)
                            .nullsFraction(0.0);
                });

        // Filter non exact match
        Expression betweenPredicateFilterNothingExact = new BetweenPredicate(new SymbolReference("z"), new DoubleLiteral("-100.0"), new DoubleLiteral("100.0"));
        assertExpression(betweenPredicateFilterNothingExact)
                .outputRowsCount(900.0)
                .symbolStats(new Symbol("z"), symbolStats -> {
                    symbolStats.distinctValuesCount(5.0)
                            .lowValue(-100.0)
                            .highValue(100.0)
                            .nullsFraction(0.0);
                });
    }

    @Test
    public void testInPredicateFilter()
    {

    }
}