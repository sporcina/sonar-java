/*
 * SonarQube Java
 * Copyright (C) 2012 SonarSource
 * dev@sonar.codehaus.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02
 */
package org.sonar.java.model;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.sonar.sslr.api.AstNode;

import javax.annotation.Nullable;

import java.util.List;

public abstract class JavaTree implements Tree {

  private final AstNode astNode;

  public JavaTree(AstNode astNode) {
    this.astNode = astNode;
  }

  public AstNode getAstNode() {
    return astNode;
  }

  public int getLine() {
    return astNode.getTokenLine();
  }

  @Override
  public final boolean is(Kind kind) {
    return getKind() == null ? false : getKind() == kind;
  }

  protected abstract Kind getKind();

  public abstract void accept(TreeVisitor visitor);

  public static class PrimitiveTypeTreeImpl extends JavaTree implements PrimitiveTypeTree {
    public PrimitiveTypeTreeImpl(AstNode astNode) {
      super(astNode);
    }

    @Override
    public Kind getKind() {
      return null;
    }

    @Override
    public void accept(TreeVisitor visitor) {
    }
  }

  public static class IdentifierTreeImpl extends JavaTree implements IdentifierTree {
    private final String name;

    public IdentifierTreeImpl(AstNode astNode, String name) {
      super(astNode);
      this.name = Preconditions.checkNotNull(name);
    }

    @Override
    protected Kind getKind() {
      return Kind.IDENTIFIER;
    }

    @Override
    public String name() {
      return name;
    }

    @Override
    public void accept(TreeVisitor visitor) {
      visitor.visitIdentifier(this);
    }
  }

  public static class CompilationUnitTreeImpl extends JavaTree implements CompilationUnitTree {
    @Nullable
    private final ExpressionTree packageName;
    private final List<? extends ImportTree> imports;
    private final List<? extends Tree> types;

    public CompilationUnitTreeImpl(AstNode astNode, @Nullable ExpressionTree packageName, List<? extends ImportTree> imports, List<? extends Tree> types) {
      super(astNode);
      this.packageName = packageName;
      this.imports = Preconditions.checkNotNull(imports);
      this.types = Preconditions.checkNotNull(types);
    }

    @Override
    protected Kind getKind() {
      return Kind.COMPILATION_UNIT;
    }

    @Override
    public List<? extends AnnotationTree> packageAnnotations() {
      throw new UnsupportedOperationException("not implemented");
    }

    @Nullable
    @Override
    public ExpressionTree packageName() {
      return packageName;
    }

    @Override
    public List<? extends ImportTree> imports() {
      return imports;
    }

    @Override
    public List<? extends Tree> types() {
      return types;
    }

    @Override
    public void accept(TreeVisitor visitor) {
      visitor.visitCompilationUnit(this);
    }
  }

  public static class ImportTreeImpl extends JavaTree implements ImportTree {
    private final boolean isStatic;
    private final Tree qualifiedIdentifier;

    public ImportTreeImpl(AstNode astNode, boolean aStatic, Tree qualifiedIdentifier) {
      super(astNode);
      isStatic = aStatic;
      this.qualifiedIdentifier = qualifiedIdentifier;
    }

    @Override
    protected Kind getKind() {
      return null;
    }

    @Override
    public boolean isStatic() {
      return isStatic;
    }

    @Override
    public Tree qualifiedIdentifier() {
      return qualifiedIdentifier;
    }

    @Override
    public void accept(TreeVisitor visitor) {
    }
  }

  public static class ClassTreeImpl extends JavaTree implements ClassTree {
    private final Kind kind;
    private final ModifiersTree modifiers;
    private final String simpleName;
    @Nullable
    private final Tree superClass;
    private final List<? extends Tree> superInterfaces;
    private final List<? extends Tree> members;

    public ClassTreeImpl(AstNode astNode, Kind kind, ModifiersTree modifiers, String simpleName, @Nullable Tree superClass, List<? extends Tree> superInterfaces, List<? extends Tree> members) {
      super(astNode);
      this.kind = Preconditions.checkNotNull(kind);
      this.modifiers = Preconditions.checkNotNull(modifiers);
      this.simpleName = simpleName;
      this.superClass = superClass;
      this.superInterfaces = Preconditions.checkNotNull(superInterfaces);
      this.members = Preconditions.checkNotNull(members);
    }

    // TODO remove:
    public ClassTreeImpl(AstNode astNode, Kind kind, ModifiersTree modifiers, List<? extends Tree> members) {
      this(astNode, kind, modifiers, null, null, ImmutableList.<Tree>of(), members);
    }

