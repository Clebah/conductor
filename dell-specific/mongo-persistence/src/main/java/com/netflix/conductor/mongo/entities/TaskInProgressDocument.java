/*
 * Copyright 2020 Netflix, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package com.netflix.conductor.mongo.entities;

import java.util.Date;

import javax.persistence.PrePersist;
import javax.persistence.PreUpdate;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;

import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import lombok.Data;

@Document(collection = "task_in_progress")
@Data
@CompoundIndexes({
    @CompoundIndex(name = "task_in_progress_pk_uk", def = "{'task_def_name' : 1, 'task_id': 1}", unique = true)
})
public class TaskInProgressDocument {

	@Field("task_def_name")
	private String task_def_name;
 	@Field("workflow_id")
	private String workflow_id;
    @Field("task_id")
	private String task_id;
    @Field("in_progress_status")
	private boolean in_progress_status;
    @Field("created_on")
	@Temporal(TemporalType.TIMESTAMP)
	private Date created_on;
    @Field("modified_on")
	@Temporal(TemporalType.TIMESTAMP)
	private Date modified_on;
    
	public TaskInProgressDocument() {
		super();
		// TODO Auto-generated constructor stub
	}

	public TaskInProgressDocument(String task_def_name, String workflow_id, String task_id, boolean in_progress_status,
			Date created_on, Date modified_on) {
		super();
		this.task_def_name = task_def_name;
		this.workflow_id = workflow_id;
		this.task_id = task_id;
		this.in_progress_status = in_progress_status;
		this.created_on = created_on;
		this.modified_on = modified_on;
	}
    
	@PrePersist
	protected void onCreate() {
		created_on = modified_on = new Date();
	}

	@PreUpdate
	protected void onUpdate() {
		modified_on = new Date();
	}

	public String getTask_def_name() {
		return task_def_name;
	}

	public void setTask_def_name(String task_def_name) {
		this.task_def_name = task_def_name;
	}

	public String getWorkflow_id() {
		return workflow_id;
	}

	public void setWorkflow_id(String workflow_id) {
		this.workflow_id = workflow_id;
	}

	public String getTask_id() {
		return task_id;
	}

	public void setTask_id(String task_id) {
		this.task_id = task_id;
	}

	public boolean isIn_progress_status() {
		return in_progress_status;
	}

	public void setIn_progress_status(boolean in_progress_status) {
		this.in_progress_status = in_progress_status;
	}

	public Date getCreated_on() {
		return created_on;
	}

	public void setCreated_on(Date created_on) {
		this.created_on = created_on;
	}

	public Date getModified_on() {
		return modified_on;
	}

	public void setModified_on(Date modified_on) {
		this.modified_on = modified_on;
	}
	
}
