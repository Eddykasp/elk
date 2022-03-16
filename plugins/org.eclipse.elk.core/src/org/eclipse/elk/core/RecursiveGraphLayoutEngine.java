/*******************************************************************************
 * Copyright (c) 2008, 2018 Kiel University and others.
 * 
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.elk.core;

import java.util.Collections;
import java.util.List;
import java.util.Queue;
import org.eclipse.elk.core.data.DeprecatedLayoutOptionReplacer;
import org.eclipse.elk.core.data.LayoutAlgorithmData;
import org.eclipse.elk.core.data.LayoutAlgorithmResolver;
import org.eclipse.elk.core.math.ElkPadding;
import org.eclipse.elk.core.options.CoreOptions;
import org.eclipse.elk.core.options.HierarchyHandling;
import org.eclipse.elk.core.options.TopdownNodeTypes;
import org.eclipse.elk.core.testing.TestController;
import org.eclipse.elk.core.util.ElkUtil;
import org.eclipse.elk.core.util.IElkProgressMonitor;
import org.eclipse.elk.graph.ElkBendPoint;
import org.eclipse.elk.graph.ElkConnectableShape;
import org.eclipse.elk.graph.ElkEdge;
import org.eclipse.elk.graph.ElkEdgeSection;
import org.eclipse.elk.graph.ElkNode;
import org.eclipse.elk.graph.properties.GraphFeature;
import org.eclipse.elk.graph.util.ElkGraphUtil;

import com.google.common.collect.Lists;

/**
 * Performs layout on a graph with hierarchy by executing a layout algorithm on each level of the
 * hierarchy. This is done recursively from the leafs to the root of the nodes in the graph, using
 * size information from lower levels in the levels above.
 * 
 * <p>
 * The actual layout algorithms to execute are determined with the {@link CoreOptions#RESOLVED_ALGORITHM}
 * option. This option is configured by the {@link LayoutAlgorithmResolver}. If it is not yet set on
 * the top-level node, this implementation applies the default algorithm resolver by running
 * <pre>
 * ElkUtil.applyVisitors(layoutGraph, new LayoutAlgorithmResolver());
 * </pre>
 * If you need to customize the algorithm resolution or apply validation, you should run the algorithm
 * resolver before invoking this class.
 * </p>
 * 
 * <p>
 * Layout can be performed either as usual ({@link #layout(ElkNode, IElkProgressMonitor)}) or as part of
 * a unit test ({@link #layout(ElkNode, TestController, IElkProgressMonitor)}). The latter should
 * usually not be called directly, but is used by ELK's unit test framework for layout algorithms.
 * </p>
 * 
 * <p>
 * MIGRATE Extend the graph layout engine to offset edge coordinates properly
 * </p> 
 * 
 * @author ars
 * @author msp
 */
public class RecursiveGraphLayoutEngine implements IGraphLayoutEngine {
    
    private double HIERARCHICAL_NODE_WIDTH;
    private double HIERARCHICAL_NODE_ASPECT_RATIO;
    
    /**
     * Performs recursive layout on the given layout graph.
     * 
     * @param layoutGraph top-level node of the graph to be laid out.
     * @param progressMonitor monitor to which progress of the layout algorithms is reported.
     */
    public void layout(final ElkNode layoutGraph, final IElkProgressMonitor progressMonitor) {
        layout(layoutGraph, null, progressMonitor);
    }
    
