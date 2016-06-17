--- Test query with window functions in window specifications as follows:
--- window specification A
--- window specification B
--- window specification A
--- No window function in any window has constant parameters.
-- database: presto; groups: window;
select
suppkey, orderkey, partkey,
sum(quantity) over (partition by suppkey order by orderkey rows between unbounded preceding and current row) sum_quantity_A,
sum(quantity) over (partition by orderkey order by shipdate rows between UNBOUNDED preceding and CURRENT ROW) sum_quantity_B,
sum(discount) over (partition by suppkey order by orderkey rows between unbounded preceding and current row) sum_discount_A

from tpch.tiny.lineitem where (partkey = 272 or partkey = 273) and suppkey > 50
