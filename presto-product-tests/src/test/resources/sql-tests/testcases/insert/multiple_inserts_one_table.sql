-- database: presto; groups: insert; mutable_tables: datatype|created; 
-- delimiter: |; ignoreOrder: true; 
--!
insert into ${mutableTables.datatype} values(1, 2.1, 'abc', cast('2014-01-01' as date), cast('2015-01-01 03:15:16' as timestamp), FALSE);
insert into ${mutableTables.datatype} values(1, 2.1, 'abc', cast('2014-01-01' as date), cast('2015-01-01 03:15:16' as timestamp), FALSE);
insert into ${mutableTables.datatype} values(1, 2.1, 'abc', cast('2014-01-01' as date), cast('2015-01-01 03:15:16' as timestamp), FALSE);
select * from ${mutableTables.datatype}
--!
1|2.1|abc|2014-01-01|2015-01-01 03:15:16|false|
1|2.1|abc|2014-01-01|2015-01-01 03:15:16|false|
1|2.1|abc|2014-01-01|2015-01-01 03:15:16|false|
