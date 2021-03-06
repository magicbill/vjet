/*******************************************************************************
 * Copyright (c) 2000, 2006 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.dltk.mod.javascript.internal.debug.ui.interpreters;


import org.eclipse.dltk.mod.internal.debug.ui.interpreters.AbstractInterpreterLibraryBlock;
import org.eclipse.dltk.mod.internal.debug.ui.interpreters.AddScriptInterpreterDialog;
import org.eclipse.dltk.mod.internal.debug.ui.interpreters.IAddInterpreterDialogRequestor;
import org.eclipse.dltk.mod.launching.IInterpreterInstall;
import org.eclipse.dltk.mod.launching.IInterpreterInstallType;
import org.eclipse.swt.widgets.Shell;


public class AddJavaScriptInterpreterDialog extends AddScriptInterpreterDialog {
	public AddJavaScriptInterpreterDialog(IAddInterpreterDialogRequestor requestor, Shell shell, IInterpreterInstallType[] interpreterInstallTypes, IInterpreterInstall editedInterpreter) {
		super(requestor, shell, interpreterInstallTypes, editedInterpreter);
	}

	protected AbstractInterpreterLibraryBlock createLibraryBlock(AddScriptInterpreterDialog dialog) {
		return new JavaScriptInterpreterLibraryBlock(dialog);
	}

	protected boolean useInterpreterArgs() {
		return false;
	}
	
}
