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
     * Computes the multiplier to use for a box size dependent on the contents of the graph and defined cutoffs.
     * @param originalGraph the parent graph
     * @return the multiplier value to scale the final area with
     */
    public static SizeCategory getSizeCategory(final ElkNode originalGraph) {
        final int CUTTOFF_SMALL = originalGraph.getProperty(CoreOptions.TOPDOWN_CUTOFF_SMALL);
        final int CUTTOFF_MEDIUM = originalGraph.getProperty(CoreOptions.TOPDOWN_CUTOFF_MEDIUM);;
        
        SizeCategory category = SizeCategory.SMALL;
        
        int childCount = originalGraph.getChildren().size();
        
        if (childCount < CUTTOFF_SMALL) {
            category = SizeCategory.TINY;
        } else if (childCount >= CUTTOFF_MEDIUM) {
            category = SizeCategory.MEDIUM;
        }
        
        // graphs go up one category if they have hierarchy
        boolean hasGrandchildren = false;
        for (ElkNode child : originalGraph.getChildren()) {
            if (child.getChildren().size() > 0) {
                hasGrandchildren = true;
                break;
            }
        }
        if (hasGrandchildren) {
            category = category.nextCategory();
        }
        return category;
    }

}
