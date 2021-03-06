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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.sonar.sslr.api.AstNode;
import com.sonar.sslr.impl.ast.AstXmlPrinter;
import org.sonar.java.ast.api.JavaKeyword;
import org.sonar.java.ast.api.JavaPunctuator;
import org.sonar.java.ast.api.JavaTokenType;
import org.sonar.java.ast.parser.JavaGrammar;

import javax.annotation.Nullable;
import java.util.List;

public class JavaTreeMaker {

  private final KindMaps kindMaps = new KindMaps();

  private IdentifierTree identifier(AstNode astNode) {
    Preconditions.checkArgument(astNode.is(
      JavaTokenType.IDENTIFIER,
      JavaKeyword.THIS,
      JavaKeyword.CLASS,
      JavaKeyword.SUPER
    ), "Unexpected AstNodeType: %s", astNode.getType().toString());
    return new JavaTree.IdentifierTreeImpl(astNode, astNode.getTokenValue());
  }

  private ExpressionTree qualifiedIdentifier(AstNode astNode) {
    Preconditions.checkArgument(astNode.is(JavaGrammar.QUALIFIED_IDENTIFIER), "Unexpected AstNodeType: %s", astNode.getType().toString());
    List<AstNode> identifierNodes = astNode.getChildren(JavaTokenType.IDENTIFIER);
    ExpressionTree result = identifier(identifierNodes.get(0));
    for (int i = 1; i < identifierNodes.size(); i++) {
      result = new JavaTree.MemberSelectExpressionTreeImpl(
        identifierNodes.get(i),
        result,
        identifier(identifierNodes.get(i))
      );
    }
    return result;
  }

  private List<ExpressionTree> qualifiedIdentifierList(AstNode astNode) {
    Preconditions.checkArgument(astNode.is(JavaGrammar.QUALIFIED_IDENTIFIER_LIST), "Unexpected AstNodeType: %s", astNode.getType().toString());
    ImmutableList.Builder<ExpressionTree> result = ImmutableList.builder();
    for (AstNode qualifiedIdentifierNode : astNode.getChildren(JavaGrammar.QUALIFIED_IDENTIFIER)) {
      result.add(qualifiedIdentifier(qualifiedIdentifierNode));
    }
    return result.build();
  }

  @VisibleForTesting
  LiteralTree literal(AstNode astNode) {
    Preconditions.checkArgument(astNode.is(JavaGrammar.LITERAL), "Unexpected AstNodeType: %s", astNode.getType().toString());
    astNode = astNode.getFirstChild();
    return new JavaTree.LiteralTreeImpl(astNode, kindMaps.getLiteral(astNode.getType()));
  }

  /*
   * 4. Types, Values and Variables
   */

  @VisibleForTesting
  PrimitiveTypeTree basicType(AstNode astNode) {
    Preconditions.checkArgument(astNode.is(JavaGrammar.BASIC_TYPE, JavaKeyword.VOID), "Unexpected AstNodeType: %s", astNode.getType().toString());
    return new JavaTree.PrimitiveTypeTreeImpl(astNode);
  }

  private ExpressionTree classType(AstNode astNode) {
    Preconditions.checkArgument(astNode.is(JavaGrammar.CLASS_TYPE), "Unexpected AstNodeType: %s", astNode.getType().toString());
    // TODO
    return identifier(astNode.getFirstChild());
  }

  @VisibleForTesting
  ExpressionTree referenceType(AstNode astNode) {
    Preconditions.checkArgument(astNode.is(JavaGrammar.REFERENCE_TYPE, JavaGrammar.TYPE), "Unexpected AstNodeType: %s", astNode.getType().toString());
    ExpressionTree result = astNode.getFirstChild().is(JavaGrammar.BASIC_TYPE) ? basicType(astNode.getFirstChild()) : classType(astNode.getFirstChild());
    return applyDim(result, astNode.getChildren(JavaGrammar.DIM).size());
  }

  private Tree typeArgument(AstNode astNode) {
    Preconditions.checkArgument(astNode.is(JavaGrammar.TYPE_ARGUMENT), "Unexpected AstNodeType: %s", astNode.getType().toString());
    Tree result = referenceType(astNode.getFirstChild(JavaGrammar.REFERENCE_TYPE));
    if (astNode.getFirstChild().is(JavaPunctuator.QUERY)) {
      result = new JavaTree.WildcardTreeImpl(astNode, result);
    }
    return result;
  }

  private Tree typeArguments(AstNode astNode) {
    Preconditions.checkArgument(astNode.is(JavaGrammar.TYPE_ARGUMENTS), "Unexpected AstNodeType: %s", astNode.getType().toString());
    // TODO
    return null;
  }

  private ModifiersTree modifiers(List<AstNode> modifierNodes) {
    if (modifierNodes.isEmpty()) {
      return JavaTree.ModifiersTreeImpl.EMPTY;
    }

    ImmutableList.Builder<Modifier> modifiers = ImmutableList.builder();
    for (AstNode astNode : modifierNodes) {
      Preconditions.checkArgument(astNode.is(JavaGrammar.MODIFIER), "Unexpected AstNodeType: %s", astNode.getType().toString());
      astNode = astNode.getFirstChild();
      if (astNode.is(JavaGrammar.ANNOTATION)) {
        // TODO
      } else {
        JavaKeyword keyword = (JavaKeyword) astNode.getType();
        modifiers.add(kindMaps.getModifier(keyword));
      }
    }
    return new JavaTree.ModifiersTreeImpl(modifierNodes.get(0), modifiers.build());
  }

  private VariableTree variableDeclarator(ModifiersTree modifiers, ExpressionTree type, AstNode astNode) {
    Preconditions.checkArgument(astNode.is(JavaGrammar.VARIABLE_DECLARATOR), "Unexpected AstNodeType: %s", astNode.getType().toString());
    return new JavaTree.VariableTreeImpl(
      astNode,
      modifiers,
      applyDim(type, astNode.getChildren(JavaGrammar.DIM).size()),
      astNode.getFirstChild(JavaTokenType.IDENTIFIER).getTokenValue(),
      astNode.hasDirectChildren(JavaGrammar.VARIABLE_INITIALIZER) ? variableInitializer(astNode.getFirstChild(JavaGrammar.VARIABLE_INITIALIZER)) : null
    );
  }

  private List<StatementTree> variableDeclarators(ModifiersTree modifiers, ExpressionTree type, AstNode astNode) {
    Preconditions.checkArgument(astNode.is(JavaGrammar.VARIABLE_DECLARATORS), "Unexpected AstNodeType: %s", astNode.getType().toString());
    ImmutableList.Builder<StatementTree> result = ImmutableList.builder();
    for (AstNode variableDeclaratorNode : astNode.getChildren(JavaGrammar.VARIABLE_DECLARATOR)) {
      result.add(variableDeclarator(modifiers, type, variableDeclaratorNode));
    }
    return result.build();
  }

  /*
   * 7.3. Compilation Units
   */

  public CompilationUnitTree compilationUnit(AstNode astNode) {
    Preconditions.checkArgument(astNode.is(JavaGrammar.COMPILATION_UNIT), "Unexpected AstNodeType: %s", astNode.getType().toString());
    ImmutableList.Builder<ImportTree> imports = ImmutableList.builder();
    for (AstNode importNode : astNode.getChildren(JavaGrammar.IMPORT_DECLARATION)) {
      // TODO star import?
      imports.add(new JavaTree.ImportTreeImpl(
        importNode,
        importNode.hasDirectChildren(JavaKeyword.STATIC),
        qualifiedIdentifier(importNode.getFirstChild(JavaGrammar.QUALIFIED_IDENTIFIER))
      ));
    }
    ImmutableList.Builder<Tree> types = ImmutableList.builder();
    for (AstNode typeNode : astNode.getChildren(JavaGrammar.TYPE_DECLARATION)) {
      AstNode declarationNode = typeNode.getFirstChild(
        JavaGrammar.CLASS_DECLARATION,
        JavaGrammar.ENUM_DECLARATION,
        JavaGrammar.INTERFACE_DECLARATION,
        JavaGrammar.ANNOTATION_TYPE_DECLARATION
      );
      if (declarationNode != null) {
        types.add(typeDeclaration(modifiers(typeNode.getChildren(JavaGrammar.MODIFIER)), declarationNode));
      }
    }
    // TODO package annotations
    ExpressionTree packageDeclaration = null;
    if (astNode.hasDirectChildren(JavaGrammar.PACKAGE_DECLARATION)) {
      packageDeclaration = qualifiedIdentifier(astNode.getFirstChild(JavaGrammar.PACKAGE_DECLARATION).getFirstChild(JavaGrammar.QUALIFIED_IDENTIFIER));
    }
    return new JavaTree.CompilationUnitTreeImpl(
      astNode,
      packageDeclaration,
      imports.build(),
      types.build()
    );
  }

