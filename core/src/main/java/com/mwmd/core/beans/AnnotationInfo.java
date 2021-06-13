package com.mwmd.core.beans;

import java.util.HashSet;
import java.util.Set;

/**
 * Summary for the annotation state of a page.
 */
public class AnnotationInfo {

    /**
     * count of annotations across all components on the page
     */
    private int count;

    /**
     * components within the page which carry annotations
     */
    private Set<String> components;

    public AnnotationInfo() {

        this.components = new HashSet<>();
    }

    public int getCount() {
        return count;
    }

    public Set<String> getComponents() {
        return components;
    }

    public void addAnnotation() {
        count++;
    }

    public void addComponent(String path) {
        components.add(path);
    }

}
