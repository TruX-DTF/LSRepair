package code.parser.jdt.visitor;


import org.eclipse.jdt.core.dom.ASTNode;

/**
 * Create AbstractRowTokenJdtVisitor by extending AbstractJdtVisitor and overriding pushNode method.
 * 
 * Remove the ASTNode type in trees.
 */
public abstract class AbstractRawTokenJdtVisitor extends AbstractJdtVisitor {

    public AbstractRawTokenJdtVisitor() {
        super();
    }

    @Override
    protected void pushNode(ASTNode n, String label) {
        push(0, "", label, n.getStartPosition(), n.getLength());
    }

}
