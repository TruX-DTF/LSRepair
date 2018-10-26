package java.code.parser.jdt.generator;

import java.code.parser.jdt.visitor.AbstractJdtVisitor;
import java.code.parser.jdt.visitor.ExpJdtVisitor;

@Register(id = "java-jdt-cd")
public class ExpJdtTreeGenerator extends AbstractJdtTreeGenerator {
    @Override
    protected AbstractJdtVisitor createVisitor() {
        return new ExpJdtVisitor();
    }
}
