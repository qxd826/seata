/*
 *  Copyright 1999-2018 Alibaba Group Holding Ltd.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package io.seata.server.coordinator;

import io.seata.common.XID;
import io.seata.core.exception.TransactionException;
import io.seata.core.model.BranchStatus;
import io.seata.core.model.BranchType;
import io.seata.core.model.GlobalStatus;
import io.seata.core.model.ResourceManagerInbound;
import io.seata.server.session.BranchSession;
import io.seata.server.session.GlobalSession;
import io.seata.server.session.SessionHolder;
import org.testng.Assert;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.Collection;

/**
 * The type Default core test.
 *
 * @author zhimo.xiao @gmail.com
 * @since 2019 /1/23
 */
public class DefaultCoreTest {

    private static Core core = new DefaultCore();

    private static final String applicationId = "demo-child-app";

    private static final String txServiceGroup = "my_test_tx_group";

    private static final String txName = "tx-1";

    private static final int timeout = 3000;

    private static final String resourceId = "tb_1";

    private static final String clientId = "c_1";

    private static final String lockKeys_1 = "tb_1:11";

    private static final String lockKeys_2 = "tb_1:12";

    private static final String applicationData = "{\"data\":\"test\"}";

    /**
     * Init session manager.
     *
     * @throws Exception the exception
     */
    @BeforeTest
    public void initSessionManager() throws Exception {
        SessionHolder.init(null);
    }

    /**
     * Branch register test.
     *
     * @param xid the xid
     * @throws Exception the exception
     */
    @Test(dataProvider = "xidProvider")
    public void branchRegisterTest(String xid) throws Exception {
        core.branchRegister(BranchType.AT, resourceId, clientId, xid, "abc", lockKeys_1);
        long transactionId = XID.getTransactionId(xid);
        GlobalSession globalSession = SessionHolder.findGlobalSession(transactionId);
        Assert.assertEquals(globalSession.getSortedBranches().size(), 1);
    }

    /**
     * Branch report test.
     *
     * @param xid      the xid
     * @param branchId the branch id
     * @throws Exception the exception
     */
    @Test(dataProvider = "xidAndBranchIdProvider")
    public void branchReportTest(String xid, Long branchId) throws Exception {
        core.branchReport(BranchType.AT, xid, branchId, BranchStatus.PhaseOne_Done, applicationData);
        long transactionId = XID.getTransactionId(xid);
        GlobalSession globalSession = SessionHolder.findGlobalSession(transactionId);
        BranchSession branchSession = globalSession.getBranch(branchId);
        Assert.assertEquals(branchSession.getStatus(), BranchStatus.PhaseOne_Done);
    }

    /**
     * Begin test.
     *
     * @throws Exception the exception
     */
    @Test
    public void beginTest() throws Exception {
        String xid = core.begin(applicationId, txServiceGroup, txName, timeout);
        long transactionId = XID.getTransactionId(xid);
        GlobalSession globalSession = SessionHolder.findGlobalSession(transactionId);
        Assert.assertNotNull(globalSession);
    }

    /**
     * Commit test.
     *
     * @param xid the xid
     * @throws Exception the exception
     */
    @Test(dataProvider = "xidProvider")
    public void commitTest(String xid) throws Exception {
        GlobalStatus globalStatus = core.commit(xid);
        Assert.assertNotEquals(globalStatus, GlobalStatus.Begin);
    }

    /**
     * Do global commit test.
     *
     * @param xid the xid
     * @throws Exception the exception
     */
    @Test(dataProvider = "xidProvider")
    public void doGlobalCommitCommitTest(String xid) throws Exception {
        GlobalSession globalSession = SessionHolder.findGlobalSession(XID.getTransactionId(xid));
        BranchSession branchSession = new BranchSession();
        globalSession.addBranch(branchSession);
        globalSession.changeBranchStatus(branchSession, BranchStatus.PhaseOne_Done);
        core.setResourceManagerInbound(new MockResourceManagerInbound(BranchStatus.PhaseTwo_Committed, BranchStatus.PhaseOne_Done));
        core.doGlobalCommit(globalSession, false);
        Assert.assertEquals(globalSession.getStatus(), GlobalStatus.Committed);
    }


    /**
     * Do global commit test.
     *
     * @param xid the xid
     * @throws Exception the exception
     */
    @Test(dataProvider = "xidProvider")
    public void doGlobalCommitUnretryableTest(String xid) throws Exception {
        GlobalSession globalSession = SessionHolder.findGlobalSession(XID.getTransactionId(xid));
        BranchSession branchSession = new BranchSession();
        globalSession.addBranch(branchSession);
        globalSession.changeBranchStatus(branchSession, BranchStatus.PhaseOne_Done);
        core.setResourceManagerInbound(new MockResourceManagerInbound(BranchStatus.PhaseTwo_CommitFailed_Unretryable, BranchStatus.PhaseOne_Done));
        core.doGlobalCommit(globalSession, false);
        Assert.assertEquals(globalSession.getStatus(), GlobalStatus.Begin);
    }

    /**
     * Do global commit test.
     *
     * @param xid the xid
     * @throws Exception the exception
     */
    @Test(dataProvider = "xidProvider")
    public void doGlobalCommitExpTest(String xid) throws Exception {
        GlobalSession globalSession = SessionHolder.findGlobalSession(XID.getTransactionId(xid));
        BranchSession branchSession = new BranchSession();
        globalSession.addBranch(branchSession);
        globalSession.changeBranchStatus(branchSession, BranchStatus.PhaseOne_Done);
        core.setResourceManagerInbound(new MockResourceManagerInbound(BranchStatus.PhaseOne_Timeout, BranchStatus.PhaseOne_Done));
        core.doGlobalCommit(globalSession, false);
        Assert.assertEquals(globalSession.getStatus(), GlobalStatus.CommitRetrying);
    }

