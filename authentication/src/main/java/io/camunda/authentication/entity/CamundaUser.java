/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.entity;

import io.camunda.search.entities.TenantEntity;
import io.camunda.security.entity.ClusterMetadata;
import io.camunda.security.entity.Permission;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;

public class CamundaUser extends User {

  private final Long userKey;
  private final String displayName;
  private List<Permission> permissions = List.of();
  private List<TenantEntity> tenants = List.of();
  private List<String> groups = List.of();
  private String salesPlanType;
  private Map<ClusterMetadata.AppName, String> c8Links = new HashMap<>();
  private boolean canLogout;
  private boolean apiUser;

  public CamundaUser(
      final Long userKey, final String displayName, final String username, final String password) {
    super(username, password, Collections.emptyList());
    this.userKey = userKey;
    this.displayName = displayName;
  }

  public CamundaUser(
      final String displayName,
      final String username,
      final String password,
      final List<String> roles) {
    super(username, password, prepareAuthorities(roles));
    userKey = null;
    this.displayName = displayName;
  }

  public CamundaUser(
      final Long userKey,
      final String displayName,
      final String username,
      final String password,
      final List<String> roles,
      final List<Permission> permissions,
      final List<TenantEntity> tenants,
      final List<String> groups,
      final String salesPlanType,
      final Map<ClusterMetadata.AppName, String> c8Links,
      final boolean canLogout,
      final boolean apiUser) {
    super(username, password, prepareAuthorities(roles));
    this.userKey = userKey;
    this.displayName = displayName;
    this.permissions = permissions;
    this.tenants = tenants;
    this.groups = groups;
    this.salesPlanType = salesPlanType;
    this.c8Links = c8Links;
    this.canLogout = canLogout;
    this.apiUser = apiUser;
  }

  public Long getUserKey() {
    return userKey;
  }

  public String getName() {
    return displayName;
  }

  public String getUserId() {
    return getUsername();
  }

  public String getDisplayName() {
    return displayName;
  }

  public List<String> getRoles() {
    return getAuthorities().stream().map(GrantedAuthority::getAuthority).toList();
  }

  public List<Permission> getPermissions() {
    return permissions;
  }

  public List<TenantEntity> getTenants() {
    return tenants;
  }

  public String getSalesPlanType() {
    return salesPlanType;
  }

  public Map<ClusterMetadata.AppName, String> getC8Links() {
    return c8Links;
  }

  public boolean canLogout() {
    return canLogout;
  }

  private static List<SimpleGrantedAuthority> prepareAuthorities(final List<String> roles) {
    return roles.stream().map(SimpleGrantedAuthority::new).toList();
  }

  public static final class CamundaUserBuilder {
    private Long userKey;
    private String name;
    private String username;
    private String password;
    private List<String> roles = List.of();
    private List<Permission> permissions = List.of();
    private List<TenantEntity> tenants = List.of();
    private List<String> groups = List.of();
    private String salesPlanType;
    private Map<ClusterMetadata.AppName, String> c8Links;
    private boolean canLogout;
    private boolean apiUser;

    private CamundaUserBuilder() {}

    public static CamundaUserBuilder aCamundaUser() {
      return new CamundaUserBuilder();
    }

    public CamundaUserBuilder withUserKey(final Long userKey) {
      this.userKey = userKey;
      return this;
    }

    public CamundaUserBuilder withName(final String name) {
      this.name = name;
      return this;
    }

    public CamundaUserBuilder withUsername(final String username) {
      this.username = username;
      return this;
    }

    public CamundaUserBuilder withPassword(final String password) {
      this.password = password;
      return this;
    }

    public CamundaUserBuilder withRoles(final List<String> roles) {
      this.roles = roles;
      return this;
    }

    public CamundaUserBuilder withPermissions(final List<Permission> permissions) {
      this.permissions = permissions;
      return this;
    }

    public CamundaUserBuilder withTenants(final List<TenantEntity> tenants) {
      this.tenants = tenants;
      return this;
    }

    public CamundaUserBuilder withGroups(final List<String> groups) {
      this.groups = groups;
      return this;
    }

    public CamundaUserBuilder withSalesPlanType(final String salesPlanType) {
      this.salesPlanType = salesPlanType;
      return this;
    }

    public CamundaUserBuilder withC8Links(final Map<ClusterMetadata.AppName, String> c8Links) {
      this.c8Links = c8Links;
      return this;
    }

    public CamundaUserBuilder withCanLogout(final boolean canLogout) {
      this.canLogout = canLogout;
      return this;
    }

    public CamundaUserBuilder withApiUser(final boolean apiUser) {
      this.apiUser = apiUser;
      return this;
    }

    public CamundaUser build() {
      return new CamundaUser(
          userKey,
          name,
          username,
          password,
          roles,
          permissions,
          tenants,
          groups,
          salesPlanType,
          c8Links,
          canLogout,
          apiUser);
    }
  }
}