  private ClassTree typeDeclaration(ModifiersTree modifiers, AstNode astNode) {
    if (astNode.is(JavaGrammar.CLASS_DECLARATION)) {
      return classDeclaration(modifiers, astNode);
    } else if (astNode.is(JavaGrammar.ENUM_DECLARATION)) {
      return enumDeclaration(modifiers, astNode);
    } else if (astNode.is(JavaGrammar.INTERFACE_DECLARATION)) {
      return interfaceDeclaration(modifiers, astNode);
    } else if (astNode.is(JavaGrammar.ANNOTATION_TYPE_DECLARATION)) {
      return annotationTypeDeclaration(modifiers, astNode);
    } else {
      throw new IllegalArgumentException("Unexpected AstNodeType: " + astNode.getType().toString());
    }
  }

  /*
   * 8. Classes
   */

  /**
   * 8.1. Class Declarations
   */
  private ClassTree classDeclaration(ModifiersTree modifiers, AstNode astNode) {
    Preconditions.checkArgument(astNode.is(JavaGrammar.CLASS_DECLARATION), "Unexpected AstNodeType: %s", astNode.getType().toString());
    String simpleName = astNode.getFirstChild(JavaTokenType.IDENTIFIER).getTokenValue();
    AstNode extendsNode = astNode.getFirstChild(JavaKeyword.EXTENDS);
    Tree superClass = extendsNode != null ? classType(extendsNode.getNextSibling()) : null;
    AstNode implementsNode = astNode.getFirstChild(JavaKeyword.IMPLEMENTS);
    List<? extends Tree> superInterfaces = implementsNode != null ? classTypeList(implementsNode.getNextSibling()) : ImmutableList.<Tree>of();
    return new JavaTree.ClassTreeImpl(astNode, Tree.Kind.CLASS,
      modifiers,
      simpleName,
      superClass,
      superInterfaces,
      classBody(astNode.getFirstChild(JavaGrammar.CLASS_BODY))
    );
  }

  private List<? extends Tree> classTypeList(AstNode astNode) {
    Preconditions.checkArgument(astNode.is(JavaGrammar.CLASS_TYPE_LIST));
    ImmutableList.Builder<Tree> result = ImmutableList.builder();
    for (AstNode classTypeNode : astNode.getChildren(JavaGrammar.CLASS_TYPE)) {
      result.add(classType(classTypeNode));
    }
    return result.build();
  }

  /**
   * 8.1.6. Class Body and Member Declarations
   */
  private List<Tree> classBody(AstNode astNode) {
    Preconditions.checkArgument(astNode.is(JavaGrammar.CLASS_BODY, JavaGrammar.ENUM_BODY_DECLARATIONS), "Unexpected AstNodeType: %s", astNode.getType().toString());
    ImmutableList.Builder<Tree> members = ImmutableList.builder();
    for (AstNode classBodyDeclaration : astNode.getChildren(JavaGrammar.CLASS_BODY_DECLARATION)) {
      ModifiersTree modifiers = modifiers(classBodyDeclaration.getChildren(JavaGrammar.MODIFIER));
      if (classBodyDeclaration.hasDirectChildren(JavaGrammar.MEMBER_DECL)) {
        AstNode memberDeclNode = classBodyDeclaration.getFirstChild(JavaGrammar.MEMBER_DECL);
        if (memberDeclNode.hasDirectChildren(JavaGrammar.FIELD_DECLARATION)) {
          members.addAll(fieldDeclaration(
            modifiers,
            memberDeclNode.getFirstChild(JavaGrammar.FIELD_DECLARATION)
          ));
        } else {
          members.add(memberDeclaration(modifiers, memberDeclNode));
        }
      } else if (classBodyDeclaration.getFirstChild().is(JavaGrammar.CLASS_INIT_DECLARATION)) {
        AstNode classInitDeclarationNode = classBodyDeclaration.getFirstChild();
        members.add(new JavaTree.BlockTreeImpl(
          classInitDeclarationNode,
          classInitDeclarationNode.hasDirectChildren(JavaKeyword.STATIC) ? Tree.Kind.STATIC_INITIALIZER : Tree.Kind.INITIALIZER,
          blockStatements(classInitDeclarationNode.getFirstChild(JavaGrammar.BLOCK).getFirstChild(JavaGrammar.BLOCK_STATEMENTS))
        ));
      }
    }
    return members.build();
  }

  /**
   * 8.2. Class Members
   */
  private Tree memberDeclaration(ModifiersTree modifiers, AstNode astNode) {
    Preconditions.checkArgument(astNode.is(JavaGrammar.MEMBER_DECL));
    AstNode declaration = astNode.getFirstChild(
      JavaGrammar.INTERFACE_DECLARATION,
      JavaGrammar.CLASS_DECLARATION,
      JavaGrammar.ENUM_DECLARATION,
      JavaGrammar.ANNOTATION_TYPE_DECLARATION
    );
    if (declaration != null) {
      return typeDeclaration(modifiers, declaration);
    }
    declaration = astNode.getFirstChild(JavaGrammar.GENERIC_METHOD_OR_CONSTRUCTOR_REST);
    if (declaration != null) {
      // TODO TYPE_PARAMETERS
      return methodDeclarator(
        modifiers,
        /* type */ declaration.getFirstChild(JavaGrammar.TYPE, JavaKeyword.VOID),
        /* name */ declaration.getFirstChild(JavaTokenType.IDENTIFIER),
        declaration.getFirstChild(JavaGrammar.METHOD_DECLARATOR_REST, JavaGrammar.CONSTRUCTOR_DECLARATOR_REST)
      );
    }
    declaration = astNode.getFirstChild(
      JavaGrammar.METHOD_DECLARATOR_REST,
      JavaGrammar.VOID_METHOD_DECLARATOR_REST,
      JavaGrammar.CONSTRUCTOR_DECLARATOR_REST
    );
    if (declaration != null) {
      return methodDeclarator(
        modifiers,
        /* type */ astNode.getFirstChild(JavaGrammar.TYPE, JavaKeyword.VOID),
        /* name */ astNode.getFirstChild(JavaTokenType.IDENTIFIER),
        declaration
      );
    }
    throw new IllegalStateException();
  }

  /**
   * 8.3. Field Declarations
   */
  private List<StatementTree> fieldDeclaration(ModifiersTree modifiers, AstNode astNode) {
    Preconditions.checkArgument(astNode.is(JavaGrammar.FIELD_DECLARATION), "Unexpected AstNodeType: %s", astNode.getType().toString());
    return variableDeclarators(modifiers, referenceType(astNode.getFirstChild(JavaGrammar.TYPE)), astNode.getFirstChild(JavaGrammar.VARIABLE_DECLARATORS));
  }

  /**
   * 8.4. Method Declarations
   */
  private MethodTree methodDeclarator(ModifiersTree modifiers, @Nullable AstNode returnTypeNode, AstNode name, AstNode astNode) {
    Preconditions.checkArgument(name.is(JavaTokenType.IDENTIFIER));
    Preconditions.checkArgument(astNode.is(
      JavaGrammar.METHOD_DECLARATOR_REST,
      JavaGrammar.VOID_METHOD_DECLARATOR_REST,
      JavaGrammar.CONSTRUCTOR_DECLARATOR_REST,
      JavaGrammar.VOID_INTERFACE_METHOD_DECLARATORS_REST,
      JavaGrammar.INTERFACE_METHOD_DECLARATOR_REST
    ), "Unexpected AstNodeType: %s", astNode.getType().toString());
    // TODO type parameters
    Tree returnType = null;
    if (returnTypeNode != null) {
      if (returnTypeNode.is(JavaKeyword.VOID)) {
        returnType = basicType(returnTypeNode);
      } else {
        returnType = referenceType(returnTypeNode);
      }
    }
    BlockTree body = null;
    if (astNode.hasDirectChildren(JavaGrammar.METHOD_BODY)) {
      body = block(astNode.getFirstChild(JavaGrammar.METHOD_BODY).getFirstChild(JavaGrammar.BLOCK));
    }
    AstNode throwsClauseNode = astNode.getFirstChild(JavaGrammar.QUALIFIED_IDENTIFIER_LIST);
    return new JavaTree.MethodTreeImpl(
      astNode,
      modifiers,
      returnType,
      name.getTokenValue(),
      formalParameters(astNode.getFirstChild(JavaGrammar.FORMAL_PARAMETERS)),
      body,
      throwsClauseNode != null ? qualifiedIdentifierList(throwsClauseNode) : ImmutableList.<ExpressionTree>of(),
      null
    );
  }

