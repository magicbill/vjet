/*******************************************************************************
 * Copyright (c) 2005, 2007 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *

 *******************************************************************************/
package org.eclipse.dltk.mod.evaluation.types;

import org.eclipse.dltk.mod.core.IType;
import org.eclipse.dltk.mod.ti.types.IEvaluatedType;

public class ModelClassType implements IClassType {
	private IType fClass;

	public ModelClassType(IType classElement) {
		this.fClass = classElement;
	}

	public boolean equals(Object obj) {

		if (obj instanceof ModelClassType) {
			ModelClassType m = (ModelClassType) obj;
			if (this.fClass == m.fClass) {
				return true;
			}
		}
		return false;
	}
	

	public int hashCode() {
		return this.fClass.hashCode();
	}

	public String getTypeName() {
		if (fClass != null) {
			return "class:" + fClass.getElementName(); //$NON-NLS-1$
		}
		return "class: !!unknown!!"; //$NON-NLS-1$
	}

	public IType getTypeDeclaration() {

		return this.fClass;
	}

	public boolean subtypeOf(IEvaluatedType type) {
		// TODO Auto-generated method stub
		return false;
	}
}
