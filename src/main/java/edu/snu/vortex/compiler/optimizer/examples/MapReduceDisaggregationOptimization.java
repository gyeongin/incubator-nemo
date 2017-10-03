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
package edu.snu.vortex.compiler.optimizer.examples;

import edu.snu.vortex.common.coder.Coder;
import edu.snu.vortex.compiler.frontend.beam.BoundedSourceVertex;
import edu.snu.vortex.compiler.frontend.beam.transform.DoTransform;
import edu.snu.vortex.compiler.ir.*;
import edu.snu.vortex.compiler.optimizer.Optimizer;
import edu.snu.vortex.common.dag.DAG;
import edu.snu.vortex.common.dag.DAGBuilder;

import edu.snu.vortex.compiler.optimizer.policy.DisaggregationPolicy;
import edu.snu.vortex.runtime.executor.datatransfer.data_communication_pattern.OneToOne;
import edu.snu.vortex.runtime.executor.datatransfer.data_communication_pattern.ScatterGather;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static edu.snu.vortex.common.dag.DAG.EMPTY_DAG_DIRECTORY;

/**
 * A sample MapReduceDisaggregationOptimization application.
 */
public final class MapReduceDisaggregationOptimization {
  private static final Logger LOG = LoggerFactory.getLogger(MapReduceDisaggregationOptimization.class.getName());

  /**
   * Private constructor.
   */
  private MapReduceDisaggregationOptimization() {
  }

  /**
   * Main function of the example MR program.
   * @param args arguments.
   * @throws Exception Exceptions on the way.
   */
  public static void main(final String[] args) throws Exception {
    final IRVertex source = new BoundedSourceVertex<>(new EmptyComponents.EmptyBoundedSource("Source"));
    final IRVertex map = new OperatorVertex(new EmptyComponents.EmptyTransform("MapVertex"));
    final IRVertex reduce = new OperatorVertex(new DoTransform(null, null));

    // Before
    final DAGBuilder<IRVertex, IREdge> builder = new DAGBuilder<>();
    builder.addVertex(source);
    builder.addVertex(map);
    builder.addVertex(reduce);

    final IREdge edge1 = new IREdge(OneToOne.class, source, map, Coder.DUMMY_CODER);
    builder.connectVertices(edge1);

    final IREdge edge2 = new IREdge(ScatterGather.class, map, reduce, Coder.DUMMY_CODER);
    builder.connectVertices(edge2);

    final DAG<IRVertex, IREdge> dag = builder.build();
    LOG.info("Before Optimization");
    LOG.info(dag.toString());

    // Optimize
    final DAG optimizedDAG = Optimizer.optimize(dag, new DisaggregationPolicy(), EMPTY_DAG_DIRECTORY);

    // After
    LOG.info("After Optimization");
    LOG.info(optimizedDAG.toString());
  }
}