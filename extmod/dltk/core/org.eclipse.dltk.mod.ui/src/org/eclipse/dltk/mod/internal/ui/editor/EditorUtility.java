/*******************************************************************************
 * Copyright (c) 2000-2011 IBM Corporation and others, eBay Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     eBay Inc - modification
 *******************************************************************************/
package org.eclipse.dltk.mod.internal.ui.editor;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IStorage;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.dltk.mod.core.DLTKCore;
import org.eclipse.dltk.mod.core.IExternalSourceModule;
import org.eclipse.dltk.mod.core.IForeignElement;
import org.eclipse.dltk.mod.core.IMember;
import org.eclipse.dltk.mod.core.IModelElement;
import org.eclipse.dltk.mod.core.IScriptProject;
import org.eclipse.dltk.mod.core.ISourceModule;
import org.eclipse.dltk.mod.core.ISourceRange;
import org.eclipse.dltk.mod.core.ISourceReference;
import org.eclipse.dltk.mod.core.ModelException;
import org.eclipse.dltk.mod.core.ScriptModelUtil;
import org.eclipse.dltk.mod.internal.core.JSSourceType;
import org.eclipse.dltk.mod.internal.corext.util.Messages;
import org.eclipse.dltk.mod.ui.DLTKUILanguageManager;
import org.eclipse.dltk.mod.ui.DLTKUIPlugin;
import org.eclipse.dltk.mod.ui.IDLTKUILanguageToolkit;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.TextSelection;
import org.eclipse.jface.viewers.ISelectionProvider;
import org.eclipse.swt.SWT;
import org.eclipse.ui.IEditorDescriptor;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IEditorSite;
import org.eclipse.ui.IFileEditorInput;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.actions.WorkspaceModifyOperation;
import org.eclipse.ui.ide.IDE;
import org.eclipse.ui.ide.IGotoMarker;
import org.eclipse.ui.part.FileEditorInput;
import org.eclipse.ui.texteditor.IDocumentProvider;
import org.eclipse.ui.texteditor.ITextEditor;
import org.eclipse.ui.texteditor.ITextEditorActionDefinitionIds;
import org.eclipse.ui.texteditor.TextEditorAction;

public class EditorUtility {
	private static String nativeTypeName = "";

	/**
	 * Returns the DLTK project for a given editor input or <code>null</code> if
	 * no corresponding DLTK project exists.
	 * 
	 * @param input
	 *            the editor input
	 * @return the corresponding DLTK project
	 */
	public static IScriptProject getScriptProject(IEditorInput input) {
		IScriptProject dProject = null;
		if (input instanceof IFileEditorInput) {
			IProject project = ((IFileEditorInput) input).getFile()
					.getProject();
			if (project != null) {
				dProject = DLTKCore.create(project);
				if (!dProject.exists())
					dProject = null;
			}
		} else if (input instanceof ExternalStorageEditorInput) {
			IModelElement element = (IModelElement) input
					.getAdapter(IModelElement.class);
			if (element != null) {
				IScriptProject project = element.getScriptProject();
				if (project != null && project.exists()) {
					return project;
				}
			}
		}
		return dProject;
	}

	/**
	 * Returns the given editor's input as model element.
	 * 
	 * @param editor
	 *            the editor
	 * @param primaryOnly
	 *            if <code>true</code> only primary working copies will be
	 *            returned
	 * @return the given editor's input as model element or <code>null</code> if
	 *         none
	 */
	public static ISourceModule getEditorInputModelElement(IEditorPart editor,
			boolean primaryOnly) {
		IEditorInput editorInput = editor.getEditorInput();
		if (editorInput == null)
			return null;
		ISourceModule je = DLTKUIPlugin.getEditorInputModelElement(editorInput);
		if (je != null || primaryOnly)
			return je;
		return DLTKUIPlugin.getDefault().getWorkingCopyManager()
				.getWorkingCopy(editorInput, primaryOnly);
	}

