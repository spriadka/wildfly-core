/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2014, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.jboss.as.test.integration.domain;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HOST;
import static org.jboss.as.test.integration.management.util.ModelUtil.createOpNode;

import java.io.File;

import org.apache.commons.io.FileUtils;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.helpers.domain.DomainClient;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.test.integration.domain.management.util.DomainLifecycleUtil;
import org.jboss.as.test.integration.domain.management.util.DomainTestSupport;
import org.jboss.as.test.integration.security.common.AbstractBaseSecurityRealmsServerSetupTask;
import org.jboss.as.test.integration.security.common.CoreUtils;
import org.jboss.as.test.integration.security.common.SecurityTestConstants;
import org.jboss.as.test.integration.security.common.config.realm.RealmKeystore;
import org.jboss.as.test.integration.security.common.config.realm.SecurityRealm;
import org.jboss.as.test.integration.security.common.config.realm.ServerIdentity;
import org.jboss.dmr.ModelNode;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Tests one way SSL secured communication between master and slave.
 *
 * @author Ondrej Kotek <okotek@redhat.com>
 */
public class SSLMasterSlaveOneWayTestCase extends AbstractSSLMasterSlaveTestCase {

    private static final File WORK_DIR = new File("target" + File.separatorChar +  "ssl-master-slave-1way-workdir");
    private static final File SERVER_KEYSTORE_FILE = new File(WORK_DIR, SecurityTestConstants.SERVER_KEYSTORE);

    private static final MasterManagementRealmSetup masterManagementRealmSetup = new MasterManagementRealmSetup();

    private static DomainTestSupport testSupport;
    private static DomainLifecycleUtil domainMasterLifecycleUtil;


    @BeforeClass
    public static void setupDomain() throws Exception {
        keyMaterialSetup(WORK_DIR);

        DomainTestSupport.Configuration configuration = DomainTestSupport.Configuration.create(
                SSLMasterSlaveOneWayTestCase.class.getSimpleName(), "domain-configs/domain-standard.xml",
                "host-configs/host-master-ssl.xml", "host-configs/host-slave-ssl-1way.xml");

        testSupport = DomainTestSupport.createAndStartSupport(configuration);
        domainMasterLifecycleUtil = testSupport.getDomainMasterLifecycleUtil();

        masterManagementRealmSetup.setup(domainMasterLifecycleUtil.getDomainClient());
    }

    @AfterClass
    public static void tearDownDomain() throws Exception {
        masterManagementRealmSetup.tearDown(domainMasterLifecycleUtil.getDomainClient());

        testSupport.stop();
        testSupport = null;
        domainMasterLifecycleUtil = null;

        FileUtils.deleteDirectory(WORK_DIR);
    }

    @Before
    public void setMasterManagementNativeInterface() throws Exception {
        DomainClient masterClient = domainMasterLifecycleUtil.getDomainClient();

        addLocalAuthentication(masterClient);
        setMasterManagementNativeInterfaceAndCheck(masterClient);
    }

    @After
    public void setOriginMasterManagementNativeInterface() throws Exception {
        setOriginMasterManagementNativeInterfaceAndCheck();
    }


    @Test
    public void testReadSlaveStatusFromMaster() throws Exception {
        checkHostStatusOnMaster("slave");
    }


    private static void addLocalAuthentication(DomainClient client) throws Exception {
        ModelNode operation = createOpNode("host=master/core-service=management/security-realm=" + MASTER_MANAGEMENT_REALM
                + "/authentication=local", ModelDescriptionConstants.ADD);
        operation.get("default-user").set("$local");
        operation.get("skip-group-loading").set("true");
        CoreUtils.applyUpdate(operation, client);
    }

    static class MasterManagementRealmSetup extends AbstractBaseSecurityRealmsServerSetupTask {

        // Overridden just to expose locally
        @Override
        protected void setup(ModelControllerClient modelControllerClient) throws Exception {
            super.setup(modelControllerClient);
        }

        // Overridden just to expose locally
        @Override
        protected void tearDown(ModelControllerClient modelControllerClient) throws Exception {
            super.tearDown(modelControllerClient);
        }

        @Override
        protected PathAddress getBaseAddress() {
            return PathAddress.pathAddress(PathElement.pathElement(HOST, "master"));
        }

        @Override
        protected SecurityRealm[] getSecurityRealms() throws Exception {
            final ServerIdentity serverIdentity = new ServerIdentity.Builder().ssl(
                    new RealmKeystore.Builder().keystorePassword(SecurityTestConstants.KEYSTORE_PASSWORD)
                            .keystorePath(SERVER_KEYSTORE_FILE.getAbsolutePath()).build()).build();
            final SecurityRealm realm = new SecurityRealm.Builder().name(MASTER_MANAGEMENT_REALM)
                    .serverIdentity(serverIdentity).build();
            return new SecurityRealm[] { realm };
        }
    }
}
