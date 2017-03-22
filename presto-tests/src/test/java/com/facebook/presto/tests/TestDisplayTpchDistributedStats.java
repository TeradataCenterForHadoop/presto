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
package com.facebook.presto.tests;

import com.facebook.presto.execution.QueryPlan;
import com.facebook.presto.sql.planner.optimizations.PlanNodeSearcher;
import com.facebook.presto.sql.planner.plan.PlanNode;
import com.facebook.presto.tests.statistics.Metric;
import com.facebook.presto.tests.statistics.MetricComparator;
import com.facebook.presto.tests.statistics.MetricComparison;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static com.facebook.presto.tests.statistics.MetricComparison.Result.DIFFER;
import static com.facebook.presto.tests.statistics.MetricComparison.Result.MATCH;
import static com.facebook.presto.tests.statistics.MetricComparison.Result.NO_BASELINE;
import static com.facebook.presto.tests.statistics.MetricComparison.Result.NO_ESTIMATE;
import static java.lang.String.format;
import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.groupingBy;

public class TestDisplayTpchDistributedStats
        extends AbstractTestDistributedStats
{
    public static final int NUMBER_OF_TPCH_QUERIES = 22;

    public TestDisplayTpchDistributedStats()
            throws Exception
    {}

    /**
     * This is a development tool for manual inspection of differences between
     * cost estimates and actual execution costs. Its outputs need to be inspected
     * manually because at this point no sensible assertions can be formulated
     * for the entirety of TPCH queries.
     */
    @Test
    void testCostEstimatesVsRealityDifferences()
    {
        IntStream.rangeClosed(1, NUMBER_OF_TPCH_QUERIES)
                .filter(i -> i != 15) //query 15 creates a view, which TPCH connector does not support.
                .forEach(i -> summarizeQuery(i, getTpchQuery(i)));
    }

    private void summarizeQuery(int queryNumber, String query)
    {
        String queryId = executeQuery(query);
        QueryPlan queryPlan = getQueryPlan(queryId);

        List<PlanNode> allPlanNodes = new PlanNodeSearcher(queryPlan.getPlan().getRoot()).findAll();

        System.out.println(format("Query TPCH [%s] produces [%s] plan nodes.\n", queryNumber, allPlanNodes.size()));

        List<MetricComparison> comparisons = new MetricComparator().getMetricComparisons(queryPlan, getOutputStageInfo(queryId));

        Map<Metric, Map<MetricComparison.Result, List<MetricComparison>>> metricSummaries =
                comparisons.stream()
                        .collect(groupingBy(MetricComparison::getMetric, groupingBy(MetricComparison::result)));

        metricSummaries.forEach((metricName, resultSummaries) -> {
            System.out.println(format("Summary for metric [%s]", metricName));
            outputSummary(resultSummaries, NO_ESTIMATE);
            outputSummary(resultSummaries, NO_BASELINE);
            outputSummary(resultSummaries, DIFFER);
            outputSummary(resultSummaries, MATCH);
            System.out.println();
        });

        System.out.println("Detailed results:\n");

        comparisons.forEach(System.out::println);
    }

    private void outputSummary(Map<MetricComparison.Result, List<MetricComparison>> resultSummaries, MetricComparison.Result result)
    {
        System.out.println(format("[%s]\t-\t[%s]", result, resultSummaries.getOrDefault(result, emptyList()).size()));
    }
}