    /**
     * Performs recursive layout on the given layout graph, possibly in a test setting.
     * 
     * @param layoutGraph top-level node of the graph to be laid out.
     * @param testController an optional test controller if the layout run is performed as part of a unit test.
     * @param progressMonitor monitor to which progress of the layout algorithms is reported.
     */
    public void layout(final ElkNode layoutGraph, final TestController testController,
            final IElkProgressMonitor progressMonitor) {
        // If using topdown layout get relevant properties
        if (layoutGraph.hasProperty(CoreOptions.TOPDOWN_LAYOUT) 
                && layoutGraph.getProperty(CoreOptions.TOPDOWN_LAYOUT)) {
            HIERARCHICAL_NODE_WIDTH = layoutGraph.getProperty(CoreOptions.TOPDOWN_HIERARCHICAL_NODE_WIDTH);
            HIERARCHICAL_NODE_ASPECT_RATIO = layoutGraph.getProperty(CoreOptions.TOPDOWN_HIERARCHICAL_NODE_ASPECT_RATIO);
        }
        
        int nodeCount = countNodesRecursively(layoutGraph, true);
        progressMonitor.begin("Recursive Graph Layout", nodeCount);
        
        ElkUtil.applyVisitors(layoutGraph, new DeprecatedLayoutOptionReplacer());

        if (!layoutGraph.hasProperty(CoreOptions.RESOLVED_ALGORITHM)) {
            // Apply the default algorithm resolver to the graph in order to obtain algorithm meta data
            ElkUtil.applyVisitors(layoutGraph, new LayoutAlgorithmResolver());
        }
        
        // Perform recursive layout of the whole substructure of the given node
        layoutRecursively(layoutGraph, testController, progressMonitor);
        
        progressMonitor.done();
    }

