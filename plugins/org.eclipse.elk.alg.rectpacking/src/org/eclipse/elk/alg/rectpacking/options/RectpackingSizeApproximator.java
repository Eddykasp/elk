/*******************************************************************************
 * Copyright (c) 2024 Kiel University and others.
 * 
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 * 
 * SPDX-License-Identifier: EPL-2.0 
 *******************************************************************************/
package org.eclipse.elk.alg.rectpacking.options;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.elk.core.AbstractLayoutProvider;
import org.eclipse.elk.core.data.LayoutAlgorithmData;
import org.eclipse.elk.core.math.KVector;
import org.eclipse.elk.core.options.CoreOptions;
import org.eclipse.elk.core.options.ITopdownSizeApproximator;
import org.eclipse.elk.core.options.TopdownSizeApproximator;
import org.eclipse.elk.core.util.ElkUtil;
import org.eclipse.elk.core.util.NullElkProgressMonitor;
import org.eclipse.elk.graph.ElkEdge;
import org.eclipse.elk.graph.ElkGraphFactory;
import org.eclipse.elk.graph.ElkNode;

/**
 * TODO
 *
 */
public enum RectpackingSizeApproximator implements ITopdownSizeApproximator {

    /**
     * TODO
     */
    RECTPACKING_EXPAND_TO_ASPECT_RATIO_LOOKAHEAD {
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
            
           
            KVector estimatedSize = new KVector(Math.min(unitSize, childAreaDesiredWidth),
                    Math.min(unitSize/childAreaDesiredAspectRatio, childAreaDesiredHeight));
            // VERY EVIL SIDE EFFECT: set a property on the original graph here
            originalGraph.setProperty(RectPackingOptions.ASPECT_RATIO, estimatedSize.x / estimatedSize.y);
            
            return estimatedSize;
            
        }
        
    };

}
