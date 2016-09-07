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
package com.facebook.presto.tests.jdbc;

import com.facebook.presto.tests.ImmutableTpchTablesRequirements.ImmutableNationTable;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.teradata.tempto.BeforeTestWithContext;
import com.teradata.tempto.ProductTest;
import com.teradata.tempto.Requirement;
import com.teradata.tempto.RequirementsProvider;
import com.teradata.tempto.Requires;
import com.teradata.tempto.configuration.Configuration;
import com.teradata.tempto.fulfillment.ldap.LdapObjectRequirement;
import com.teradata.tempto.query.QueryResult;
import org.slf4j.LoggerFactory;
import org.testng.annotations.Test;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;

import static com.facebook.presto.tests.ImmutableLdapObjectDefinitions.AMERICA_ORG;
import static com.facebook.presto.tests.ImmutableLdapObjectDefinitions.ASIA_ORG;
import static com.facebook.presto.tests.ImmutableLdapObjectDefinitions.CHILD_GROUP;
import static com.facebook.presto.tests.ImmutableLdapObjectDefinitions.CHILD_GROUP_USER;
import static com.facebook.presto.tests.ImmutableLdapObjectDefinitions.DEFAULT_GROUP;
import static com.facebook.presto.tests.ImmutableLdapObjectDefinitions.DEFAULT_GROUP_USER;
import static com.facebook.presto.tests.ImmutableLdapObjectDefinitions.ORPHAN_USER;
import static com.facebook.presto.tests.ImmutableLdapObjectDefinitions.PARENT_GROUP;
import static com.facebook.presto.tests.ImmutableLdapObjectDefinitions.PARENT_GROUP_USER;
import static com.facebook.presto.tests.TestGroups.LDAP;
import static com.facebook.presto.tests.TestGroups.PROFILE_SPECIFIC_TESTS;
import static com.facebook.presto.tests.TestGroups.SIMBA_JDBC;
import static com.facebook.presto.tests.TpchTableResults.PRESTO_NATION_RESULT;
import static com.facebook.presto.tests.utils.JdbcDriverUtils.usingTeradataJdbcDriver;
import static com.teradata.tempto.assertions.QueryAssert.assertThat;
import static com.teradata.tempto.query.QueryExecutor.defaultQueryExecutor;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;

public class LdapTests
        extends ProductTest
        implements RequirementsProvider