    /**
     * Recursive function to enable layout of hierarchy. The leafs are laid out first to use their
     * layout information in the levels above.
     * 
     * If the 'Topdown Layout' option is enabled, root nodes are laid out first and inner layouts are scaled down
     * so the respective parent nodes can accommodate their children.
     * 
     * <p>This method returns self loops routed inside the given layout node. Those will have
     * coordinates relative to the node's top left corner, which is incorrect. Once the node's
     * final coordinates in its container are determined, any inside self loops will have to be offset
     * by the node's position.</p>
     * 
     * @param layoutNode the node with children to be laid out
     * @param testController an optional test controller if this layout run is part of a unit test
     * @param progressMonitor monitor used to keep track of progress
     * @return list of self loops routed inside the node.
     */
    protected List<ElkEdge> layoutRecursively(final ElkNode layoutNode, final TestController testController,
            final IElkProgressMonitor progressMonitor) {
        
        if (progressMonitor.isCanceled()) {
            return Collections.emptyList();
        }
        
        // Check if the node should be laid out at all
        if (layoutNode.getProperty(CoreOptions.NO_LAYOUT)) {
            return Collections.emptyList();
        }
        
        // We have to process the node if it has children...
        final boolean hasChildren = !layoutNode.getChildren().isEmpty();
        
        // ...or if inside self loop processing is enabled and it actually has inside self loops
        final List<ElkEdge> insideSelfLoops = gatherInsideSelfLoops(layoutNode);
        final boolean hasInsideSelfLoops = !insideSelfLoops.isEmpty();
        
        if (hasChildren || hasInsideSelfLoops) {
            // Fetch the layout algorithm that should be used to compute a layout for its content
            final LayoutAlgorithmData algorithmData = layoutNode.getProperty(CoreOptions.RESOLVED_ALGORITHM);
            if (algorithmData == null) {
                throw new UnsupportedConfigurationException("Resolved algorithm is not set;"
                        + " apply a LayoutAlgorithmResolver before computing layout.");
            }
            final boolean supportsInsideSelfLoops = algorithmData.supportsFeature(GraphFeature.INSIDE_SELF_LOOPS);
           
            // Persist the Hierarchy Handling in the nodes by querying the parent node
            evaluateHierarchyHandlingInheritance(layoutNode);
            
            // If the node contains inside self loops, but no regular children and if the layout
            // algorithm doesn't actually support inside self loops, we cancel
            if (!hasChildren && hasInsideSelfLoops && !supportsInsideSelfLoops) {
                return Collections.emptyList();
            }
            
            // We collect inside self loops of children and post-process them later
            List<ElkEdge> childrenInsideSelfLoops = Lists.newArrayList();
            
            // If the layout provider supports hierarchy, it is expected to layout the node's compound
            // node children as well
            int nodeCount;
            if (layoutNode.getProperty(CoreOptions.HIERARCHY_HANDLING) == HierarchyHandling.INCLUDE_CHILDREN
                    && (algorithmData.supportsFeature(GraphFeature.COMPOUND)
                            || algorithmData.supportsFeature(GraphFeature.CLUSTERS))) {
                
                // The layout algorithm will compute a layout for multiple levels of hierarchy under the current one
                nodeCount = countNodesWithHierarchy(layoutNode);
                
                // Look for nodes that stop the hierarchy handling, evaluating the inheritance on the way
                final Queue<ElkNode> nodeQueue = Lists.newLinkedList();
                nodeQueue.addAll(layoutNode.getChildren());
                
                while (!nodeQueue.isEmpty()) {
                    ElkNode node = nodeQueue.poll();
                    // Persist the Hierarchy Handling in every case. (Won't hurt with nodes that are
                    // evaluated in the next recursion)
                    evaluateHierarchyHandlingInheritance(node);
                    final boolean stopHierarchy = node.getProperty(CoreOptions.HIERARCHY_HANDLING)
                            == HierarchyHandling.SEPARATE_CHILDREN;

                    // Hierarchical layout is stopped by explicitly disabling or switching the algorithm. 
                    // In that case, a separate recursive call is used for child nodes
                    if (stopHierarchy 
                          || (node.hasProperty(CoreOptions.ALGORITHM) 
                                  && !algorithmData.equals(node.getProperty(CoreOptions.RESOLVED_ALGORITHM)))) {
                        List<ElkEdge> childLayoutSelfLoops = layoutRecursively(node, testController, progressMonitor);
                        childrenInsideSelfLoops.addAll(childLayoutSelfLoops);
                        // Explicitly disable hierarchical layout for the child node. Simplifies the
                        // handling of switching algorithms in the layouter.
                        node.setProperty(CoreOptions.HIERARCHY_HANDLING, HierarchyHandling.SEPARATE_CHILDREN);

                        // Apply the LayoutOptions.SCALE_FACTOR if present
                        ElkUtil.applyConfiguredNodeScaling(node);
                    } else {
                        // Child should be included in current layout, possibly adding its own children
                        nodeQueue.addAll(node.getChildren());
                    }
                }

            } else {
                nodeCount = layoutNode.getChildren().size();
                // If performing topdown layout, layout this node first, then compute scale factors and layout children
                // recursively
                if (layoutNode.hasProperty(CoreOptions.TOPDOWN_LAYOUT) 
                        && layoutNode.getProperty(CoreOptions.TOPDOWN_LAYOUT)) {
                    
                    System.out.println(layoutNode.getIdentifier() + " ---- " + layoutNode.getProperty(CoreOptions.TOPDOWN_NODE_TYPE));
                                        
                    IElkProgressMonitor topdownLayoutMonitor = progressMonitor.subTask(1);
                    topdownLayoutMonitor.begin("Topdown Layout", 1);
                    ElkPadding padding = layoutNode.getProperty(CoreOptions.PADDING);
                    double nodeNodeSpacing = layoutNode.getProperty(CoreOptions.SPACING_NODE_NODE);
                    
                    // propagate properties
                    for (ElkNode node : layoutNode.getChildren()) {
                     // TODO: think about whether it is possible to have mixed topdown and bottomup layout
                        //       for now just recursively set all children to topdown as well
                        // set mode to topdown layout, this could potentially be handled differently in the future
                        // Leave this on, because currently only set in statesynthesis, but would need to be set in 
                        // regionsynthesis too
                        // TODO: make a decision on how to handle this (global values, that are sometimes also node specific
                        node.setProperty(CoreOptions.TOPDOWN_LAYOUT, true);
                        // this needs to be set (technically only for regions), because top down packing needs the 
                        // value to be set on the regions
                        node.setProperty(CoreOptions.TOPDOWN_HIERARCHICAL_NODE_WIDTH, HIERARCHICAL_NODE_WIDTH);
                        node.setProperty(CoreOptions.TOPDOWN_HIERARCHICAL_NODE_ASPECT_RATIO, HIERARCHICAL_NODE_ASPECT_RATIO);
                    }
                    
                    // set state size of root node FIXME: how can I reliably get hold of the root state?
                    if (layoutNode.getProperty(CoreOptions.TOPDOWN_NODE_TYPE).equals(TopdownNodeTypes.ROOT_NODE)) {
                        // for theoretical multiple root nodes
                        for (ElkNode rootNode : layoutNode.getChildren()) {
                            int cols = (int) Math.ceil(Math.sqrt(rootNode.getChildren().size()));
                            double requiredWidth = cols * HIERARCHICAL_NODE_WIDTH + padding.left + padding.right + (cols - 1)*nodeNodeSpacing; 
                            double requiredHeight = cols * HIERARCHICAL_NODE_WIDTH/HIERARCHICAL_NODE_ASPECT_RATIO + padding.top + padding.bottom + (cols - 1)*nodeNodeSpacing;
                            rootNode.setDimensions(requiredWidth, requiredHeight);
                            rootNode.setProperty(CoreOptions.NODE_REQUIRED_WIDTH, requiredWidth);
                            rootNode.setProperty(CoreOptions.NODE_REQUIRED_HEIGHT, requiredHeight);
                            System.out.println("Set root: " + rootNode.getIdentifier() + " " + requiredWidth);
                        }
                    }
                    

                    // if node has size requirements, restore them
                    // this affects states whose sizes are set by me before layered layout, this is probably a problem
                    if (layoutNode.hasProperty(CoreOptions.NODE_REQUIRED_WIDTH) && layoutNode.hasProperty(CoreOptions.NODE_REQUIRED_HEIGHT)) {
                        //layoutNode.setDimensions(layoutNode.getProperty(CoreOptions.NODE_REQUIRED_WIDTH), layoutNode.getProperty(CoreOptions.NODE_REQUIRED_HEIGHT));
                    }
                    
                    double oldWidth = layoutNode.getWidth();
                    double oldHeight = layoutNode.getHeight();
                    topdownLayoutMonitor.log("Before Layout: " + layoutNode.getIdentifier() + " " + layoutNode.getWidth() + " " + layoutNode.getHeight());
                    System.out.println("Before Layout: " + layoutNode.getIdentifier() + " " + layoutNode.getWidth() + " " + layoutNode.getHeight());
                    
                    // if we are currently in a region and about to produce a layered layout,
                    // then we need to step through the states and set the sizes of the states
                    if (layoutNode.getProperty(CoreOptions.TOPDOWN_NODE_TYPE).equals(TopdownNodeTypes.HIERARCHICAL_NODE)) {
                        for (ElkNode child : layoutNode.getChildren()) {
                            // check if child has children, if yes its size needs to be pre-computed before computing the layout
                            if (child.getChildren().size() > 0) {
                                // TODO: predict required size of node depending on the algorithm that will be used
                                // what is here now is specific to topdownpacking, and this should be externalized and included with the 
                                // algorithms, this will result in clearly defined support of topdown layout by the algorithms
                                // TODO: design sensible mechanism to facilitate this switching
                                // TODO: this doesn't cover the very first initial case (root scchart)
                                int cols = (int) Math.ceil(Math.sqrt(child.getChildren().size()));
                                double requiredWidth = cols * HIERARCHICAL_NODE_WIDTH + padding.left + padding.right + (cols - 1)*nodeNodeSpacing; 
                                double requiredHeight = cols * HIERARCHICAL_NODE_WIDTH/HIERARCHICAL_NODE_ASPECT_RATIO + padding.top + padding.bottom + (cols - 1)*nodeNodeSpacing;
                                child.setDimensions(requiredWidth, requiredHeight);
                                // the values I set here are getting lost again
                                // try storing values in properties and restoring them later
                                // this might not work though, because I assume during the layered layout they are being
                                // set and laid out accordingly, in which case I will need to make modifications there
                                // sort of seems to do what it should although the size is now not quite right for some reason
                                child.setProperty(CoreOptions.NODE_REQUIRED_WIDTH, requiredWidth);
                                child.setProperty(CoreOptions.NODE_REQUIRED_HEIGHT, requiredHeight);
                            }
                        }
                    }
                        
                    System.out.println(layoutNode.getChildren().get(0).getIdentifier() + " " + layoutNode.getChildren().get(0).getWidth());
                    executeAlgorithm(layoutNode, algorithmData, testController, progressMonitor.subTask(nodeCount));
                    topdownLayoutMonitor.log("After Layout: " + layoutNode.getIdentifier() + " " + layoutNode.getWidth() + " " + layoutNode.getHeight());
                    System.out.println("After Layout: " + layoutNode.getIdentifier() + " " + layoutNode.getWidth() + " " + layoutNode.getHeight());
                    // Normally layout algorithms adjust the size of the layoutNode at the end to resize it to appropriately fit the 
                    // the computed layout of the children. For topdown layout this is a big issue, because we actually
                    // need the layoutNode to keep it's own size so that we can subsequently shrink down the children
                    // if node is a region, resize it after layered altered its size
                    // TODO: this should no longer be necessary with fixed graph size, remove 
                    if (layoutNode.getProperty(CoreOptions.TOPDOWN_NODE_TYPE).equals(TopdownNodeTypes.HIERARCHICAL_NODE)) {
                        layoutNode.setDimensions(oldWidth, oldHeight);
                        System.out.println("After Resetting old dimensions: " + layoutNode.getIdentifier() + " " + layoutNode.getWidth() + " " + layoutNode.getHeight());
                    }
                    //layoutNode.setWidth(oldWidth);
                    //layoutNode.setHeight(oldHeight);
                    topdownLayoutMonitor.log("Executed layout algorithm: " 
                            + layoutNode.getProperty(CoreOptions.ALGORITHM)
                            + " on node " + layoutNode.getIdentifier());
                    System.out.println("Executed layout algorithm: " 
                            + layoutNode.getProperty(CoreOptions.ALGORITHM)
                            + " on node " + layoutNode.getIdentifier());
                    System.out.println(layoutNode.getChildren().get(0).getIdentifier() + " " + layoutNode.getChildren().get(0).getWidth());
                    
                    if (layoutNode.getProperty(CoreOptions.TOPDOWN_NODE_TYPE).equals(TopdownNodeTypes.HIERARCHICAL_NODE)) {
                        topdownLayoutMonitor.log(layoutNode.getProperty(CoreOptions.ALGORITHM));
                        System.out.println(layoutNode.getProperty(CoreOptions.ALGORITHM));
                        // determine dimensions of child area 
                        // TODO: we don't actually know how big the child area will be
                        //       because it depends on the number of regions in it, so we need to look ahead
                        
                        // padding is handled on layout algorithm level, we do not need to take it into account here
                        // apparently padding is sometimes handled in different ways)
                        double childAreaAvailableWidth = layoutNode.getWidth() - padding.left - padding.right;
                        double childAreaAvailableHeight = layoutNode.getHeight() - padding.top - padding.bottom;
                        topdownLayoutMonitor.log("Available Child Area: (" + childAreaAvailableWidth + "|" + childAreaAvailableHeight + ")");
                        System.out.println("Available Child Area: (" + childAreaAvailableWidth + "|" + childAreaAvailableHeight + ")");
                        
                        
                        // check whether child area has been set, and if it hasn't run the util function to determine area
                        if (!(layoutNode.hasProperty(CoreOptions.CHILD_AREA_WIDTH) 
                                || layoutNode.hasProperty(CoreOptions.CHILD_AREA_HEIGHT))) {
                            // compute child area if it hasn't been set by the layout algorithm
                            ElkUtil.computeChildAreaDimensions(layoutNode);
                        }
                        
                        double childAreaDesiredWidth = layoutNode.getProperty(CoreOptions.CHILD_AREA_WIDTH);
                        double childAreaDesiredHeight = layoutNode.getProperty(CoreOptions.CHILD_AREA_HEIGHT);
                        // This desired child area is wrong, probably because the sizes are set and reset somewhere else
                        // this causes the weird large scalings in places where they should be smaller than 1
                        topdownLayoutMonitor.log("Desired Child Area: (" + childAreaDesiredWidth + "|" + childAreaDesiredHeight + ")");
                        System.out.println("Desired Child Area: (" + childAreaDesiredWidth + "|" + childAreaDesiredHeight + ")");
                        
                        // compute scaleFactor
                        double scaleFactorX = childAreaAvailableWidth/childAreaDesiredWidth;
                        double scaleFactorY = childAreaAvailableHeight/childAreaDesiredHeight;
                        //double scaleFactor = Math.min(scaleFactorX, Math.min(scaleFactorY, 1)); // restrict to 1 to see what happens
                        double scaleFactor = Math.min(scaleFactorX, scaleFactorY);
                        layoutNode.setProperty(CoreOptions.TOPDOWN_SCALE_FACTOR, scaleFactor);
                        // layoutNode.setProperty(CoreOptions.SCALE_FACTOR, scaleFactor);
                        topdownLayoutMonitor.log("Local Scale Factor (X|Y): (" + scaleFactorX + "|" + scaleFactorY + ")");
                        System.out.println("Local Scale Factor (X|Y): (" + scaleFactorX + "|" + scaleFactorY + ")");
                        
                        // compute translation vector to keep children centered in child area, 
                        // this is necessary because the aspect ratio is not the same as the parent aspect ratio
                        double xShift = 0;
                        double yShift = 0;
                        if (scaleFactorX > scaleFactorY) {
                            // horizontal shift necessary
                            // TODO: still a little off, maybe need to consider padding here
                            xShift = 0.5 * (childAreaAvailableWidth - childAreaDesiredWidth * scaleFactorY);
                        } else {
                            // vertical shift necessary
                            yShift = 0.5 * (childAreaAvailableHeight - childAreaDesiredHeight * scaleFactorX);
                        }
                        topdownLayoutMonitor.log("Shift: (" + xShift + "|" + yShift + ")");
                        for (ElkNode node : layoutNode.getChildren()) {
                            // topdownLayoutMonitor.log(node.getX());
                            // shift all nodes in layout
                            node.setX(node.getX() + xShift);
                            node.setY(node.getY() + yShift); // this doesn't shift the edges, a translation of the whole part of the svg during rendering is probably the better way to do it
                        }
                        
                        // log child sizes
                        for (ElkNode node : layoutNode.getChildren()) {
                            topdownLayoutMonitor.log(node.getIdentifier() + ": (" + node.getWidth() + "|" + node.getHeight() + ")");
                            System.out.println(node.getIdentifier() + ": (" + node.getWidth() + "|" + node.getHeight() + ")");
                        }
                    }
                    topdownLayoutMonitor.done();
                } else {
                    layoutNode.setProperty(CoreOptions.TOPDOWN_SCALE_FACTOR, 1.0);
                }
                
                // Layout each compound node contained in this node separately
                for (ElkNode child : layoutNode.getChildren()) {
                    List<ElkEdge> childLayoutSelfLoops = layoutRecursively(child, testController, progressMonitor); 
                    childrenInsideSelfLoops.addAll(childLayoutSelfLoops);
                    
                    // Apply the LayoutOptions.SCALE_FACTOR if present
                    // TODO: temporary disable here
                    // ElkUtil.applyConfiguredNodeScaling(child);
                }
            }

            if (progressMonitor.isCanceled()) {
                return Collections.emptyList();
            }
            
            // Before running layout on our node, we need to exclude any inside self loops of children
            // from being laid out again
            for (final ElkEdge selfLoop : childrenInsideSelfLoops) {
                selfLoop.setProperty(CoreOptions.NO_LAYOUT, true);
            }

            if (!layoutNode.hasProperty(CoreOptions.TOPDOWN_LAYOUT) 
                    || !layoutNode.getProperty(CoreOptions.TOPDOWN_LAYOUT)) {
                executeAlgorithm(layoutNode, algorithmData, testController, progressMonitor.subTask(nodeCount));
            }
            
            // Post-process the inner self loops we collected
            postProcessInsideSelfLoops(childrenInsideSelfLoops);
            
            // Return our own inside self loops to be processed later
            if (hasInsideSelfLoops && supportsInsideSelfLoops) {
                return insideSelfLoops;
            } else {
                return Collections.emptyList();
            }
        } else {
            return Collections.emptyList();
        }
    }

