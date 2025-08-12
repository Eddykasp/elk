module org.eclipse.elk.core {
    // Dependencies from Require-Bundle
    requires org.eclipse.emf.ecore;
    requires org.eclipse.emf.ecore.xmi;
    requires org.eclipse.elk.graph;

    // Public API exports (no internal packages unless explicitly needed)
    exports org.eclipse.elk.core;
    exports org.eclipse.elk.core.alg;
    exports org.eclipse.elk.core.comments;
    exports org.eclipse.elk.core.data;
    exports org.eclipse.elk.core.labels;
    exports org.eclipse.elk.core.math;
    exports org.eclipse.elk.core.options;
    exports org.eclipse.elk.core.testing;
    exports org.eclipse.elk.core.util;
    exports org.eclipse.elk.core.util.adapters;
    exports org.eclipse.elk.core.util.persistence;
    exports org.eclipse.elk.core.util.selection;
    exports org.eclipse.elk.core.validation;

    // Friend package: accessible to org.eclipse.elk.graph.text
    opens org.eclipse.elk.core.util.internal to org.eclipse.elk.graph.text;
}