  private List<VariableTree> formalParameters(AstNode astNode) {
    Preconditions.checkArgument(astNode.is(JavaGrammar.FORMAL_PARAMETERS), "Unexpected AstNodeType: %s", astNode.getType().toString());
    ImmutableList.Builder<VariableTree> result = ImmutableList.builder();
    for (AstNode variableDeclaratorIdNode : astNode.getDescendants(JavaGrammar.VARIABLE_DECLARATOR_ID)) {
      AstNode typeNode = variableDeclaratorIdNode.getPreviousAstNode();
      Tree type = typeNode.is(JavaPunctuator.ELLIPSIS) ? new JavaTree.ArrayTypeTreeImpl(typeNode, referenceType(typeNode.getPreviousAstNode())) : referenceType(typeNode);
      result.add(new JavaTree.VariableTreeImpl(
        variableDeclaratorIdNode,
        JavaTree.ModifiersTreeImpl.EMPTY,
        type,
        variableDeclaratorIdNode.getFirstChild(JavaTokenType.IDENTIFIER).getTokenValue(),
        null
      ));
    }
    return result.build();
  }

  /**
   * 8.9. Enums
   */
  private ClassTree enumDeclaration(ModifiersTree modifiers, AstNode astNode) {
    Preconditions.checkArgument(astNode.is(JavaGrammar.ENUM_DECLARATION), "Unexpected AstNodeType: %s", astNode.getType().toString());
    IdentifierTree enumType = identifier(astNode.getFirstChild(JavaTokenType.IDENTIFIER));
    ImmutableList.Builder<Tree> members = ImmutableList.builder();
    AstNode enumBodyNode = astNode.getFirstChild(JavaGrammar.ENUM_BODY);
    AstNode enumConstantsNode = enumBodyNode.getFirstChild(JavaGrammar.ENUM_CONSTANTS);
    if (enumConstantsNode != null) {
      for (AstNode enumConstantNode : enumConstantsNode.getChildren(JavaGrammar.ENUM_CONSTANT)) {
        AstNode argumentsNode = enumConstantNode.getFirstChild(JavaGrammar.ARGUMENTS);
        AstNode classBodyNode = enumConstantNode.getFirstChild(JavaGrammar.CLASS_BODY);
        members.add(new JavaTree.EnumConstantTreeImpl(
          enumConstantNode,
          JavaTree.ModifiersTreeImpl.EMPTY,
          enumType,
          enumConstantNode.getFirstChild(JavaTokenType.IDENTIFIER).getTokenValue(),
          new JavaTree.NewClassTreeImpl(
            enumConstantNode,
            /* enclosing expression: */ null,
            argumentsNode != null ? arguments(argumentsNode) : ImmutableList.<ExpressionTree>of(),
            classBodyNode == null ? null : new JavaTree.ClassTreeImpl(
              classBodyNode,
              // TODO verify:
              Tree.Kind.CLASS,
              JavaTree.ModifiersTreeImpl.EMPTY,
              classBody(classBodyNode)
            )
          )
        ));
      }
    }
    AstNode enumBodyDeclarationsNode = enumBodyNode.getFirstChild(JavaGrammar.ENUM_BODY_DECLARATIONS);
    if (enumBodyDeclarationsNode != null) {
      members.addAll(classBody(enumBodyDeclarationsNode));
    }
    AstNode implementsNode = astNode.getFirstChild(JavaKeyword.IMPLEMENTS);
    List<? extends Tree> superInterfaces = implementsNode != null ? classTypeList(implementsNode.getNextSibling()) : ImmutableList.<Tree>of();
    return new JavaTree.ClassTreeImpl(astNode, Tree.Kind.ENUM, modifiers, enumType.name(), null, superInterfaces, members.build());
  }

  /*
   * 9. Interfaces
   */

  /**
   * 9.1. Interface Declarations
   */
  private ClassTree interfaceDeclaration(ModifiersTree modifiers, AstNode astNode) {
    Preconditions.checkArgument(astNode.is(JavaGrammar.INTERFACE_DECLARATION), "Unexpected AstNodeType: %s", astNode.getType().toString());
    String simpleName = astNode.getFirstChild(JavaTokenType.IDENTIFIER).getTokenValue();
    ImmutableList.Builder<Tree> members = ImmutableList.builder();
    for (AstNode interfaceBodyDeclarationNode : astNode.getFirstChild(JavaGrammar.INTERFACE_BODY).getChildren(JavaGrammar.INTERFACE_BODY_DECLARATION)) {
      ModifiersTree memberModifiers = modifiers(interfaceBodyDeclarationNode.getChildren(JavaGrammar.MODIFIER));
      AstNode interfaceMemberDeclNode = interfaceBodyDeclarationNode.getFirstChild(JavaGrammar.INTERFACE_MEMBER_DECL);
      if (interfaceMemberDeclNode != null) {
        appendInterfaceMember(memberModifiers, members, interfaceMemberDeclNode);
      }
    }
    AstNode extendsNode = astNode.getFirstChild(JavaKeyword.EXTENDS);
    List<? extends Tree> superInterfaces = extendsNode != null ? classTypeList(extendsNode.getNextSibling()) : ImmutableList.<Tree>of();
    return new JavaTree.ClassTreeImpl(astNode, Tree.Kind.INTERFACE, modifiers, simpleName, null, superInterfaces, members.build());
  }

  /**
   * 9.1.4. Interface Body and Member Declarations
   */
  private void appendInterfaceMember(ModifiersTree modifiers, ImmutableList.Builder<Tree> members, AstNode astNode) {
    Preconditions.checkArgument(astNode.is(JavaGrammar.INTERFACE_MEMBER_DECL), "Unexpected AstNodeType: %s", astNode.getType().toString());
    AstNode declarationNode = astNode.getFirstChild(
      JavaGrammar.INTERFACE_DECLARATION,
      JavaGrammar.CLASS_DECLARATION,
      JavaGrammar.ENUM_DECLARATION,
      JavaGrammar.ANNOTATION_TYPE_DECLARATION
    );
    if (declarationNode != null) {
      members.add(typeDeclaration(modifiers, declarationNode));
      return;
    }
    declarationNode = astNode.getFirstChild(JavaGrammar.INTERFACE_METHOD_OR_FIELD_DECL);
    if (declarationNode != null) {
      AstNode interfaceMethodOrFieldRestNode = declarationNode.getFirstChild(JavaGrammar.INTERFACE_METHOD_OR_FIELD_REST);
      AstNode interfaceMethodDeclaratorRestNode = interfaceMethodOrFieldRestNode.getFirstChild(JavaGrammar.INTERFACE_METHOD_DECLARATOR_REST);
      if (interfaceMethodDeclaratorRestNode != null) {
        members.add(methodDeclarator(
          modifiers,
          declarationNode.getFirstChild(JavaGrammar.TYPE, JavaKeyword.VOID),
          declarationNode.getFirstChild(JavaTokenType.IDENTIFIER),
          interfaceMethodDeclaratorRestNode
        ));
        return;
      } else {
        appendConstantDeclarations(modifiers, members, declarationNode);
        return;
      }
    }
    declarationNode = astNode.getFirstChild(JavaGrammar.INTERFACE_GENERIC_METHOD_DECL);
    if (declarationNode != null) {
      // TODO TYPE_PARAMETERS
      members.add(methodDeclarator(
        modifiers,
        /* type */ declarationNode.getFirstChild(JavaGrammar.TYPE, JavaKeyword.VOID),
        /* name */ declarationNode.getFirstChild(JavaTokenType.IDENTIFIER),
        declarationNode.getFirstChild(JavaGrammar.INTERFACE_METHOD_DECLARATOR_REST)
      ));
      return;
    }
    declarationNode = astNode.getFirstChild(JavaGrammar.VOID_INTERFACE_METHOD_DECLARATORS_REST);
    if (declarationNode != null) {
      members.add(methodDeclarator(
        modifiers,
        /* type */ astNode.getFirstChild(JavaKeyword.VOID),
        /* name */ astNode.getFirstChild(JavaTokenType.IDENTIFIER),
        declarationNode
      ));
      return;
    }
    throw new IllegalStateException();
  }

