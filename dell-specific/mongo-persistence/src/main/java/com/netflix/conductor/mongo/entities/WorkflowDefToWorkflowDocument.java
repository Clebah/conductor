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
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import lombok.Data;

@Document(collection = "workflow_def_to_workflow")
@Data
@CompoundIndexes({
    @CompoundIndex(name = "workflow_def_to_workflow_pk_uk", def = "{'workflow_def' : 1, 'date_str': 1, 'workflow_id': 1}", unique = true)
})
public class WorkflowDefToWorkflowDocument {

	@Indexed(name = "workflow_def_to_workflow_workflow_id_idx")
	@Field("workflow_id")
	private String workflow_id;
	@Field("workflow_def")
	private String workflow_def;
	@Field("date_str")
	private String date_str;
    @Field("created_on")
	@Temporal(TemporalType.TIMESTAMP)
	private Date created_on;
    @Field("modified_on")
	@Temporal(TemporalType.TIMESTAMP)
	private Date modified_on;
    
	public WorkflowDefToWorkflowDocument() {
		super();
		// TODO Auto-generated constructor stub
	}

	public WorkflowDefToWorkflowDocument(String workflow_id, String workflow_def, String date_str, Date created_on,
			Date modified_on) {
		super();
		this.workflow_id = workflow_id;
		this.workflow_def = workflow_def;
		this.date_str = date_str;
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

	public String getWorkflow_id() {
		return workflow_id;
	}

	public void setWorkflow_id(String workflow_id) {
		this.workflow_id = workflow_id;
	}

	public String getWorkflow_def() {
		return workflow_def;
	}

	public void setWorkflow_def(String workflow_def) {
		this.workflow_def = workflow_def;
	}

	public String getDate_str() {
		return date_str;
	}

	public void setDate_str(String date_str) {
		this.date_str = date_str;
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