    /**
     * Execute the given layout algorithm on a parent node.
     */
    protected void executeAlgorithm(final ElkNode layoutNode, final LayoutAlgorithmData algorithmData,
            final TestController testController, final IElkProgressMonitor progressMonitor) {
        
        // Get an instance of the layout provider
        AbstractLayoutProvider layoutProvider = algorithmData.getInstancePool().fetch();
        
        // If we have a test controller and the layout algorithm supports test controllers, setup the test
        if (testController != null && testController.targets(algorithmData)) {
            testController.install(layoutProvider);
        }
        
        try {
            // Perform layout on the current hierarchy level
            layoutProvider.layout(layoutNode, progressMonitor);
            algorithmData.getInstancePool().release(layoutProvider);
        } catch (Exception exception) {
            // The layout provider has failed - destroy it slowly and painfully
            layoutProvider.dispose();
            throw exception;
        } finally {
            if (testController != null) {
                testController.uninstall();
            }
        }
    }

    /**
     * Determines the total number of layout nodes in the given layout graph.
     * 
     * @param layoutNode parent layout node to examine
     * @param countAncestors if true, the nodes on the ancestors path are also counted
     * @return total number of child layout nodes
     */
    protected int countNodesRecursively(final ElkNode layoutNode, final boolean countAncestors) {
        // count the content of the given node
        int count = layoutNode.getChildren().size();
        for (ElkNode childNode : layoutNode.getChildren()) {
            if (!childNode.getChildren().isEmpty()) {
                count += countNodesRecursively(childNode, false);
            }
        }
        // count the ancestors path
        if (countAncestors) {
            ElkNode parent = layoutNode.getParent();
            while (parent != null) {
                count += parent.getChildren().size();
                parent = parent.getParent();
            }
        }
        return count;
    }
    
    
    ////////////////////////////////////////////////////////////////////////////////////////
    // Hierarchy Handling
    
