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
package com.facebook.presto.sql.tree;

import com.google.common.collect.ImmutableList;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static com.google.common.base.MoreObjects.toStringHelper;
import static java.util.Objects.requireNonNull;

public class Union
        extends SetOperation
{
    private final List<Relation> relations;
    private final boolean distinct;

    public Union(List<Relation> relations, boolean distinct)
    {
        this(Optional.empty(), relations, distinct);
    }

    public Union(NodeLocation location, List<Relation> relations, boolean distinct)
    {
        this(Optional.of(location), relations, distinct);
    }

    public Union(Optional<NodeLocation> location, List<Relation> relations, boolean distinct)
    {
        super(location);
        requireNonNull(relations, "relations is null");

        this.relations = ImmutableList.copyOf(relations);
        this.distinct = distinct;
    }

    public List<Relation> getRelations()
    {
        return relations;
    }

    public boolean isDistinct()
    {
        return distinct;
    }

    @Override
    public <R, C> R accept(AstVisitor<R, C> visitor, C context)
    {
        return visitor.visitUnion(this, context);
    }

    @Override
    public String toString()
    {
        return toStringHelper(this)
                .add("relations", relations)
                .add("distinct", distinct)
                .toString();
    }

    @Override
    public boolean equals(Object obj)
    {
        if (this == obj) {
            return true;
        }
        if ((obj == null) || (getClass() != obj.getClass())) {
            return false;
        }
        Union o = (Union) obj;
        return Objects.equals(relations, o.relations) &&
                Objects.equals(distinct, o.distinct);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(relations, distinct);
    }
}
