package com.asu.secureBankApp.dao;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import constants.RoleType;

import javax.persistence.*;
import java.io.IOException;
import java.io.Serializable;
import java.sql.Timestamp;
import java.util.Date;
import java.util.Map;

@Entity(name = "account_request")
public class AccountRequestDAO {
	@Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long request_id;

    @Column(name="status_id")
    private Integer status_id;

    @Column(name="created_at")
    private Date createdAt;

    @Column(name="approved_at")
    private Date approvedAt;

    @Column(name="type")
    private int type;

    @Column(name="created_by")
    private String created_by;

    @Column(name="approved_by")
    private String approved_by;

    @Column(name="description")
    private String description;

    @Column(name="account")
    private String account;

    @Column(name="user")
    private String user;
    
    @Column(name="role")
    private Integer role;

	public Long getRequest_id() {
		return request_id;
	}

	public void setRequest_id(Long request_id) {
		this.request_id = request_id;
	}

	public Integer getStatus_id() {
		return status_id;
	}

	public void setStatus_id(Integer status_id) {
		this.status_id = status_id;
	}

	public Date getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(Date createdAt) {
		this.createdAt = createdAt;
	}

	public Date getApprovedAt() {
		return approvedAt;
	}

	public void setApprovedAt(Date approvedAt) {
		this.approvedAt = approvedAt;
	}

	public int getType() {
		return type;
	}

	public void setType(int type) {
		this.type = type;
	}

	public String getCreated_by() {
		return created_by;
	}

	public void setCreated_by(String created_by) {
		this.created_by = created_by;
	}

	public String getApproved_by() {
		return approved_by;
	}

	public void setApproved_by(String approved_by) {
		this.approved_by = approved_by;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public String getAccount() {
		return account;
	}

	public void setAccount(String account) {
		this.account = account;
	}

	public String getUser() {
		return user;
	}

	public void setUser(String user) {
		this.user = user;
	}

	public Integer getRole() {
		return role;
	}

	public void setRole(Integer role) {
		this.role = role;
	}

}
