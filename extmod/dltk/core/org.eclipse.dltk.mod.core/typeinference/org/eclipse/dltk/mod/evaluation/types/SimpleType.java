/*******************************************************************************
 * Copyright (c) 2005, 2007 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *

 *******************************************************************************/
package org.eclipse.dltk.mod.evaluation.types;

import org.eclipse.dltk.mod.ti.types.ClassType;
import org.eclipse.dltk.mod.ti.types.IEvaluatedType;

public class SimpleType extends ClassType implements IClassType {
	public final static int TYPE_STRING = 0;
	public final static int TYPE_NUMBER = 1;
	public final static int TYPE_ARRAY = 2;
	public final static int TYPE_LIST = 3;
	public final static int TYPE_DICT = 4;
	public final static int TYPE_BOOLEAN = 5;
	public final static int TYPE_NONE = 6;
	public final static int TYPE_TUPLE = 7;
	public final static int TYPE_NULL = 8;

	private int fType;

	public SimpleType(int type) {

		this.fType = type;
	}

	public String getTypeName() {

		return getTypeString(this.fType);
	}

	public int getType() {
		return this.fType;
	}

	/**
	 * Return type string for selected type.
	 *
	 * @param type
	 * @return
	 */
	public static String getTypeString(int type) {
		switch (type) {
		case TYPE_STRING:
			return "string"; //$NON-NLS-1$
		case TYPE_NUMBER:
			return "number"; //$NON-NLS-1$
		case TYPE_ARRAY:
			return "array"; //$NON-NLS-1$
		case TYPE_LIST:
			return "list"; //$NON-NLS-1$
		case TYPE_DICT:
			return "dict"; //$NON-NLS-1$
		case TYPE_BOOLEAN:
			return "boolean"; //$NON-NLS-1$
		case TYPE_TUPLE:
			return "tuple"; //$NON-NLS-1$
		case TYPE_NONE:
			return "void"; //$NON-NLS-1$
		case TYPE_NULL:
			return "NULL"; //$NON-NLS-1$
		}
		return "unknown"; //$NON-NLS-1$
	}

	public int hashCode() {
		return fType ^ 0xDEADBEEF;
	}

	public boolean equals(Object obj) {

		if (obj instanceof SimpleType) {
			SimpleType o2 = (SimpleType) obj;
			return this.fType == o2.fType;
		}
		return false;
	}

	public boolean subtypeOf(IEvaluatedType type) {
		// TODO Auto-generated method stub
		return false;
	}

	public String getModelKey() {
		// TODO Auto-generated method stub
		return null;
	}
}
