/*
 * Copyright 2015-present Open Networking Foundation
 * Copyright © 2020 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.atomix.raft.impl;

import static com.google.common.base.MoreObjects.toStringHelper;
import static com.google.common.base.Preconditions.checkNotNull;

import io.atomix.cluster.MemberId;
import io.atomix.raft.RaftRoleChangeListener;
import io.atomix.raft.RaftServer;
import io.atomix.raft.RaftThreadContextFactory;
import io.atomix.raft.cluster.RaftCluster;
import io.atomix.raft.storage.RaftStorage;
import io.atomix.utils.logging.ContextualLoggerFactory;
import io.atomix.utils.logging.LoggerContext;
import io.camunda.zeebe.util.health.FailureListener;
import java.util.Collection;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.slf4j.Logger;

/**
 * Provides a standalone implementation of the <a href="http://raft.github.io/">Raft consensus
 * algorithm</a>.
 *
 * @see RaftStorage
 */
public class DefaultRaftServer implements RaftServer {

  protected final RaftContext context;
  private final Logger log;
  private final AtomicReference<CompletableFuture<RaftServer>> openFutureRef =
      new AtomicReference<>();
  private volatile boolean started;
  private volatile boolean stopped = false;

  public DefaultRaftServer(final RaftContext context) {
    this.context = checkNotNull(context, "context cannot be null");
    log =
        ContextualLoggerFactory.getLogger(
            getClass(),
            LoggerContext.builder(RaftServer.class).addValue(context.getName()).build());
  }

  @Override
  public String toString() {
    return toStringHelper(this).add("name", name()).toString();
  }

  @Override
  public String name() {
    return context.getName();
  }

  @Override
  public RaftCluster cluster() {
    return context.getCluster();
  }

  @Override
  public void addRoleChangeListener(final RaftRoleChangeListener listener) {
    context.addRoleChangeListener(listener);
  }

  @Override
  public void removeRoleChangeListener(final RaftRoleChangeListener listener) {
    context.removeRoleChangeListener(listener);
  }

  @Override
  public void addFailureListener(final FailureListener listener) {
    context.addFailureListener(listener);
  }

  @Override
  public void removeFailureListener(final FailureListener listener) {
    context.removeFailureListener(listener);
  }

  @Override
  public CompletableFuture<RaftServer> bootstrap(final Collection<MemberId> cluster) {
    return start(() -> cluster().bootstrap(cluster));
  }

  @Override
  public CompletableFuture<RaftServer> leave() {
    return context.leave().thenApply(v -> this);
  }

  @Override
  public CompletableFuture<RaftServer> promote() {
    return context.anoint().thenApply(v -> this);
  }

  @Override
  public CompletableFuture<Void> flushLog() {
    return context.flushLog();
  }

  /**
   * Shuts down the server without leaving the Raft cluster.
   *
   * @return A completable future to be completed once the server has been shutdown.
   */
  @Override
  public CompletableFuture<Void> shutdown() {
    if (!started && !stopped) {
      return CompletableFuture.failedFuture(new IllegalStateException("Server not running"));
    }

    if (stopped) {
      return CompletableFuture.completedFuture(null);
    }

    return CompletableFuture.runAsync(
        () -> {
          stopped = true;
          started = false;
          context.transition(Role.INACTIVE);
          context.close();
        },
        context.getThreadContext());
  }

  @Override
  public RaftContext getContext() {
    return context;
  }

  @Override
  public long getTerm() {
    return context.getTerm();
  }

  @Override
  public Role getRole() {
    return context.getRole();
  }

  /**
   * Returns a boolean indicating whether the server is running.
   *
   * @return Indicates whether the server is running.
   */
  @Override
  public boolean isRunning() {
    return started && !stopped && context.isRunning();
  }

  @Override
  public CompletableFuture<Void> stepDown() {
    return CompletableFuture.runAsync(
        () -> context.transition(Role.FOLLOWER), context.getThreadContext());
  }

  @Override
  public CompletableFuture<RaftServer> join(final Collection<MemberId> cluster) {
    return start(() -> cluster().join(cluster));
  }

  /** Starts the server. */
  private CompletableFuture<RaftServer> start(final Supplier<CompletableFuture<Void>> joiner) {
    if (started) {
      return CompletableFuture.completedFuture(this);
    }

    if (openFutureRef.compareAndSet(null, new CompletableFuture<>())) {
      stopped = false;
      joiner
          .get()
          .whenComplete(
              (result, error) -> {
                if (error == null) {
                  log.info("Server join completed. Waiting for the server to be READY");
                  context.awaitState(
                      RaftContext.State.READY,
                      state -> {
                        started = true;
                        openFutureRef.get().complete(this);
                      });
                } else {
                  openFutureRef.get().completeExceptionally(error);
                }
              });
    }

    return openFutureRef
        .get()
        .whenComplete(
            (result, error) -> {
              if (error == null) {
                log.debug("Server started successfully!");
              } else {
                log.warn("Failed to start server", error);
              }
            });
  }

  /** Default Raft server builder. */
  public static class Builder extends RaftServer.Builder {

    public Builder(final MemberId localMemberId) {
      super(localMemberId);
    }

    @Override
    public RaftServer build() {

      // If the server name is null, set it to the member ID.
      if (name == null) {
        name = localMemberId.id();
      }

      // If the storage is not configured, create a new Storage instance with the configured
      // serializer.
      if (storage == null) {
        storage = RaftStorage.builder().build();
      }

      final RaftThreadContextFactory singleThreadFactory =
          threadContextFactory == null
              ? new DefaultRaftSingleThreadContextFactory()
              : threadContextFactory;
      final Supplier<Random> randomSupplier = randomFactory == null ? Random::new : randomFactory;

      final RaftContext raft =
          new RaftContext(
              name,
              partitionId,
              localMemberId,
              membershipService,
              protocol,
              storage,
              singleThreadFactory,
              randomSupplier,
              electionConfig,
              partitionConfig);
      raft.setEntryValidator(entryValidator);

      return new DefaultRaftServer(raft);
    }
  }
}
