package java.code.parser.utils;

public class Checker {
	
	public static boolean withBlockStatement(int type) {
		if (type == 19) return true; // DoStatement
		if (type == 24) return true; // ForStatement
		if (type == 25) return true; // IfStatement
		if (type == 30) return true; // LabeledStatement
		if (type == 50) return true; // SwitchStatement
		if (type == 51) return true; // SynchronizedStatement
		if (type == 54) return true; // TryStatement
		if (type == 61) return true; // WhileStatement
		if (type == 70) return true; // EnhancedForStatement
		return false;
	}
	
	public static boolean isStatement(int type) {
		if (type == 6)  return true; // AssertStatement
		if (type == 10) return true; // BreakStatement
		if (type == 17) return true; // ConstructorInvocation
		if (type == 18) return true; // ContinueStatement
		if (type == 21) return true; // ExpressionStatement
		if (type == 41) return true; // ReturnStatement
		if (type == 46) return true; // SuperConstructorInvocation
		if (type == 49) return true; // SwitchCase
		if (type == 53) return true; // ThrowStatement
		if (type == 56) return true; // TypeDeclarationStatement
		if (type == 60) return true; // VariableDeclarationStatement
		return withBlockStatement(type);
	}
	
	public static boolean isStatement2(int type) {
//		if (type == 8)  return true; // block
		if (type == 12) return true; // CatchClause
		return isStatement(type);
	}

	public static boolean isComplexExpression(int type) {
		if (type == 2)  return true; // ArrayAccess
		if (type == 3)  return true; // ArrayCreation
		if (type == 4)  return true; // ArrayInitializer
		if (type == 7)  return true; // Assignment
		if (type == 11) return true; // CastExpression
		if (type == 14) return true; // ClassInstanceCreation
		if (type == 16) return true; // ConditionalExpression
		if (type == 17) return true; // ConstructorInvocation
		if (type == 22) return true; // FieldAccess
		if (type == 27) return true; // InfixExpression
		if (type == 32) return true; // MethodInvocation
		if (type == 36) return true; // ParenthesizedExpression
		if (type == 37) return true; // PostfixExpression
		if (type == 38) return true; // PrefixExpression
		if (type == 40) return true; // QualifiedName
		if (type == 47) return true; // SuperFieldAccess
		if (type == 48) return true; // SuperMethodInvocation
		if (type == 58) return true; // VariableDeclarationExpression
		if (type == 59) return true; // VariableDeclarationFragment FIXME: this node is not an expression node.
		if (type == 62) return true; // InstanceofExpression
		if (type == 86) return true; // LambdaExpression
		return false;
	}

	public static boolean isValidExpression(int type) {
		if (type == 9)  return true; // BooleanLiteral
		if (type == 13) return true; // CharacterLiteral
		if (type == 33) return true; // NullLiteral
		if (type == 34) return true; // NumberLiteral
		if (type == 45) return true; // StringLiteral
		if (type == 42)  return true; // SimpleName
		if (type == 52) return true; // ThisExpression
		return isComplexExpression(type);
	}
	
	public static boolean isTrivalExpression(int type) {
		if (type == 57) return true; // TypeLiteral
		if (type == 77) return true; // NormalAnnotation
		if (type == 78) return true; // MarkerAnnotation
		if (type == 79) return true; // SingleMemberAnnotation
		if (type == 89) return true; // CreationReference
		if (type == 90) return true; // ExpressionMethodReference
		if (type == 91) return true; // SuperMethodReference
		if (type == 92) return true; // TypeMethodReference
		return false;
	}
}