    /**
     * Roll back test.
     *
     * @param xid the xid
     * @throws Exception the exception
     */
    @Test(dataProvider = "xidProvider")
    public void rollBackTest(String xid) throws Exception {
        GlobalStatus globalStatus = core.rollback(xid);
        Assert.assertEquals(globalStatus, GlobalStatus.Rollbacked);
    }

    /**
     * Do global roll back test.
     *
     * @param xid the xid
     * @throws Exception the exception
     */
    @Test(dataProvider = "xidProvider")
    public void doGlobalRollBackRollbackedTest(String xid) throws Exception {
        GlobalSession globalSession = SessionHolder.findGlobalSession(XID.getTransactionId(xid));
        BranchSession branchSession = new BranchSession();
        globalSession.addBranch(branchSession);
        globalSession.changeBranchStatus(branchSession, BranchStatus.PhaseOne_Done);
        core.setResourceManagerInbound(new MockResourceManagerInbound(BranchStatus.PhaseTwo_Committed, BranchStatus.PhaseTwo_Rollbacked));
        core.doGlobalRollback(globalSession, false);
        Assert.assertEquals(globalSession.getStatus(), GlobalStatus.Rollbacked);
    }


    /**
     * Do global roll back test.
     *
     * @param xid the xid
     * @throws Exception the exception
     */
    @Test(dataProvider = "xidProvider")
    public void doGlobalRollBackUnretryableTest(String xid) throws Exception {
        GlobalSession globalSession = SessionHolder.findGlobalSession(XID.getTransactionId(xid));
        BranchSession branchSession = new BranchSession();
        globalSession.addBranch(branchSession);
        globalSession.changeBranchStatus(branchSession, BranchStatus.PhaseOne_Done);
        core.setResourceManagerInbound(new MockResourceManagerInbound(BranchStatus.PhaseTwo_Committed, BranchStatus.PhaseTwo_RollbackFailed_Unretryable));
        core.doGlobalRollback(globalSession, false);
        Assert.assertEquals(globalSession.getStatus(), GlobalStatus.RollbackFailed);
    }

    /**
     * Do global roll back test.
     *
     * @param xid the xid
     * @throws Exception the exception
     */
    @Test(dataProvider = "xidProvider")
    public void doGlobalRollBackRetryableExpTest(String xid) throws Exception {
        GlobalSession globalSession = SessionHolder.findGlobalSession(XID.getTransactionId(xid));
        BranchSession branchSession = new BranchSession();
        globalSession.addBranch(branchSession);
        globalSession.changeBranchStatus(branchSession, BranchStatus.PhaseOne_Done);
        core.setResourceManagerInbound(new MockResourceManagerInbound(BranchStatus.PhaseTwo_Committed, BranchStatus.PhaseTwo_RollbackFailed_Retryable));
        core.doGlobalRollback(globalSession, false);
        Assert.assertEquals(globalSession.getStatus(), GlobalStatus.RollbackRetrying);
    }

    /**
     * Xid provider object [ ] [ ].
     *
     * @return the object [ ] [ ]
     * @throws Exception the exception
     */
    @DataProvider
    public static Object[][] xidProvider() throws Exception {
        String xid = core.begin(applicationId, txServiceGroup, txName, timeout);
        return new Object[][]{{xid}};
    }

    /**
     * Xid and branch id provider object [ ] [ ].
     *
     * @return the object [ ] [ ]
     * @throws Exception the exception
     */
    @DataProvider
    public static Object[][] xidAndBranchIdProvider() throws Exception {
        String xid = core.begin(applicationId, txServiceGroup, txName, timeout);
        Long branchId = core.branchRegister(BranchType.AT, resourceId, clientId, xid, null, lockKeys_2);
        return new Object[][]{{xid, branchId}};
    }

    /**
     * Release session manager.
     *
     * @throws Exception the exception
     */
    @AfterTest
    public void releaseSessionManager() throws Exception {
        Collection<GlobalSession> globalSessions = SessionHolder.getRootSessionManager().allSessions();
        Collection<GlobalSession> asyncGlobalSessions = SessionHolder.getAsyncCommittingSessionManager().allSessions();
        for (GlobalSession asyncGlobalSession : asyncGlobalSessions) {
            asyncGlobalSession.closeAndClean();
        }
        for (GlobalSession globalSession : globalSessions) {
            globalSession.closeAndClean();
        }
    }


    private static class MockResourceManagerInbound implements ResourceManagerInbound {

        private BranchStatus commitStatus;
        private BranchStatus rollbackStatus;

        public MockResourceManagerInbound(BranchStatus commitStatus, BranchStatus rollbackStatus) {
            this.commitStatus = commitStatus;
            this.rollbackStatus = rollbackStatus;
        }

        @Override
        public BranchStatus branchCommit(BranchType branchType, String xid, long branchId, String resourceId, String applicationData) throws TransactionException {
            return commitStatus;
        }

        @Override
        public BranchStatus branchRollback(BranchType branchType, String xid, long branchId, String resourceId, String applicationData) throws TransactionException {
            return rollbackStatus;
        }
    }

}
