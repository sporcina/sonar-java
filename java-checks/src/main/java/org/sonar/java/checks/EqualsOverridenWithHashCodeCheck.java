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
package org.sonar.java.checks;

import org.sonar.api.rule.RuleKey;
import org.sonar.check.BelongsToProfile;
import org.sonar.check.Priority;
import org.sonar.check.Rule;
import org.sonar.java.model.BaseTreeVisitor;
import org.sonar.java.model.ClassTree;
import org.sonar.java.model.JavaFileScanner;
import org.sonar.java.model.JavaFileScannerContext;
import org.sonar.java.model.MethodTree;
import org.sonar.java.model.Tree;

@Rule(
  key = EqualsOverridenWithHashCodeCheck.KEY,
  priority = Priority.BLOCKER)
@BelongsToProfile(title = "Sonar way", priority = Priority.BLOCKER)
public class EqualsOverridenWithHashCodeCheck extends BaseTreeVisitor implements JavaFileScanner {

  public static final String KEY = "S1206";
  private static final RuleKey RULE_KEY = RuleKey.of(CheckList.REPOSITORY_KEY, KEY);

  private static final String HASHCODE = "hashCode";
  private static final String EQUALS = "equals";

  private JavaFileScannerContext context;

  @Override
  public void scanFile(JavaFileScannerContext context) {
    this.context = context;
    scan(context.getTree());
  }

  @Override
  public void visitClass(ClassTree tree) {
    super.visitClass(tree);

    if (tree.is(Tree.Kind.CLASS) || tree.is(Tree.Kind.ENUM) || tree.is(Tree.Kind.INTERFACE)) {
      MethodTree equalsMethod = null;
      MethodTree hashCodeMethod = null;

      for (Tree memberTree : tree.members()) {
        if (memberTree.is(Tree.Kind.METHOD)) {
          MethodTree methodTree = (MethodTree) memberTree;
          if (EQUALS.equals(methodTree.simpleName()) && methodTree.parameters().size() == 1) {
            equalsMethod = methodTree;
          } else if (HASHCODE.equals(methodTree.simpleName()) && methodTree.parameters().isEmpty()) {
            hashCodeMethod = methodTree;
          }
        }
      }

      if (equalsMethod != null && hashCodeMethod == null) {
        context.addIssue(equalsMethod, RULE_KEY, getMessage(classTreeType(tree), EQUALS, HASHCODE));
      } else if (hashCodeMethod != null && equalsMethod == null) {
        context.addIssue(hashCodeMethod, RULE_KEY, getMessage(classTreeType(tree), HASHCODE, EQUALS));
      }
    }
  }

  private static String classTreeType(ClassTree tree) {
    String type;

    if (tree.is(Tree.Kind.CLASS)) {
      type = "class";
    } else if (tree.is(Tree.Kind.ENUM)) {
      type = "enum";
    } else if (tree.is(Tree.Kind.INTERFACE)) {
      type = "interface";
    } else {
      throw new IllegalStateException();
    }

    return type;
  }

  private static String getMessage(String type, String overridenMethod, String methodToOverride) {
    return "This " + type + " overrides \"" + overridenMethod + "()\" and should therefore also override \"" + methodToOverride + "()\".";
  }

}