  private void appendConstantDeclarations(ModifiersTree modifiers, ImmutableList.Builder<Tree> members, AstNode astNode) {
    Preconditions.checkArgument(astNode.is(JavaGrammar.INTERFACE_METHOD_OR_FIELD_DECL, JavaGrammar.ANNOTATION_TYPE_ELEMENT_REST), "Unexpected AstNodeType: %s", astNode.getType().toString());
    ExpressionTree type = referenceType(astNode.getFirstChild(JavaGrammar.TYPE, JavaKeyword.VOID));
    for (AstNode constantDeclaratorRestNode : astNode.getDescendants(JavaGrammar.CONSTANT_DECLARATOR_REST)) {
      AstNode identifierNode = constantDeclaratorRestNode.getPreviousAstNode();
      Preconditions.checkState(identifierNode.is(JavaTokenType.IDENTIFIER));
      members.add(new JavaTree.VariableTreeImpl(
        constantDeclaratorRestNode,
        modifiers,
        applyDim(type, constantDeclaratorRestNode.getChildren(JavaGrammar.DIM).size()),
        identifierNode.getTokenValue(),
        variableInitializer(constantDeclaratorRestNode.getFirstChild(JavaGrammar.VARIABLE_INITIALIZER))
      ));
    }
  }

  /**
   * 9.6. Annotation Types
   */
  private ClassTree annotationTypeDeclaration(ModifiersTree modifiers, AstNode astNode) {
    Preconditions.checkArgument(astNode.is(JavaGrammar.ANNOTATION_TYPE_DECLARATION), "Unexpected AstNodeType: %s", astNode.getType().toString());
    String simpleName = astNode.getFirstChild(JavaTokenType.IDENTIFIER).getTokenValue();
    ImmutableList.Builder<Tree> members = ImmutableList.builder();
    for (AstNode annotationTypeElementDeclarationNode : astNode.getFirstChild(JavaGrammar.ANNOTATION_TYPE_BODY).getChildren(JavaGrammar.ANNOTATION_TYPE_ELEMENT_DECLARATION)) {
      AstNode annotationTypeElementRestNode = annotationTypeElementDeclarationNode.getFirstChild(JavaGrammar.ANNOTATION_TYPE_ELEMENT_REST);
      if (annotationTypeElementRestNode != null) {
        appendAnnotationTypeElementDeclaration(members, annotationTypeElementRestNode);
      }
    }
    return new JavaTree.ClassTreeImpl(astNode, Tree.Kind.ANNOTATION_TYPE,
      modifiers,
      simpleName,
      null,
      ImmutableList.<Tree>of(),
      members.build()
    );
  }

  /**
   * 9.6.1. Annotation Type Elements
   */
  private void appendAnnotationTypeElementDeclaration(ImmutableList.Builder<Tree> members, AstNode astNode) {
    Preconditions.checkArgument(astNode.is(JavaGrammar.ANNOTATION_TYPE_ELEMENT_REST), "Unexpected AstNodeType: %s", astNode.getType().toString());
    AstNode declarationNode = astNode.getFirstChild(
      JavaGrammar.INTERFACE_DECLARATION,
      JavaGrammar.CLASS_DECLARATION,
      JavaGrammar.ENUM_DECLARATION,
      JavaGrammar.ANNOTATION_TYPE_DECLARATION
    );
    if (declarationNode != null) {
      members.add(typeDeclaration(JavaTree.ModifiersTreeImpl.EMPTY, declarationNode));
      return;
    }
    AstNode typeNode = astNode.getFirstChild(JavaGrammar.TYPE);
    AstNode identifierNode = astNode.getFirstChild(JavaTokenType.IDENTIFIER);
    AstNode annotationMethodRestNode = astNode.getFirstChild(JavaGrammar.ANNOTATION_METHOD_OR_CONSTANT_REST).getFirstChild(JavaGrammar.ANNOTATION_METHOD_REST);
    if (annotationMethodRestNode != null) {
      members.add(new JavaTree.MethodTreeImpl(
        annotationMethodRestNode,
        /* modifiers */ JavaTree.ModifiersTreeImpl.EMPTY,
        /* return type */ referenceType(typeNode),
        /* name */ identifierNode.getTokenValue(),
        /* parameters */ ImmutableList.<VariableTree>of(),
        /* block */ null,
        /* throws */ ImmutableList.<ExpressionTree>of(),
        /* default value */ null // TODO DEFAULT_VALUE
      ));
      return;
    } else {
      appendConstantDeclarations(JavaTree.ModifiersTreeImpl.EMPTY, members, astNode);
    }
  }

  /*
   * 14. Blocks and Statements
   */

  @VisibleForTesting
  public BlockTree block(AstNode astNode) {
    Preconditions.checkArgument(astNode.is(JavaGrammar.BLOCK), "Unexpected AstNodeType: %s", astNode.getType().toString());
    return new JavaTree.BlockTreeImpl(astNode, Tree.Kind.BLOCK, blockStatements(astNode.getFirstChild(JavaGrammar.BLOCK_STATEMENTS)));
  }

  private List<StatementTree> blockStatements(AstNode astNode) {
    Preconditions.checkArgument(astNode.is(JavaGrammar.BLOCK_STATEMENTS), "Unexpected AstNodeType: %s", astNode.getType().toString());
    ImmutableList.Builder<StatementTree> statements = ImmutableList.builder();
    for (AstNode statementNode : astNode.getChildren(JavaGrammar.BLOCK_STATEMENT)) {
      statementNode = statementNode.getFirstChild(
        JavaGrammar.STATEMENT,
        JavaGrammar.LOCAL_VARIABLE_DECLARATION_STATEMENT,
        JavaGrammar.CLASS_DECLARATION,
        JavaGrammar.ENUM_DECLARATION
      );
      if (statementNode.is(JavaGrammar.STATEMENT)) {
        statements.add(statement(statementNode));
      } else if (statementNode.is(JavaGrammar.LOCAL_VARIABLE_DECLARATION_STATEMENT)) {
        // TODO modifiers
        statements.addAll(variableDeclarators(
          JavaTree.ModifiersTreeImpl.EMPTY,
          referenceType(statementNode.getFirstChild(JavaGrammar.TYPE)),
          statementNode.getFirstChild(JavaGrammar.VARIABLE_DECLARATORS)
        ));
      } else if (statementNode.is(JavaGrammar.CLASS_DECLARATION)) {
        statements.add(classDeclaration(JavaTree.ModifiersTreeImpl.EMPTY, statementNode));
      } else if (statementNode.is(JavaGrammar.ENUM_DECLARATION)) {
        statements.add(enumDeclaration(JavaTree.ModifiersTreeImpl.EMPTY, statementNode));
      } else {
        throw new IllegalStateException("Unexpected AstNodeType: " + statementNode.getType().toString());
      }
    }
    return statements.build();
  }

  @VisibleForTesting
  public StatementTree statement(AstNode astNode) {
    Preconditions.checkArgument(astNode.is(JavaGrammar.STATEMENT), "Unexpected AstNodeType: %s", astNode.getType().toString());
    astNode = astNode.getFirstChild();
    switch ((JavaGrammar) astNode.getType()) {
      case BLOCK:
        return block(astNode);
      case EMPTY_STATEMENT:
        return emptyStatement(astNode);
      case LABELED_STATEMENT:
        return labeledStatement(astNode);
      case EXPRESSION_STATEMENT:
        return expressionStatement(astNode);
      case IF_STATEMENT:
        return ifStatement(astNode);
      case ASSERT_STATEMENT:
        return assertStatement(astNode);
      case SWITCH_STATEMENT:
        return switchStatement(astNode);
      case WHILE_STATEMENT:
        return whileStatement(astNode);
      case DO_STATEMENT:
        return doStatement(astNode);
      case FOR_STATEMENT:
        return forStatement(astNode);
      case BREAK_STATEMENT:
        return breakStatement(astNode);
      case CONTINUE_STATEMENT:
        return continueStatement(astNode);
      case RETURN_STATEMENT:
        return returnStatement(astNode);
      case THROW_STATEMENT:
        return throwStatement(astNode);
      case SYNCHRONIZED_STATEMENT:
        return synchronizedStatement(astNode);
      case TRY_STATEMENT:
        return tryStatement(astNode);
      default:
        throw new IllegalStateException("Unexpected AstNodeType: " + astNode.getType().toString());
    }
  }