    /**
     * Evaluates one level of inheritance for property {@link CoreOptions#HIERARCHY_HANDLING}. If the root node is
     * evaluated and it is set to inherit (or not set at all) the property is set to
     * {@link HierarchyHandling#SEPARATE_CHILDREN}.
     * 
     * @param layoutNode
     *            The current node which should be evaluated
     */
    private void evaluateHierarchyHandlingInheritance(final ElkNode layoutNode) {
        // Pre-process the hierarchy handling to substitute inherited handling by the parent
        // value. If the root node is set to inherit, it is set to separate the children.
        if (layoutNode.getProperty(CoreOptions.HIERARCHY_HANDLING) == HierarchyHandling.INHERIT) {
            if (layoutNode.getParent() == null) {
                // Set root node to separate children handling
                layoutNode.setProperty(CoreOptions.HIERARCHY_HANDLING, HierarchyHandling.SEPARATE_CHILDREN);
            } else {
                // Set hierarchy handling to the value of the parent. 
                // It is safe to assume that the parent has been handled before and is not set to
                // INHERIT anymore.
                HierarchyHandling parentHandling = layoutNode.getParent().getProperty(CoreOptions.HIERARCHY_HANDLING);
                layoutNode.setProperty(CoreOptions.HIERARCHY_HANDLING, parentHandling);
            }
        }
    }

