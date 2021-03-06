/*******************************************************************************
 * Copyright (c) 2005, 2007 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 
 *******************************************************************************/
package org.eclipse.dltk.mod.ast.statements;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.eclipse.dltk.mod.ast.ASTNode;
import org.eclipse.dltk.mod.ast.ASTVisitor;
import org.eclipse.dltk.mod.ast.expressions.Expression;
import org.eclipse.dltk.mod.utils.CorePrinter;

public class Block extends Expression {
	private List statements;

	public Block() {
		this.statements = new ArrayList();
	}

	public Block(int start, int end) {
		this(start, end, null);
	}
	
	public Block(int start, int end, List statems) {
		super(start, end);
		this.statements = statems != null ? new ArrayList(statems)
				: new ArrayList();
	}

	public void traverse(ASTVisitor visitor) throws Exception {
		if (visitor.visit(this)) {
			Iterator it = statements.iterator();
			while (it.hasNext()) {
				ASTNode node = (ASTNode) it.next();
				node.traverse(visitor);
			}
			visitor.endvisit(this);
		}
	}

	public int getKind() {
		return S_BLOCK;
	}

	public void acceptStatements(List statems) {
		if (statems == null) {
			throw new IllegalArgumentException();
		}

		statements.addAll(statems);
	}

	public List getStatements() {
		return statements;
	}

	public void addStatement(ASTNode statem) {
		if (statem == null) {
			throw new IllegalArgumentException();
		}

		statements.add(statem);
	}

	public void printNode(CorePrinter output) {
		output.indent();
		Iterator it = statements.iterator();
		while (it.hasNext()) {
			((ASTNode) it.next()).printNode(output);
			output.formatPrint(""); //$NON-NLS-1$
		}
		output.formatPrint(""); //$NON-NLS-1$
		output.dedent();
	}

	public void removeStatement(ASTNode node) {
		if (node == null) {
			throw new IllegalArgumentException();
		}
		statements.remove(node);
	}
}