  /**
   * 14.6. The Empty Statement
   */
  private EmptyStatementTree emptyStatement(AstNode astNode) {
    return new JavaTree.EmptyStatementTreeImpl(astNode);
  }

  /**
   * 14.7. Labeled Statements
   */
  private LabeledStatementTree labeledStatement(AstNode astNode) {
    return new JavaTree.LabeledStatementTreeImpl(
      astNode,
      astNode.getFirstChild(JavaTokenType.IDENTIFIER).getTokenValue(),
      statement(astNode.getFirstChild(JavaGrammar.STATEMENT))
    );
  }

  /**
   * 14.8. Expression Statements
   */
  private ExpressionStatementTree expressionStatement(AstNode astNode) {
    return new JavaTree.ExpressionStatementTreeImpl(
      astNode,
      expression(astNode.getFirstChild(JavaGrammar.STATEMENT_EXPRESSION))
    );
  }

  /**
   * 14.9. The if Statement
   */
  private IfStatementTree ifStatement(AstNode astNode) {
    List<AstNode> statements = astNode.getChildren(JavaGrammar.STATEMENT);
    return new JavaTree.IfStatementTreeImpl(
      astNode,
      expression(astNode.getFirstChild(JavaGrammar.PAR_EXPRESSION)),
      statement(statements.get(0)),
      (statements.size() > 1 ? statement(statements.get(1)) : null)
    );
  }

  /**
   * 14.10. The assert Statement
   */
  private AssertStatementTree assertStatement(AstNode astNode) {
    List<AstNode> expressions = astNode.getChildren(JavaGrammar.EXPRESSION);
    return new JavaTree.AssertStatementTreeImpl(
      astNode,
      expression(expressions.get(0)),
      (expressions.size() > 1 ? expression(expressions.get(1)) : null)
    );
  }

  /**
   * 14.11. The switch Statement
   */
  private SwitchStatementTree switchStatement(AstNode astNode) {
    ImmutableList.Builder<CaseGroupTree> cases = ImmutableList.builder();
    List<JavaTree.CaseLabelTreeImpl> labels = Lists.newArrayList();
    for (AstNode caseNode : astNode.getFirstChild(JavaGrammar.SWITCH_BLOCK_STATEMENT_GROUPS).getChildren(JavaGrammar.SWITCH_BLOCK_STATEMENT_GROUP)) {
      AstNode expressionNode = caseNode.getFirstChild(JavaGrammar.SWITCH_LABEL).getFirstChild(JavaGrammar.CONSTANT_EXPRESSION);
      AstNode blockStatementsNode = caseNode.getFirstChild(JavaGrammar.BLOCK_STATEMENTS);
      labels.add(new JavaTree.CaseLabelTreeImpl(caseNode, expressionNode != null ? expression(expressionNode) : null));
      if (blockStatementsNode.hasChildren()) {
        cases.add(new JavaTree.CaseGroupTreeImpl(
          labels.get(0).getAstNode(),
          ImmutableList.copyOf(labels),
          blockStatements(caseNode.getFirstChild(JavaGrammar.BLOCK_STATEMENTS))
        ));
        labels.clear();
      }
    }
    if (!labels.isEmpty()) {
      cases.add(new JavaTree.CaseGroupTreeImpl(
        labels.get(0).getAstNode(),
        ImmutableList.copyOf(labels),
        ImmutableList.<StatementTree>of()
      ));
    }
    return new JavaTree.SwitchStatementTreeImpl(
      astNode,
      expression(astNode.getFirstChild(JavaGrammar.PAR_EXPRESSION)),
      cases.build()
    );
  }

  /**
   * 14.12. The while Statement
   */
  private WhileStatementTree whileStatement(AstNode astNode) {
    return new JavaTree.WhileStatementTreeImpl(
      astNode,
      expression(astNode.getFirstChild(JavaGrammar.PAR_EXPRESSION)),
      statement(astNode.getFirstChild(JavaGrammar.STATEMENT))
    );
  }

  /**
   * 14.13. The do Statement
   */
  private DoWhileStatementTree doStatement(AstNode astNode) {
    return new JavaTree.DoWhileStatementTreeImpl(
      astNode,
      statement(astNode.getFirstChild(JavaGrammar.STATEMENT)),
      expression(astNode.getFirstChild(JavaGrammar.PAR_EXPRESSION))
    );
  }

  /**
   * 14.14. The for Statement
   */
  private StatementTree forStatement(AstNode astNode) {
    AstNode formalParameterNode = astNode.getFirstChild(JavaGrammar.FORMAL_PARAMETER);
    if (formalParameterNode == null) {
      AstNode forInitNode = astNode.getFirstChild(JavaGrammar.FOR_INIT);
      final List<StatementTree> forInit;
      if (forInitNode == null) {
        forInit = ImmutableList.of();
      } else if (forInitNode.hasDirectChildren(JavaGrammar.VARIABLE_DECLARATORS)) {
        // TODO modifiers
        forInit = variableDeclarators(
          JavaTree.ModifiersTreeImpl.EMPTY,
          referenceType(forInitNode.getFirstChild(JavaGrammar.TYPE)),
          forInitNode.getFirstChild(JavaGrammar.VARIABLE_DECLARATORS)
        );
      } else {
        forInit = statementExpressions(astNode.getFirstChild(JavaGrammar.FOR_INIT));
      }
      return new JavaTree.ForStatementTreeImpl(
        astNode,
        forInit,
        astNode.hasDirectChildren(JavaGrammar.EXPRESSION) ? expression(astNode.getFirstChild(JavaGrammar.EXPRESSION)) : null,
        astNode.hasDirectChildren(JavaGrammar.FOR_UPDATE) ? statementExpressions(astNode.getFirstChild(JavaGrammar.FOR_UPDATE)) : ImmutableList.<StatementTree>of(),
        statement(astNode.getFirstChild(JavaGrammar.STATEMENT))
      );
    } else {
      return new JavaTree.EnhancedForStatementTreeImpl(
        astNode,
        new JavaTree.VariableTreeImpl(
          formalParameterNode,
          JavaTree.ModifiersTreeImpl.EMPTY,
          // TODO dim
          referenceType(formalParameterNode.getFirstChild(JavaGrammar.TYPE)),
          formalParameterNode.getFirstChild(JavaGrammar.VARIABLE_DECLARATOR_ID).getFirstChild(JavaTokenType.IDENTIFIER).getTokenValue(),
          null
        ),
        expression(astNode.getFirstChild(JavaGrammar.EXPRESSION)),
        statement(astNode.getFirstChild(JavaGrammar.STATEMENT))
      );
    }
  }

  private List<StatementTree> statementExpressions(AstNode astNode) {
    Preconditions.checkArgument(astNode.is(JavaGrammar.FOR_INIT, JavaGrammar.FOR_UPDATE), "Unexpected AstNodeType: %s", astNode.getType().toString());
    ImmutableList.Builder<StatementTree> result = ImmutableList.builder();
    for (AstNode statementExpressionNode : astNode.getChildren(JavaGrammar.STATEMENT_EXPRESSION)) {
      result.add(new JavaTree.ExpressionStatementTreeImpl(statementExpressionNode, expression(statementExpressionNode)));
    }
    return result.build();
  }

  /**
   * 14.15. The break Statement
   */
  private BreakStatementTree breakStatement(AstNode astNode) {
    AstNode identifierNode = astNode.getFirstChild(JavaTokenType.IDENTIFIER);
    return new JavaTree.BreakStatementTreeImpl(astNode, identifierNode != null ? identifierNode.getTokenValue() : null);
  }

  /**
   * 14.16. The continue Statement
   */
  private ContinueStatementTree continueStatement(AstNode astNode) {
    AstNode identifierNode = astNode.getFirstChild(JavaTokenType.IDENTIFIER);
    return new JavaTree.ContinueStatementTreeImpl(astNode, identifierNode != null ? identifierNode.getTokenValue() : null);
  }

  /**
   * 14.17. The return Statement
   */
  private ReturnStatementTree returnStatement(AstNode astNode) {
    return new JavaTree.ReturnStatementTreeImpl(
      astNode,
      astNode.hasDirectChildren(JavaGrammar.EXPRESSION) ? expression(astNode.getFirstChild(JavaGrammar.EXPRESSION)) : null
    );
  }

  /**
   * 14.18. The throw Statement
   */
  private ThrowStatementTree throwStatement(AstNode astNode) {
    return new JavaTree.ThrowStatementTreeImpl(
      astNode,
      expression(astNode.getFirstChild(JavaGrammar.EXPRESSION))
    );
  }

