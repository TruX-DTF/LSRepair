package code.parser.jdt.generator;

import code.parser.jdt.visitor.AbstractJdtVisitor;
import code.parser.jdt.visitor.ExpJdtVisitor;

@Register(id = "java-jdt-cd")
public class ExpJdtTreeGenerator extends AbstractJdtTreeGenerator {
    @Override
    protected AbstractJdtVisitor createVisitor() {
        return new ExpJdtVisitor();
    }
}
