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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.elk.graph.properties.IProperty;

/**
 * Utility to manage a set of options and their values to generate all possible combinations.
 *
 */
public class OptionCombinations {
    
    public static List<Map<IProperty<Object>,Object>> generateAllValueCombinations(Map<IProperty<?>, List<?>> optionValueMap) {
        
        return combine(optionValueMap, new ArrayList<Object>(optionValueMap.keySet()), new HashMap<>(), new ArrayList<>());
        
    }
    
    private static List<Map<IProperty<Object>,Object>> combine(Map<IProperty<?>, List<?>> optionValueMap, List<Object> keys, Map<IProperty<Object>,Object> currentMap, List<Map<IProperty<Object>,Object>> accumulated) {
        boolean last = keys.size() == 1;
        
        // for every option recursively add all other options
        for (Object key : keys) {
            for (Object value : optionValueMap.get(key)) {
                Map<IProperty<Object>,Object> forkedMap = new HashMap<>(currentMap);
                forkedMap.put((IProperty<Object>) key, value);
                // now add all remaining options
                List<Object> remainingKeys = keys.subList(1, keys.size());
                if (last && forkedMap.size() == optionValueMap.keySet().size()) {
                    accumulated.add(forkedMap);
                } else {
                    combine(optionValueMap, remainingKeys, forkedMap, accumulated);
                }
            }
        }
        
        return accumulated;
    }

}
