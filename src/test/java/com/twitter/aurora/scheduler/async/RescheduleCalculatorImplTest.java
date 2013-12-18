/*
 * Copyright 2013 Twitter, Inc.
 *
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
package com.twitter.aurora.scheduler.async;

import java.util.EnumSet;

import javax.annotation.Nullable;

import com.google.common.collect.ImmutableSet;

import org.easymock.Capture;
import org.junit.Before;
import org.junit.Test;

import com.twitter.aurora.gen.ScheduleStatus;
import com.twitter.aurora.gen.ScheduledTask;
import com.twitter.aurora.gen.TaskEvent;
import com.twitter.aurora.scheduler.base.Query;
import com.twitter.aurora.scheduler.base.Tasks;
import com.twitter.aurora.scheduler.storage.Storage.MutableStoreProvider;
import com.twitter.aurora.scheduler.storage.Storage.MutateWork;
import com.twitter.aurora.scheduler.storage.TaskStore;
import com.twitter.aurora.scheduler.storage.entities.IScheduledTask;
import com.twitter.common.quantity.Amount;
import com.twitter.common.quantity.Time;
import com.twitter.common.testing.easymock.EasyMockTest;

import static com.twitter.aurora.gen.ScheduleStatus.ASSIGNED;
import static com.twitter.aurora.gen.ScheduleStatus.FAILED;
import static com.twitter.aurora.gen.ScheduleStatus.INIT;
import static com.twitter.aurora.gen.ScheduleStatus.PENDING;
import static com.twitter.aurora.gen.ScheduleStatus.RESTARTING;
import static com.twitter.aurora.gen.ScheduleStatus.RUNNING;
import static org.easymock.EasyMock.expect;

public class RescheduleCalculatorImplTest extends EasyMockTest {
  @Before
  public void setUp() {

  }

  @Test
  public void testNoPenaltyForNoAncestor() {
    // If a task doesn't have an ancestor there should be no penality for flapping.
    IScheduledTask task = makeTask("a1", INIT);

    expectOfferDeclineIn(10);
    Capture<Runnable> first = expectTaskGroupBackoff(1);
    expectTaskScheduled(task);

    replayAndCreateScheduler();
    offerQueue.addOffer(OFFER_A);

    changeState(task, INIT, PENDING);

    first.getValue().run();
  }

  @Test
  public void testFlappingTasksBackoffTruncation() {
    makeFlappyTask("a0", null);
    makeFlappyTask("a1", "a0");
    makeFlappyTask("a2", "a1");
    IScheduledTask taskA3 = IScheduledTask.build(makeTask("a3", INIT).newBuilder()
        .setAncestorId("a2"));

    expectOfferDeclineIn(10);

    Capture<Runnable> first = expectTaskGroupBackoff(10);
    // The ancestry chain is 3 long, but if the backoff strategy truncates, we don't traverse the
    // entire history.
    expect(flappingStrategy.calculateBackoffMs(0)).andReturn(5L);
    expect(flappingStrategy.calculateBackoffMs(5L)).andReturn(5L);
    Capture<Runnable> flapping = expectTaskRetryIn(10);

    expectTaskScheduled(taskA3);

    replayAndCreateScheduler();
    offerQueue.addOffer(OFFER_A);

    changeState(taskA3, INIT, PENDING);

    first.getValue().run();
    clock.waitFor(10);
    flapping.getValue().run();
  }

  @Test
  public void testFlappingTasks() {
    makeFlappyTask("a0", null);
    IScheduledTask taskA1 = IScheduledTask.build(makeTask("a1", INIT).newBuilder()
        .setAncestorId("a0"));

    expectOfferDeclineIn(10);
    Capture<Runnable> first = expectTaskGroupBackoff(10);

    expect(flappingStrategy.calculateBackoffMs(0)).andReturn(5L);
    // Since A1 has been penalized, the task has to wait for another 10 ms until the penalty has
    // expired.
    Capture<Runnable> flapping = expectTaskRetryIn(10);

    expectTaskScheduled(taskA1);

    replayAndCreateScheduler();

    offerQueue.addOffer(OFFER_A);

    changeState(taskA1, INIT, PENDING);

    first.getValue().run();
    clock.waitFor(10);
    flapping.getValue().run();
  }

  @Test
  public void testNoPenaltyForInterruptedTasks() {
    makeFlappyTaskWithStates("a0", EnumSet.of(INIT, PENDING, ASSIGNED, RESTARTING, FAILED), null);
    IScheduledTask taskA1 = IScheduledTask.build(makeTask("a1", INIT).newBuilder()
        .setAncestorId("a0"));

    expectOfferDeclineIn(10);
    Capture<Runnable> first = expectTaskGroupBackoff(10);

    expectTaskScheduled(taskA1);

    replayAndCreateScheduler();

    offerQueue.addOffer(OFFER_A);

    changeState(taskA1, INIT, PENDING);

    first.getValue().run();
  }

  private IScheduledTask makeFlappyTaskWithStates(
      String taskId,
      Iterable<ScheduleStatus> states,
      @Nullable String ancestorId) {

    Amount<Long, Time> timeInState = Amount.of(10L, Time.SECONDS);

    ScheduledTask base = makeTask(taskId, INIT).newBuilder();

    for (ScheduleStatus status : states) {
      base.addToTaskEvents(new TaskEvent(clock.nowMillis(), status));
      clock.advance(timeInState);
    }

    base.setAncestorId(ancestorId);

    final IScheduledTask result = IScheduledTask.build(base);

    // Insert the task if it doesn't already exist.
    storage.write(new MutateWork.NoResult.Quiet() {
      @Override protected void execute(MutableStoreProvider storeProvider) {
        TaskStore.Mutable taskStore = storeProvider.getUnsafeTaskStore();
        if (taskStore.fetchTasks(Query.taskScoped(Tasks.id(result))).isEmpty()) {
          taskStore.saveTasks(ImmutableSet.of(result));
        }
      }
    });

    return result;
  }

  private IScheduledTask makeFlappyTask(String taskId, @Nullable String ancestorId) {
    return makeFlappyTaskWithStates(
        taskId,
        EnumSet.of(INIT, PENDING, ASSIGNED, RUNNING, FAILED),
        ancestorId);
  }
}
