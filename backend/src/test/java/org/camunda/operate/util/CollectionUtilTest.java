/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.util;

import static org.assertj.core.api.Assertions.*;

import java.util.List;
import java.util.Map;

import org.assertj.core.util.Arrays;
import org.camunda.operate.exceptions.OperateRuntimeException;
import org.junit.Test;


public class CollectionUtilTest {

  @Test
  public void testAsMapOneEntry() {
    Map<String,Object> result = CollectionUtil.asMap("key1","value1");
    assertThat(result).hasSize(1);
    assertThat(result).containsEntry("key1", "value1");
  }
  
  @Test
  public void testAsMapManyEntries() {
    Map<String,Object> result = CollectionUtil.asMap("key1","value1","key2","value2","key3","value3");
    assertThat(result).hasSize(3);
    assertThat(result).containsEntry("key2", "value2");
    assertThat(result).containsEntry("key3", "value3");
  }
  
  @Test
  public void testAsMapException() {
    assertThatExceptionOfType(OperateRuntimeException.class).isThrownBy(() -> CollectionUtil.asMap((Object[])null)); 
    assertThatExceptionOfType(OperateRuntimeException.class).isThrownBy(() -> CollectionUtil.asMap("key1"));
    assertThatExceptionOfType(OperateRuntimeException.class).isThrownBy(() -> CollectionUtil.asMap("key1","value1","key2")); 
  }
  
  @Test
  public void testFromTo() {
    assertThat(CollectionUtil.fromTo(0,0)).contains(0);
    assertThat(CollectionUtil.fromTo(0,-1)).isEmpty();
    assertThat(CollectionUtil.fromTo(-1,0)).contains(-1,0);
    assertThat(CollectionUtil.fromTo(1,5)).contains(1,2,3,4,5);
  }
  
  @Test
  public void testWithoutNulls() {
    List<Object> ids = Arrays.asList(new Object[] {"id-1",null,"id3",null,null,"id5"});
    assertThat(CollectionUtil.withoutNulls(ids)).containsExactly("id-1","id3","id5");
  }
  
  @Test
  public void testToSafeListOfStrings() {
    List<Object> ids = Arrays.asList(new Object[] {"id-1",null,"id3",null,null,"id5"});
    assertThat(CollectionUtil.withoutNulls(ids)).containsExactly("id-1","id3","id5");
  }

}
