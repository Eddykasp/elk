/*******************************************************************************
 * Copyright (c) 2022 Kiel University and others.
 * 
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 * 
 * SPDX-License-Identifier: EPL-2.0 
 *******************************************************************************/
package org.eclipse.elk.core.options;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.elk.core.AbstractLayoutProvider;
import org.eclipse.elk.core.data.LayoutAlgorithmData;
import org.eclipse.elk.core.math.ElkPadding;
import org.eclipse.elk.core.math.KVector;
import org.eclipse.elk.core.util.ElkUtil;
import org.eclipse.elk.core.util.NullElkProgressMonitor;
import org.eclipse.elk.graph.ElkEdge;
import org.eclipse.elk.graph.ElkGraphFactory;
import org.eclipse.elk.graph.ElkNode;

/**
 * A size approximator is used to dynamically decide a size for a node to be used during topdown layout
 * of hierarchical nodes. This allows the use of a size approximation strategy to minimize white space
 * in the final result.
 */
public enum TopdownSizeApproximator implements ITopdownSizeApproximator {
    
    /**
     * Computes the square root of the number of children and uses that as a multiplier for the base size
     * of the node. Nodes with no children will have a resulting size of 0, which means any other factors
     * determining the size will be dominant. Uses {@link CoreOptions#TOPDOWN_HIERARCHICAL_NODE_WIDTH} and 
     * {@link CoreOptions#TOPDOWN_HIERARCHICAL_NODE_ASPECT_RATIO} as the base size.
     */
    COUNT_CHILDREN {
        @Override
        public KVector getSize(final ElkNode node) {
            double size = node.getProperty(CoreOptions.TOPDOWN_HIERARCHICAL_NODE_WIDTH) 
                    * Math.sqrt(node.getChildren().size()) * 0.4;
            return new KVector(size, size / node.getProperty(CoreOptions.TOPDOWN_HIERARCHICAL_NODE_ASPECT_RATIO));
        }
        
    },
    