    @Override
    protected Kind getKind() {
      return kind;
    }

    @Nullable
    @Override
    public String simpleName() {
      return simpleName;
    }

    @Override
    public List<? extends Tree> typeParameters() {
      throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public ModifiersTree modifiers() {
      return modifiers;
    }

    @Nullable
    @Override
    public Tree superClass() {
      return superClass;
    }

    @Override
    public List<? extends Tree> superInterfaces() {
      return superInterfaces;
    }

    @Override
    public List<? extends Tree> members() {
      return members;
    }

    @Override
    public void accept(TreeVisitor visitor) {
      visitor.visitClass(this);
    }
  }

  public static class MethodTreeImpl extends JavaTree implements MethodTree {
    private final ModifiersTree modifiers;
    @Nullable
    private final Tree returnType;
    private final String simpleName;
    private final List<? extends VariableTree> parameters;
    @Nullable
    private final BlockTree block;
    private final List<? extends ExpressionTree> throwsClauses;
    private final ExpressionTree defaultValue;

    public MethodTreeImpl(AstNode astNode, ModifiersTree modifiers, @Nullable Tree returnType, String simpleName, List<? extends VariableTree> parameters, @Nullable BlockTree block,
      List<? extends ExpressionTree> throwsClauses, @Nullable ExpressionTree defaultValue) {
      super(astNode);
      this.modifiers = Preconditions.checkNotNull(modifiers);
      this.returnType = returnType;
      this.simpleName = Preconditions.checkNotNull(simpleName);
      this.parameters = Preconditions.checkNotNull(parameters);
      this.block = block;
      this.throwsClauses = Preconditions.checkNotNull(throwsClauses);
      this.defaultValue = defaultValue;
    }

    @Override
    protected Kind getKind() {
      return Kind.METHOD;
    }

    @Override
    public ModifiersTree modifiers() {
      return modifiers;
    }

    @Override
    public List<? extends Tree> typeParameters() {
      throw new UnsupportedOperationException("not implemented");
    }

    @Nullable
    @Override
    public Tree returnType() {
      return returnType;
    }

    @Override
    public String simpleName() {
      return simpleName;
    }

    @Override
    public List<? extends VariableTree> parameters() {
      return parameters;
    }

    @Override
    public List<? extends ExpressionTree> throwsClauses() {
      return throwsClauses;
    }

    @Nullable
    @Override
    public BlockTree block() {
      return block;
    }

    @Nullable
    @Override
    public ExpressionTree defaultValue() {
      return defaultValue;
    }

    @Override
    public void accept(TreeVisitor visitor) {
      visitor.visitMethod(this);
    }
  }

  public static class BlockTreeImpl extends JavaTree implements BlockTree {
    private final Kind kind;
    private final List<? extends StatementTree> body;

    public BlockTreeImpl(AstNode astNode, Kind kind, List<? extends StatementTree> body) {
      super(astNode);
      this.kind = kind;
      this.body = Preconditions.checkNotNull(body);
    }

    @Override
    protected Kind getKind() {
      return kind;
    }

    @Override
    public List<? extends StatementTree> body() {
      return body;
    }

    @Override
    public void accept(TreeVisitor visitor) {
      visitor.visitBlock(this);
    }
  }

  public static class IfStatementTreeImpl extends JavaTree implements IfStatementTree {
    private final ExpressionTree condition;
    private final StatementTree thenStatement;
    @Nullable
    private final StatementTree elseStatement;

    public IfStatementTreeImpl(AstNode astNode, ExpressionTree condition, StatementTree thenStatement, @Nullable StatementTree elseStatement) {
      super(astNode);
      this.condition = Preconditions.checkNotNull(condition);
      this.thenStatement = Preconditions.checkNotNull(thenStatement);
      this.elseStatement = elseStatement;
    }

    @Override
    protected Kind getKind() {
      return Kind.IF_STATEMENT;
    }

    @Override
    public ExpressionTree condition() {
      return condition;
    }

    @Override
    public StatementTree thenStatement() {
      return thenStatement;
    }

    @Nullable
    @Override
    public StatementTree elseStatement() {
      return elseStatement;
    }

    @Override
    public void accept(TreeVisitor visitor) {
      visitor.visitIfStatement(this);
    }
  }

  public static class WhileStatementTreeImpl extends JavaTree implements WhileStatementTree {
    private final ExpressionTree condition;
    private final StatementTree statement;

    public WhileStatementTreeImpl(AstNode astNode, ExpressionTree condition, StatementTree statement) {
      super(astNode);
      this.condition = Preconditions.checkNotNull(condition);
      this.statement = Preconditions.checkNotNull(statement);
    }

