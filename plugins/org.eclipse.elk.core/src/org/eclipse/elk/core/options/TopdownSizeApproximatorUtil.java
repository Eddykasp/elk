/*******************************************************************************
 * Copyright (c) 2024 Kiel University and others.
 * 
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 * 
 * SPDX-License-Identifier: EPL-2.0 
 *******************************************************************************/
package org.eclipse.elk.core.options;

import org.eclipse.elk.graph.ElkNode;

/**
 * Utility functions for reuse across different size approximators.
 *
 */
public class TopdownSizeApproximatorUtil {
    
    /**
     * Dynamically calculate the multiplier to be applied for the side length of the input node based on the number 
     * of children (with and without hierarchy) it and its siblings have. The distribution is mapped to a log scale,
     * which is divided into a number of categories that determine the multiplier.
     * 
     * Category i => 2^i
     * 
     * @param originalGraph
     * @return
     */
    public static double getSizeCategoryMultiplier(final ElkNode originalGraph) {
        ElkNode parent = originalGraph.getParent();
        int thisGraphsSize = getGraphSize(originalGraph);
        
        int CATEGORIES = originalGraph.getProperty(CoreOptions.TOPDOWN_SIZE_CATEGORIES);
        
        
        if (parent != null) {
            // 1. compute distribution of node sizes
            int sizeMinFound = Integer.MAX_VALUE;
            int sizeMaxFound = Integer.MIN_VALUE;
            
            for (ElkNode child : parent.getChildren()) {
                int size = getGraphSize(child);
                
                if (size > sizeMaxFound) {
                    sizeMaxFound = size;
                }
                if (size < sizeMinFound) {
                    sizeMinFound = size;
                }
            }
            
            
            double sizeMin = 1;
            double sizeMax = Math.pow(4, CATEGORIES);
            // shift the range to encompass the largest graph in the local neighbourhood
            if (sizeMaxFound > sizeMax) {
                sizeMax = sizeMaxFound;
            }
            
            // 2. set cutoffs at quarter percentiles on logarithmic scale 
            double x = (Math.log(sizeMax) - Math.log(sizeMin)) / CATEGORIES;
            double factor = Math.exp(x);
            
            // 3. assign node size according to dynamic cutoffs
            double cutoff = sizeMin * factor;
            for (int i = 0; i < CATEGORIES; i++) {
                if (thisGraphsSize <= cutoff) {
                    return Math.pow(2, i);
                } else {
                    cutoff *= factor;
                }
            }
            // largest category
            return Math.pow(2, CATEGORIES-1);
            
        } else {
            return 1.0;
        }
        
    }
    
    public static int getGraphSize(final ElkNode originalGraph) {
        // nodes with hierarchy are counted with a factor of 4 (currently regardless of further details of the subgraph)
        boolean CONSIDER_GREAT_GRANDCHILDREN = false;
        
        int sum = 0;
        
        final int HIERARCHICAL_NODE_WEIGHT = originalGraph.getProperty(CoreOptions.TOPDOWN_SIZE_CATEGORIES_HIERARCHICAL_NODE_WEIGHT);
        for (ElkNode child : originalGraph.getChildren()) {
            if (child.getChildren() != null && child.getChildren().size() > 0) {
                sum += HIERARCHICAL_NODE_WEIGHT;
            } else {
                sum += 1;
            }
            
            if (CONSIDER_GREAT_GRANDCHILDREN) {
                // look down two more hierarchy levels
                for (ElkNode grandChild : child.getChildren()) {
                    if (grandChild.getChildren() != null && grandChild.getChildren().size() > 0) {
                        sum += HIERARCHICAL_NODE_WEIGHT;
                    } else {
                        sum += 1;
                    }
                    
                    for (ElkNode greatGrandChild : grandChild.getChildren()) {
                        if (greatGrandChild.getChildren() != null && greatGrandChild.getChildren().size() > 0) {
                            sum += HIERARCHICAL_NODE_WEIGHT;
                        } else {
                            sum += 1;
                        }
                    }
                }
            }
        }
        return sum;
    }

}