  /**
   * 14.19. The synchronized Statement
   */
  private SynchronizedStatementTree synchronizedStatement(AstNode astNode) {
    return new JavaTree.SynchronizedStatementTreeImpl(
      astNode,
      expression(astNode.getFirstChild(JavaGrammar.PAR_EXPRESSION)),
      block(astNode.getFirstChild(JavaGrammar.BLOCK))
    );
  }

  /**
   * 14.20. The try statement
   */
  private TryStatementTree tryStatement(AstNode astNode) {
    if (astNode.hasDirectChildren(JavaGrammar.TRY_WITH_RESOURCES_STATEMENT)) {
      astNode = astNode.getFirstChild(JavaGrammar.TRY_WITH_RESOURCES_STATEMENT);
    }
    ImmutableList.Builder<CatchTree> catches = ImmutableList.builder();
    for (AstNode catchNode : astNode.getChildren(JavaGrammar.CATCH_CLAUSE)) {
      AstNode catchFormalParameterNode = catchNode.getFirstChild(JavaGrammar.CATCH_FORMAL_PARAMETER);
      // TODO multi-catch
      // TODO WTF why VARIABLE_DECLARATOR_ID in grammar?
      catches.add(new JavaTree.CatchTreeImpl(
        catchNode,
        new JavaTree.VariableTreeImpl(
          catchFormalParameterNode,
          // TODO modifiers:
          JavaTree.ModifiersTreeImpl.EMPTY,
          qualifiedIdentifier(catchFormalParameterNode.getFirstChild(JavaGrammar.CATCH_TYPE).getFirstChild(JavaGrammar.QUALIFIED_IDENTIFIER)),
          catchFormalParameterNode.getFirstChild(JavaGrammar.VARIABLE_DECLARATOR_ID).getFirstChild(JavaTokenType.IDENTIFIER).getTokenValue(),
          null
        ),
        block(catchNode.getFirstChild(JavaGrammar.BLOCK))
      ));
    }
    BlockTree finallyBlock = null;
    if (astNode.hasDirectChildren(JavaGrammar.FINALLY_)) {
      finallyBlock = block(astNode.getFirstChild(JavaGrammar.FINALLY_).getFirstChild(JavaGrammar.BLOCK));
    }
    AstNode resourceSpecificationNode = astNode.getFirstChild(JavaGrammar.RESOURCE_SPECIFICATION);
    return new JavaTree.TryStatementTreeImpl(
      astNode,
      resourceSpecificationNode == null ? ImmutableList.<VariableTree>of() : resourceSpecification(resourceSpecificationNode),
      block(astNode.getFirstChild(JavaGrammar.BLOCK)),
      catches.build(),
      finallyBlock
    );
  }

  private List<VariableTree> resourceSpecification(AstNode astNode) {
    Preconditions.checkArgument(astNode.is(JavaGrammar.RESOURCE_SPECIFICATION), "Unexpected AstNodeType: %s", astNode.getType().toString());
    ImmutableList.Builder<VariableTree> result = ImmutableList.builder();
    for (AstNode resourceNode : astNode.getChildren(JavaGrammar.RESOURCE)) {
      result.add(new JavaTree.VariableTreeImpl(
        resourceNode,
        // TODO modifiers:
        JavaTree.ModifiersTreeImpl.EMPTY,
        classType(resourceNode.getFirstChild(JavaGrammar.CLASS_TYPE)),
        resourceNode.getFirstChild(JavaGrammar.VARIABLE_DECLARATOR_ID).getFirstChild(JavaTokenType.IDENTIFIER).getTokenValue(),
        expression(resourceNode.getFirstChild(JavaGrammar.EXPRESSION))
      ));
    }
    return result.build();
  }

  @VisibleForTesting
  public ExpressionTree expression(AstNode astNode) {
    if (astNode.is(JavaGrammar.CONSTANT_EXPRESSION, JavaGrammar.STATEMENT_EXPRESSION)) {
      astNode = astNode.getFirstChild(JavaGrammar.EXPRESSION).getFirstChild();
    } else if (astNode.is(JavaGrammar.EXPRESSION)) {
      astNode = astNode.getFirstChild();
    }

    if (astNode.is(JavaGrammar.PAR_EXPRESSION)) {
      return new JavaTree.ParenthesizedTreeImpl(astNode, expression(astNode.getFirstChild(JavaGrammar.EXPRESSION)));
    } else if (astNode.is(JavaGrammar.PRIMARY)) {
      return primary(astNode);
    } else if (astNode.is(JavaGrammar.CONDITIONAL_OR_EXPRESSION,
      JavaGrammar.CONDITIONAL_AND_EXPRESSION,
      JavaGrammar.INCLUSIVE_OR_EXPRESSION,
      JavaGrammar.EXCLUSIVE_OR_EXPRESSION,
      JavaGrammar.AND_EXPRESSION,
      JavaGrammar.EQUALITY_EXPRESSION,
      JavaGrammar.RELATIONAL_EXPRESSION,
      JavaGrammar.SHIFT_EXPRESSION,
      JavaGrammar.ADDITIVE_EXPRESSION,
      JavaGrammar.MULTIPLICATIVE_EXPRESSION)) {
      return binaryExpression(astNode);
    } else if (astNode.is(JavaGrammar.CONDITIONAL_EXPRESSION)) {
      return conditionalExpression(astNode);
    } else if (astNode.is(JavaGrammar.ASSIGNMENT_EXPRESSION)) {
      return assignmentExpression(astNode);
    } else if (astNode.is(JavaGrammar.UNARY_EXPRESSION)) {
      return unaryExpression(astNode);
    } else {
      throw new IllegalArgumentException("Unexpected AstNodeType: " + astNode.getType().toString());
    }
  }