    @Override
    protected Kind getKind() {
      return Kind.WHILE_STATEMENT;
    }

    @Override
    public ExpressionTree condition() {
      return condition;
    }

    @Override
    public StatementTree statement() {
      return statement;
    }

    @Override
    public void accept(TreeVisitor visitor) {
      visitor.visitWhileStatement(this);
    }
  }

  public static class ForStatementTreeImpl extends JavaTree implements ForStatementTree {
    private final List<? extends StatementTree> initializer;
    @Nullable
    private final ExpressionTree condition;
    private final List<? extends StatementTree> update;
    private final StatementTree statement;

    public ForStatementTreeImpl(AstNode astNode, List<? extends StatementTree> initializer, @Nullable ExpressionTree condition, List<? extends StatementTree> update,
      StatementTree statement) {
      super(astNode);
      this.initializer = Preconditions.checkNotNull(initializer);
      this.condition = condition;
      this.update = Preconditions.checkNotNull(update);
      this.statement = Preconditions.checkNotNull(statement);
    }

    @Override
    protected Kind getKind() {
      return Kind.FOR_STATEMENT;
    }

    @Override
    public List<? extends StatementTree> initializer() {
      return initializer;
    }

    @Nullable
    @Override
    public ExpressionTree condition() {
      return condition;
    }

    @Override
    public List<? extends StatementTree> update() {
      return update;
    }

    @Override
    public StatementTree statement() {
      return statement;
    }

    @Override
    public void accept(TreeVisitor visitor) {
      visitor.visitForStatement(this);
    }
  }

  public static class EnhancedForStatementTreeImpl extends JavaTree implements EnhancedForStatementTree {
    private final VariableTree variable;
    private final ExpressionTree expression;
    private final StatementTree statement;

    public EnhancedForStatementTreeImpl(AstNode astNode, VariableTree variable, ExpressionTree expression, StatementTree statement) {
      super(astNode);
      this.variable = Preconditions.checkNotNull(variable);
      this.expression = Preconditions.checkNotNull(expression);
      this.statement = Preconditions.checkNotNull(statement);
    }

    @Override
    protected Kind getKind() {
      return Kind.ENHANCED_FOR_STATEMENT;
    }

    @Override
    public VariableTree variable() {
      return variable;
    }

    @Override
    public ExpressionTree expression() {
      return expression;
    }

    @Override
    public StatementTree statement() {
      return statement;
    }

    @Override
    public void accept(TreeVisitor visitor) {
      visitor.visitEnhancedForStatement(this);
    }
  }

  public static class EmptyStatementTreeImpl extends JavaTree implements EmptyStatementTree {
    public EmptyStatementTreeImpl(AstNode astNode) {
      super(astNode);
    }

    @Override
    protected Kind getKind() {
      return Kind.EMPTY_STATEMENT;
    }

    @Override
    public void accept(TreeVisitor visitor) {
      visitor.visitEmptyStatement(this);
    }
  }

  public static class ExpressionStatementTreeImpl extends JavaTree implements ExpressionStatementTree {
    private final ExpressionTree expression;

    public ExpressionStatementTreeImpl(AstNode astNode, ExpressionTree expression) {
      super(astNode);
      this.expression = Preconditions.checkNotNull(expression);
    }

    @Override
    protected Kind getKind() {
      return Kind.EXPRESSION_STATEMENT;
    }

    @Override
    public ExpressionTree expression() {
      return expression;
    }

    @Override
    public void accept(TreeVisitor visitor) {
      visitor.visitExpressionStatement(this);
    }
  }

  public static class AssertStatementTreeImpl extends JavaTree implements AssertStatementTree {
    private final ExpressionTree condition;
    @Nullable
    private final ExpressionTree detail;

    public AssertStatementTreeImpl(AstNode astNode, ExpressionTree condition, @Nullable ExpressionTree detail) {
      super(astNode);
      this.condition = Preconditions.checkNotNull(condition);
      this.detail = detail;
    }

    @Override
    protected Kind getKind() {
      return Kind.ASSERT_STATEMENT;
    }

    @Override
    public ExpressionTree condition() {
      return condition;
    }

    @Nullable
    @Override
    public ExpressionTree detail() {
      return detail;
    }

    @Override
    public void accept(TreeVisitor visitor) {
      visitor.visitAssertStatement(this);
    }
  }

  public static class SwitchStatementTreeImpl extends JavaTree implements SwitchStatementTree {
    private final ExpressionTree expression;
    private final List<? extends CaseGroupTree> cases;

