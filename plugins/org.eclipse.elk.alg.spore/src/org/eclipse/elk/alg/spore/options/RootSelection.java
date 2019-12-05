/*******************************************************************************
 * Copyright (c) 2017 Kiel University and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.eclipse.elk.alg.spore.options;

/**
 * An enumeration of methods for selecting a root node when constructing a spanning tree.
 */
public enum RootSelection {
    /** A Node is manually selected by label as root. */
    FIXED,
    /** The node most central to the drawing is selected as root. */
    CENTER_NODE;
}