	// EBAY - START MOD
	/**
	 * Generate a temporary native type source that content comes from
	 * openContentStream() method.
	 * 
	 * @param containerName
	 * @param fileName
	 * @return
	 * @throws CoreException
	 */
	private static IFileEditorInput generateTempNativeType(
			String containerName, final String fileName) throws CoreException {

		// file.create(stream, true, monitor);
		IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();
		IResource resource = root.findMember(new Path("/"));
		IProject[] projects = root.getProjects();
		if (!resource.exists() || !(resource instanceof IContainer)
				|| projects.length == 0) {
			return null;
		}

		IFolder folder = ((IProject) projects[0]).getFolder(".nativeType");
		if (!folder.exists()) {
			try {
				folder.create(true, true, new NullProgressMonitor());
			} catch (CoreException e) {
				e.printStackTrace();
			}
		}

		IFile file = folder.getFile(new Path(fileName));
		try {
			InputStream stream = openContentStream();
			if (file.exists()) {
				file.setContents(stream, true, true, new NullProgressMonitor());
			} else {
				file.create(stream, true, new NullProgressMonitor());
			}
			stream.close();
		} catch (IOException e) {
		}

		return new FileEditorInput(file);

		// try {
		// IDE.openEditor(page, file, true);
		// } catch (PartInitException e) {
		// }

	}

	/**
	 * We will initialize file contents with a text.
	 */
	private static InputStream openContentStream() {
		String contents = "/* my test only the name gets by varaible,\n other infromation is hard code. */\n";
		contents += "vjo.ctype('" + nativeTypeName + "') //< public\n";
		contents += ".props({\n";
		contents += "    //> public void main(String[] args)\n";
		contents += "    //> " + nativeTypeName + " main()\n";
		contents += "    main:function(args){\n";
		contents += "        \n";
		contents += "    }\n";
		contents += "})\n";
		contents += ".endType();\n";
		return new ByteArrayInputStream(contents.getBytes());
	}

	// EBAY - END MOD

	/**
	 * Opens a Script editor for an element such as <code>IModelElement</code>,
	 * <code>IFile</code>, or <code>IStorage</code>. The editor is activated by
	 * default.
	 * 
	 * @return the IEditorPart or null if wrong element type or opening failed
	 */
	public static IEditorPart openInEditor(Object inputElement)
			throws ModelException, PartInitException {
		return openInEditor(inputElement, true);
	}

	/**
	 * Opens a Script editor for an element (IModelElement, IFile, IStorage...)
	 * 
	 * @return the IEditorPart or null if wrong element type or opening failed
	 */
	public static IEditorPart openInEditor(Object inputElement, boolean activate)
			throws ModelException, PartInitException {
		if (inputElement instanceof IFile) {
			return openInEditor((IFile) inputElement, activate);
		}

		if (inputElement instanceof IModelElement) {
			IModelElement modelElement = (IModelElement) inputElement;
			ISourceModule cu = (ISourceModule) (modelElement)
					.getAncestor(IModelElement.SOURCE_MODULE);
			if (cu != null && !ScriptModelUtil.isPrimary(cu)) {
				/*
				 * Support for non-primary working copy. Try to reveal it in the
				 * active editor.
				 */
				IWorkbenchPage page = DLTKUIPlugin.getActivePage();
				if (page != null) {
					IEditorPart editor = page.getActiveEditor();
					if (editor != null) {
						IModelElement editorCU = EditorUtility
								.getEditorInputModelElement(editor, false);
						if (editorCU == cu) {
							EditorUtility.revealInEditor(editor, modelElement);
							return editor;
						}
					}
				}
			}
		}
		if (inputElement instanceof IForeignElement) {
			IForeignElement el = (IForeignElement) inputElement;
			el.codeSelect();
		} else {
			if (inputElement instanceof JSSourceType) {
				inputElement = ((JSSourceType) inputElement).getParent();
			}
			IEditorInput input = getEditorInput(inputElement);
			if (input != null) {
				if (inputElement instanceof IModelElement) {

					String editorId = null;

					if (editorId == null) { // Transitional code
						if (input != null) {
							IDLTKUILanguageToolkit toolkit = DLTKUILanguageManager
									.getLanguageToolkit((IModelElement) inputElement);
							if (toolkit != null) {
								editorId = toolkit.getEditorId(inputElement);
							}
						}
					}

					if (editorId != null) {
						IModelElement modelElement = (IModelElement) inputElement;

						String elemetnName = modelElement.getElementName();
						boolean openInROEditor = false;
						// if (isNativeType(elemetnName)) {
						// openInROEditor = true;
						// nativeTypeName = elemetnName;
						// }
						return openInEditor(input, editorId, activate,
								openInROEditor);
					}

				} else
					return openInEditor(input,
							getEditorID(input, inputElement), activate, false);
			}
		}
		return null;
	}