    public SwitchStatementTreeImpl(AstNode astNode, ExpressionTree expression, List<? extends CaseGroupTree> cases) {
      super(astNode);
      this.expression = Preconditions.checkNotNull(expression);
      this.cases = Preconditions.checkNotNull(cases);
    }

    @Override
    protected Kind getKind() {
      return Kind.SWITCH_STATEMENT;
    }

    @Override
    public ExpressionTree expression() {
      return expression;
    }

    @Override
    public List<? extends CaseGroupTree> cases() {
      return cases;
    }

    @Override
    public void accept(TreeVisitor visitor) {
      visitor.visitSwitchStatement(this);
    }
  }

  public static class CaseGroupTreeImpl extends JavaTree implements CaseGroupTree {
    private final List<? extends CaseLabelTree> labels;
    private final List<? extends StatementTree> body;

    public CaseGroupTreeImpl(AstNode astNode, List<? extends CaseLabelTree> labels, List<? extends StatementTree> body) {
      super(astNode);
      this.labels = Preconditions.checkNotNull(labels);
      this.body = Preconditions.checkNotNull(body);
    }

    @Override
    protected Kind getKind() {
      return Kind.CASE_GROUP;
    }

    @Override
    public List<? extends CaseLabelTree> labels() {
      return labels;
    }

    @Override
    public List<? extends StatementTree> body() {
      return body;
    }

    @Override
    public void accept(TreeVisitor visitor) {
      visitor.visitCaseGroup(this);
    }
  }

  public static class CaseLabelTreeImpl extends JavaTree implements CaseLabelTree {
    @Nullable
    private final ExpressionTree expression;

    public CaseLabelTreeImpl(AstNode astNode, @Nullable ExpressionTree expression) {
      super(astNode);
      this.expression = expression;
    }

    @Override
    protected Kind getKind() {
      return Kind.CASE_LABEL;
    }

    @Nullable
    @Override
    public ExpressionTree expression() {
      return expression;
    }

    @Override
    public void accept(TreeVisitor visitor) {
      visitor.visitCaseLabel(this);
    }
  }

  public static class DoWhileStatementTreeImpl extends JavaTree implements DoWhileStatementTree {
    private final StatementTree statement;
    private final ExpressionTree condition;

    public DoWhileStatementTreeImpl(AstNode astNode, StatementTree statement, ExpressionTree condition) {
      super(astNode);
      this.statement = Preconditions.checkNotNull(statement);
      this.condition = Preconditions.checkNotNull(condition);
    }

    @Override
    protected Kind getKind() {
      return Kind.DO_STATEMENT;
    }

    @Override
    public StatementTree statement() {
      return statement;
    }

    @Override
    public ExpressionTree condition() {
      return condition;
    }

    @Override
    public void accept(TreeVisitor visitor) {
      visitor.visitDoWhileStatement(this);
    }
  }

  public static class BreakStatementTreeImpl extends JavaTree implements BreakStatementTree {
    @Nullable
    private final String label;

    public BreakStatementTreeImpl(AstNode astNode, @Nullable String label) {
      super(astNode);
      this.label = label;
    }

    @Override
    protected Kind getKind() {
      return Kind.BREAK_STATEMENT;
    }

    @Nullable
    @Override
    public String label() {
      return label;
    }

    @Override
    public void accept(TreeVisitor visitor) {
      visitor.visitBreakStatement(this);
    }
  }

  public static class ContinueStatementTreeImpl extends JavaTree implements ContinueStatementTree {
    @Nullable
    private final String label;

    public ContinueStatementTreeImpl(AstNode astNode, @Nullable String label) {
      super(astNode);
      this.label = label;
    }

    @Override
    protected Kind getKind() {
      return Kind.CONTINUE_STATEMENT;
    }

    @Nullable
    @Override
    public String label() {
      return label;
    }

    @Override
    public void accept(TreeVisitor visitor) {
      visitor.visitContinueStatement(this);
    }
  }

  public static class ReturnStatementTreeImpl extends JavaTree implements ReturnStatementTree {
    @Nullable
    private final ExpressionTree expression;

    public ReturnStatementTreeImpl(AstNode astNode, @Nullable ExpressionTree expression) {
      super(astNode);
      this.expression = expression;
    }

    @Override
    protected Kind getKind() {
      return Kind.RETURN_STATEMENT;
    }

    @Nullable
    @Override
    public ExpressionTree expression() {
      return expression;
    }

    @Override
    public void accept(TreeVisitor visitor) {
      visitor.visitReturnStatement(this);
    }
  }

