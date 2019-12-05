/*******************************************************************************
 * Copyright (c) 2017 Kiel University and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.eclipse.elk.alg.spore;

import java.util.List;

import org.eclipse.elk.alg.spore.graph.Graph;
import org.eclipse.elk.alg.spore.options.StructureExtractionStrategy;
import org.eclipse.elk.core.alg.AlgorithmAssembler;
import org.eclipse.elk.core.alg.ILayoutProcessor;
import org.eclipse.elk.core.util.IElkProgressMonitor;

/**
 * Implements a compaction algorithm for graphs with focus on layout stability.
 * The approach is comprised of three phases:
 * <p> <h4>Structure Phase:</h4> A set of vertices is created where each vertex is the center position of an 
 * element in the input graph. The set of vertices is triangulated so a list of edges can be derived that span
 * all vertices in a connected component.</p>
 * <p> <h4>Processing Order Phase:</h4> This phase finds a spanning tree for the connected component generated by the 
 * previous phase.</p>
 * <p> <h4>Execution Phase:</h4> The spanning tree is now used to traverse the graph in a post-order and
 * gaps between graph elements are closed to achieve a compaction effect. This shrinking of a tree is the origin of 
 * the class' name.</p>
 * 
 * <dt>precondition: </dt> The input graph has to be laid out in a way that is overlap free.
 * <dt>postcondition: </dt>
 */
public final class ShrinkTree {
    private AlgorithmAssembler<SPOrEPhases, Graph> algorithmAssembler =
            AlgorithmAssembler.<SPOrEPhases, Graph>create(SPOrEPhases.class);
    private List<ILayoutProcessor<Graph>> algorithm;
    
    /**
     * Executes the phases of compaction by shrinking a tree.
     * 
     * @param graph the graph to compact
     * @param progressMonitor a progress monitor
     */
    public void shrink(final Graph graph, final IElkProgressMonitor progressMonitor) {
        // assembling the algorithm
        algorithmAssembler.reset();
        algorithmAssembler.setPhase(SPOrEPhases.P1_STRUCTURE, 
                StructureExtractionStrategy.DELAUNAY_TRIANGULATION);
        algorithmAssembler.setPhase(SPOrEPhases.P2_PROCESSING_ORDER, 
                graph.treeConstructionStrategy);
        algorithmAssembler.setPhase(SPOrEPhases.P3_EXECUTION, 
                graph.compactionStrategy);
        algorithm = algorithmAssembler.build(graph);
        // ------------------------

        progressMonitor.begin("Compaction by shrinking a tree", algorithm.size());
        
        // only compact if there's more than one element to avoid lack of compaction edges and root node
        if (graph.vertices.size() > 1) {
            for (ILayoutProcessor<Graph> processor : algorithm) {
                processor.process(graph, progressMonitor.subTask(1));
            }
        }

        progressMonitor.done();
    }
}
