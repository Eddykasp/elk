/*******************************************************************************
 * Copyright (c) 2024 Kiel University and others.
 * 
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 * 
 * SPDX-License-Identifier: EPL-2.0 
 *******************************************************************************/
package org.eclipse.elk.alg.layered.options;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.elk.alg.layered.LayeredPhases;
import org.eclipse.elk.alg.layered.graph.LEdge;
import org.eclipse.elk.alg.layered.graph.LGraph;
import org.eclipse.elk.alg.layered.graph.LNode;
import org.eclipse.elk.alg.layered.graph.transform.ElkGraphTransformer;
import org.eclipse.elk.alg.layered.graph.transform.IGraphTransformer;
import org.eclipse.elk.core.AbstractLayoutProvider;
import org.eclipse.elk.core.alg.ILayoutPhase;
import org.eclipse.elk.core.data.LayoutAlgorithmData;
import org.eclipse.elk.core.math.ElkPadding;
import org.eclipse.elk.core.math.KVector;
import org.eclipse.elk.core.options.CoreOptions;
import org.eclipse.elk.core.options.ITopdownSizeApproximator;
import org.eclipse.elk.core.util.ElkUtil;
import org.eclipse.elk.core.util.NullElkProgressMonitor;
import org.eclipse.elk.graph.ElkEdge;
import org.eclipse.elk.graph.ElkGraphFactory;
import org.eclipse.elk.graph.ElkNode;

/**
 * 
 * TODO:
 */

enum OnlyCycleBreaking {
    
    /** Elimination of cycles through edge reversal. */
    P1_CYCLE_BREAKING
}

public enum LayeredSizeApproximator implements ITopdownSizeApproximator {