	public static String getEditorID(IEditorInput input, Object inputObject) {
		IEditorDescriptor editorDescriptor;
		try {
			if (input instanceof IFileEditorInput) {
				editorDescriptor = IDE
						.getEditorDescriptor(((IFileEditorInput) input)
								.getFile());
			} else if (input instanceof ExternalStorageEditorInput) {
				editorDescriptor = IDE.getEditorDescriptor(input.getName());
			} else {
				editorDescriptor = IDE.getEditorDescriptor(input.getName());
			}

		} catch (PartInitException e) {
			return null;
		}
		if (editorDescriptor != null)
			return editorDescriptor.getId();
		return null;
	}

	private static IEditorInput getEditorInput(IModelElement element)
			throws ModelException {
		while (element != null) {
			if (element instanceof IExternalSourceModule) {
				ISourceModule unit = ((ISourceModule) element).getPrimary();
				if (unit instanceof IStorage) {
					return new ExternalStorageEditorInput((IStorage) unit);
				}

			} else if (element instanceof ISourceModule) {
				ISourceModule unit = ((ISourceModule) element).getPrimary();
				IResource resource = unit.getResource();
				if (resource == null || !resource.exists()) {
					return new ExternalFileEditorInput(element);
				} else if (resource.exists() && resource instanceof IFile) {
					return new FileEditorInput((IFile) resource);
				}
			}
			element = element.getParent();
		}
		return null;
	}

	public static IEditorInput getEditorInput(Object input)
			throws ModelException {
		if (input instanceof IFile)
			return new FileEditorInput((IFile) input);
		if (DLTKCore.DEBUG) {
			System.err
					.println("Add archive entry and external source folder editor input.."); //$NON-NLS-1$
		}
		if (input instanceof IStorage) {
			return new ExternalStorageEditorInput((IStorage) input);
		}
		IEditorInput einput = null;
		if (input instanceof IModelElement) {
			einput = getEditorInput((IModelElement) input);
			return einput;
		}

		// try to find the editor input from jsNative local cache
		return null;
	}

	/**
	 * Selects a Script Element in an editor
	 */
	public static void revealInEditor(IEditorPart part, IModelElement element) {
		if (element == null)
			return;
		if (part instanceof ScriptEditor) {
			((ScriptEditor) part).setSelection(element);
			if (DLTKCore.DEBUG) {
				System.err.println("Add revealInEditor set selection"); //$NON-NLS-1$
			}
			return;
		}
		// Support for non-Script editor
		try {
			ISourceRange range = null;
			if (element instanceof IExternalSourceModule) {

			} else if (element instanceof ISourceModule) {
				range = null;
			}
			// else if (element instanceof IClassFile)
			// range= null;
			// else if (element instanceof ILocalVariable)
			// range= ((ILocalVariable)element).getNameRange();
			else if (element instanceof IMember)
				range = ((IMember) element).getNameRange();
			// else if (element instanceof ITypeParameter)
			// range= ((ITypeParameter)element).getNameRange();
			else if (element instanceof ISourceReference)
				range = ((ISourceReference) element).getSourceRange();
			if (range != null)
				revealInEditor(part, range.getOffset(), range.getLength());
		} catch (ModelException e) {
			// don't reveal
		}
	}

