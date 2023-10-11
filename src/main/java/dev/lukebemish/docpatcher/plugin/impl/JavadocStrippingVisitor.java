package dev.lukebemish.docpatcher.plugin.impl;

import spoon.reflect.code.CtJavaDoc;
import spoon.reflect.cu.SourcePosition;
import spoon.reflect.declaration.CtElement;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class JavadocStrippingVisitor {
    public void visit(SourcePosition pos) {
        if (pos.isValidPosition()) {
            var begin = pos.getSourceStart();
            var end = pos.getSourceEnd();
            var endLast = end;
            while (end < value.length()) {
                if (!Character.isWhitespace(value.charAt(end))) {
                    break;
                }
                endLast = end;
                end++;
            }
            breakStarts.add(begin);
            breakEnds.add(endLast);
        }
    }

    public void visit(CtElement element) {
        if (element instanceof CtJavaDoc) {
            visit(element.getPosition());
        }
        for (var child : element.getDirectChildren()) {
            visit(child);
        }
    }

    final List<Integer> breakStarts = new ArrayList<>();
    final List<Integer> breakEnds = new ArrayList<>();
    final String value;

    public JavadocStrippingVisitor(String value) {
        this.value = value;
    }

    public String build() {
        List<Integer> indices = indices();

        String out = value;
        for (int i = indices.size() - 1; i >= 0; i--) {
            int start = breakStarts.get(indices.get(i));
            int end = breakEnds.get(indices.get(i));
            if (end == out.length() - 1) {
                out = out.substring(0, start);
            } else {
                out = out.substring(0, start) + out.substring(end+1);
            }
        }
        return out;
    }

    private List<Integer> indices() {
        if (breakStarts.isEmpty()) {
            return Collections.emptyList();
        }
        var indices = new ArrayList<Integer>(breakStarts.size());
        for (int i = 0; i < breakStarts.size(); i++) {
            indices.add(i);
        }
        indices.sort((i1, i2) -> breakStarts.get(i1).compareTo(breakStarts.get(i2)));
        return indices;
    }
}
