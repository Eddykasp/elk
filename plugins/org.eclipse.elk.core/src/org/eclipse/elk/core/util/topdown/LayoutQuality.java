/*******************************************************************************
 * Copyright (c) 2024 Kiel University and others.
 * 
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 * 
 * SPDX-License-Identifier: EPL-2.0 
 *******************************************************************************/
package org.eclipse.elk.core.util.topdown;

import org.eclipse.elk.core.math.KVector;

/**
 * Used to evaluate the quality of top-down layout.
 *
 */
public class LayoutQuality {
    
    public static double extraWhitespace(KVector actualSize, KVector desiredSize) {
        
        double scaleX = actualSize.x / desiredSize.x;
        double scaleY = actualSize.y / desiredSize.y;
        double whitespace = 0;
        
        if (scaleX > scaleY) { 
            whitespace = ((actualSize.x - (scaleY * desiredSize.x)) * actualSize.y ) / scaleY;
        } else {
            whitespace = ((actualSize.y - (scaleX * desiredSize.y)) * actualSize.x ) / scaleX;
        }
        
        return whitespace;
    }

}
