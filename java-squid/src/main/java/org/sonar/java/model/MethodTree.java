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

import javax.annotation.Nullable;
import java.util.List;

/**
 * Method or annotation type element declaration.
 * <p>
 * JLS 8.8. Constructor:
 * <pre>
 *   {@link #modifiers()} {@link #typeParameters()} {@link #simpleName()} ()} ( {@link #parameters()} ) throws {@link #throwsClauses()} {@link #block()}
 * </pre>
 * JLS 8.4, 9.4. Method:
 * <pre>
 *   {@link #modifiers()} {@link #typeParameters()} {@link #returnType()} {@link #simpleName()} ()} ( {@link #parameters()} ) throws {@link #throwsClauses()} {@link #block()}
 * </pre>
 * JLS 9.6.1, 9.6.2. Annotation type element:
 * <pre>
 *   {@link #modifiers()} {@link #returnType()} {@link #simpleName()} ()} default {@link #defaultValue()} ;
 * </pre>
 * </p>
 *
 * @since Java 1.3
 */
public interface MethodTree extends Tree {

  ModifiersTree modifiers();

  List<? extends Tree> typeParameters();

  /**
   * @return null in case of constructor
   */
  @Nullable
  Tree returnType();

  String simpleName();

  List<? extends VariableTree> parameters();

  List<? extends ExpressionTree> throwsClauses();

  @Nullable
  BlockTree block();

  /**
   * @since Java 1.5
   */
  @Nullable
  ExpressionTree defaultValue();

}
