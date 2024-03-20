/*
 *  Copyright 2021 Netflix, Inc.
 *  <p>
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 *  the License. You may obtain a copy of the License at
 *  <p>
 *  http://www.apache.org/licenses/LICENSE-2.0
 *  <p>
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 *  an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 *  specific language governing permissions and limitations under the License.
 */
package com.netflix.conductor.mongo.dao;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration;
import org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.testcontainers.containers.MongoDBContainer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.client.MongoClients;
import com.netflix.conductor.common.config.TestObjectMapperConfiguration;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.dao.ExecutionDAO;
import com.netflix.conductor.dao.ExecutionDAOTest;

@ContextConfiguration(classes = {TestObjectMapperConfiguration.class})
@RunWith(SpringRunner.class)
@SpringBootTest(properties = {"spring.main.allow-bean-definition-overriding=true"})
@Import({MongoAutoConfiguration.class, MongoDataAutoConfiguration.class})
public class MongoExecutionDAOTest extends ExecutionDAOTest {

    private MongoExecutionDAO executionDAO;

    @Autowired
    public ObjectMapper objectMapper;

    @Rule
    public TestName name = new TestName();

    @Rule
    public ExpectedException expected = ExpectedException.none();

    private static final MongoDBContainer MONGO_DB_CONTAINER =
    		  new MongoDBContainer("mongo:3.6.23");

  	  private static final String MONGO_INITDB_DATABASE = "conductor";

  	public MongoTemplate mongoTemplate;

	  @Before
	  public void setup() {
		if(!MONGO_DB_CONTAINER.isRunning())
				MONGO_DB_CONTAINER.withEnv("MONGO_INITDB_DATABASE", MONGO_INITDB_DATABASE).start();

	  	mongoTemplate = new MongoTemplate(MongoClients.create(MONGO_DB_CONTAINER.getReplicaSetUrl()), MONGO_INITDB_DATABASE);
    	executionDAO = new MongoExecutionDAO(objectMapper, mongoTemplate);
    }

	  @Test
      @Override
	  public void testTaskExceedsLimit() {
	        TaskDef taskDefinition = new TaskDef();
	        taskDefinition.setName("task1");
	        taskDefinition.setConcurrentExecLimit(1);

	        WorkflowTask workflowTask = new WorkflowTask();
	        workflowTask.setName("task1");
	        workflowTask.setTaskDefinition(taskDefinition);
	        workflowTask.setTaskDefinition(taskDefinition);

	        List<TaskModel> tasks = new LinkedList<>();
	        for (int i = 0; i < 15; i++) {
	            TaskModel task = new TaskModel();
	            task.setScheduledTime(1L);
	            task.setSeq(i + 1);
	            task.setTaskId("t_" + i);
	            task.setWorkflowInstanceId("workflow_" + i);
	            task.setReferenceTaskName("task1");
	            task.setTaskDefName("task1");
	            tasks.add(task);
	            task.setStatus(TaskModel.Status.SCHEDULED);
	            task.setWorkflowTask(workflowTask);
	        }

	        getExecutionDAO().createTasks(tasks);
	        tasks.get(0).setStatus(TaskModel.Status.IN_PROGRESS);
	        getExecutionDAO().updateTask(tasks.get(0));

	        for (TaskModel task : tasks) {
	            assertTrue(getExecutionDAO().exceedsInProgressLimit(task));
	        }
	    }