  /**
   * 15.11. Field Access Expressions
   * 15.12. Method Invocation Expressions
   * 15.13. Array Access Expressions
   */
  @VisibleForTesting
  public ExpressionTree primary(AstNode astNode) {
    AstNode firstChildNode = astNode.getFirstChild();
    if (firstChildNode.is(JavaGrammar.PAR_EXPRESSION)) {
      // (expression)
      return expression(firstChildNode);
    } else if (firstChildNode.is(JavaGrammar.NON_WILDCARD_TYPE_ARGUMENTS)) {
      // FIXME
      throw new UnsupportedOperationException("not implemented");
    } else if (firstChildNode.is(JavaKeyword.THIS)) {
      IdentifierTree identifier = identifier(firstChildNode);
      if (astNode.hasDirectChildren(JavaGrammar.ARGUMENTS)) {
        // this(arguments)
        return new JavaTree.MethodInvocationTreeImpl(
          astNode,
          identifier,
          arguments(astNode.getFirstChild(JavaGrammar.ARGUMENTS))
        );
      } else {
        // this
        return identifier;
      }
    } else if (firstChildNode.is(JavaKeyword.SUPER)) {
      // super...
      return applySuperSuffix(
        identifier(firstChildNode),
        astNode.getFirstChild(JavaGrammar.SUPER_SUFFIX)
      );
    } else if (firstChildNode.is(JavaGrammar.LITERAL)) {
      // "literal"
      return literal(firstChildNode);
    } else if (firstChildNode.is(JavaKeyword.NEW)) {
      // new...
      return creator(astNode.getFirstChild(JavaGrammar.CREATOR));
    } else if (firstChildNode.is(JavaGrammar.QUALIFIED_IDENTIFIER)) {
      ExpressionTree identifier = qualifiedIdentifier(firstChildNode);
      AstNode identifierSuffixNode = astNode.getFirstChild(JavaGrammar.IDENTIFIER_SUFFIX);
      if (identifierSuffixNode == null) {
        // id
        return identifier;
      } else {
        if (identifierSuffixNode.getFirstChild().is(JavaPunctuator.LBRK)) {
          if (identifierSuffixNode.hasDirectChildren(JavaKeyword.CLASS)) {
            // 15.8.2. Class Literals
            // id[].class
            return new JavaTree.MemberSelectExpressionTreeImpl(
              astNode,
              applyDim(identifier, identifierSuffixNode.getChildren(JavaGrammar.DIM).size() + 1),
              identifier(identifierSuffixNode.getFirstChild(JavaKeyword.CLASS))
            );
          } else {
            // id[expression]
            return new JavaTree.ArrayAccessExpressionTreeImpl(
              astNode,
              identifier,
              expression(identifierSuffixNode.getFirstChild(JavaGrammar.EXPRESSION))
            );
          }
        } else if (identifierSuffixNode.getFirstChild().is(JavaGrammar.ARGUMENTS)) {
          // id(arguments)
          return new JavaTree.MethodInvocationTreeImpl(
            astNode,
            identifier,
            arguments(identifierSuffixNode.getFirstChild())
          );
        } else if (identifierSuffixNode.getFirstChild().is(JavaPunctuator.DOT)) {
          if (identifierSuffixNode.hasDirectChildren(JavaKeyword.CLASS)) {
            // 15.8.2. Class Literals
            // id.class
            return new JavaTree.MemberSelectExpressionTreeImpl(
              astNode,
              identifier,
              identifier(identifierSuffixNode.getFirstChild(JavaKeyword.CLASS))
            );
          } else if (identifierSuffixNode.hasDirectChildren(JavaGrammar.EXPLICIT_GENERIC_INVOCATION)) {
            // id.<...>...
            return applyExplicitGenericInvocation(identifier, identifierSuffixNode.getFirstChild(JavaGrammar.EXPLICIT_GENERIC_INVOCATION));
          } else if (identifierSuffixNode.hasDirectChildren(JavaKeyword.THIS)) {
            // id.this
            return new JavaTree.MemberSelectExpressionTreeImpl(
              astNode,
              identifier,
              identifier(identifierSuffixNode.getFirstChild(JavaKeyword.THIS))
            );
          } else if (identifierSuffixNode.hasDirectChildren(JavaKeyword.SUPER)) {
            // id.super(arguments)
            return new JavaTree.MethodInvocationTreeImpl(
              astNode,
              new JavaTree.MemberSelectExpressionTreeImpl(
                astNode,
                identifier,
                identifier(identifierSuffixNode.getFirstChild(JavaKeyword.SUPER))
              ),
              arguments(identifierSuffixNode.getFirstChild(JavaGrammar.ARGUMENTS))
            );
          } else if (identifierSuffixNode.hasDirectChildren(JavaKeyword.NEW)) {
            // id.new...
            return applyClassCreatorRest(identifier, identifierSuffixNode.getFirstChild(JavaGrammar.INNER_CREATOR).getFirstChild(JavaGrammar.CLASS_CREATOR_REST));
          } else {
            throw new IllegalArgumentException("Unexpected AstNodeType: " + identifierSuffixNode.getChild(1));
          }
        } else {
          throw new IllegalArgumentException("Unexpected AstNodeType: " + identifierSuffixNode.getFirstChild());
        }
      }
    } else if (firstChildNode.is(JavaGrammar.BASIC_TYPE, JavaKeyword.VOID)) {
      // 15.8.2. Class Literals
      // int.class
      // int[].class
      // void.class
      return new JavaTree.MemberSelectExpressionTreeImpl(
        astNode,
        applyDim(basicType(firstChildNode), astNode.getChildren(JavaGrammar.DIM).size()),
        identifier(astNode.getFirstChild(JavaKeyword.CLASS))
      );
    } else {
      throw new IllegalArgumentException("Unexpected AstNodeType: " + firstChildNode.getType());
    }
  }

  private ExpressionTree creator(AstNode astNode) {
    // TODO NON_WILDCARD_TYPE_ARGUMENTS
    if (astNode.hasDirectChildren(JavaGrammar.CLASS_CREATOR_REST)) {
      return applyClassCreatorRest(null, astNode.getFirstChild(JavaGrammar.CLASS_CREATOR_REST));
    } else if (astNode.hasDirectChildren(JavaGrammar.ARRAY_CREATOR_REST)) {
      AstNode arrayCreatorRestNode = astNode.getFirstChild(JavaGrammar.ARRAY_CREATOR_REST);
      AstNode typeNode = arrayCreatorRestNode.getPreviousSibling();
      Tree type = typeNode.is(JavaGrammar.BASIC_TYPE) ? basicType(typeNode) : classType(typeNode);
      if (arrayCreatorRestNode.hasDirectChildren(JavaGrammar.ARRAY_INITIALIZER)) {
        return arrayInitializer(type, arrayCreatorRestNode.getFirstChild(JavaGrammar.ARRAY_INITIALIZER));
      } else {
        ImmutableList.Builder<ExpressionTree> dimensions = ImmutableList.builder();
        dimensions.add(expression(arrayCreatorRestNode.getFirstChild(JavaGrammar.EXPRESSION)));
        for (AstNode dimExpr : arrayCreatorRestNode.getChildren(JavaGrammar.DIM_EXPR)) {
          dimensions.add(expression(dimExpr.getFirstChild(JavaGrammar.EXPRESSION)));
        }
        return new JavaTree.NewArrayTreeImpl(astNode, type, dimensions.build(), ImmutableList.<ExpressionTree>of());
      }
    } else {
      throw new IllegalArgumentException("Unexpected AstNodeType: " + astNode);
    }
  }

  private ExpressionTree arrayInitializer(@Nullable Tree t, AstNode astNode) {
    ImmutableList.Builder<ExpressionTree> elems = ImmutableList.builder();
    for (AstNode elem : astNode.getChildren(JavaGrammar.VARIABLE_INITIALIZER)) {
      elems.add(variableInitializer(elem));
    }
    return new JavaTree.NewArrayTreeImpl(astNode, t, ImmutableList.<ExpressionTree>of(), elems.build());
  }

  private ExpressionTree variableInitializer(AstNode astNode) {
    if (astNode.getFirstChild().is(JavaGrammar.EXPRESSION)) {
      return expression(astNode.getFirstChild());
    } else {
      return arrayInitializer(null, astNode.getFirstChild());
    }
  }

  /**
   * 15.14. Postfix Expressions
   * 15.15. Unary Operators
   * 15.16. Cast Expressions
   */
  private ExpressionTree unaryExpression(AstNode astNode) {
    if (astNode.hasDirectChildren(JavaGrammar.TYPE)) {
      // 15.16. Cast Expressions
      return new JavaTree.TypeCastExpressionTreeImpl(
        astNode,
        referenceType(astNode.getFirstChild(JavaGrammar.TYPE)),
        expression(astNode.getChild(3))
      );
    } else if (astNode.hasDirectChildren(JavaGrammar.PREFIX_OP)) {
      // 15.15. Unary Operators
      JavaPunctuator punctuator = (JavaPunctuator) astNode.getFirstChild(JavaGrammar.PREFIX_OP).getFirstChild().getType();
      Tree.Kind kind = kindMaps.getPrefixOperator(punctuator);
      return new JavaTree.UnaryExpressionTreeImpl(
        astNode,
        kind,
        expression(astNode.getChild(1))
      );
    } else {
      // 15.14. Postfix Expressions
      ExpressionTree result = expression(astNode.getFirstChild());
      for (AstNode selectorNode : astNode.getChildren(JavaGrammar.SELECTOR)) {
        result = applySelector(result, selectorNode);
      }
      for (AstNode postfixOpNode : astNode.getChildren(JavaGrammar.POST_FIX_OP)) {
        JavaPunctuator punctuator = (JavaPunctuator) postfixOpNode.getFirstChild().getType();
        Tree.Kind kind = kindMaps.getPostfixOperator(punctuator);
        result = new JavaTree.UnaryExpressionTreeImpl(astNode, kind, result);
      }
      return result;
    }
  }

  /**
   * 15.17. Multiplicative Operators
   * 15.18. Additive Operators
   * 15.19. Shift Operators
   * 15.20. Relational Operators
   * 15.21. Equality Operators
   * 15.22. Bitwise and Logical Operators
   * 15.23. Conditional-And Operator &&
   * 15.24. Conditional-Or Operator ||
   */
  private ExpressionTree binaryExpression(AstNode astNode) {
    if (astNode.hasDirectChildren(JavaKeyword.INSTANCEOF)) {
      // 15.20.2. Type Comparison Operator instanceof
      // TODO fix grammar - instanceof can't be chained
      return new JavaTree.InstanceOfTreeImpl(
        astNode,
        expression(astNode.getFirstChild()),
        referenceType(astNode.getFirstChild(JavaGrammar.REFERENCE_TYPE))
      );
    }

    ExpressionTree expression = expression(astNode.getLastChild());
    for (int i = astNode.getNumberOfChildren() - 3; i >= 0; i -= 2) {
      JavaPunctuator punctuator = (JavaPunctuator) astNode.getChild(i + 1).getType();
      Tree.Kind kind = kindMaps.getBinaryOperator(punctuator);
      expression = new JavaTree.BinaryExpressionTreeImpl(
        astNode,
        expression(astNode.getChild(i)),
        kind,
        expression
      );
    }
    return expression;
  }