    /**
     * TODO: better documentation
     * Use assumptions about layered layouts to estimate better aspect ratios for lookahead layout.
     */
    LAYERED_ASPECT_RATIO_LOOKAHEAD {
        
        private double aspectRatioHeuristic(final ElkNode parent) {
            // determine the longest path through the graph and use that as w
            // determine highest in- or outdegree and use it as h
            // aspect ratio is w/h
            
            

            //// FIND ESTIMATED LENGTH OF GRAPH
            // import graph into LGraph
            IGraphTransformer<ElkNode> graphTransformer = new ElkGraphTransformer();
            LGraph layeredGraph = graphTransformer.importGraph(parent);
            
            // run cycle breaker
            ILayoutPhase<LayeredPhases, LGraph> cycleBreaker = layeredGraph.getProperty(LayeredOptions.CYCLE_BREAKING_STRATEGY).create();
            cycleBreaker.process(layeredGraph, new NullElkProgressMonitor());
            
            // find longest path in resulting DAG
            double maxLength = longestPathDAG(layeredGraph);
            System.out.println("----" + parent.getIdentifier() + " len: " + maxLength);
            
            //// FIND ESTIMATED WIDTH OF GRAPH
            /// find highest in- or outdegree, at the end maxDegree = max(in_max, out_max, 1)
            double maxDegree = 1.0;
            for (ElkNode child: parent.getChildren()) {
                double inDegree = child.getIncomingEdges().size();
                double outDegree = child.getOutgoingEdges().size();
                if (inDegree > maxDegree) {
                    maxDegree = inDegree;
                }
                if (outDegree > maxDegree) {
                    maxDegree = outDegree;
                }
            }
            System.out.println("----" + parent.getIdentifier() + " deg: " + maxDegree);
            
            /// return estimated aspect ratio, rotate according to layout direction
            double estimatedAspectRatio = magicHeuristic(maxLength, maxDegree);
            if (parent.getProperty(CoreOptions.DIRECTION).isVertical()) {
                return estimatedAspectRatio;
            } else {
                return 1 / estimatedAspectRatio;
            }
        }

        /**
         * MAGIC
         * @param parent
         * @param maxLength
         * @param maxDegree
         * @return
         */
        private double magicHeuristic(double maxLength, double maxDegree) {
            // TODO: this is where the magic happens, how to correctly combine length and max degree to figure out approximate aspect ratio. ML?
            double dampening = 0.5 + (1-0.5) / (1 + Math.pow(maxDegree/5, 4)); // TODO: grows slower than degree increases, how to figure out suitable value?
            return (dampening * maxDegree) / (maxLength * 0.4);
        }
        
        // TODO: add 1 if an edge has a label
        /// PROBLEM this doesn't account for dummy nodes (e.g. edge labels) multiply 1.x where x is the typical ratio of edges that have labels (determine experimentally) somewhere between 0 and 1
        private double longestPathDAG(LGraph layeredGraph) {
            List<LNode> nodes = layeredGraph.getLayerlessNodes();
            Map<LNode, Double> longestPathTable = new HashMap<>();
            for (LNode node : nodes) {
                // path length is equal to the number of nodes in the path i.e. single node results in a length of 1
                longestPathTable.put(node, 1.0);
            }
            
            Map<LNode, Boolean> visited = new HashMap<>();
            for (LNode node : nodes) {
                visited.put(node, false);
            }
            
            for (LNode node : nodes) {
                if (!visited.get(node)) {
                    dfs(node, longestPathTable, visited);
                }
            }
            double length = 0;
            for (LNode node : longestPathTable.keySet()) {
                length = Math.max(length, longestPathTable.get(node));
            }
            
            return length;
        }
        
        private void dfs(LNode node, Map<LNode, Double> pathTable, Map<LNode, Boolean> visited) {
            visited.put(node, true);
            
            for (LEdge edge : node.getOutgoingEdges()) {
                LNode neighbour = edge.getOther(node);
                if (!visited.get(neighbour)) {
                    dfs(neighbour, pathTable, visited);
                }
                pathTable.put(node, Math.max(pathTable.get(node), 1 + pathTable.get(neighbour)));
            }
        }

        @Override
        public KVector getSize(final ElkNode originalGraph) {
            
            // for each child
                // analyse graph structure of grandchildren to determine child aspect ratio * some fixed unit size
            
            // perform layout on children
            
            // return approximated size of this node
            
            final LayoutAlgorithmData algorithmData = originalGraph.getProperty(CoreOptions.RESOLVED_ALGORITHM);
            
            /** LOOKAHEAD LAYOUT */
            // clone the current hierarchy
            ElkNode node = ElkGraphFactory.eINSTANCE.createElkNode();
            node.copyProperties(originalGraph);
            Map<ElkNode, ElkNode> oldToNewNodeMap = new HashMap<>();
            // copy children
            System.out.println("lookahead: " + originalGraph.getIdentifier());
            for (ElkNode child : originalGraph.getChildren()) {
                ElkNode newChild = ElkGraphFactory.eINSTANCE.createElkNode();
                newChild.setParent(node);
                newChild.copyProperties(child);
                /** GRANDCHILD LOOKAHEAD FOR SIZE ESTIMATION */
                // set size according to microlayout or analyse graph structure of grandchildren to determine child aspect ratio * some fixed unit size
                double aspectRatio = aspectRatioHeuristic(child);
                double unitSize = node.getProperty(CoreOptions.TOPDOWN_HIERARCHICAL_NODE_WIDTH); // TODO: <-- figure this out better
                if (child.getChildren() == null || child.getChildren().size() == 0) {
                    unitSize = 40;
                }
                // TODO: some unit size, not HIERARCHICAL_NODE_WIDTH because that would be too large, but arbitrary fixed 
                //       numbers are also generally bad
                newChild.setDimensions(unitSize, unitSize / aspectRatio);
                System.out.println("--" + child.getIdentifier() + ": " + unitSize + " " + aspectRatio);
                /** END GRANDCHILD LOOKAHEAD */
                oldToNewNodeMap.put(child, newChild);
            }
            // copy edges, explicitly assuming no hyperedges here
            for (ElkNode child : originalGraph.getChildren()) {
                for (ElkEdge edge : child.getOutgoingEdges()) {
                    ElkNode newSrc = oldToNewNodeMap.get(child);
                    ElkNode newTar = oldToNewNodeMap.get(edge.getTargets().get(0));
                    ElkEdge newEdge = ElkGraphFactory.eINSTANCE.createElkEdge();
                    newEdge.getSources().add(newSrc);
                    newEdge.getTargets().add(newTar);
                    newEdge.setContainingNode(newSrc.getParent());
                    newEdge.copyProperties(edge);
                }
            }
            
            AbstractLayoutProvider layoutProvider = algorithmData.getInstancePool().fetch();
            try {
                // Perform layout on the current hierarchy level
                if (node.getChildren() != null && node.getChildren().size() > 0) {
                    layoutProvider.layout(node, new NullElkProgressMonitor());
                    algorithmData.getInstancePool().release(layoutProvider);
                }
            } catch (Exception exception) {
                // The layout provider has failed - destroy it slowly and painfully
                layoutProvider.dispose();
                throw exception;
            }
            
            if (!(node.hasProperty(CoreOptions.CHILD_AREA_WIDTH) 
                    || node.hasProperty(CoreOptions.CHILD_AREA_HEIGHT))) {
                // compute child area if it hasn't been set by the layout algorithm
                ElkUtil.computeChildAreaDimensions(node);
            }
            
            ElkPadding padding = node.getProperty(CoreOptions.PADDING);
            double desiredWidth = node.getProperty(CoreOptions.CHILD_AREA_WIDTH) + padding.left + padding.right;
            double desiredHeight = node.getProperty(CoreOptions.CHILD_AREA_HEIGHT) + padding.top + padding.bottom;

            double desiredAspectRatio = desiredWidth / desiredHeight;

            // TODO: this doesn't really seem to have any effect, can I use this to control the final aspect ratio of states
            //       i.e. region layout when using the aspect ratio white space elimination strategy?
            originalGraph.setProperty(CoreOptions.ASPECT_RATIO, desiredAspectRatio);

            /** END OF LOOKAHEAD */
            
            // apply desired size and aspect ratio to node, capped to the set size
            double unitSize = node.getProperty(CoreOptions.TOPDOWN_HIERARCHICAL_NODE_WIDTH);
            System.out.println("END--" + desiredWidth + " " + desiredHeight);
            
            if (originalGraph.getProperty(CoreOptions.DIRECTION).isVertical()) {
                return new KVector(Math.min(unitSize, desiredWidth),
                        Math.min(unitSize/desiredAspectRatio, desiredHeight));
            } else {
                return new KVector(Math.min(unitSize * desiredAspectRatio, desiredWidth),
                        Math.min(unitSize, desiredHeight));
            }
            
            
        }
    };
}
