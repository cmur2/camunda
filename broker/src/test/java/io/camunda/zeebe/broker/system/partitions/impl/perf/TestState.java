/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.system.partitions.impl.perf;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.ConsistencyChecksSettings;
import io.camunda.zeebe.db.ZeebeDbFactory;
import io.camunda.zeebe.db.impl.DbString;
import io.camunda.zeebe.db.impl.rocksdb.RocksDbConfiguration;
import io.camunda.zeebe.db.impl.rocksdb.ZeebeRocksDbFactory;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.scheduler.ActorScheduler;
import io.camunda.zeebe.snapshots.ConstructableSnapshotStore;
import io.camunda.zeebe.snapshots.impl.FileBasedSnapshotStoreFactory;
import io.camunda.zeebe.util.FileUtil;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import org.agrona.CloseHelper;
import org.agrona.concurrent.UnsafeBuffer;

final class TestState {

  private static final int BATCH_INSERT_SIZE = 10_000;
  private static final int KEY_VALUE_SIZE = 8096;

  TestContext generateContext(final long sizeInBytes) throws Exception {
    final var tempDirectory = Files.createTempDirectory("statePerf");
    final var actorScheduler =
        ActorScheduler.newActorScheduler()
            .setIoBoundActorThreadCount(1)
            .setCpuBoundActorThreadCount(1)
            .build();
    actorScheduler.start();

    final var storeFactory = new FileBasedSnapshotStoreFactory(actorScheduler, 1);
    final var snapshotStore = createSnapshotStore(storeFactory, tempDirectory);
    generateSnapshot(snapshotStore, sizeInBytes);

    return new TestContext(
        actorScheduler, tempDirectory, snapshotStore, createDbFactory(), storeFactory);
  }

  private void generateSnapshot(
      final ConstructableSnapshotStore snapshotStore, final long sizeInBytes) {
    final var snapshot = snapshotStore.newTransientSnapshot(1, 1, 1, 1).get();
    snapshot.take(path -> generateSnapshot(path, sizeInBytes)).join();
    snapshot.persist().join();
  }

  @SuppressWarnings("resource")
  private ConstructableSnapshotStore createSnapshotStore(
      final FileBasedSnapshotStoreFactory storeFactory, final Path tempDirectory) {
    storeFactory.createReceivableSnapshotStore(tempDirectory, 1);
    return storeFactory.getConstructableSnapshotStore(1);
  }

  private void generateSnapshot(final Path path, final long sizeInBytes) {
    final var dbFactory = createDbFactory();

    //noinspection ResultOfMethodCallIgnored
    path.toFile().mkdirs();

    do {
      try (final var db = dbFactory.createDb(path.toFile())) {
        final var txn = db.createContext();
        final var columns =
            Arrays.stream(ZbColumnFamilies.values())
                .map(col -> db.createColumnFamily(col, txn, new DbString(), new DbString()))
                .toList();
        txn.runInTransaction(() -> insertData(columns));
      }
    } while (computeSnapshotSize(path) < sizeInBytes);
  }

  private ZeebeRocksDbFactory<ZbColumnFamilies> createDbFactory() {
    return new ZeebeRocksDbFactory<>(
        new RocksDbConfiguration(), new ConsistencyChecksSettings(false, false));
  }

  private void insertData(final List<ColumnFamily<DbString, DbString>> columns) {
    final var random = ThreadLocalRandom.current();

    for (int i = 0; i < BATCH_INSERT_SIZE; i++) {
      final var column = columns.get(random.nextInt(columns.size()));
      column.insert(generateData(), generateData());
    }
  }

  private DbString generateData() {
    final var buffer = new byte[KEY_VALUE_SIZE];
    final var data = new DbString();
    ThreadLocalRandom.current().nextBytes(buffer);
    data.wrapBuffer(new UnsafeBuffer(buffer));

    return data;
  }

  private static long computeSnapshotSize(final Path root) {
    try (final var files = Files.walk(root)) {
      return files.mapToLong(TestState::uncheckedFileSize).sum();
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static long uncheckedFileSize(final Path file) {
    try {
      return Files.size(file);
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  public record TestContext(
      ActorScheduler actorScheduler,
      Path temporaryFolder,
      ConstructableSnapshotStore snapshotStore,
      ZeebeDbFactory<ZbColumnFamilies> dbFactory,
      FileBasedSnapshotStoreFactory snapshotStoreFactory)
      implements AutoCloseable {

    @Override
    public void close() throws Exception {
      CloseHelper.quietCloseAll(snapshotStore, actorScheduler);
      FileUtil.deleteFolder(temporaryFolder);
    }

    public long snapshotSize() {
      return computeSnapshotSize(temporaryFolder);
    }
  }
}