	/**
	 * Selects and reveals the given line in the given editor part.
	 * 
	 * @param editorPart
	 * @param lineNumber
	 * @throws CoreException
	 */
	public static void revealInEditor(IEditorPart editorPart, int lineNumber)
			throws CoreException {
		if (editorPart instanceof ITextEditor && lineNumber >= 0) {
			final ITextEditor textEditor = (ITextEditor) editorPart;
			final IDocumentProvider provider = textEditor.getDocumentProvider();
			final IEditorInput input = editorPart.getEditorInput();
			provider.connect(input);
			final IDocument document = provider.getDocument(input);
			try {
				final IRegion line = document.getLineInformation(lineNumber);
				textEditor.selectAndReveal(line.getOffset(), line.getLength());
			} catch (BadLocationException e) {

			}
			provider.disconnect(input);
		}
	}

	/**
	 * Selects and reveals the given region in the given editor part.
	 */
	public static void revealInEditor(IEditorPart part, IRegion region) {
		if (part != null && region != null)
			revealInEditor(part, region.getOffset(), region.getLength());
	}

	/**
	 * Selects and reveals the given offset and length in the given editor part.
	 */
	public static void revealInEditor(IEditorPart editor, final int offset,
			final int length) {
		if (editor instanceof ITextEditor) {
			((ITextEditor) editor).selectAndReveal(offset, length);
			return;
		}
		// Support for non-text editor - try IGotoMarker interface
		if (editor instanceof IGotoMarker) {
			final IEditorInput input = editor.getEditorInput();
			if (input instanceof IFileEditorInput) {
				final IGotoMarker gotoMarkerTarget = (IGotoMarker) editor;
				WorkspaceModifyOperation op = new WorkspaceModifyOperation() {
					protected void execute(IProgressMonitor monitor)
							throws CoreException {
						IMarker marker = null;
						try {
							marker = ((IFileEditorInput) input).getFile()
									.createMarker(IMarker.TEXT);
							marker.setAttribute(IMarker.CHAR_START, offset);
							marker.setAttribute(IMarker.CHAR_END, offset
									+ length);
							gotoMarkerTarget.gotoMarker(marker);
						} finally {
							if (marker != null)
								marker.delete();
						}
					}
				};
				try {
					op.run(null);
				} catch (InvocationTargetException ex) {
					// reveal failed
				} catch (InterruptedException e) {
					// Assert.isTrue(false, "this operation can not be
					// canceled"); //$NON-NLS-1$
				}
			} else if (input instanceof ExternalStorageEditorInput) {
				System.err
						.println("TODO: Add external storage editor input reveal..."); //$NON-NLS-1$
			}
			return;
		}
		/*
		 * Workaround: send out a text selection XXX: Needs to be improved, see
		 * https://bugs.eclipse.org/bugs/show_bug.cgi?id=32214
		 */
		if (editor != null
				&& editor.getEditorSite().getSelectionProvider() != null) {
			IEditorSite site = editor.getEditorSite();
			if (site == null)
				return;
			ISelectionProvider provider = editor.getEditorSite()
					.getSelectionProvider();
			if (provider == null)
				return;
			provider.setSelection(new TextSelection(offset, length));
		}
	}

	private static IEditorPart openInEditor(IFile file, boolean activate)
			throws PartInitException {
		if (file != null) {
			IWorkbenchPage p = DLTKUIPlugin.getActivePage();
			if (p != null) {
				IEditorPart editorPart = IDE.openEditor(p, file, activate);
				initializeHighlightRange(editorPart);
				return editorPart;
			}
		}
		return null;
	}

