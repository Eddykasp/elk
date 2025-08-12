module org.eclipse.elk.graph {

    // Public API packages
    exports org.eclipse.elk.graph;
    exports org.eclipse.elk.graph.impl;
    exports org.eclipse.elk.graph.properties;
    exports org.eclipse.elk.graph.util;

    // Required dependencies
    requires org.eclipse.emf.ecore;
    requires com.google.common;
}
