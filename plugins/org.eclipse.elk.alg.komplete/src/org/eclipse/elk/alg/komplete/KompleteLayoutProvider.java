/*******************************************************************************
 * Copyright (c) 2023 Kiel University and others.
 * 
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.elk.alg.komplete;

import org.eclipse.elk.core.AbstractLayoutProvider;
import org.eclipse.elk.core.math.KVector;
import org.eclipse.elk.core.options.CoreOptions;
import org.eclipse.elk.core.util.IElkProgressMonitor;
import org.eclipse.elk.graph.ElkNode;

/**
 * Layout provider for the komplete layout algorithms.
 */
public final class KompleteLayoutProvider extends AbstractLayoutProvider {


    @Override
    public void layout(final ElkNode elkGraph, final IElkProgressMonitor progressMonitor) {
        progressMonitor.begin("ELK Komplete", 1);
        
        double innerAngle = ((elkGraph.getChildren().size() - 2) * 180) / elkGraph.getChildren().size();
        double nodeNodeSpacing = elkGraph.getProperty(CoreOptions.SPACING_NODE_NODE);
        
        // Arrange nodes in a symmetric circle
        KVector nextPosition = new KVector(0, 0);
        // rotating unit vector
        KVector rotatingVector = new KVector(1, 0);
        for (ElkNode node : elkGraph.getChildren()) {
            
            // position current node on ring
            node.setX(nextPosition.x);
            node.setY(nextPosition.y);
            
            // compute next position on ring relative to previous
            // TODO: take sizes of nodes into account
            nextPosition.add(rotatingVector.clone().scale(nodeNodeSpacing));
            
            rotatingVector.rotate(Math.toRadians(innerAngle));
            
            
        }
        
        
        progressMonitor.done();
    }

}
