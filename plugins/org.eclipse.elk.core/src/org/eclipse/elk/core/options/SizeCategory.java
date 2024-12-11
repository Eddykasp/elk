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

/**
 * Describes size categories and maps them to scale multipliers.
 *
 */
public enum SizeCategory {
    
    TINY(0.5),
    SMALL(1.0),
    MEDIUM(2.0),
    LARGE(4.0);
    
    private final double multiplier;
    private SizeCategory(double multiplier) {
        this.multiplier = multiplier;
    }
    
    /**
     * Increases the category to next higher one.
     */
    public SizeCategory nextCategory() {
        switch(this) {
        case TINY:
            return SMALL;
        case SMALL:
            return MEDIUM;
        case MEDIUM:
            return LARGE;
        default:
            return this;
        }
    }
    
    /**
     * Get the multiplier for the category
     * @return the multiplier
     */
    public double getMultiplier() {
        return this.multiplier;
    }
}
