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
package com.facebook.presto.plugin.postgresql;

import com.facebook.presto.tests.AbstractTestQueries;
import com.facebook.presto.tests.datatype.CreateAndInsertDataSetup;
import com.facebook.presto.tests.datatype.CreateAsSelectDataSetup;
import com.facebook.presto.tests.datatype.DataTypeTest;
import com.facebook.presto.tests.sql.JdbcSqlExecutor;
import com.facebook.presto.tests.sql.PrestoSqlExecutor;
import io.airlift.testing.postgresql.TestingPostgreSqlServer;
import io.airlift.tpch.TpchTable;
import org.testng.annotations.AfterClass;
import org.testng.annotations.Test;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

import static com.facebook.presto.plugin.postgresql.PostgreSqlQueryRunner.createPostgreSqlQueryRunner;
import static com.facebook.presto.tests.datatype.DataType.varcharDataType;
import static io.airlift.testing.Closeables.closeAllRuntimeException;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

@Test
public class TestPostgreSqlDistributedQueries
        extends AbstractTestQueries
{
    private final TestingPostgreSqlServer postgreSqlServer;

    public TestPostgreSqlDistributedQueries()
            throws Exception
    {
        this(new TestingPostgreSqlServer("testuser", "tpch"));
    }

    public TestPostgreSqlDistributedQueries(TestingPostgreSqlServer postgreSqlServer)
            throws Exception
    {
        super(createPostgreSqlQueryRunner(postgreSqlServer, TpchTable.getTables()));
        this.postgreSqlServer = postgreSqlServer;
    }

    @AfterClass(alwaysRun = true)
    public final void destroy()
            throws IOException
    {
        closeAllRuntimeException(postgreSqlServer);
    }

    @Test
    public void testDropTable()
            throws Exception
    {
        assertUpdate("CREATE TABLE test_drop AS SELECT 123 x", 1);
        assertTrue(queryRunner.tableExists(getSession(), "test_drop"));

        assertUpdate("DROP TABLE test_drop");
        assertFalse(queryRunner.tableExists(getSession(), "test_drop"));
    }

    @Test
    public void testViews()
            throws Exception
    {
        execute("CREATE OR REPLACE VIEW tpch.test_view AS SELECT * FROM tpch.orders");

        assertQuery("SELECT orderkey FROM test_view", "SELECT orderkey FROM orders");

        execute("DROP VIEW IF EXISTS tpch.test_view");
    }

    @Test
    public void testPrestoCreatedParametrizedVarchar()
            throws Exception
    {
        PrestoSqlExecutor presto = new PrestoSqlExecutor(queryRunner);
        createVarcharDataTypeTest()
                .execute(queryRunner, new CreateAsSelectDataSetup(presto, "presto_test_parameterized_varchar"));
    }

    @Test
    public void testPostgreSqlCreatedParametrizedVarchar()
            throws Exception
    {
        JdbcSqlExecutor postgres = new JdbcSqlExecutor(postgreSqlServer.getJdbcUrl());
        createVarcharDataTypeTest()
                .execute(queryRunner, new CreateAndInsertDataSetup(postgres, "tpch.postgresql_test_parameterized_varchar"));
    }

    private DataTypeTest createVarcharDataTypeTest()
    {
        return DataTypeTest.create()
                .addRoundtrip(varcharDataType(10), "text_a")
                .addRoundtrip(varcharDataType(255), "text_b")
                .addRoundtrip(varcharDataType(65535), "text_d")
                .addRoundtrip(varcharDataType(10485760), "text_f")
                .addRoundtrip(varcharDataType(), "unbounded");
    }

    private void execute(String sql)
            throws SQLException
    {
        try (Connection connection = DriverManager.getConnection(postgreSqlServer.getJdbcUrl());
                Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }
}