  public static class SynchronizedStatementTreeImpl extends JavaTree implements SynchronizedStatementTree {
    private final ExpressionTree expression;
    private final BlockTree block;

    public SynchronizedStatementTreeImpl(AstNode astNode, ExpressionTree expression, BlockTree block) {
      super(astNode);
      this.expression = Preconditions.checkNotNull(expression);
      this.block = Preconditions.checkNotNull(block);
    }

    @Override
    protected Kind getKind() {
      return Kind.SYNCHRONIZED_STATEMENT;
    }

    @Override
    public ExpressionTree expression() {
      return expression;
    }

    @Override
    public BlockTree block() {
      return block;
    }

    @Override
    public void accept(TreeVisitor visitor) {
      visitor.visitSynchronizedStatement(this);
    }
  }

  public static class ThrowStatementTreeImpl extends JavaTree implements ThrowStatementTree {
    private final ExpressionTree expression;

    public ThrowStatementTreeImpl(AstNode astNode, ExpressionTree expression) {
      super(astNode);
      this.expression = Preconditions.checkNotNull(expression);
    }

    @Override
    protected Kind getKind() {
      return Kind.THROW_STATEMENT;
    }

    @Override
    public ExpressionTree expression() {
      return expression;
    }

    @Override
    public void accept(TreeVisitor visitor) {
      visitor.visitThrowStatement(this);
    }
  }

  public static class TryStatementTreeImpl extends JavaTree implements TryStatementTree {
    private final List<? extends VariableTree> resources;
    private final BlockTree block;
    private final List<? extends CatchTree> catches;
    @Nullable
    private final BlockTree finallyBlock;

    public TryStatementTreeImpl(AstNode astNode, List<? extends VariableTree> resources, BlockTree block, List<? extends CatchTree> catches, @Nullable BlockTree finallyBlock) {
      super(astNode);
      this.resources = Preconditions.checkNotNull(resources);
      this.block = Preconditions.checkNotNull(block);
      this.catches = Preconditions.checkNotNull(catches);
      this.finallyBlock = finallyBlock;
    }

    @Override
    protected Kind getKind() {
      return Kind.TRY_STATEMENT;
    }

    @Override
    public List<? extends VariableTree> resources() {
      return resources;
    }

    @Override
    public BlockTree block() {
      return block;
    }

    @Override
    public List<? extends CatchTree> catches() {
      return catches;
    }

    @Nullable
    @Override
    public BlockTree finallyBlock() {
      return finallyBlock;
    }

    @Override
    public void accept(TreeVisitor visitor) {
      visitor.visitTryStatement(this);
    }
  }

  public static class CatchTreeImpl extends JavaTree implements CatchTree {
    private final VariableTree parameter;
    private final BlockTree block;

    public CatchTreeImpl(AstNode astNode, VariableTree parameter, BlockTree block) {
      super(astNode);
      this.parameter = Preconditions.checkNotNull(parameter);
      this.block = Preconditions.checkNotNull(block);
    }

    @Override
    protected Kind getKind() {
      return Kind.CATCH;
    }

    @Override
    public VariableTree parameter() {
      return parameter;
    }

    @Override
    public BlockTree block() {
      return block;
    }

    @Override
    public void accept(TreeVisitor visitor) {
      visitor.visitCatch(this);
    }
  }

  public static class LabeledStatementTreeImpl extends JavaTree implements LabeledStatementTree {
    private final String label;
    private final StatementTree statement;

    public LabeledStatementTreeImpl(AstNode astNode, String label, StatementTree statement) {
      super(astNode);
      this.label = Preconditions.checkNotNull(label);
      this.statement = Preconditions.checkNotNull(statement);
    }

    @Override
    protected Kind getKind() {
      return Kind.LABELED_STATEMENT;
    }

    @Override
    public String label() {
      return label;
    }

    @Override
    public StatementTree statement() {
      return statement;
    }

    @Override
    public void accept(TreeVisitor visitor) {
      visitor.visitLabeledStatement(this);
    }
  }

  public static class EnumConstantTreeImpl extends VariableTreeImpl implements EnumConstantTree {
    public EnumConstantTreeImpl(AstNode astNode, ModifiersTree modifiers, Tree type, String simpleName, ExpressionTree initializer) {
      super(astNode, modifiers, type, simpleName, Preconditions.checkNotNull(initializer));
    }

    @Override
    protected Kind getKind() {
      return Kind.ENUM_CONSTANT;
    }

    @Override
    public void accept(TreeVisitor visitor) {
      visitor.visitEnumConstant(this);
    }
  }

