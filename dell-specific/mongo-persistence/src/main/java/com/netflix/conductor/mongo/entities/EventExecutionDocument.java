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

@Document(collection = "event_execution")
@Data
@CompoundIndexes({
    @CompoundIndex(name = "event_execution_pk_uk", def = "{'event_handler_name' : 1, 'event_name': 1, 'execution_id': 1}", unique = true)
})
public class EventExecutionDocument {
	
	@Indexed(name = "event_execution_event_name_idx")
	@Field("event_name")
	private String event_name;
 	@Indexed
 	@Field("event_handler_name")
	private String event_handler_name;
 	@Field("message_id")
	private String message_id;
 	@Field("execution_id")
	private String execution_id;
    @Field("json_data")
	private String json_data;
    @Field("created_on")
	@Temporal(TemporalType.TIMESTAMP)
	private Date created_on;
    @Field("modified_on")
	@Temporal(TemporalType.TIMESTAMP)
	private Date modified_on;
    
	public EventExecutionDocument() {
		super();
		// TODO Auto-generated constructor stub
	}

	public EventExecutionDocument(String event_name, String event_handler_name, String message_id, String execution_id,
			String json_data, Date created_on, Date modified_on) {
		super();
		this.event_name = event_name;
		this.event_handler_name = event_handler_name;
		this.message_id = message_id;
		this.execution_id = execution_id;
		this.json_data = json_data;
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


	public String getEvent_name() {
		return event_name;
	}

	public void setEvent_name(String event_name) {
		this.event_name = event_name;
	}

	public String getEvent_handler_name() {
		return event_handler_name;
	}

	public void setEvent_handler_name(String event_handler_name) {
		this.event_handler_name = event_handler_name;
	}

	public String getMessage_id() {
		return message_id;
	}

	public void setMessage_id(String message_id) {
		this.message_id = message_id;
	}

	public String getExecution_id() {
		return execution_id;
	}

	public void setExecution_id(String execution_id) {
		this.execution_id = execution_id;
	}

	public String getJson_data() {
		return json_data;
	}

	public void setJson_data(String json_data) {
		this.json_data = json_data;
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