    /**
     * Determines the number of layout nodes in the given layout graph across multiple levels of
     * hierarchy. Counting is stopped at nodes which disable the hierarchical layout or are
     * configured to use a different layout algorithm.
     * 
     * @param parentNode
     *            parent layout node to examine
     * @return total number of child layout nodes
     */
    private int countNodesWithHierarchy(final ElkNode parentNode) {
        // Count the content of the given node
        int count = parentNode.getChildren().size();
        for (ElkNode childNode : parentNode.getChildren()) {
            if (childNode.getProperty(CoreOptions.HIERARCHY_HANDLING) != HierarchyHandling.SEPARATE_CHILDREN) {
                LayoutAlgorithmData parentData = parentNode.getProperty(CoreOptions.RESOLVED_ALGORITHM);
                LayoutAlgorithmData childData = childNode.getProperty(CoreOptions.RESOLVED_ALGORITHM);
                // Only count nodes that don't abort the hierarchical layout
                if ((parentData == childData || parentData != null && parentData.equals(childData))
                        && !childNode.getChildren().isEmpty()) {
                    count += countNodesWithHierarchy(childNode);
                }
            }
        }
        return count;
    }
    
    
    ////////////////////////////////////////////////////////////////////////////////////////
    // Inside Self Loops
    
    /**
     * Returns a list of self loops of the given node that should be routed inside that node instead
     * of around it. For the node to even be considered for inside self loop processing, its
     * {@link CoreOptions#SELF_LOOP_INSIDE} property must be set to {@code true}. The returned
     * list will then consist of those of its outgoing edges that are self loops and that have that
     * property set to {@code true} as well.
     * 
     * @param node
     *            the node whose inside self loops to return.
     * @return possibly empty list of inside self loops.
     */
    protected List<ElkEdge> gatherInsideSelfLoops(final ElkNode node) {
        if (node.getProperty(CoreOptions.INSIDE_SELF_LOOPS_ACTIVATE)) {
            List<ElkEdge> insideSelfLoops = Lists.newArrayList();
            
            for (ElkEdge edge : ElkGraphUtil.allOutgoingEdges(node)) {
                // MIGRATE Adapt to hyperedges and make error-safe
                if (edge.isSelfloop()) {
                    if (edge.getProperty(CoreOptions.INSIDE_SELF_LOOPS_YO)) {
                        insideSelfLoops.add(edge);
                    }
                }
            }
            
            return insideSelfLoops;
        } else {
            return Collections.emptyList();
        }
    }
    