  public static class VariableTreeImpl extends JavaTree implements VariableTree {
    private final ModifiersTree modifiers;
    private final Tree type;
    private final String simpleName;
    @Nullable
    private final ExpressionTree initializer;

    public VariableTreeImpl(AstNode astNode, ModifiersTree modifiers, Tree type, String simpleName, @Nullable ExpressionTree initializer) {
      super(astNode);
      this.modifiers = Preconditions.checkNotNull(modifiers);
      this.type = Preconditions.checkNotNull(type);
      this.simpleName = Preconditions.checkNotNull(simpleName);
      this.initializer = initializer;
    }

    @Override
    protected Kind getKind() {
      return Kind.VARIABLE;
    }

    @Override
    public ModifiersTree modifiers() {
      return modifiers;
    }

    @Override
    public Tree type() {
      return type;
    }

    @Override
    public String simpleName() {
      return simpleName;
    }

    @Nullable
    @Override
    public ExpressionTree initializer() {
      return initializer;
    }

    @Override
    public void accept(TreeVisitor visitor) {
      visitor.visitVariable(this);
    }
  }

  public static class LiteralTreeImpl extends JavaTree implements LiteralTree {
    private final Kind kind;

    public LiteralTreeImpl(AstNode astNode, Kind kind) {
      super(astNode);
      this.kind = Preconditions.checkNotNull(kind);
    }

    @Override
    protected Kind getKind() {
      return kind;
    }

    @Override
    public String value() {
      return super.astNode.getTokenOriginalValue();
    }

    @Override
    public void accept(TreeVisitor visitor) {
      visitor.visitLiteral(this);
    }
  }

  public static class BinaryExpressionTreeImpl extends JavaTree implements BinaryExpressionTree {
    private final ExpressionTree leftOperand;
    private final Kind kind;
    private final ExpressionTree rightOperand;

    public BinaryExpressionTreeImpl(AstNode astNode, ExpressionTree leftOperand, Kind kind, ExpressionTree rightOperand) {
      super(astNode);
      this.leftOperand = Preconditions.checkNotNull(leftOperand);
      this.kind = Preconditions.checkNotNull(kind);
      this.rightOperand = Preconditions.checkNotNull(rightOperand);
    }

    @Override
    public ExpressionTree leftOperand() {
      return leftOperand;
    }

    @Override
    public ExpressionTree rightOperand() {
      return rightOperand;
    }

    @Override
    protected Kind getKind() {
      return kind;
    }

    @Override
    public void accept(TreeVisitor visitor) {
      visitor.visitBinaryExpression(this);
    }
  }

  public static class UnaryExpressionTreeImpl extends JavaTree implements UnaryExpressionTree {
    private final Kind kind;
    private final ExpressionTree expression;

    public UnaryExpressionTreeImpl(AstNode astNode, Tree.Kind kind, ExpressionTree expression) {
      super(astNode);
      this.kind = Preconditions.checkNotNull(kind);
      this.expression = Preconditions.checkNotNull(expression);
    }

    @Override
    protected Kind getKind() {
      return kind;
    }

    @Override
    public ExpressionTree expression() {
      return expression;
    }

    @Override
    public void accept(TreeVisitor visitor) {
      visitor.visitUnaryExpression(this);
    }
  }

  public static class ParenthesizedTreeImpl extends JavaTree implements ParenthesizedTree {
    private final ExpressionTree expression;

    public ParenthesizedTreeImpl(AstNode astNode, ExpressionTree expression) {
      super(astNode);
      this.expression = Preconditions.checkNotNull(expression);
    }

    @Override
    protected Kind getKind() {
      return Kind.PARENTHESIZED_EXPRESSION;
    }

    @Override
    public ExpressionTree expression() {
      return expression;
    }

    @Override
    public void accept(TreeVisitor visitor) {
      visitor.visitParenthesized(this);
    }
  }

  public static class ConditionalExpressionTreeImpl extends JavaTree implements ConditionalExpressionTree {
    private final ExpressionTree condition;
    private final ExpressionTree trueExpression;
    private final ExpressionTree falseExpression;

    public ConditionalExpressionTreeImpl(AstNode astNode, ExpressionTree condition, ExpressionTree trueExpression, ExpressionTree falseExpression) {
      super(astNode);
      this.condition = Preconditions.checkNotNull(condition);
      this.trueExpression = Preconditions.checkNotNull(trueExpression);
      this.falseExpression = Preconditions.checkNotNull(falseExpression);
    }

    @Override
    protected Kind getKind() {
      return Kind.CONDITIONAL_EXPRESSION;
    }