	private static IEditorPart openInEditor(IEditorInput input,
			String editorID, boolean activate, boolean openInRO)
			throws PartInitException {
		if (input != null) {
			IWorkbenchPage p = DLTKUIPlugin.getActivePage();
			IEditorPart editorPart;
			if (p != null) {
				// EBAY - START MOD
				if (!openInRO) {
					editorPart = p.openEditor(input, editorID, activate);
				} else {
					// IJstType nativeJstType = null;
					// if (type.getSourceModule() instanceof
					// NativeVjoSourceModule) {
					//
					// List<IJstType> jstTypes =
					// TypeSpaceMgr.getInstance().findType(
					// type.getElementName());
					// if(jstTypes != null && jstTypes.size() > 0){
					// nativeJstType = jstTypes.get(0);
					// }
					try {
						if (nativeTypeName.trim().length() > 0) {
							input = generateTempNativeType(".nativeType", "\\"
									+ nativeTypeName + ".js");
						}
					} catch (CoreException e) {
						e.printStackTrace();
					}

					editorPart = p.openEditor(input,
							"org.ebayopensource.vjet.eclipse.ui.VjoROEditor",
							activate);

				}
				// EBAY - END MOD
				initializeHighlightRange(editorPart);
				return editorPart;
			}
		}
		return null;
	}

	private static void initializeHighlightRange(IEditorPart editorPart) {
		if (editorPart instanceof ITextEditor) {
			IAction toggleAction = editorPart
					.getEditorSite()
					.getActionBars()
					.getGlobalActionHandler(
							ITextEditorActionDefinitionIds.TOGGLE_SHOW_SELECTED_ELEMENT_ONLY);
			boolean enable = toggleAction != null;
			// if (enable && editorPart instanceof Editor)
			// enable=
			// DLTKUIPlugin.getDefault().getPreferenceStore().getBoolean(PreferenceConstants.EDITOR_SHOW_SEGMENTS);
			// else
			if (DLTKCore.DEBUG) {
				System.err
						.println("Add initializeHighlightRange support of preferences."); //$NON-NLS-1$
			}
			enable = enable && toggleAction.isEnabled()
					&& toggleAction.isChecked();
			if (enable) {
				if (toggleAction instanceof TextEditorAction) {
					// Reset the action
					((TextEditorAction) toggleAction).setEditor(null);
					// Restore the action
					((TextEditorAction) toggleAction)
							.setEditor((ITextEditor) editorPart);
				} else {
					// Un-check
					toggleAction.run();
					// Check
					toggleAction.run();
				}
			}
		}
	}

	private static boolean isNativeType(String typeName) {
		return nativeGlobalObjects.contains(typeName);
	}

	static List nativeGlobalObjects = new ArrayList();
	static {
		nativeGlobalObjects.add("Array");
		nativeGlobalObjects.add("Boolean");
		nativeGlobalObjects.add("Date");
		nativeGlobalObjects.add("Error");
		nativeGlobalObjects.add("EvalError");
		nativeGlobalObjects.add("Function");
		nativeGlobalObjects.add("Math");
		nativeGlobalObjects.add("Number");
		nativeGlobalObjects.add("Object");
		nativeGlobalObjects.add("RangeError");
		nativeGlobalObjects.add("ReferenceError");
		nativeGlobalObjects.add("RegExp");
		nativeGlobalObjects.add("String");
		nativeGlobalObjects.add("SyntaxError");
		nativeGlobalObjects.add("TypeError");
		nativeGlobalObjects.add("URIError");
		nativeGlobalObjects.add("Window");
		nativeGlobalObjects.add("Global");
		nativeGlobalObjects.add("Object");
	}

	/**
	 * Tests if a CU is currently shown in an editor
	 * 
	 * @return the IEditorPart if shown, null if element is not open in an
	 *         editor
	 */
	public static IEditorPart isOpenInEditor(Object inputElement) {
		IEditorInput input = null;

		try {
			input = getEditorInput(inputElement);
		} catch (ModelException x) {
			DLTKUIPlugin.log(x.getStatus());
		}

		if (input != null) {
			IWorkbenchPage p = DLTKUIPlugin.getActivePage();
			if (p != null) {
				return p.findEditor(input);
			}
		}

		return null;
	}