{
    private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(LdapTests.class);
    private static final long TIMEOUT = 300 * 1000; // 30 secs per test

    private static final String NATION_SELECT_ALL_QUERY = "select * from tpch.tiny.nation";
    private static final String JDBC_URL_FORMAT = "jdbc:presto://%s:%s;AuthenticationType=LDAP Authentication;" +
            "SSLTrustStorePath=%s;SSLTrustStorePwd=%s;AllowSelfSignedServerCert=1;AllowHostNameCNMismatch=1";

    private static final String DEFAULT_SSL_PORT = "8443";

    private static final String SSL_CERTIFICATE_ERROR =
            "[Teradata][Presto](100140) SSL certificate error: Keystore was tampered with, or password was incorrect.";
    private static final String INVALID_CREDENTIALS_ERROR =
            "[Teradata][Presto](100240) Authentication failed: Invalid credentials.";
    private static final String UNAUTHORIZED_USER_ERROR =
            "[Teradata][Presto](100240) Authentication failed: Unauthorized User.";
    private static final String INVALID_SSL_PROPERTY =
            "[Teradata][Presto](100200) Connection string is invalid: SSL value is not valid for given AuthenticationType.";

    @Inject(optional = true)
    @Named("databases.presto.cli_ldap_truststore_path")
    private String ldapTruststorePath;

    @Inject(optional = true)
    @Named("databases.presto.cli_ldap_truststore_password")
    private String ldapTruststorePassword;

    @Inject(optional = true)
    @Named("databases.presto.cli_ldap_user_name")
    private String ldapUserName;

    @Inject(optional = true)
    @Named("databases.presto.cli_ldap_user_password")
    private String ldapUserPassword;

    @Inject(optional = true)
    @Named("databases.presto.host")
    private String prestoHost;

    // TODO: Find out why this doesn't bring in the value from the config file.
    @Inject(optional = true)
    @Named("databases.presto.ssl_port")
    private String prestoSslPort;

    private boolean usingTeradataDriver = false;

    @BeforeTestWithContext
    public void setup()
            throws SQLException
    {
        if (usingTeradataJdbcDriver(defaultQueryExecutor().getConnection())) {
            if (prestoSslPort == null) {
                LOGGER.warn("databases.presto.ssl_port not specified in properties file.  Using default value: " +
                        DEFAULT_SSL_PORT);
                prestoSslPort = DEFAULT_SSL_PORT;
            }
            LOGGER.debug("Using Presto server SSL port: " + prestoSslPort);
            usingTeradataDriver = true;
        }
        else {
            LOGGER.warn("Tests in this class only apply to Teradata Jdbc Driver");
        }
    }

    @Override
    public Requirement getRequirements(Configuration configuration)
    {
        return new LdapObjectRequirement(
                Arrays.asList(
                        AMERICA_ORG, ASIA_ORG,
                        DEFAULT_GROUP, PARENT_GROUP, CHILD_GROUP,
                        DEFAULT_GROUP_USER, PARENT_GROUP_USER, CHILD_GROUP_USER, ORPHAN_USER
                ));
    }

    @Requires(ImmutableNationTable.class)
    @Test(groups = {LDAP, SIMBA_JDBC, PROFILE_SPECIFIC_TESTS}, timeOut = TIMEOUT)
    public void shouldRunQueryWithLdap()
            throws InterruptedException, SQLException
    {
        if (usingTeradataDriver) {
            assertThat(executeLdapQuery(NATION_SELECT_ALL_QUERY, ldapUserName, ldapUserPassword)).matches(PRESTO_NATION_RESULT);
        }
    }

    @Test(groups = {LDAP, SIMBA_JDBC, PROFILE_SPECIFIC_TESTS}, timeOut = TIMEOUT)
    public void shouldFailQueryForLdapUserInChildGroup()
            throws InterruptedException
    {
        if (usingTeradataDriver) {
            String name = CHILD_GROUP_USER.getAttributes().get("cn");
            expectQueryToFailForUserNotInGroup(name);
        }
    }

    @Test(groups = {LDAP, SIMBA_JDBC, PROFILE_SPECIFIC_TESTS}, timeOut = TIMEOUT)
    public void shouldFailQueryForLdapUserInParentGroup()
            throws InterruptedException
    {
        if (usingTeradataDriver) {
            String name = PARENT_GROUP_USER.getAttributes().get("cn");
            expectQueryToFailForUserNotInGroup(name);
        }
    }

    @Test(groups = {LDAP, SIMBA_JDBC, PROFILE_SPECIFIC_TESTS}, timeOut = TIMEOUT)
    public void shouldFailQueryForOrphanLdapUser()
            throws InterruptedException
    {
        if (usingTeradataDriver) {
            String name = ORPHAN_USER.getAttributes().get("cn");
            expectQueryToFailForUserNotInGroup(name);
        }
    }

    @Test(groups = {LDAP, SIMBA_JDBC, PROFILE_SPECIFIC_TESTS}, timeOut = TIMEOUT)
    public void shouldFailQueryForWrongLdapPassword()
            throws IOException, InterruptedException
    {
        if (usingTeradataDriver) {
            expectQueryToFail(ldapUserName, "wrong_password", INVALID_CREDENTIALS_ERROR);
        }
    }

    @Test(groups = {LDAP, SIMBA_JDBC, PROFILE_SPECIFIC_TESTS}, timeOut = TIMEOUT)
    public void shouldFailQueryForWrongLdapUser()
            throws IOException, InterruptedException
    {
        if (usingTeradataDriver) {
            expectQueryToFail("invalid_user", ldapUserPassword, INVALID_CREDENTIALS_ERROR);
        }
    }

    @Test(groups = {LDAP, SIMBA_JDBC, PROFILE_SPECIFIC_TESTS}, timeOut = TIMEOUT)
    public void shouldFailQueryForEmptyUser()
            throws IOException, InterruptedException
    {
        if (usingTeradataDriver) {
            expectQueryToFail("", ldapUserPassword, INVALID_CREDENTIALS_ERROR);
        }
    }

    @Test(groups = {LDAP, SIMBA_JDBC, PROFILE_SPECIFIC_TESTS}, timeOut = TIMEOUT)
    public void shouldFailQueryForLdapWithoutPassword()
            throws IOException, InterruptedException
    {
        if (usingTeradataDriver) {
            expectQueryToFail(ldapUserName, "", INVALID_CREDENTIALS_ERROR);
        }
    }

    @Test(groups = {LDAP, SIMBA_JDBC, PROFILE_SPECIFIC_TESTS}, timeOut = TIMEOUT)
    public void shouldFailQueryForLdapWithoutSsl()
            throws IOException, InterruptedException
    {
        if (usingTeradataDriver) {
            try {
                DriverManager.getConnection(getLdapUrl() + ";SSL=0", ldapUserName, ldapUserPassword);
                fail();
            }
            catch (SQLException exception) {
                assertEquals(exception.getMessage(), INVALID_SSL_PROPERTY);
            }
        }
    }

    @Test(groups = {LDAP, SIMBA_JDBC, PROFILE_SPECIFIC_TESTS}, timeOut = TIMEOUT)
    public void shouldFailForIncorrectTrustStore()
            throws IOException, InterruptedException
    {
        if (usingTeradataDriver) {
            try {
                String url = String.format(JDBC_URL_FORMAT, prestoHost, prestoSslPort, ldapTruststorePath, "wrong_password");
                DriverManager.getConnection(url, ldapUserName, ldapUserPassword);
                fail();
            }
            catch (SQLException exception) {
                assertEquals(exception.getMessage(), SSL_CERTIFICATE_ERROR);
            }
        }
    }

    @Test(groups = {LDAP, SIMBA_JDBC, PROFILE_SPECIFIC_TESTS}, timeOut = TIMEOUT)
    public void shouldFailForUserWithColon()
           throws SQLException, InterruptedException
    {
        expectQueryToFail("UserWith:Colon", ldapUserPassword, "Illegal character ':' found in user name");
    }

    private void expectQueryToFailForUserNotInGroup(String user)
    {
        expectQueryToFail(user, ldapUserPassword, UNAUTHORIZED_USER_ERROR);
    }

    private void expectQueryToFail(String user, String password, String message)
    {
        try {
            executeLdapQuery(NATION_SELECT_ALL_QUERY, user, password);
            fail();
        }
        catch (SQLException exception) {
            assertEquals(exception.getMessage(), message);
        }
    }

    private QueryResult executeLdapQuery(String query, String name, String password)
        throws SQLException
    {
        try (Connection connection = getLdapConnection(name, password)) {
            Statement statement = connection.createStatement();
            ResultSet rs = statement.executeQuery(query);
            return QueryResult.forResultSet(rs);
        }
    }

    private Connection getLdapConnection(String name, String password)
            throws SQLException
    {
        return DriverManager.getConnection(getLdapUrl(), name, password);
    }

    private String getLdapUrl()
    {
        return String.format(JDBC_URL_FORMAT, prestoHost, prestoSslPort, ldapTruststorePath, ldapTruststorePassword);
    }
}
