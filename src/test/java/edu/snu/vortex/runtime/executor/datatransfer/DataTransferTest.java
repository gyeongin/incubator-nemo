/*
 * Copyright (C) 2017 Seoul National University
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package edu.snu.vortex.runtime.executor.datatransfer;

import edu.snu.vortex.compiler.frontend.beam.BeamElement;
import edu.snu.vortex.compiler.ir.Element;
import edu.snu.vortex.runtime.common.RuntimeAttribute;
import edu.snu.vortex.runtime.common.RuntimeAttributeMap;
import edu.snu.vortex.runtime.executor.block.BlockManagerWorker;
import edu.snu.vortex.runtime.executor.block.LocalStore;
import edu.snu.vortex.runtime.master.BlockManagerMaster;
import org.apache.beam.sdk.values.KV;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.Assert.assertTrue;

/**
 * Tests {@link InputReader} and {@link OutputWriter}.
 */
public final class DataTransferTest {
  private static final RuntimeAttribute STORE = RuntimeAttribute.Local;
  private static final int PARALLELISM_TEN = 10;

  private BlockManagerMaster master;
  private BlockManagerWorker worker1;
  private BlockManagerWorker worker2;

  @Before
  public void setUp() {
    this.master = new BlockManagerMaster();
    this.worker1 = new BlockManagerWorker("worker1", master, new LocalStore());
    this.worker2 = new BlockManagerWorker("worker2", master, new LocalStore());
    this.master.addNewWorker(worker1);
    this.master.addNewWorker(worker2);
  }

  @Test
  public void testOneToOneSameWorker() {
    writeAndRead(worker1, worker1, RuntimeAttribute.OneToOne);
  }

  @Test
  public void testOneToOneDifferentWorker() {
    writeAndRead(worker1, worker2, RuntimeAttribute.OneToOne);
  }

  @Test
  public void testOneToManySameWorker() {
    writeAndRead(worker1, worker1, RuntimeAttribute.Broadcast);
  }

  @Test
  public void testOneToManyDifferentWorker() {
    writeAndRead(worker1, worker2, RuntimeAttribute.Broadcast);
  }

  @Test
  public void testManyToManySameWorker() {
    writeAndRead(worker1, worker1, RuntimeAttribute.ScatterGather);
  }

  @Test
  public void testManyToManyDifferentWorker() {
    writeAndRead(worker1, worker2, RuntimeAttribute.ScatterGather);
  }

  private void writeAndRead(final BlockManagerWorker sender,
                            final BlockManagerWorker receiver,
                            final RuntimeAttribute commPattern) {
    // Src setup
    final RuntimeAttributeMap srcVertexAttributes = new RuntimeAttributeMap();
    srcVertexAttributes.put(RuntimeAttribute.IntegerKey.Parallelism, PARALLELISM_TEN);

    // Dst setup
    final RuntimeAttributeMap dstVertexAttributes = new RuntimeAttributeMap();
    dstVertexAttributes.put(RuntimeAttribute.IntegerKey.Parallelism, PARALLELISM_TEN);

    // Edge setup
    final String edgeId = "Dummy";
    final RuntimeAttributeMap edgeAttributes = new RuntimeAttributeMap();
    edgeAttributes.put(RuntimeAttribute.Key.CommPattern, commPattern);
    edgeAttributes.put(RuntimeAttribute.Key.Partition, RuntimeAttribute.Hash);
    edgeAttributes.put(RuntimeAttribute.Key.BlockStore, STORE);

    // Initialize states in Master
    IntStream.range(0, PARALLELISM_TEN).forEach(srcTaskIndex -> {
      if (commPattern == RuntimeAttribute.ScatterGather) {
        IntStream.range(0, PARALLELISM_TEN).forEach(dstTaskIndex -> {
          master.initializeState(edgeId, srcTaskIndex, dstTaskIndex);
        });
      } else {
        master.initializeState(edgeId, srcTaskIndex);
      }
    });

    // Write
    final List<List<Element>> dataWrittenList = new ArrayList<>();
    IntStream.range(0, PARALLELISM_TEN).forEach(srcTaskIndex -> {
      final List<Element> dataWritten = getListOfZeroToNine();
      final OutputWriter writer = new OutputWriter(edgeId, srcTaskIndex, dstVertexAttributes, edgeAttributes, sender);
      writer.write(dataWritten);
      dataWrittenList.add(dataWritten);
    });

    // Read
    final List<List<Element>> dataReadList = new ArrayList<>();
    IntStream.range(0, PARALLELISM_TEN).forEach(dstTaskIndex -> {
      final InputReader reader = new InputReader(edgeId, dstTaskIndex, srcVertexAttributes, edgeAttributes, receiver);
      final List<Element> dataRead = new ArrayList<>();
      reader.read().forEach(dataRead::add);
      dataReadList.add(dataRead);
    });

    // Compare (should be the same)
    final List<Element> flattenedWrittenData = flatten(dataWrittenList);
    final List<Element> flattenedReadData = flatten(dataReadList);
    assertTrue(doTheyHaveSameElements(flattenedWrittenData, flattenedReadData));
  }

  private List<Element> getListOfZeroToNine() {
    final List<Element> dummy = new ArrayList<>();
    IntStream.range(0, PARALLELISM_TEN).forEach(number -> dummy.add(new BeamElement<>(KV.of(number, number))));
    return dummy;
  }

  private List<Element> flatten(final List<List<Element>> listOfList) {
    return listOfList.stream().flatMap(list -> list.stream()).collect(Collectors.toList());
  }

  private boolean doTheyHaveSameElements(final List<Element> l, final List<Element> r) {
    // Check equality, ignoring list order
    final Set<Element> s1 = new HashSet<>(l);
    final Set<Element> s2 = new HashSet<>(r);
    return s1.equals(s2);
  }
}