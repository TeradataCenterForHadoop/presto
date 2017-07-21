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

import com.facebook.presto.matching.Captures;
import com.facebook.presto.matching.Pattern;
import com.facebook.presto.sql.planner.iterative.PatternBasedRule;
import com.facebook.presto.sql.planner.optimizations.CanonicalizeExpressions;
import com.facebook.presto.sql.planner.plan.Assignments;
import com.facebook.presto.sql.planner.plan.PlanNode;
import com.facebook.presto.sql.planner.plan.ProjectNode;

import java.util.Optional;

import static com.facebook.presto.sql.planner.plan.Patterns.project;

public class CanonicalizeProjectExpressions
        implements PatternBasedRule<ProjectNode>
{
    private static final Pattern<ProjectNode> PATTERN = project();

    @Override
    public Pattern<ProjectNode> getPattern()
    {
        return PATTERN;
    }

    @Override
    public Optional<PlanNode> apply(ProjectNode projectNode, Captures captures, Context context)
    {
        Assignments assignments = projectNode.getAssignments()
                .rewrite(CanonicalizeExpressions::canonicalizeExpression);

        if (assignments.equals(projectNode.getAssignments())) {
            return Optional.empty();
        }

        PlanNode replacement = new ProjectNode(projectNode.getId(), projectNode.getSource(), assignments);

        return Optional.of(replacement);
    }
}