	public static IEditorPart[] getDirtyEditors() {
		Set inputs = new HashSet();
		List result = new ArrayList(0);
		IWorkbench workbench = PlatformUI.getWorkbench();
		IWorkbenchWindow[] windows = workbench.getWorkbenchWindows();
		for (int i = 0; i < windows.length; i++) {
			IWorkbenchPage[] pages = windows[i].getPages();
			for (int x = 0; x < pages.length; x++) {
				IEditorPart[] editors = pages[x].getDirtyEditors();
				for (int z = 0; z < editors.length; z++) {
					IEditorPart ep = editors[z];
					IEditorInput input = ep.getEditorInput();
					if (!inputs.contains(input)) {
						inputs.add(input);
						result.add(ep);
					}
				}
			}
		}
		return (IEditorPart[]) result.toArray(new IEditorPart[result.size()]);
	}

	/**
	 * If the current active editor edits ascriptelement return it, else return
	 * null
	 */
	public static IModelElement getActiveEditorModelInput() {
		IWorkbenchPage page = DLTKUIPlugin.getActivePage();
		if (page != null) {
			IEditorPart part = page.getActiveEditor();
			if (part != null) {
				IEditorInput editorInput = part.getEditorInput();
				if (editorInput != null) {
					return DLTKUIPlugin.getEditorInputModelElement(editorInput);
				}
			}
		}
		return null;
	}

	/**
	 * Appends to modifier string of the given SWT modifier bit to the given
	 * modifierString.
	 * 
	 * @param modifierString
	 *            the modifier string
	 * @param modifier
	 *            an int with SWT modifier bit
	 * @return the concatenated modifier string
	 * 
	 */
	private static String appendModifierString(String modifierString,
			int modifier) {
		if (modifierString == null)
			modifierString = ""; //$NON-NLS-1$
		String newModifierString = Action.findModifierString(modifier);
		if (modifierString.length() == 0)
			return newModifierString;
		return Messages.format(
				DLTKEditorMessages.EditorUtility_concatModifierStrings,
				new String[] { modifierString, newModifierString });
	}

	/**
	 * Returns the modifier string for the given SWT modifier modifier bits.
	 * 
	 * @param stateMask
	 *            the SWT modifier bits
	 * @return the modifier string
	 * 
	 */
	public static String getModifierString(int stateMask) {
		String modifierString = ""; //$NON-NLS-1$
		if ((stateMask & SWT.CTRL) == SWT.CTRL)
			modifierString = appendModifierString(modifierString, SWT.CTRL);
		if ((stateMask & SWT.ALT) == SWT.ALT)
			modifierString = appendModifierString(modifierString, SWT.ALT);
		if ((stateMask & SWT.SHIFT) == SWT.SHIFT)
			modifierString = appendModifierString(modifierString, SWT.SHIFT);
		if ((stateMask & SWT.COMMAND) == SWT.COMMAND)
			modifierString = appendModifierString(modifierString, SWT.COMMAND);

		return modifierString;
	}

	/**
	 * Maps the localized modifier name to a code in the same manner as
	 * #findModifier.
	 * 
	 * @param modifierName
	 *            the modifier name
	 * @return the SWT modifier bit, or <code>0</code> if no match was found
	 * 
	 */
	public static int findLocalizedModifier(String modifierName) {
		if (modifierName == null)
			return 0;

		if (modifierName.equalsIgnoreCase(Action.findModifierString(SWT.CTRL)))
			return SWT.CTRL;
		if (modifierName.equalsIgnoreCase(Action.findModifierString(SWT.SHIFT)))
			return SWT.SHIFT;
		if (modifierName.equalsIgnoreCase(Action.findModifierString(SWT.ALT)))
			return SWT.ALT;
		if (modifierName.equalsIgnoreCase(Action
				.findModifierString(SWT.COMMAND)))
			return SWT.COMMAND;

		return 0;
	}
}
