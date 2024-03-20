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

import javax.persistence.GeneratedValue;
import javax.persistence.PrePersist;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import lombok.Data;

@Document
@Data
@CompoundIndexes({
    @CompoundIndex(name = "queue_message_pk", def = "{'queue_name' : 1, 'message_id': 1}", unique = true),
    @CompoundIndex(name = "combo_queue_message", def = "{'queue_name' : 1, 'priority': 1, 'popped' : 1, 'deliver_on': 1, 'created_on': 1}")
})
public class QueueMessageDocument {
	
	@Id
	@GeneratedValue
	@Field("_id")
	private String id; 
	
	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}
	
	@Indexed(name = "queue_message_queue_name_idx")
	@Field("queue_name")
	private String queueName;
 	@Field("message_id")
	private String messageId;
 	@Field("priority")
 	private int priority;
    @Field("popped")
	private boolean popped;
    @Field("offset_time_seconds")
	private long offsetTimeSeconds;
    @Field("payload")
	private String payload;
    @Field("created_on")
	@Temporal(TemporalType.TIMESTAMP)
	private Date created_on;
    @Field("deliver_on")
	@Temporal(TemporalType.TIMESTAMP)
	private Date deliverOn;
    
	public QueueMessageDocument() {
		super();
		// TODO Auto-generated constructor stub
	}

	public QueueMessageDocument(String queueName, String messageId, int priority, boolean popped,
			long offsetTimeSeconds, String payload, Date created_on, Date deliverOn) {
		super();
		this.queueName = queueName;
		this.messageId = messageId;
		this.priority = priority;
		this.popped = popped;
		this.offsetTimeSeconds = offsetTimeSeconds;
		this.payload = payload;
		this.created_on = created_on;
		this.deliverOn = deliverOn;
	}

	@PrePersist
	protected void onCreate() {
		created_on = deliverOn = new Date();
	}

	public int getPriority() {
		return priority;
	}

	public void setPriority(int priority) {
		this.priority = priority;
	}

	public String getQueueName() {
		return queueName;
	}

	public void setQueueName(String queueName) {
		this.queueName = queueName;
	}

	public String getMessageId() {
		return messageId;
	}

	public void setMessageId(String messageId) {
		this.messageId = messageId;
	}

	public long getOffsetTimeSeconds() {
		return offsetTimeSeconds;
	}

	public void setOffsetTimeSeconds(long offsetTimeSeconds) {
		this.offsetTimeSeconds = offsetTimeSeconds;
	}

	public boolean isPopped() {
		return popped;
	}

	public void setPopped(boolean popped) {
		this.popped = popped;
	}

	public String getPayload() {
		return payload;
	}

	public void setPayload(String payload) {
		this.payload = payload;
	}

	public Date getCreated_on() {
		return created_on;
	}

	public void setCreated_on(Date created_on) {
		this.created_on = created_on;
	}

	public Date getDeliverOn() {
		return deliverOn;
	}

	public void setDeliverOn(Date deliverOn) {
		this.deliverOn = deliverOn;
	}

}
