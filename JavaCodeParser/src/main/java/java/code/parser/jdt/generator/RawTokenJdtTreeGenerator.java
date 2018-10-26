package java.code.parser.jdt.generator;

import java.code.parser.jdt.visitor.AbstractRawTokenJdtVisitor;
import java.code.parser.jdt.visitor.RawTokenJdtVisitor;

@Register(id = "java-jdt", accept = "\\.java$", priority = Registry.Priority.MAXIMUM)
public class RawTokenJdtTreeGenerator extends AbstractRawTokenJdtTreeGenerator {

    @Override
    protected AbstractRawTokenJdtVisitor createVisitor() {
        return new RawTokenJdtVisitor();
    }

}
