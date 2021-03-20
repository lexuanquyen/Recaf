package me.coley.recaf.compiler;

import javassist.CtClass;
import javassist.bytecode.Bytecode;
import javassist.compiler.*;
import javassist.compiler.ast.Declarator;
import javassist.compiler.ast.Stmnt;
import me.coley.recaf.Recaf;
import me.coley.recaf.parse.bytecode.VariableNameCache;
import me.coley.recaf.workspace.Workspace;
import org.objectweb.asm.Type;

import java.lang.reflect.Field;

/**
 * An extension of Javassist's {@link Javac} that exposes some internal structures
 * needed to properly inject local variable information.
 *
 * @author Matt
 */
class JavassistExpressionJavac extends Javac {
	private static final Field fGen;
	private static final Field fSTable;
	private static final Field fBytecode;
	private final VariableNameCache varCache;
	private final CtClass declaringClass;
	private final JvstCodeGen gen;
	private SymbolTable lastCompiledSymbols;

	/**
	 * @param declaringClass
	 * 		Type that contains the declared method body/expression.
	 * @param varCache
	 * 		Variable name cache of the declared method.
	 */
	public JavassistExpressionJavac(CtClass declaringClass, VariableNameCache varCache) {
		super(declaringClass);
		this.declaringClass = declaringClass;
		this.varCache = varCache;
		gen = hookCodeGen();
	}

	@Override
	public void compileStmnt(String src) throws CompileError {
		Parser p = new Parser(new Lex(src));
		lastCompiledSymbols = new SymbolTable(getRootSTable());
		while (p.hasMore()) {
			Stmnt s = p.parseStatement(lastCompiledSymbols);
			// Patch the index so the following "accept" call doesn't generate with the wrong var index
			if (varCache != null && s.getLeft() instanceof Declarator)
				patchDeclarator(varCache, (Declarator) s.getLeft());
			// Generate bytecode
			if (s != null)
				s.accept(getGen());
		}
	}

	/**
	 * Updates the generated AST of the variable declarator to use the correct local variable index.
	 *
	 * @param varCache
	 * 		Cache of variable names and indices.
	 * @param dec
	 * 		Declarator to patch.
	 */
	private void patchDeclarator(VariableNameCache varCache, Declarator dec) {
		String name = dec.getLeft().toString();
		// Update variable index if it exists already
		try {
			int index = varCache.getIndex(name);
			dec.setLocalVar(index);
			return;
		} catch (Exception ignored) {
			// ignored
		}
		// Otherwise define it
		String desc = dec.getClassName();
		if (desc == null) {
			switch (dec.getType()) {
				case TokenId.BOOLEAN:
				case TokenId.BYTE:
				case TokenId.SHORT:
				case TokenId.INT:
					desc = "I";
					break;
				case TokenId.CHAR:
					desc = "C";
					break;
				case TokenId.FLOAT:
					desc = "F";
					break;
				case TokenId.DOUBLE:
					desc = "D";
					break;
				case TokenId.LONG:
					desc = "J";
					break;
				default:
					throw new IllegalArgumentException("Unknown primitive type for expression defined var");
			}
		}
		Type type = Type.getType(desc);
		int index = varCache.getAndIncrementNext(name, type);
		dec.setLocalVar(index);
		dec.setClassName(type.getClassName());
		setMaxLocals(index);
	}

	/**
	 * @return Modified code gen to pull information from Recaf.
	 */
	private JvstCodeGen hookCodeGen() {
		try {
			Workspace workspace = Recaf.getCurrentWorkspace();
			JvstCodeGen internalCodeGen = new JavassistCodeGen(workspace, getBytecode(), declaringClass,
					declaringClass.getClassPool());
			fGen.set(this, internalCodeGen);
			return internalCodeGen;
		} catch (IllegalAccessException ex) {
			throw new IllegalStateException(ex);
		}
	}

	/**
	 * @return Code generator.
	 */
	public JvstCodeGen getGen() {
		return gen;
	}

	/**
	 * @return Generated bytecode.
	 */
	public Bytecode getGeneratedBytecode() {
		try {
			return (Bytecode) fBytecode.get(getGen());
		} catch (IllegalAccessException ex) {
			throw new IllegalStateException(ex);
		}
	}

	/**
	 * @return Symbol table used in AST analysis.
	 */
	public SymbolTable getRootSTable() {
		try {
			return (SymbolTable) fSTable.get(this);
		} catch (IllegalAccessException ex) {
			throw new IllegalStateException(ex);
		}
	}

	/**
	 * @return Symbol table containing new symbols from the passed body/expression.
	 */
	public SymbolTable getLastCompiledSymbols() {
		return lastCompiledSymbols;
	}

	static {
		try {
			fGen = Javac.class.getDeclaredField("gen");
			fGen.setAccessible(true);
			fSTable = Javac.class.getDeclaredField("stable");
			fSTable.setAccessible(true);
			fBytecode = CodeGen.class.getDeclaredField("bytecode");
			fBytecode.setAccessible(true);
		} catch (ReflectiveOperationException ex) {
			throw new IllegalStateException(ex);
		}
	}

}