  /**
   * 15.25. Conditional Operators
   */
  private ExpressionTree conditionalExpression(AstNode astNode) {
    // TODO verify
    ExpressionTree expression = expression(astNode.getLastChild());
    for (int i = astNode.getNumberOfChildren() - 5; i >= 0; i -= 4) {
      expression = new JavaTree.ConditionalExpressionTreeImpl(
        astNode,
        expression(astNode.getChild(i)),
        expression(astNode.getChild(i + 2)),
        expression
      );
    }
    return expression;
  }

  /**
   * 15.26. Assignment Operators
   */
  private ExpressionTree assignmentExpression(AstNode astNode) {
    ExpressionTree expression = expression(astNode.getLastChild());
    for (int i = astNode.getNumberOfChildren() - 3; i >= 0; i -= 2) {
      JavaPunctuator punctuator = (JavaPunctuator) astNode.getChild(i + 1).getFirstChild().getType();
      Tree.Kind kind = kindMaps.getAssignmentOperator(punctuator);
      expression = new JavaTree.AssignmentExpressionTreeImpl(
        astNode,
        expression(astNode.getChild(i)),
        kind,
        expression
      );
    }
    return expression;
  }

  private ExpressionTree applySelector(ExpressionTree expression, AstNode selectorNode) {
    Preconditions.checkArgument(selectorNode.is(JavaGrammar.SELECTOR), "Unexpected AstNodeType: %s", selectorNode.getType().toString());
    if (selectorNode.hasDirectChildren(JavaGrammar.ARGUMENTS)) {
      return new JavaTree.MethodInvocationTreeImpl(
        selectorNode,
        new JavaTree.MemberSelectExpressionTreeImpl(
          selectorNode,
          expression,
          identifier(selectorNode.getFirstChild(JavaTokenType.IDENTIFIER))
        ),
        arguments(selectorNode.getFirstChild(JavaGrammar.ARGUMENTS))
      );
    } else if (selectorNode.hasDirectChildren(JavaTokenType.IDENTIFIER)) {
      return new JavaTree.MemberSelectExpressionTreeImpl(
        selectorNode,
        expression,
        identifier(selectorNode.getFirstChild(JavaTokenType.IDENTIFIER))
      );
    } else if (selectorNode.hasDirectChildren(JavaGrammar.EXPLICIT_GENERIC_INVOCATION)) {
      return applyExplicitGenericInvocation(expression, selectorNode.getFirstChild(JavaGrammar.EXPLICIT_GENERIC_INVOCATION));
    } else if (selectorNode.hasDirectChildren(JavaKeyword.THIS)) {
      return new JavaTree.MemberSelectExpressionTreeImpl(
        selectorNode,
        expression,
        identifier(selectorNode.getFirstChild(JavaKeyword.THIS))
      );
    } else if (selectorNode.hasDirectChildren(JavaGrammar.SUPER_SUFFIX)) {
      return applySuperSuffix(
        new JavaTree.MemberSelectExpressionTreeImpl(
          selectorNode,
          expression,
          identifier(selectorNode.getFirstChild(JavaKeyword.SUPER))
        ),
        selectorNode.getFirstChild(JavaGrammar.SUPER_SUFFIX)
      );
    } else if (selectorNode.hasDirectChildren(JavaKeyword.NEW)) {
      // dead grammar part?
      throw new UnsupportedOperationException("not implemented");
    } else if (selectorNode.hasDirectChildren(JavaGrammar.DIM_EXPR)) {
      return new JavaTree.ArrayAccessExpressionTreeImpl(
        selectorNode,
        expression,
        expression(selectorNode.getFirstChild(JavaGrammar.DIM_EXPR).getFirstChild(JavaGrammar.EXPRESSION))
      );
    } else {
      throw new IllegalStateException(AstXmlPrinter.print(selectorNode));
    }
  }

  private ExpressionTree applySuperSuffix(ExpressionTree expression, AstNode superSuffixNode) {
    Preconditions.checkArgument(superSuffixNode.is(JavaGrammar.SUPER_SUFFIX), "Unexpected AstNodeType: %s", superSuffixNode.getType().toString());
    if (superSuffixNode.hasDirectChildren(JavaGrammar.ARGUMENTS)) {
      // super(arguments)
      // super.method(arguments)
      // super.<T>method(arguments)
      // TODO typeArguments
      ExpressionTree methodSelect = expression;
      if (superSuffixNode.hasDirectChildren(JavaTokenType.IDENTIFIER)) {
        methodSelect = new JavaTree.MemberSelectExpressionTreeImpl(
          superSuffixNode,
          expression,
          identifier(superSuffixNode.getFirstChild(JavaTokenType.IDENTIFIER))
        );
      }
      return new JavaTree.MethodInvocationTreeImpl(
        superSuffixNode,
        methodSelect,
        arguments(superSuffixNode.getFirstChild(JavaGrammar.ARGUMENTS))
      );
    } else {
      // super.field
      return new JavaTree.MemberSelectExpressionTreeImpl(
        superSuffixNode,
        expression,
        identifier(superSuffixNode.getFirstChild(JavaTokenType.IDENTIFIER))
      );
    }
  }

  private ExpressionTree applyClassCreatorRest(ExpressionTree enclosingExpression, AstNode classCreatorRestNode) {
    Preconditions.checkArgument(classCreatorRestNode.is(JavaGrammar.CLASS_CREATOR_REST), "Unexpected AstNodeType: %s", classCreatorRestNode.getType().toString());
    ClassTree classBody = null;
    if (classCreatorRestNode.hasDirectChildren(JavaGrammar.CLASS_BODY)) {
      classBody = new JavaTree.ClassTreeImpl(
        classCreatorRestNode,
        /* TODO verify: */ Tree.Kind.CLASS,
        JavaTree.ModifiersTreeImpl.EMPTY,
        classBody(classCreatorRestNode.getFirstChild(JavaGrammar.CLASS_BODY))
      );
    }
    return new JavaTree.NewClassTreeImpl(
      classCreatorRestNode,
      enclosingExpression,
      arguments(classCreatorRestNode.getFirstChild(JavaGrammar.ARGUMENTS)),
      classBody
    );
  }

  private ExpressionTree applyExplicitGenericInvocation(ExpressionTree expression, AstNode astNode) {
    Preconditions.checkArgument(astNode.is(JavaGrammar.EXPLICIT_GENERIC_INVOCATION), "Unexpected AstNodeType: %s", astNode.getType().toString());
    // TODO NON_WILDCARD_TYPE_ARGUMENTS
    AstNode explicitGenericInvocationSuffixNode = astNode.getFirstChild(JavaGrammar.EXPLICIT_GENERIC_INVOCATION_SUFFIX);
    if (explicitGenericInvocationSuffixNode.hasDirectChildren(JavaGrammar.SUPER_SUFFIX)) {
      expression = new JavaTree.MemberSelectExpressionTreeImpl(
        astNode,
        expression,
        identifier(explicitGenericInvocationSuffixNode.getFirstChild(JavaKeyword.SUPER))
      );
      return applySuperSuffix(expression, explicitGenericInvocationSuffixNode.getFirstChild(JavaGrammar.SUPER_SUFFIX));
    } else {
      return new JavaTree.MethodInvocationTreeImpl(
        astNode,
        new JavaTree.MemberSelectExpressionTreeImpl(
          astNode,
          expression,
          identifier(explicitGenericInvocationSuffixNode.getFirstChild(JavaTokenType.IDENTIFIER))
        ),
        arguments(explicitGenericInvocationSuffixNode.getFirstChild(JavaGrammar.ARGUMENTS))
      );
    }
  }

  private List<? extends ExpressionTree> arguments(AstNode astNode) {
    Preconditions.checkArgument(astNode.is(JavaGrammar.ARGUMENTS), "Unexpected AstNodeType: %s", astNode.getType().toString());
    ImmutableList.Builder<ExpressionTree> arguments = ImmutableList.builder();
    for (AstNode argument : astNode.getChildren(JavaGrammar.EXPRESSION)) {
      arguments.add(expression(argument));
    }
    return arguments.build();
  }

  private ExpressionTree applyDim(ExpressionTree expression, int count) {
    for (int i = 0; i < count; i++) {
      expression = new JavaTree.ArrayTypeTreeImpl(/* FIXME should not be null */null, expression);
    }
    return expression;
  }

}
