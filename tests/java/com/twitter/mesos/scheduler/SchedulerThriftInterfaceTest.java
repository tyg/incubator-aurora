package com.twitter.mesos.scheduler;

import com.google.common.collect.ImmutableSet;

import org.easymock.Capture;
import org.junit.Before;
import org.junit.Test;

import com.twitter.common.testing.EasyMockTest;
import com.twitter.mesos.gen.AssignedTask;
import com.twitter.mesos.gen.CreateJobResponse;
import com.twitter.mesos.gen.Identity;
import com.twitter.mesos.gen.JobConfiguration;
import com.twitter.mesos.gen.KillResponse;
import com.twitter.mesos.gen.Quota;
import com.twitter.mesos.gen.ResponseCode;
import com.twitter.mesos.gen.ScheduledTask;
import com.twitter.mesos.gen.SessionKey;
import com.twitter.mesos.gen.SetQuotaResponse;
import com.twitter.mesos.gen.TaskQuery;
import com.twitter.mesos.gen.TwitterTaskInfo;
import com.twitter.mesos.scheduler.auth.SessionValidator;
import com.twitter.mesos.scheduler.auth.SessionValidator.AuthFailedException;
import com.twitter.mesos.scheduler.quota.QuotaManager;

import static org.easymock.EasyMock.capture;
import static org.easymock.EasyMock.eq;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.expectLastCall;
import static org.junit.Assert.assertEquals;

/**
 * @author William Farner
 */
public class SchedulerThriftInterfaceTest extends EasyMockTest {

  private static final String ROLE = "bar_role";
  private static final String USER = "foo_user";
  private static final Identity ROLE_IDENTITY = new Identity(ROLE, USER);
  private static final SessionKey SESSION = new SessionKey()
      .setUser(USER);

  private SchedulerCore scheduler;
  private SessionValidator sessionValidator;
  private QuotaManager quotaManager;
  private SchedulerThriftInterface thriftInterface;

  @Before
  public void setUp() {
    scheduler = createMock(SchedulerCore.class);
    sessionValidator = createMock(SessionValidator.class);
    quotaManager = createMock(QuotaManager.class);
    thriftInterface = new SchedulerThriftInterface(scheduler, sessionValidator, quotaManager);
  }

  @Test
  public void testCreateJob() throws Exception {
    JobConfiguration job = makeJob();
    expectAuth(ROLE, true);
    scheduler.createJob(job);

    control.replay();

    CreateJobResponse response = thriftInterface.createJob(job, SESSION);
    assertEquals(ResponseCode.OK, response.getResponseCode());
  }

  @Test
  public void testCreateJobBadRequest() throws Exception {
    JobConfiguration job = makeJob();
    expectAuth(ROLE, true);
    scheduler.createJob(job);
    expectLastCall().andThrow(new ScheduleException("Error!"));

    control.replay();

    CreateJobResponse response = thriftInterface.createJob(job, SESSION);
    assertEquals(ResponseCode.INVALID_REQUEST, response.getResponseCode());
  }

  @Test
  public void testCreateJobAuthFailure() throws Exception {
    expectAuth(ROLE, false);

    control.replay();

    CreateJobResponse response = thriftInterface.createJob(makeJob(), SESSION);
    assertEquals(ResponseCode.AUTH_FAILED, response.getResponseCode());
  }

  @Test
  public void testKillTasks() throws Exception {
    TaskQuery query = new TaskQuery()
        .setOwner(ROLE_IDENTITY)
        .setJobName("foo_job");
    TaskState task = new TaskState(
        new ScheduledTask()
            .setAssignedTask(new AssignedTask()
                .setTask(new TwitterTaskInfo()
                    .setOwner(ROLE_IDENTITY))),
        new VolatileTaskState());

    expectAdminAuth(false);
    expectAuth(ROLE, true);
    Capture<Query> queryCapture = new Capture<Query>();
    expect(scheduler.getTasks(capture(queryCapture)))
        .andReturn(ImmutableSet.of(task));
    Capture<Query> killQueryCapture = new Capture<Query>();
    scheduler.killTasks(capture(killQueryCapture), eq(USER));

    control.replay();

    KillResponse response = thriftInterface.killTasks(query, SESSION);
    assertEquals(ResponseCode.OK, response.getResponseCode());
    assertEquals(queryCapture.getValue().base(), query);
    assertEquals(killQueryCapture.getValue().base(), query);
  }

  @Test
  public void testKillTasksAuthFailure() throws Exception {
    TaskQuery query = new TaskQuery()
        .setOwner(ROLE_IDENTITY)
        .setJobName("foo_job");
    TaskState task = new TaskState(
        new ScheduledTask()
            .setAssignedTask(new AssignedTask()
                .setTask(new TwitterTaskInfo()
                .setOwner(ROLE_IDENTITY))),
        new VolatileTaskState());

    expectAdminAuth(false);
    expectAuth(ROLE, false);
    Capture<Query> queryCapture = new Capture<Query>();
    expect(scheduler.getTasks(capture(queryCapture)))
        .andReturn(ImmutableSet.of(task));

    control.replay();

    KillResponse response = thriftInterface.killTasks(query, SESSION);
    assertEquals(ResponseCode.AUTH_FAILED, response.getResponseCode());
    assertEquals(queryCapture.getValue().base(), query);
  }

  @Test
  public void testAdminKillTasks() throws Exception {
    TaskQuery query = new TaskQuery()
        .setOwner(ROLE_IDENTITY)
        .setJobName("foo_job");

    expectAdminAuth(true);
    Capture<Query> killQueryCapture = new Capture<Query>();
    scheduler.killTasks(capture(killQueryCapture), eq(USER));

    control.replay();

    KillResponse response = thriftInterface.killTasks(query, SESSION);
    assertEquals(ResponseCode.OK, response.getResponseCode());
    assertEquals(killQueryCapture.getValue().base(), query);
  }

  @Test
  public void testSetQuota() throws Exception {
    Quota quota = new Quota()
        .setNumCpus(10)
        .setDiskMb(100)
        .setRamMb(200);
    expectAdminAuth(true);
    quotaManager.setQuota(ROLE, quota);

    control.replay();

    SetQuotaResponse response = thriftInterface.setQuota(ROLE, quota, SESSION);
    assertEquals(ResponseCode.OK, response.getResponseCode());
  }

  @Test
  public void testSetQuotaAuthFailure() throws Exception {
    Quota quota = new Quota()
        .setNumCpus(10)
        .setDiskMb(100)
        .setRamMb(200);
    expectAdminAuth(false);

    control.replay();

    SetQuotaResponse response = thriftInterface.setQuota(ROLE, quota, SESSION);
    assertEquals(ResponseCode.AUTH_FAILED, response.getResponseCode());
  }

  private JobConfiguration makeJob() {
    TwitterTaskInfo task = new TwitterTaskInfo();

    JobConfiguration job = new JobConfiguration()
        .setName("foo")
        .setOwner(ROLE_IDENTITY);
    job.addToTaskConfigs(task);
    return job;
  }

  private void expectAuth(String role, boolean allowed) throws AuthFailedException {
    sessionValidator.checkAuthenticated(SESSION, role);
    if (!allowed) {
      expectLastCall().andThrow(new AuthFailedException("Denied!"));
    }
  }

  private void expectAdminAuth(boolean allowed) throws AuthFailedException {
    expectAuth(SchedulerThriftInterface.ADMIN_ROLE.get(), allowed);
  }
}