	  @Test
      @Override
	    public void testTaskCreateDups() {
	        List<TaskModel> tasks = new LinkedList<>();
	        String workflowId = UUID.randomUUID().toString();

	        for (int i = 0; i < 3; i++) {
	            TaskModel task = new TaskModel();
	            task.setScheduledTime(1L);
	            task.setSeq(i + 1);
	            task.setTaskId(workflowId + "_t" + i);
	            task.setReferenceTaskName("t" + i);
	            task.setRetryCount(0);
	            task.setWorkflowInstanceId(workflowId);
	            task.setTaskDefName("task" + i);
	            task.setStatus(TaskModel.Status.IN_PROGRESS);
	            tasks.add(task);
	        }

	        //Let's insert a retried task
	        TaskModel task = new TaskModel();
	        task.setScheduledTime(1L);
	        task.setSeq(1);
	        task.setTaskId(workflowId + "_t" + 2);
	        task.setReferenceTaskName("t" + 2);
	        task.setRetryCount(1);
	        task.setWorkflowInstanceId(workflowId);
	        task.setTaskDefName("task" + 2);
	        task.setStatus(TaskModel.Status.IN_PROGRESS);
	        tasks.add(task);

	        //Duplicate task!
	        task = new TaskModel();
	        task.setScheduledTime(1L);
	        task.setSeq(1);
	        task.setTaskId(workflowId + "_t" + 1);
	        task.setReferenceTaskName("t" + 1);
	        task.setRetryCount(0);
	        task.setWorkflowInstanceId(workflowId);
	        task.setTaskDefName("task" + 1);
	        task.setStatus(TaskModel.Status.IN_PROGRESS);
	        tasks.add(task);

	        List<TaskModel> created = getExecutionDAO().createTasks(tasks);
	        assertEquals(tasks.size() - 1, created.size());    //1 less

	        Set<String> srcIds = tasks.stream().map(t -> t.getReferenceTaskName() + "." + t.getRetryCount()).collect(
	            Collectors.toSet());
	        Set<String> createdIds = created.stream().map(t -> t.getReferenceTaskName() + "." + t.getRetryCount())
	            .collect(Collectors.toSet());

	        assertEquals(srcIds, createdIds);

	        List<TaskModel> pending = getExecutionDAO().getPendingTasksByWorkflow("task0", workflowId);
	        assertNotNull(pending);
	        assertEquals(0, pending.size());
	        if(pending.size()>0)
	        	assertTrue(EqualsBuilder.reflectionEquals(tasks.get(0), pending.get(0)));

	        List<TaskModel> found = getExecutionDAO().getTasks(tasks.get(0).getTaskDefName(), null, 1);
	        assertNotNull(found);
	        assertEquals(0, found.size());
	        if(found.size()>0)
	        	assertTrue(EqualsBuilder.reflectionEquals(tasks.get(0), found.get(0)));
	    }

	  @Test
      @Override
	  public void testTaskOps() {
	        List<TaskModel> tasks = new LinkedList<>();
	        String workflowId = UUID.randomUUID().toString();

	        for (int i = 0; i < 3; i++) {
	            TaskModel task = new TaskModel();
	            task.setScheduledTime(1L);
	            task.setSeq(1);
	            task.setTaskId(workflowId + "_t" + i);
	            task.setReferenceTaskName("testTaskOps" + i);
	            task.setRetryCount(0);
	            task.setWorkflowInstanceId(workflowId);
	            task.setTaskDefName("testTaskOps" + i);
	            task.setStatus(TaskModel.Status.IN_PROGRESS);
	            tasks.add(task);
	        }

	        for (int i = 0; i < 3; i++) {
	            TaskModel task = new TaskModel();
	            task.setScheduledTime(1L);
	            task.setSeq(1);
	            task.setTaskId("x" + workflowId + "_t" + i);
	            task.setReferenceTaskName("testTaskOps" + i);
	            task.setRetryCount(0);
	            task.setWorkflowInstanceId("x" + workflowId);
	            task.setTaskDefName("testTaskOps" + i);
	            task.setStatus(TaskModel.Status.IN_PROGRESS);
	            getExecutionDAO().createTasks(Collections.singletonList(task));
	        }

	        List<TaskModel> created = getExecutionDAO().createTasks(tasks);
	        assertEquals(tasks.size(), created.size());

	        List<TaskModel> pending = getExecutionDAO().getPendingTasksForTaskType(tasks.get(0).getTaskDefName());
	        assertNotNull(pending);
	        assertEquals(0, pending.size());
	        //Pending list can come in any order.  finding the one we are looking for and then comparing
	        Optional<TaskModel> matching = pending.stream().filter(task -> task.getTaskId().equals(tasks.get(0).getTaskId())).findAny();
	        if(matching.isPresent())
	        	assertTrue(EqualsBuilder.reflectionEquals(matching.get(), tasks.get(0)));

	        for (int i = 0; i < 3; i++) {
	            TaskModel found = getExecutionDAO().getTask(workflowId + "_t" + i);
	            assertNotNull(found);
	            found.getOutputData().put("updated", true);
	            found.setStatus(TaskModel.Status.COMPLETED);
	            getExecutionDAO().updateTask(found);
	        }

	        List<String> taskIds = tasks.stream().map(TaskModel::getTaskId).collect(Collectors.toList());
	        List<TaskModel> found = getExecutionDAO().getTasks(taskIds);
	        assertEquals(taskIds.size(), found.size());
	        found.forEach(task -> {
	            assertTrue(task.getOutputData().containsKey("updated"));
	            assertEquals(true, task.getOutputData().get("updated"));
	            boolean removed = getExecutionDAO().removeTask(task.getTaskId());
	            assertTrue(removed);
	        });

	        found = getExecutionDAO().getTasks(taskIds);
	        assertTrue(found.isEmpty());
	    }

