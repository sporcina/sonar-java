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
import org.sonar.java.model.BlockTree;
import org.sonar.java.model.CaseGroupTree;
import org.sonar.java.model.JavaFileScanner;
import org.sonar.java.model.JavaFileScannerContext;
import org.sonar.java.model.StatementTree;
import org.sonar.java.model.Tree;

import java.util.List;

@Rule(
  key = NestedBlocksCheck.RULE_KEY,
  priority = Priority.MAJOR)
@BelongsToProfile(title = "Sonar way", priority = Priority.MAJOR)
public class NestedBlocksCheck extends BaseTreeVisitor implements JavaFileScanner {

  public static final String RULE_KEY = "S1199";
  private final RuleKey ruleKey = RuleKey.of(CheckList.REPOSITORY_KEY, RULE_KEY);

  private JavaFileScannerContext context;

  @Override
  public void scanFile(JavaFileScannerContext context) {
    this.context = context;
    scan(context.getTree());
  }

  @Override
  public void visitCaseGroup(CaseGroupTree tree) {
    checkStatements(tree.body());
    super.visitCaseGroup(tree);
  }

  @Override
  public void visitBlock(BlockTree tree) {
    checkStatements(tree.body());
    super.visitBlock(tree);
  }

  private void checkStatements(List<? extends StatementTree> statements) {
    for (StatementTree statement : statements) {
      if (statement.is(Tree.Kind.BLOCK)) {
        context.addIssue(statement, ruleKey, "Extract this nested code block into a method.");
      }
    }
  }

}
