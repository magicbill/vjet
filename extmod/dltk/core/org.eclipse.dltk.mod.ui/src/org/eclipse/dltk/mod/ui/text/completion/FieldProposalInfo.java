/*******************************************************************************
 * Copyright (c) 2005, 2007 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 
 *******************************************************************************/
package org.eclipse.dltk.mod.ui.text.completion;

import org.eclipse.dltk.mod.core.CompletionProposal;
import org.eclipse.dltk.mod.core.IScriptProject;

/**
 * Proposal info that computes the javadoc lazily when it is queried.
 * 
 */
public final class FieldProposalInfo extends MemberProposalInfo {

	/**
	 * Creates a new proposal info.
	 * 
	 * @param project
	 *            thescriptproject to reference when resolving types
	 * @param proposal
	 *            the proposal to generate information for
	 */
	public FieldProposalInfo(IScriptProject project, CompletionProposal proposal) {
		super(project, proposal);
	}
}