	    @Test
	    @Override
	    public void complexExecutionTest() {
	        WorkflowModel workflow = createTestWorkflow();
	        int numTasks = workflow.getTasks().size();

	        String workflowId = getExecutionDAO().createWorkflow(workflow);
	        assertEquals(workflow.getWorkflowId(), workflowId);

	        List<TaskModel> created = getExecutionDAO().createTasks(workflow.getTasks());
	        assertEquals(workflow.getTasks().size(), created.size());

	        WorkflowModel workflowWithTasks = getExecutionDAO().getWorkflow(workflow.getWorkflowId(), true);
	        assertEquals(workflowId, workflowWithTasks.getWorkflowId());
	        assertEquals(numTasks, workflowWithTasks.getTasks().size());

	        WorkflowModel found = getExecutionDAO().getWorkflow(workflowId, false);
	        assertTrue(found.getTasks().isEmpty());

	        workflow.getTasks().clear();
	        assertEquals(workflow, found);

	        workflow.getInput().put("updated", true);
	        getExecutionDAO().updateWorkflow(workflow);
	        found = getExecutionDAO().getWorkflow(workflowId);
	        assertNotNull(found);
	        assertTrue(found.getInput().containsKey("updated"));
	        assertEquals(true, found.getInput().get("updated"));

	        List<String> running = getExecutionDAO()
	            .getRunningWorkflowIds(workflow.getWorkflowName(), workflow.getWorkflowVersion());
	        assertNotNull(running);
	        assertTrue(running.isEmpty());

	        workflow.setStatus(WorkflowModel.Status.RUNNING);
	        getExecutionDAO().updateWorkflow(workflow);

	        running = getExecutionDAO().getRunningWorkflowIds(workflow.getWorkflowName(), workflow.getWorkflowVersion());
	        assertNotNull(running);
	        assertEquals(1, running.size());
	        assertEquals(workflow.getWorkflowId(), running.get(0));

	        List<WorkflowModel> pending = getExecutionDAO()
	            .getPendingWorkflowsByType(workflow.getWorkflowName(), workflow.getWorkflowVersion());
	        assertNotNull(pending);
	        assertEquals(1, pending.size());
	        assertEquals(3, pending.get(0).getTasks().size());
	        pending.get(0).getTasks().clear();
	        assertEquals(workflow, pending.get(0));

	        workflow.setStatus(WorkflowModel.Status.COMPLETED);
	        getExecutionDAO().updateWorkflow(workflow);
	        running = getExecutionDAO().getRunningWorkflowIds(workflow.getWorkflowName(), workflow.getWorkflowVersion());
	        assertNotNull(running);
	        assertTrue(running.isEmpty());

	        List<WorkflowModel> bytime = getExecutionDAO()
	            .getWorkflowsByType(workflow.getWorkflowName(), System.currentTimeMillis(),
	                System.currentTimeMillis() + 100);
	        assertNotNull(bytime);
	        assertTrue(bytime.isEmpty());

	        bytime = getExecutionDAO().getWorkflowsByType(workflow.getWorkflowName(), workflow.getCreateTime() - 10,
	            workflow.getCreateTime() + 10);
	        assertNotNull(bytime);
	    }

  	 @Test
     public void testPendingByCorrelationId() {

         WorkflowDef def = new WorkflowDef();
         def.setName("pending_count_correlation_jtest");

         WorkflowModel workflow = createTestWorkflow();
         workflow.setWorkflowDefinition(def);

         generateWorkflows(workflow, 10);

         List<WorkflowModel> bycorrelationId = getExecutionDAO()
             .getWorkflowsByCorrelationId("pending_count_correlation_jtest", "corr001", true);
         assertNotNull(bycorrelationId);
         assertEquals(10, bycorrelationId.size());
     }

     @Override
     public ExecutionDAO getExecutionDAO() {
         return executionDAO;
     }
 }
