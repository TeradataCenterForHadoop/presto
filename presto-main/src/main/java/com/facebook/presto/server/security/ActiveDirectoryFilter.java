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
package com.facebook.presto.server.security;

import com.google.inject.Inject;

import javax.naming.NamingException;

import static java.util.Objects.requireNonNull;

public class ActiveDirectoryFilter
        extends LdapFilter
{
        private final String activeDirectoryDomain;

        @Inject
        public ActiveDirectoryFilter(LdapServerConfig config)
        {
                super(config);
                activeDirectoryDomain = requireNonNull(config.getActiveDirectoryDomain(), "ldap.ad.domain is null");
        }

        @Override
        protected String getPrincipal(String user)
                throws NamingException
        {
                return String.format("%s@%s", user, activeDirectoryDomain);
        }
}
