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
package com.facebook.presto.sql.planner.iterative.rule;

import com.facebook.presto.cost.PlanNodeStatsEstimate;
import com.facebook.presto.spi.statistics.Estimate;
import com.facebook.presto.sql.planner.iterative.rule.test.RuleTester;
import com.facebook.presto.sql.planner.plan.JoinNode;
import com.facebook.presto.sql.planner.plan.PlanNodeId;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.testng.annotations.Test;

import static com.facebook.presto.spi.type.BigintType.BIGINT;
import static com.facebook.presto.sql.planner.assertions.PlanMatchPattern.equiJoinClause;
import static com.facebook.presto.sql.planner.assertions.PlanMatchPattern.join;
import static com.facebook.presto.sql.planner.assertions.PlanMatchPattern.values;

public class TestReorderJoins
{
    // TODO: Add tests for choosing the least cost join once limitations of the cost calculator are resolved
    @Test
    public void testChoosesAJoin()
    {
        new RuleTester().assertThat(new ReorderJoins())
                .on(p ->
                        p.joinGraph(
                                ImmutableList.of(
                                        p.values(new PlanNodeId("values1"), p.symbol("A1", BIGINT)),
                                        p.values(new PlanNodeId("values2"), p.symbol("B1", BIGINT)),
                                        p.values(new PlanNodeId("values3"), p.symbol("C1", BIGINT))),
                                ImmutableList.of(
                                        new JoinNode.EquiJoinClause(p.symbol("A1", BIGINT), p.symbol("B1", BIGINT)),
                                        new JoinNode.EquiJoinClause(p.symbol("B1", BIGINT), p.symbol("C1", BIGINT)),
                                        new JoinNode.EquiJoinClause(p.symbol("A1", BIGINT), p.symbol("C1", BIGINT))),
                                ImmutableList.of()))
                .withStats(ImmutableMap.of(
                        new PlanNodeId("values1"), PlanNodeStatsEstimate.builder().setOutputRowCount(new Estimate(10000)).build(),
                        new PlanNodeId("values2"), PlanNodeStatsEstimate.builder().setOutputRowCount(new Estimate(10000)).build(),
                        new PlanNodeId("values3"), PlanNodeStatsEstimate.builder().setOutputRowCount(new Estimate(10000)).build()))
                .matches(join(
                        JoinNode.Type.INNER,
                        ImmutableList.of(equiJoinClause("B1", "C1"), equiJoinClause("A1", "C1")),
                        join(
                                JoinNode.Type.INNER,
                                ImmutableList.of(equiJoinClause("A1", "B1")),
                                values(ImmutableMap.of("A1", 0)),
                                values(ImmutableMap.of("B1", 0)
                                )),
                        values(ImmutableMap.of("C1", 0))));
    }
}