    @Override
    public ExpressionTree condition() {
      return condition;
    }

    @Override
    public ExpressionTree trueExpression() {
      return trueExpression;
    }

    @Override
    public ExpressionTree falseExpression() {
      return falseExpression;
    }

    @Override
    public void accept(TreeVisitor visitor) {
      visitor.visitConditionalExpression(this);
    }
  }

  public static class InstanceOfTreeImpl extends JavaTree implements InstanceOfTree {
    private final ExpressionTree expression;
    private final Tree type;

    public InstanceOfTreeImpl(AstNode astNode, ExpressionTree expression, Tree type) {
      super(astNode);
      this.expression = Preconditions.checkNotNull(expression);
      this.type = Preconditions.checkNotNull(type);
    }

    @Override
    protected Kind getKind() {
      return Kind.INSTANCE_OF;
    }

    @Override
    public ExpressionTree expression() {
      return expression;
    }

    @Override
    public Tree type() {
      return type;
    }

    @Override
    public void accept(TreeVisitor visitor) {
      visitor.visitInstanceOf(this);
    }
  }

  public static class TypeCastExpressionTreeImpl extends JavaTree implements TypeCastTree {
    private final Tree type;
    private final ExpressionTree expression;

    public TypeCastExpressionTreeImpl(AstNode astNode, Tree type, ExpressionTree expression) {
      super(astNode);
      this.type = Preconditions.checkNotNull(type);
      this.expression = Preconditions.checkNotNull(expression);
    }

    @Override
    protected Kind getKind() {
      return Kind.TYPE_CAST;
    }

    @Override
    public Tree type() {
      return type;
    }

    @Override
    public ExpressionTree expression() {
      return expression;
    }

    @Override
    public void accept(TreeVisitor visitor) {
      visitor.visitTypeCast(this);
    }
  }

  public static class AssignmentExpressionTreeImpl extends JavaTree implements AssignmentExpressionTree {
    private final ExpressionTree variable;
    private final Kind kind;
    private final ExpressionTree expression;

    public AssignmentExpressionTreeImpl(AstNode astNode, ExpressionTree variable, Kind kind, ExpressionTree expression) {
      super(astNode);
      this.variable = Preconditions.checkNotNull(variable);
      this.kind = Preconditions.checkNotNull(kind);
      this.expression = Preconditions.checkNotNull(expression);
    }

    @Override
    protected Kind getKind() {
      return kind;
    }

    @Override
    public ExpressionTree variable() {
      return variable;
    }

    @Override
    public ExpressionTree expression() {
      return expression;
    }

    @Override
    public void accept(TreeVisitor visitor) {
      visitor.visitAssignmentExpression(this);
    }
  }

  public static class MethodInvocationTreeImpl extends JavaTree implements MethodInvocationTree {
    private final ExpressionTree methodSelect;
    private final List<? extends ExpressionTree> arguments;

    public MethodInvocationTreeImpl(AstNode astNode, ExpressionTree methodSelect, List<? extends ExpressionTree> arguments) {
      super(astNode);
      this.methodSelect = Preconditions.checkNotNull(methodSelect);
      this.arguments = Preconditions.checkNotNull(arguments);
    }

    @Override
    protected Kind getKind() {
      return Kind.METHOD_INVOCATION;
    }

    @Override
    public List<? extends Tree> typeArguments() {
      throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public ExpressionTree methodSelect() {
      return methodSelect;
    }

    @Override
    public List<? extends ExpressionTree> arguments() {
      return arguments;
    }

    @Override
    public void accept(TreeVisitor visitor) {
      visitor.visitMethodInvocation(this);
    }
  }

  public static class NewArrayTreeImpl extends JavaTree implements NewArrayTree {
    private final Tree type;
    private final List<? extends ExpressionTree> dimensions;
    private final List<? extends ExpressionTree> initializers;

    public NewArrayTreeImpl(AstNode astNode, Tree type, List<? extends ExpressionTree> dimensions, List<? extends ExpressionTree> initializers) {
      super(astNode);
      // TODO maybe type should not be null?
      this.type = type;
      this.dimensions = Preconditions.checkNotNull(dimensions);
      this.initializers = Preconditions.checkNotNull(initializers);
    }

    @Override
    protected Kind getKind() {
      return Kind.NEW_ARRAY;
    }

    @Override
    public Tree type() {
      return type;
    }

    @Override
    public List<? extends ExpressionTree> dimensions() {
      return dimensions;
    }

    @Override
    public List<? extends ExpressionTree> initializers() {
      return initializers;
    }