    /**
     * Computes the layout of a node to get an estimate of how much space it needs. In order to do this, the
     * node and its children are copied including the edges between the children. All edges must be simple edges
     * not hyperedges.
     * The nodes are assigned sizes for the layout algorithm according to the COUNT_CHILDREN approximator.
     */
    LOOKAHEAD_LAYOUT {
        @Override
        public KVector getSize(final ElkNode originalGraph) {
            final LayoutAlgorithmData algorithmData = originalGraph.getProperty(CoreOptions.RESOLVED_ALGORITHM);
            
            // clone the current hierarchy
            ElkNode node = ElkGraphFactory.eINSTANCE.createElkNode();
            node.copyProperties(originalGraph);
            Map<ElkNode, ElkNode> oldToNewNodeMap = new HashMap<>();
            // copy children
            for (ElkNode child : originalGraph.getChildren()) {
                ElkNode newChild = ElkGraphFactory.eINSTANCE.createElkNode();
                newChild.setParent(node);
                newChild.copyProperties(child);
                // set size according to microlayout or node count approximator
                KVector size = TopdownSizeApproximator.COUNT_CHILDREN.getSize(child);
                newChild.setDimensions(Math.max(child.getWidth(), size.x),
                        Math.max(child.getHeight(), size.y));
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
                layoutProvider.layout(node, new NullElkProgressMonitor());
                algorithmData.getInstancePool().release(layoutProvider);
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
            
            double childAreaDesiredWidth = node.getProperty(CoreOptions.CHILD_AREA_WIDTH);
            double childAreaDesiredHeight = node.getProperty(CoreOptions.CHILD_AREA_HEIGHT);
            
            double childAreaDesiredAspectRatio = childAreaDesiredWidth / childAreaDesiredHeight;
            
            // square root approximation for base size
            double baseSize = node.getProperty(CoreOptions.TOPDOWN_HIERARCHICAL_NODE_WIDTH) 
                    * Math.sqrt(node.getChildren().size());
            
            ElkPadding padding = node.getProperty(CoreOptions.PADDING);
            double minWidth = padding.left + padding.right + 1;
            double minHeight = padding.top + padding.bottom + 1;
                        
            // the alternative to this is to return the desired Size directly, in that case region scales are close
            // to the children, in this case on the other hand region scales are close to their parent
            return new KVector(Math.max(minWidth, baseSize), 
                    Math.max(minHeight, baseSize / childAreaDesiredAspectRatio));
            
            // ALTERNATE WAYS TO APPROXIMATE SIZES BELOW
            
            // alternative 1 (current):
            // return new KVector(Math.max(minWidth, baseSize), Math.max(minHeight, baseSize / childAreaDesiredAspectRatio));
            
            // alternative 2:
            // double baseSize = node.getProperty(CoreOptions.TOPDOWN_HIERARCHICAL_NODE_WIDTH)
            // return new KVector(Math.max(minWidth, baseSize), Math.max(minHeight, baseSize / childAreaDesiredAspectRatio));
            
            // alternative 3:
            // return new KVector(Math.max(minWidth, childAreaDesiredWidth), Math.max(minHeight, childAreaDesiredHeight));
            
        }
    },
    
    /**
     * The dynamic size approximator uses lookahead layout to determine the nodes approximate desired size and then
     * sets the final size either directly to that desired size or to a size that is maximally the width of the top-down
     * node width but while still keeping the desired aspect ratio of the lookahead layout.
     */
    DYNAMIC {
        @Override
        public KVector getSize(final ElkNode originalGraph) {
            final LayoutAlgorithmData algorithmData = originalGraph.getProperty(CoreOptions.RESOLVED_ALGORITHM);
            
            /** LOOKAHEAD LAYOUT */
            // clone the current hierarchy
            ElkNode node = ElkGraphFactory.eINSTANCE.createElkNode();
            node.copyProperties(originalGraph);
            Map<ElkNode, ElkNode> oldToNewNodeMap = new HashMap<>();
            // copy children
            for (ElkNode child : originalGraph.getChildren()) {
                ElkNode newChild = ElkGraphFactory.eINSTANCE.createElkNode();
                newChild.setParent(node);
                newChild.copyProperties(child);
                // set size according to microlayout or node count approximator
                KVector size = TopdownSizeApproximator.COUNT_CHILDREN.getSize(child);
                newChild.setDimensions(Math.max(child.getWidth(), size.x),
                        Math.max(child.getHeight(), size.y));
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
                layoutProvider.layout(node, new NullElkProgressMonitor());
                algorithmData.getInstancePool().release(layoutProvider);
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
            
            double childAreaDesiredWidth = node.getProperty(CoreOptions.CHILD_AREA_WIDTH);
            double childAreaDesiredHeight = node.getProperty(CoreOptions.CHILD_AREA_HEIGHT);
            
            double childAreaDesiredAspectRatio = childAreaDesiredWidth / childAreaDesiredHeight;
            /** END OF LOOKAHEAD */
            
            // apply desired size and aspect ratio to node, capped to the set size
            double unitSize = node.getProperty(CoreOptions.TOPDOWN_HIERARCHICAL_NODE_WIDTH);
            return new KVector(Math.min(unitSize, childAreaDesiredWidth),
                    Math.min(unitSize/childAreaDesiredAspectRatio, childAreaDesiredHeight));
            
        }
    },
    
    /**
     * Fixed Integer Ratio Approximator
     * Dependent on the size of the child graphs, rectangles of fixed ratios are produced.
     * The goal is to enable good packings and also give bigger subgraphs more space.
     */
    FIXED_INTEGER_RATIO_BOXES {
        @Override
        public KVector getSize(final ElkNode originalGraph) {
            
            double baseWidth = originalGraph.getProperty(CoreOptions.TOPDOWN_HIERARCHICAL_NODE_WIDTH);
            double baseHeight = baseWidth / originalGraph.getProperty(CoreOptions.TOPDOWN_HIERARCHICAL_NODE_ASPECT_RATIO);
            
            // four categories of box sizes, tiny = half-width, small = base-width, medium = double-width, large = quadruple-width
            // how graph sizes are distributed into these categories has a great effect on the final result
            double multiplier = TopdownSizeApproximatorUtil.getSizeCategory(originalGraph).getMultiplier();
            
            // Combine multiplier, spacings and base size to compute final size
            ElkPadding padding = originalGraph.getProperty(CoreOptions.PADDING);
            double nodeNodeSpacing = CoreOptions.SPACING_NODE_NODE.getDefault();
            if (originalGraph.getParent() != null) {
                nodeNodeSpacing = originalGraph.getParent().getProperty(CoreOptions.SPACING_NODE_NODE);
            }
            KVector resultSize = new KVector(baseWidth, baseHeight).scale(multiplier);
            return resultSize.add(new KVector(
                    -(padding.left + padding.right) - nodeNodeSpacing,
                    -(padding.top + padding.bottom) - nodeNodeSpacing));
        }
    },
    
    /**
     * This approximator assumes that the node's contents are:
     * 1. approximated using FIXED_INTEGER_RATIO_BOXES
     * 2. laid out using a bin packing/rectpacking approach that packs the boxes in a close to optimal manner
     * 
     * This way the size approximation performed for the nodes here, later provides almost exactly the right
     * amount of space for rectpacking later.
     * 
     * Important: when using this strategy HIERARCHICAL_NODE_WIDTH and HIERARCHICAL_NODE_ASPECT_RATIO must be consistent
     *            for all nodes!
     */
    ASSUME_BIN_PACKING {
        @Override public KVector getSize(final ElkNode originalGraph) {
            
            // get the number of each box per category
            // tiny, small, medium, large
            int categoryCounts[] = {0, 0, 0, 0};
            
            for (ElkNode child : originalGraph.getChildren()) {
                SizeCategory category = TopdownSizeApproximatorUtil.getSizeCategory(child);
                switch(category) {
                case TINY:
                    categoryCounts[0] += 1;
                    break;
                case SMALL:
                    categoryCounts[1] += 1;
                    break;
                case MEDIUM:
                    categoryCounts[2] += 1;
                    break;
                case LARGE:
                    categoryCounts[3] += 1;
                    break;
                }
            }
            
            // estimate packing problem area
            // TODO:
            
            // multiply the packing area by the base size, this will only work if the base size is identical for all nodes!!
            // TODO:
            
            
            return new KVector();
        }
    };

}