    /**
     * Post-processes self loops routed inside by offsetting their coordinates by the coordinates of
     * their parent node. The post processing is necessary since the self loop coordinates are
     * relative to their parent node's upper left corner since, at that point, the parent node's
     * final coordinates are not determined yet.
     * 
     * @param insideSelfLoops
     *            list of inside self loops to post-process.
     */
    protected void postProcessInsideSelfLoops(final List<ElkEdge> insideSelfLoops) {
        for (final ElkEdge selfLoop : insideSelfLoops) {
            // MIGRATE Adapt to hyperedges and make error-safe
            final ElkConnectableShape node = ElkGraphUtil.connectableShapeToNode(selfLoop.getSources().get(0));
            
            final double xOffset = node.getX();
            final double yOffset = node.getY();
            
            // Offset the edge coordinates by the node's position
            // MIGRATE Adapt to hyperedges. Also, what about multiple edge sections?
            ElkEdgeSection section = selfLoop.getSections().get(0);
            section.setStartLocation(section.getStartX() + xOffset, section.getStartY() + yOffset);
            section.setEndLocation(section.getEndX() + xOffset, section.getEndY() + yOffset);
            
            for (final ElkBendPoint bend : section.getBendPoints()) {
                bend.set(bend.getX() + xOffset, bend.getY() + yOffset);
            }
            
            // Offset junction points by the node position
            selfLoop.getProperty(CoreOptions.JUNCTION_POINTS).offset(xOffset, yOffset);
        }
    }

}