    @Override
    public void accept(TreeVisitor visitor) {
      visitor.visitNewArray(this);
    }
  }

  public static class NewClassTreeImpl extends JavaTree implements NewClassTree {
    private final ExpressionTree enclosingExpression;
    private final List<? extends ExpressionTree> arguments;
    @Nullable
    private final ClassTree classBody;

    public NewClassTreeImpl(AstNode astNode, @Nullable ExpressionTree enclosingExpression, List<? extends ExpressionTree> arguments, @Nullable ClassTree classBody) {
      super(astNode);
      this.enclosingExpression = enclosingExpression;
      this.arguments = Preconditions.checkNotNull(arguments);
      this.classBody = classBody;
    }

    @Override
    protected Kind getKind() {
      return Kind.NEW_CLASS;
    }

    @Nullable
    @Override
    public ExpressionTree enclosingExpression() {
      return enclosingExpression;
    }

    @Override
    public List<? extends Tree> typeArguments() {
      throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public Tree identifier() {
      throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public List<? extends ExpressionTree> arguments() {
      return arguments;
    }

    @Nullable
    @Override
    public ClassTree classBody() {
      return classBody;
    }

    @Override
    public void accept(TreeVisitor visitor) {
      visitor.visitNewClass(this);
    }
  }

  public static class MemberSelectExpressionTreeImpl extends JavaTree implements MemberSelectExpressionTree {
    private final ExpressionTree expression;
    private final IdentifierTree identifier;

    public MemberSelectExpressionTreeImpl(AstNode astNode, ExpressionTree expression, IdentifierTree identifier) {
      super(astNode);
      this.expression = Preconditions.checkNotNull(expression);
      this.identifier = Preconditions.checkNotNull(identifier);
    }

    @Override
    protected Kind getKind() {
      return Kind.MEMBER_SELECT;
    }

    @Override
    public ExpressionTree expression() {
      return expression;
    }

    @Override
    public IdentifierTree identifier() {
      return identifier;
    }

    @Override
    public void accept(TreeVisitor visitor) {
      visitor.visitMemberSelectExpression(this);
    }
  }

  public static class ArrayAccessExpressionTreeImpl extends JavaTree implements ArrayAccessExpressionTree {
    private final ExpressionTree expression;
    private final ExpressionTree index;

    public ArrayAccessExpressionTreeImpl(AstNode astNode, ExpressionTree expression, ExpressionTree index) {
      super(astNode);
      this.expression = Preconditions.checkNotNull(expression);
      this.index = Preconditions.checkNotNull(index);
    }

    @Override
    protected Kind getKind() {
      return Kind.ARRAY_ACCESS_EXPRESSION;
    }

    @Override
    public ExpressionTree expression() {
      return expression;
    }

    @Override
    public ExpressionTree index() {
      return index;
    }

    @Override
    public void accept(TreeVisitor visitor) {
      visitor.visitArrayAccessExpression(this);
    }
  }

  public static class ArrayTypeTreeImpl extends JavaTree implements ArrayTypeTree {
    private final Tree type;

    public ArrayTypeTreeImpl(AstNode astNode, Tree type) {
      super(astNode);
      this.type = Preconditions.checkNotNull(type);
    }

    @Override
    protected Kind getKind() {
      return null;
    }

    @Override
    public Tree type() {
      return type;
    }

    @Override
    public void accept(TreeVisitor visitor) {
    }
  }

  public static class WildcardTreeImpl extends JavaTree implements WildcardTree {
    private final Tree bound;

    public WildcardTreeImpl(AstNode astNode, Tree bound) {
      super(astNode);
      this.bound = Preconditions.checkNotNull(bound);
    }

    @Override
    protected Kind getKind() {
      return null;
    }

    @Override
    public Tree bound() {
      return bound;
    }

    @Override
    public void accept(TreeVisitor visitor) {
    }
  }

  public static class ModifiersTreeImpl extends JavaTree implements ModifiersTree {
    // TODO remove:
    public static final ModifiersTreeImpl EMPTY = new ModifiersTreeImpl(null, ImmutableList.<Modifier> of());

    private final List<Modifier> modifiers;

    public ModifiersTreeImpl(AstNode astNode, List<Modifier> modifiers) {
      super(astNode);
      this.modifiers = Preconditions.checkNotNull(modifiers);
    }

    @Override
    protected Kind getKind() {
      return null;
    }

    @Override
    public List<Modifier> modifiers() {
      return modifiers;
    }

    @Override
    public List<? extends AnnotationTree> annotations() {
      throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public void accept(TreeVisitor visitor) {
    }
  }

}
