/*******************************************************************************
 * Copyright (c) 2000, 2007 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 
 *******************************************************************************/
package org.eclipse.dltk.mod.core.search.indexing;

import java.io.IOException;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.dltk.mod.compiler.util.SimpleLookupTable;
import org.eclipse.dltk.mod.core.DLTKCore;
import org.eclipse.dltk.mod.core.DLTKLanguageManager;
import org.eclipse.dltk.mod.core.IBuildpathEntry;
import org.eclipse.dltk.mod.core.IBuiltinModuleProvider;
import org.eclipse.dltk.mod.core.IDLTKLanguageToolkit;
import org.eclipse.dltk.mod.core.IScriptProject;
import org.eclipse.dltk.mod.core.ISourceElementParser;
import org.eclipse.dltk.mod.core.search.SearchEngine;
import org.eclipse.dltk.mod.core.search.SearchParticipant;
import org.eclipse.dltk.mod.core.search.index.Index;
import org.eclipse.dltk.mod.internal.core.BuiltinProjectFragment;
import org.eclipse.dltk.mod.internal.core.search.DLTKSearchDocument;
import org.eclipse.dltk.mod.internal.core.search.processing.JobManager;

class AddBuiltinFolderToIndex extends IndexRequest {
	IProject project;
	IScriptProject scriptProject;

	public AddBuiltinFolderToIndex(IPath folderPath, IProject project,
			IndexManager manager) {
		super(folderPath, manager);
		this.project = project;
		scriptProject = DLTKCore.create(this.project);
	}

	public int hashCode() {
		IDLTKLanguageToolkit languageToolkit = DLTKLanguageManager
				.getLanguageToolkit(scriptProject);
		return languageToolkit.getNatureId().hashCode();
	}

	public boolean equals(Object o) {
		if (o instanceof AddBuiltinFolderToIndex) {
			IDLTKLanguageToolkit languageToolkit = DLTKLanguageManager
					.getLanguageToolkit(scriptProject);
			IDLTKLanguageToolkit languageToolki2 = DLTKLanguageManager
					.getLanguageToolkit(((AddBuiltinFolderToIndex) o).scriptProject);
			if (languageToolkit.getNatureId().equals(
					languageToolki2.getNatureId())) {
				return true;
			}
		}
		return false;
	}

	// private static String EXISTS = "OK"; //$NON-NLS-1$
	// private static String DELETED = "DELETED"; //$NON-NLS-1$

	public boolean execute(IProgressMonitor progressMonitor) {
		if (this.isCancelled || progressMonitor != null
				&& progressMonitor.isCanceled())
			return true;
		if (!project.isAccessible())
			return true; // nothing to do

		/* ensure no concurrent write access to index */
		// IPath fullPath = project.getProject().getFullPath();
		String cfp = containerPath.toString();
		if (cfp.startsWith(IBuildpathEntry.BUILTIN_EXTERNAL_ENTRY_STR)) {
			cfp = cfp.substring(IBuildpathEntry.BUILTIN_EXTERNAL_ENTRY_STR
					.length());
		}
		String pathToString = containerPath.toString();

		Index index = this.manager.getSpecialIndex(
				IndexManager.SPECIAL_BUILTIN, cfp, pathToString);
		if (index == null) {
			if (JobManager.VERBOSE)
				org.eclipse.dltk.mod.internal.core.util.Util
						.verbose("-> index could not be created for " + this.containerPath); //$NON-NLS-1$
			return true;
		}
		ReadWriteMonitor monitor = index.monitor;
		if (monitor == null) {
			if (JobManager.VERBOSE)
				org.eclipse.dltk.mod.internal.core.util.Util
						.verbose("-> index for " + this.containerPath + " just got deleted"); //$NON-NLS-1$//$NON-NLS-2$
			return true; // index got deleted since acquired
		}
		try {
			monitor.enterRead(); // ask permission to read
			final IPath container = this.containerPath;
			final IndexManager indexManager = this.manager;
			final ISourceElementParser parser = indexManager
					.getSourceElementParser(scriptProject);
			final SourceIndexerRequestor requestor = indexManager
					.getSourceRequestor(scriptProject);
			if (JobManager.VERBOSE)
				org.eclipse.dltk.mod.internal.core.util.Util
						.verbose("-> indexing " + containerPath.toString()); //$NON-NLS-1$
			long initialTime = System.currentTimeMillis();

			SearchParticipant participant = SearchEngine
					.getDefaultSearchParticipant();

			visit(null, scriptProject, parser, requestor, indexManager,
					container, true, participant, index);
			this.manager.saveIndex(index);
			if (JobManager.VERBOSE)
				org.eclipse.dltk.mod.internal.core.util.Util
						.verbose("-> done indexing of " //$NON-NLS-1$
								+ containerPath.toString()
								+ " (" //$NON-NLS-1$
								+ (System.currentTimeMillis() - initialTime)
								+ "ms)"); //$NON-NLS-1$			
		} catch (IOException ex) {
			if (JobManager.VERBOSE) {
				org.eclipse.dltk.mod.internal.core.util.Util
						.verbose("-> failed to index " + this.containerPath + " because of the following exception:"); //$NON-NLS-1$ //$NON-NLS-2$
				ex.printStackTrace();
			}
			// manager.removeIndex(this.containerPath);
			return false;
		} finally {
			monitor.exitRead(); // free read lock
		}
		return true;
	}

	private void visit(SimpleLookupTable table, IScriptProject project,
			ISourceElementParser parser, SourceIndexerRequestor requestor,
			IndexManager indexManager, IPath container, boolean operation,
			SearchParticipant participant, Index index) {

		IDLTKLanguageToolkit toolkit = null;
		toolkit = DLTKLanguageManager.getLanguageToolkit(project);
		IBuiltinModuleProvider provider = BuiltinProjectFragment
				.getBuiltinProvider(project);
		if (provider == null) {
			return;
		}
		String[] files = provider.getBuiltinModules();
		if (files != null) {
			for (int i = 0; i < files.length; ++i) {
				if (this.isCancelled) {
					if (JobManager.VERBOSE)
						org.eclipse.dltk.mod.internal.core.util.Util
								.verbose("-> indexing of " + containerPath.toString() + " has been cancelled"); //$NON-NLS-1$ //$NON-NLS-2$
					return;
				}

				indexDocument(parser, requestor, participant, index, files[i],
						toolkit, provider.getBuiltinModuleContent(files[i]));
			}
		}
	}

	private void indexDocument(ISourceElementParser parser,
			SourceIndexerRequestor requestor, SearchParticipant participant,
			Index index, String path, IDLTKLanguageToolkit toolkit,
			String contents) {
		IPath dpath = new Path(path).setDevice(null);
		DLTKSearchDocument entryDocument = new DLTKSearchDocument(dpath
				.toString(), Path.EMPTY, contents.toCharArray(), participant,
				true, this.project);
		entryDocument.parser = parser;
		entryDocument.requestor = requestor;
		entryDocument.toolkit = toolkit;
		entryDocument.fullPath = this.containerPath.append(dpath);
		this.manager.indexDocument(entryDocument, participant, index,
				this.containerPath);
	}

	public String toString() {
		return "adding " + this.containerPath + " to index " + this.containerPath; //$NON-NLS-1$ //$NON-NLS-2$
	}
}
