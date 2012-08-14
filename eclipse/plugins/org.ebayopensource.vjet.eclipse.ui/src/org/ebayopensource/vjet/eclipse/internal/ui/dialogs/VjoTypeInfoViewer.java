/*******************************************************************************
 * Copyright (c) 2005-2011 eBay Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 *******************************************************************************/
package org.ebayopensource.vjet.eclipse.internal.ui.dialogs;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.ebayopensource.dsf.jst.IJstType;
import org.ebayopensource.dsf.ts.type.TypeName;
import org.ebayopensource.vjet.eclipse.codeassist.CodeassistUtils;
import org.ebayopensource.vjet.eclipse.core.ClassFileConstants;
import org.ebayopensource.vjet.eclipse.core.VjoNature;
import org.ebayopensource.vjet.eclipse.core.search.TypeSearcher;
import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.ProgressMonitorWrapper;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.dltk.mod.ast.Modifiers;
import org.eclipse.dltk.mod.core.IDLTKLanguageToolkit;
import org.eclipse.dltk.mod.core.IProjectFragment;
import org.eclipse.dltk.mod.core.IScriptProject;
import org.eclipse.dltk.mod.core.IType;
import org.eclipse.dltk.mod.core.ModelException;
import org.eclipse.dltk.mod.core.search.IDLTKSearchConstants;
import org.eclipse.dltk.mod.core.search.IDLTKSearchScope;
import org.eclipse.dltk.mod.core.search.SearchEngine;
import org.eclipse.dltk.mod.core.search.SearchPattern;
import org.eclipse.dltk.mod.core.search.TypeNameMatch;
import org.eclipse.dltk.mod.core.search.TypeNameMatchRequestor;
import org.eclipse.dltk.mod.core.search.TypeNameRequestor;
import org.eclipse.dltk.mod.internal.core.Model;
import org.eclipse.dltk.mod.internal.core.ModelManager;
import org.eclipse.dltk.mod.internal.core.ScriptProject;
import org.eclipse.dltk.mod.internal.core.VjoSourceModule;
import org.eclipse.dltk.mod.internal.core.search.DLTKSearchScope;
import org.eclipse.dltk.mod.internal.core.search.DLTKSearchTypeNameMatch;
import org.eclipse.dltk.mod.internal.corext.util.Messages;
import org.eclipse.dltk.mod.internal.corext.util.OpenTypeHistory;
import org.eclipse.dltk.mod.internal.corext.util.Strings;
import org.eclipse.dltk.mod.internal.corext.util.TypeFilter;
import org.eclipse.dltk.mod.internal.corext.util.TypeInfoFilter;
import org.eclipse.dltk.mod.internal.corext.util.TypeInfoRequestorAdapter;
import org.eclipse.dltk.mod.internal.ui.DLTKUIMessages;
import org.eclipse.dltk.mod.internal.ui.dialogs.TypeInfoViewer;
import org.eclipse.dltk.mod.launching.IInterpreterInstall;
import org.eclipse.dltk.mod.launching.IInterpreterInstallType;
import org.eclipse.dltk.mod.launching.LibraryLocation;
import org.eclipse.dltk.mod.launching.ScriptRuntime;
import org.eclipse.dltk.mod.ui.DLTKPluginImages;
import org.eclipse.dltk.mod.ui.DLTKUILanguageManager;
import org.eclipse.dltk.mod.ui.DLTKUIPlugin;
import org.eclipse.dltk.mod.ui.IDLTKUILanguageToolkit;
import org.eclipse.dltk.mod.ui.ScriptElementImageProvider;
import org.eclipse.dltk.mod.ui.ScriptElementLabels;
import org.eclipse.dltk.mod.ui.dialogs.ITypeInfoFilterExtension;
import org.eclipse.dltk.mod.ui.dialogs.ITypeInfoImageProvider;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ControlAdapter;
import org.eclipse.swt.events.ControlEvent;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.events.KeyAdapter;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.MenuAdapter;
import org.eclipse.swt.events.MenuEvent;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.ui.progress.UIJob;

/**
 * A viewer to present type queried form the type history and form the search
 * engine. All viewer updating takes place in the UI thread. Therefore no
 * synchronization of the methods is necessary. Copy more part of functionality
 * from {@link TypeInfoViewer} and added type space support.
 * 
 */
public class VjoTypeInfoViewer {

	private IDLTKUILanguageToolkit fToolkit;

	private static TypeSearcher s_typeSearcher = TypeSearcher.getInstance();

	private static class SearchRequestor extends TypeNameMatchRequestor {
		private volatile boolean fStop;

		private Set fHistory;

		private TypeInfoFilter fFilter;
		private List fResult;
		private TypeFilter fTypeFilter;

		public SearchRequestor(TypeInfoFilter filter, TypeFilter typeFilter) {
			super();
			fResult = new ArrayList(2048);
			fFilter = filter;
			fTypeFilter = typeFilter;
		}

		public TypeNameMatch[] getResult() {
			return (TypeNameMatch[]) fResult.toArray(new TypeNameMatch[fResult
					.size()]);
		}

		public void cancel() {
			fStop = true;
		}

		public void setHistory(Set history) {
			fHistory = history;
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see
		 * org.eclipse.jdt.core.search.TypeNameMatchRequestor#acceptTypeNameMatch
		 * (org.eclipse.jdt.core.search.TypeNameMatch)
		 */
		public void acceptTypeNameMatch(TypeNameMatch match) {
			if (fStop)
				return;
			if (fTypeFilter.isFiltered(match))
				return;
			if (fHistory.contains(match))
				return;
			if (fFilter.matchesFilterExtension(match))
				fResult.add(match);
		}
	}

	protected static class TypeInfoComparator implements Comparator {
		private TypeInfoLabelProvider fLabelProvider;
		private TypeInfoFilter fFilter;

		public TypeInfoComparator(TypeInfoLabelProvider labelProvider,
				TypeInfoFilter filter) {
			fLabelProvider = labelProvider;
			fFilter = filter;
		}

		public int compare(Object left, Object right) {
			TypeNameMatch leftInfo = (TypeNameMatch) left;
			TypeNameMatch rightInfo = (TypeNameMatch) right;
			//Compare camel case
			int leftCategory = getCamelCaseCategory(leftInfo);
			int rightCategory = getCamelCaseCategory(rightInfo);
			if (leftCategory < rightCategory)
				return -1;
			if (leftCategory > rightCategory)
				return +1;
			//Compare, make simple typename match stand before package name match
			boolean leftBol = fFilter.isSimpleNameMatch(leftInfo);
			boolean rightBol = fFilter.isSimpleNameMatch(rightInfo);
			 if (!leftBol && rightBol) {
				return +1;
			} else if (leftBol && !rightBol) {
				return -1;
			}
			//Compare simple type name
			int result = compareName(leftInfo.getSimpleTypeName(), rightInfo
					.getSimpleTypeName());
			if (result != 0)
				return result;
			//Compare package name
			result = compareTypeContainerName(leftInfo.getTypeContainerName(),
					rightInfo.getTypeContainerName());
			if (result != 0)
				return result;
			//Compare project name
			leftCategory = getElementTypeCategory(leftInfo);
			rightCategory = getElementTypeCategory(rightInfo);
			if (leftCategory < rightCategory)
				return -1;
			if (leftCategory > rightCategory)
				return +1;
			return compareContainerName(leftInfo, rightInfo);
		}

		private int compareName(String leftString, String rightString) {
			int result = leftString.compareToIgnoreCase(rightString);
			if (result != 0 || rightString.length() == 0) {
				return result;
			} else if (Strings.isLowerCase(leftString.charAt(0))
					&& !Strings.isLowerCase(rightString.charAt(0))) {
				return +1;
			} else if (Strings.isLowerCase(rightString.charAt(0))
					&& !Strings.isLowerCase(leftString.charAt(0))) {
				return -1;
			} else {
				return leftString.compareTo(rightString);
			}
		}

		private int compareTypeContainerName(String leftString,
				String rightString) {
			int leftLength = leftString.length();
			int rightLength = rightString.length();
			if (leftLength == 0 && rightLength > 0)
				return -1;
			if (leftLength == 0 && rightLength == 0)
				return 0;
			if (leftLength > 0 && rightLength == 0)
				return +1;
			return compareName(leftString, rightString);
		}

		private int compareContainerName(TypeNameMatch leftType,
				TypeNameMatch rightType) {
			return fLabelProvider.getContainerName(leftType).compareTo(
					fLabelProvider.getContainerName(rightType));
		}

		private int getCamelCaseCategory(TypeNameMatch type) {
			if (fFilter == null)
				return 0;
			if (!fFilter.isCamelCasePattern())
				return 0;
			return fFilter.matchesRawNamePattern(type) ? 0 : 1;
		}

		private int getElementTypeCategory(TypeNameMatch type) {
			try {
				if (type.getProjectFragment() != null
						&& type.getProjectFragment().getKind() == IProjectFragment.K_SOURCE)
					return 0;
			} catch (ModelException e) {
				// TODO Auto-generated catch block
				DLTKUIPlugin.log(e);
			}
			return 1;
		}
	}

	protected class TypeInfoLabelProvider {

		private ITypeInfoImageProvider fProviderExtension;
		private TypeInfoRequestorAdapter fAdapter = new TypeInfoRequestorAdapter();

		private Map fLib2Name = new HashMap();
		private String[] fInstallLocations;
		private String[] fVMNames;

		// private boolean fFullyQualifyDuplicates;

		public TypeInfoLabelProvider(ITypeInfoImageProvider extension) {
			fProviderExtension = extension;
			List locations = new ArrayList();
			List labels = new ArrayList();
			IInterpreterInstallType[] installs = ScriptRuntime
					.getInterpreterInstallTypes(fToolkit.getCoreToolkit()
							.getNatureId());
			for (int i = 0; i < installs.length; i++) {
				processVMInstallType(installs[i], locations, labels);
			}
			fInstallLocations = (String[]) locations
					.toArray(new String[locations.size()]);
			fVMNames = (String[]) labels.toArray(new String[labels.size()]);

		}

		public void setFullyQualifyDuplicates(boolean value) {
			// fFullyQualifyDuplicates= value;
		}

		private void processVMInstallType(IInterpreterInstallType installType,
				List locations, List labels) {
			if (installType != null) {
				IInterpreterInstall[] installs = installType
						.getInterpreterInstalls();
				final boolean isMac = Platform.OS_MACOSX.equals(Platform
						.getOS());
				for (int i = 0; i < installs.length; i++) {
					final IInterpreterInstall install = installs[i];
					final String label = getFormattedLabel(install.getName());
					final LibraryLocation[] libLocations = install
							.getLibraryLocations();
					if (libLocations != null) {
						processLibraryLocation(libLocations, label);
					} else {
						String filePath = install.getInstallLocation()
								.toOSString();
						/*
						 * filePath could be null if environment is configured,
						 * but environment-specific plugins are absent.
						 */
						if (filePath != null) {
							// on MacOS X install locations end in an additional
							// "/Home" segment; remove it
							if (isMac && filePath.endsWith(HOME_SUFFIX))
								filePath = filePath.substring(0, filePath
										.length()
										- (HOME_SUFFIX.length() - 1));
							locations.add(filePath);
							labels.add(label);
						}
					}
				}
			}
		}

		private static final String HOME_SUFFIX = "/Home"; //$NON-NLS-1$

		private void processLibraryLocation(LibraryLocation[] libLocations,
				String label) {
			for (int l = 0; l < libLocations.length; l++) {
				LibraryLocation location = libLocations[l];
				fLib2Name.put(location.getLibraryPath().toString(), label);
			}
		}

		private String getFormattedLabel(String name) {
			return Messages.format(
					DLTKUIMessages.TypeInfoViewer_library_name_format, name);
		}

		public String getText(Object element) {
			TypeNameMatch type = (TypeNameMatch) element;
			return getTypeContainerName(type, 0);
		}

		public String getQualifiedText(TypeNameMatch type) {
			StringBuffer result = new StringBuffer();
			result.append(getTypeContainerName(type, 2));
			// String containerName= type.getTypeContainerName();
			// result.append(ScriptElementLabels.CONCAT_STRING);
			// if (containerName.length() > 0) {
			// result.append(containerName);
			// } else {
			// result.append(DLTKUIMessages.TypeInfoViewer_default_package);
			// }
			return result.toString();
		}

		public String getFullyQualifiedText(TypeNameMatch type) {
			StringBuffer result = new StringBuffer();
			result.append(getTypeContainerName(type, 2));
			// IType dltkType = ((DLTKSearchTypeNameMatch) type).getType();
			// ISourceModule sourceModule = (ISourceModule)
			// dltkType.getAncestor(IModelElement.SOURCE_MODULE);
			// String sourceModuleName = sourceModule.getElementName();
			// if (sourceModuleName.length() > 0) {
			// result.append(ScriptElementLabels.CONCAT_STRING);
			// result.append(sourceModuleName);
			// }
			// result.append(ScriptElementLabels.CONCAT_STRING);
			// result.append(getContainerName(type));
			return result.toString();
		}

		public String getText(TypeNameMatch last, TypeNameMatch current,
				TypeNameMatch next) {
			int qualifications = 0;
			String current0 = getTypeContainerName(current, 0);
			String current1 = getTypeContainerName(current, 1);
			String current2 = getTypeContainerName(current, 2);
			if (last != null) {
				String last0 = getTypeContainerName(last, 0);
				String last1 = getTypeContainerName(last, 1);
				if (current0.equals(last0)) {
					if (current1.equals(last1))
						qualifications = Math.max(qualifications, 2);
					else
						qualifications = Math.max(qualifications, 1);
				}
			}
			if (next != null) {
				String next0 = getTypeContainerName(next, 0);
				String next1 = getTypeContainerName(next, 1);
				if (current0.equals(next0)) {
					if (current1.equals(next1))
						qualifications = Math.max(qualifications, 2);
					else
						qualifications = Math.max(qualifications, 1);
				}
			}
			if (qualifications > 1)
				return current2;
			if (qualifications > 0)
				return current1;
			return current0;
		}

		public String getQualificationText(TypeNameMatch type) {
			StringBuffer result = new StringBuffer();
			String containerName = type.getTypeContainerName();
			if (containerName.length() > 0) {
				result.append(containerName);
				result.append(ScriptElementLabels.CONCAT_STRING);
			}
			result.append(getContainerName(type));
			return result.toString();
		}

		// private boolean isInnerType(TypeNameMatch match) {
		// return match.getTypeQualifiedName().indexOf('.') != -1;
		// }

		public ImageDescriptor getImageDescriptor(Object element) {
			TypeNameMatch type = (TypeNameMatch) element;
			if (fProviderExtension != null) {
				fAdapter.setMatch(type);
				ImageDescriptor descriptor = fProviderExtension
						.getImageDescriptor(fAdapter);
				if (descriptor != null)
					return descriptor;
			}
			return ScriptElementImageProvider.getTypeImageDescriptor(
			/* isInnerType(type), false, */type.getModifiers(), false);
		}

		private String getTypeContainerName(TypeNameMatch info, int infoLevel) {
			// String result= info.getTypeContainerName();
			String result = ""; //$NON-NLS-1$
			if (CodeassistUtils.isDefaultNativeSourceFolder(info.getType()
					.getParent().getParent())) { // it is Native source
				// module
				VjoSourceModule module = (VjoSourceModule) info.getType()
						.getParent();
				TypeName tname = module.getTypeName();

				String typeName = tname.typeName();
				IType type = info.getType();
				String realTypeName = type.getElementName();
				if (typeName.endsWith(realTypeName)) {
					return typeName;
				} else {
					return tname.typeName() + "." + realTypeName;
				}
			}
			IDLTKUILanguageToolkit toolkit = DLTKUILanguageManager
					.getLanguageToolkit(VjoNature.NATURE_ID);
			if (toolkit != null) {
				ScriptElementLabels labels = toolkit.getScriptElementLabels();
				result = labels
						.getElementLabel(
								info.getType(),
								ScriptElementLabels.T_CONTAINER_QUALIFIED
										| (infoLevel > 0 ? ScriptElementLabels.APPEND_FILE
												: 0)
										| (infoLevel > 1 ? ScriptElementLabels.CU_POST_QUALIFIED
												: 0));
			}
			if (result.length() > 0)
				return result;
			// System.out.println(result);
			return DLTKUIMessages.TypeInfoViewer_default_package;
		}

		private String getContainerName(TypeNameMatch type) {
			IProjectFragment root = type.getProjectFragment();
			if (root == null) {
				return "";
			}
			if (root.isExternal()) {
				String name = root.getPath().toOSString();
				for (int i = 0; i < fInstallLocations.length; i++) {
					if (name.startsWith(fInstallLocations[i])) {
						return fVMNames[i];
					}
				}
				String lib = (String) fLib2Name.get(name);
				if (lib != null)
					return lib;
			}
			StringBuffer buf = new StringBuffer();
			ScriptElementLabels labels = fToolkit.getScriptElementLabels();
			labels.getProjectFragmentLabel(root,
					ScriptElementLabels.ROOT_QUALIFIED
							| ScriptElementLabels.ROOT_VARIABLE, buf);

			return buf.toString();
		}
	}

	private static class ProgressUpdateJob extends UIJob {
		private VjoTypeInfoViewer fViewer;
		private boolean fStopped;

		public ProgressUpdateJob(Display display, VjoTypeInfoViewer viewer) {
			super(display, DLTKUIMessages.TypeInfoViewer_progressJob_label);
			fViewer = viewer;
		}

		public void stop() {
			fStopped = true;
			cancel();
		}

		public IStatus runInUIThread(IProgressMonitor monitor) {
			if (stopped())
				return new Status(IStatus.CANCEL, DLTKUIPlugin.getPluginId(),
						IStatus.CANCEL, "", null); //$NON-NLS-1$
			fViewer.updateProgressMessage();
			if (!stopped())
				schedule(300);
			return new Status(IStatus.OK, DLTKUIPlugin.getPluginId(),
					IStatus.OK, "", null); //$NON-NLS-1$
		}

		private boolean stopped() {
			return fStopped || fViewer.getTable().isDisposed();
		}
	}

	private static class ProgressMonitor extends ProgressMonitorWrapper {
		private VjoTypeInfoViewer fViewer;
		private String fName;
		private int fTotalWork;
		private double fWorked;
		private boolean fDone;

		public ProgressMonitor(IProgressMonitor monitor,
				VjoTypeInfoViewer viewer) {
			super(monitor);
			fViewer = viewer;
		}

		public void setTaskName(String name) {
			super.setTaskName(name);
			fName = name;
		}

		public void beginTask(String name, int totalWork) {
			super.beginTask(name, totalWork);
			if (fName == null)
				fName = name;
			fTotalWork = totalWork;
		}

		public void worked(int work) {
			super.worked(work);
			internalWorked(work);
		}

		public void done() {
			fDone = true;
			fViewer.setProgressMessage(""); //$NON-NLS-1$
			super.done();
		}

		public void internalWorked(double work) {
			fWorked = fWorked + work;
			fViewer.setProgressMessage(getMessage());
		}

		private String getMessage() {
			if (fDone) {
				return ""; //$NON-NLS-1$
			} else if (fTotalWork == 0) {
				return fName;
			} else {
				return Messages
						.format(
								DLTKUIMessages.TypeInfoViewer_progress_label,
								new Object[] {
										fName,
										new Integer(
												(int) ((fWorked * 100) / fTotalWork)) });
			}
		}
	}

	private static abstract class AbstractJob extends Job {
		protected VjoTypeInfoViewer fViewer;

		protected AbstractJob(String name, VjoTypeInfoViewer viewer) {
			super(name);
			fViewer = viewer;
			setSystem(true);
		}

		protected final IStatus run(IProgressMonitor parent) {
			ProgressMonitor monitor = new ProgressMonitor(parent, fViewer);
			try {
				fViewer.scheduleProgressUpdateJob();
				return doRun(monitor);
			} finally {
				fViewer.stopProgressUpdateJob();
			}
		}

		protected abstract IStatus doRun(ProgressMonitor monitor);
	}

	private static abstract class AbstractSearchJob extends AbstractJob {
		private int fMode;

		protected int fTicket;
		protected TypeInfoLabelProvider fLabelProvider;

		protected TypeInfoFilter fFilter;
		protected OpenTypeHistory fHistory;

		protected AbstractSearchJob(int ticket, VjoTypeInfoViewer viewer,
				TypeInfoFilter filter, OpenTypeHistory history,
				int numberOfVisibleItems, int mode) {
			super(DLTKUIMessages.TypeInfoViewer_job_label, viewer);
			fMode = mode;
			fTicket = ticket;
			fViewer = viewer;
			fLabelProvider = fViewer.getLabelProvider();
			fFilter = filter;
			fHistory = history;
		}

		public void stop() {
			cancel();
		}

		protected IStatus doRun(ProgressMonitor monitor) {
			try {
				if (VIRTUAL) {
					internalRunVirtual(monitor);
				} else {

					internalRun(monitor);
				}
			} catch (CoreException e) {
				fViewer.searchJobFailed(fTicket, e);
				return new Status(IStatus.ERROR, DLTKUIPlugin.getPluginId(),
						IStatus.ERROR, DLTKUIMessages.TypeInfoViewer_job_error,
						e);
			} catch (InterruptedException e) {
				return canceled(e, true);
			} catch (OperationCanceledException e) {
				return canceled(e, false);
			}
			fViewer.searchJobDone(fTicket);
			return ok();
		}

		protected abstract TypeNameMatch[] getSearchResult(
				Set matchIdsInHistory, ProgressMonitor monitor)
				throws CoreException;

		private void internalRun(ProgressMonitor monitor) throws CoreException,
				InterruptedException {
			if (monitor.isCanceled())
				throw new OperationCanceledException();

			fViewer.clear(fTicket);

			// local vars to speed up rendering
			TypeNameMatch last = null;
			TypeNameMatch type = null;
			TypeNameMatch next = null;
			List elements = new ArrayList();
			List imageDescriptors = new ArrayList();
			List labels = new ArrayList();
			Set filteredMatches = new HashSet();

			TypeNameMatch[] matchingTypes = fHistory
					.getFilteredTypeInfos(fFilter);

			// Add by Oliver. Filter the history result when we switch between
			// ctype and itype.
			matchingTypes = filterTypeHistory(matchingTypes);

			if (matchingTypes.length > 0) {
				Arrays.sort(matchingTypes, new TypeInfoComparator(
						fLabelProvider, fFilter));
				type = matchingTypes[0];
				int i = 1;
				while (type != null) {
					next = (i == matchingTypes.length) ? null
							: matchingTypes[i];
					elements.add(type);
					filteredMatches.add(type);
					imageDescriptors.add(fLabelProvider
							.getImageDescriptor(type));
					labels.add(fLabelProvider.getText(last, type, next));
					last = type;
					type = next;
					i++;
				}
			}
			// matchingTypes = null;
			fViewer.fExpectedItemCount = elements.size();
			fViewer.addHistory(fTicket, elements, imageDescriptors, labels);

			if ((fMode & INDEX) == 0) {
				return;
			}
			TypeNameMatch[] result = getSearchResult(filteredMatches, monitor);
			fViewer.fExpectedItemCount += result.length;
			if (result.length == 0) {
				return;
			}
			if (monitor.isCanceled())
				throw new OperationCanceledException();
			int processed = 0;
			int nextIndex = 1;
			type = result[0];
			if (!filteredMatches.isEmpty()) {
				fViewer.addDashLineAndUpdateLastHistoryEntry(fTicket, type);
			}
			while (true) {
				long startTime = System.currentTimeMillis();
				elements.clear();
				imageDescriptors.clear();
				labels.clear();
				int delta = Math.min(nextIndex == 1 ? fViewer
						.getNumberOfVisibleItems() : 10, result.length
						- processed);
				if (delta == 0)
					break;
				processed = processed + delta;
				while (delta > 0) {
					next = (nextIndex == result.length) ? null
							: result[nextIndex];
					elements.add(type);
					labels.add(fLabelProvider.getText(last, type, next));
					imageDescriptors.add(fLabelProvider
							.getImageDescriptor(type));
					last = type;
					type = next;
					nextIndex++;
					delta--;
				}
				fViewer.addAll(fTicket, elements, imageDescriptors, labels);
				long sleep = 100 - (System.currentTimeMillis() - startTime);
				if (false)
					System.out.println("Sleeping for: " + sleep); //$NON-NLS-1$

				if (sleep > 0)
					Thread.sleep(sleep);

				if (monitor.isCanceled())
					throw new OperationCanceledException();
			}
		}

		private void internalRunVirtual(ProgressMonitor monitor)
				throws CoreException, InterruptedException {
			if (monitor.isCanceled())
				throw new OperationCanceledException();

			fViewer.clear(fTicket);

			TypeNameMatch[] matchingTypes = fHistory
					.getFilteredTypeInfos(fFilter);
			fViewer.setHistoryResult(fTicket, matchingTypes);
			if ((fMode & INDEX) == 0)
				return;

			Set filteredMatches = new HashSet(matchingTypes.length * 2);
			for (int i = 0; i < matchingTypes.length; i++) {
				filteredMatches.add(matchingTypes[i]);
			}

			TypeNameMatch[] result = getSearchResult(filteredMatches, monitor);
			if (monitor.isCanceled())
				throw new OperationCanceledException();

			fViewer.setSearchResult(fTicket, result);
		}

		private IStatus canceled(Exception e, boolean removePendingItems) {
			fViewer.searchJobCanceled(fTicket, removePendingItems);
			return new Status(IStatus.CANCEL, DLTKUIPlugin.getPluginId(),
					IStatus.CANCEL, DLTKUIMessages.TypeInfoViewer_job_cancel, e);
		}

		private IStatus ok() {
			return new Status(IStatus.OK, DLTKUIPlugin.getPluginId(),
					IStatus.OK, "", null); //$NON-NLS-1$
		}
	}

	/**
	 * for test case:
	 * org.ebayopensource.vjet.eclipse.core.test.bug.BugVerifyTests.test6664()
	 * 
	 * @param text
	 * @return
	 * @throws ModelException
	 */
	public static TypeNameMatch[] getSearchResults(String text)
			throws ModelException {
		ModelManager manager = ModelManager.getModelManager();
		Model model = manager.getModel();

		List<IJstType> list = s_typeSearcher.search(text);

		List<TypeNameMatch> matches = new ArrayList<TypeNameMatch>();
		int flags = 0;

		for (IJstType jstType : list) {

			// Add by Oliver. Filter the type only for interface.
			if (VjoOpenTypeSelectionDialog.SHOWINTERFACEONLY == VjoTypeSelectionDialog
					.getShownTypeFlag()) {
				if (!jstType.isInterface()
				// Append another condition to filter the native type.
						|| CodeassistUtils.isNativeType(jstType)) {
					continue;
				}
			}
			// Filter the type only for ctype.
			if (VjoOpenTypeSelectionDialog.SHOWCTYPEONLY == VjoTypeSelectionDialog
					.getShownTypeFlag()) {
				if (jstType.isInterface()
				// Append another condition to filter the native type.
						|| CodeassistUtils.isNativeType(jstType)) {
					continue;
				}
			}

			IType type = null;
			String name = jstType.getName();
			String group = jstType.getPackage().getGroupName();
			// Deprecated
			if (CodeassistUtils.isBinaryType(jstType)) {
				type = CodeassistUtils.findNativeSourceType(jstType);
				if (type == null)
					continue;
				if (jstType.isInterface()) {
					flags = Modifiers.AccInterface;
				}
				
				DLTKSearchTypeNameMatch match;

				match = new DLTKSearchTypeNameMatch(type, flags);
				matches.add(match);
				
			}
			// Deprecated
			else if (CodeassistUtils.isNativeType(jstType)) {
				IJstType jtype = CodeassistUtils.findNativeJstType(name);
				if (jtype == null) {
					continue;
				}
				type = CodeassistUtils.findNativeSourceType(jtype);
				if (type == null)
					continue;
				if (jstType.isInterface()) {
					flags = Modifiers.AccInterface;
				}
			} else {

				if (group == null || group.trim().length() == 0) {
					continue;
				}

				// Add by Oliver. Filter the type out of current working set.
				// if (!isProjectBelongToWorkingSetOfScope(group)) {
				// continue;
				// }

				// Search from every script project
				IScriptProject[] projects = model.getScriptProjects();
				for (int i = 0; i < projects.length; i++) {
					String rootTypeName = jstType.getRootType().getName();
					IType rootDltkType = CodeassistUtils.findType(
							(ScriptProject) projects[i], jstType);
					if (rootDltkType == null)
						continue;
					else
						type = findDLTKType(rootDltkType, name);

					if (type != null) {
						
						try {
							flags = type.getFlags();
						} catch (Exception e) {
							continue;
						}
						if ((flags & ClassFileConstants.AccInterface) != 0) {
							flags = Modifiers.AccInterface;
						}
						DLTKSearchTypeNameMatch match;

						match = new DLTKSearchTypeNameMatch(type, flags);
						matches.add(match);
						break;
					}
				}
			}

		}

		TypeNameMatch[] result = (TypeNameMatch[]) matches
				.toArray(new TypeNameMatch[matches.size()]);
		return result;
	}

	private static IType findDLTKType(IType rootType, String typeName) {
		String qualifiedName = rootType.getFullyQualifiedName(".");
		if (qualifiedName.equals(typeName)) {
			return rootType;
		} else {
			try {
				IType[] types = rootType.getTypes();
				for (int i = 0; i < types.length; i++) {
					IType type = findDLTKType(types[i], typeName);
					if (type != null)
						return type;
				}
			} catch (Exception e) {
				DLTKUIPlugin.log(e);
			}
		}
		return null;
	}

	/**
	 * Before return the search result, we will judge whether the project that
	 * this type belong to is in the selected working set if we enable working
	 * set filter.
	 * 
	 * @param groupName
	 * @return
	 */
	private static boolean isProjectBelongToWorkingSetOfScope(String groupName) {
		if (fSearchScope != null && fSearchScope instanceof DLTKSearchScope) {
			List projectInWorkingSet = ((DLTKSearchScope) fSearchScope)
					.getProjectPaths();
			if (projectInWorkingSet.contains("/" + groupName)) {
				return true;
			}
		}
		return false;
	}

	private class SearchEngineJob extends AbstractSearchJob {
		private IDLTKSearchScope fScope;
		private int fElementKind;

		public SearchEngineJob(int ticket, VjoTypeInfoViewer viewer,
				TypeInfoFilter filter, OpenTypeHistory history,
				int numberOfVisibleItems, int mode, IDLTKSearchScope scope,
				int elementKind, IDLTKUILanguageToolkit toolkit) {
			super(ticket, viewer, filter, history, numberOfVisibleItems, mode);
			fScope = scope;
			fElementKind = elementKind;
			// fReqestor = new SearchRequestor(filter, new TypeFilter(toolkit));
		}

		public void stop() {
			super.stop();
		}

		protected TypeNameMatch[] getSearchResult(Set matchIdsInHistory,
				ProgressMonitor monitor) throws CoreException {

			String text = fFilter.getText();

			TypeNameMatch[] result = getSearchResults(text);

			TypeInfoComparator comparator;
			comparator = new TypeInfoComparator(fLabelProvider, fFilter);
			Arrays.sort(result, comparator);
			fViewer.rememberResult(fTicket, result);
			return result;
		}

	}

	private class CachedResultJob extends AbstractSearchJob {
		private TypeNameMatch[] fLastResult;

		public CachedResultJob(int ticket, TypeNameMatch[] lastResult,
				VjoTypeInfoViewer viewer, TypeInfoFilter filter,
				OpenTypeHistory history, int numberOfVisibleItems, int mode) {
			super(ticket, viewer, filter, history, numberOfVisibleItems, mode);
			fLastResult = lastResult;
		}

		protected TypeNameMatch[] getSearchResult(Set filteredHistory,
				ProgressMonitor monitor) throws CoreException {
			String text = fFilter.getText();

			TypeNameMatch[] result = getSearchResults(text);

			TypeInfoComparator comparator;
			comparator = new TypeInfoComparator(fLabelProvider, fFilter);
			Arrays.sort(result, comparator);
			return result;
		}
	}

	private static class SyncJob extends AbstractJob {
		private IDLTKLanguageToolkit fToolkit;

		public SyncJob(VjoTypeInfoViewer viewer, IDLTKLanguageToolkit toolkit) {
			super(DLTKUIMessages.TypeInfoViewer_syncJob_label, viewer);
			this.fToolkit = toolkit;
		}

		public void stop() {
			cancel();
		}

		protected IStatus doRun(ProgressMonitor monitor) {
			try {
				monitor
						.setTaskName(DLTKUIMessages.TypeInfoViewer_syncJob_taskName);
				new SearchEngine().searchAllTypeNames(
						null,
						0,
						// make sure we search a concrete name. This is faster
						// according to Kent
						"_______________".toCharArray(), //$NON-NLS-1$
						SearchPattern.R_EXACT_MATCH
								| SearchPattern.R_CASE_SENSITIVE,
						IDLTKSearchConstants.TYPE, SearchEngine
								.createWorkspaceScope(fToolkit),
						new TypeNameRequestor() {
						}, IDLTKSearchConstants.WAIT_UNTIL_READY_TO_SEARCH,
						monitor);
			} catch (ModelException e) {
				DLTKUIPlugin.log(e);
				return new Status(IStatus.ERROR, DLTKUIPlugin.getPluginId(),
						IStatus.ERROR, DLTKUIMessages.TypeInfoViewer_job_error,
						e);
			} catch (OperationCanceledException e) {
				return new Status(IStatus.CANCEL, DLTKUIPlugin.getPluginId(),
						IStatus.CANCEL,
						DLTKUIMessages.TypeInfoViewer_job_cancel, e);
			} finally {
				fViewer.syncJobDone();
			}
			return new Status(IStatus.OK, DLTKUIPlugin.getPluginId(),
					IStatus.OK, "", null); //$NON-NLS-1$
		}
	}

	private static class DashLine {
		private int fSeparatorWidth;
		private String fMessage;
		private int fMessageLength;

		public String getText(int width) {
			StringBuffer dashes = new StringBuffer();
			int chars = (((width - fMessageLength) / fSeparatorWidth) / 2) - 2;
			for (int i = 0; i < chars; i++) {
				dashes.append(SEPARATOR);
			}
			StringBuffer result = new StringBuffer();
			result.append(dashes);
			result.append(fMessage);
			result.append(dashes);
			return result.toString();
		}

		public void initialize(GC gc) {
			fSeparatorWidth = gc.getAdvanceWidth(SEPARATOR);
			fMessage = " " + DLTKUIMessages.TypeInfoViewer_separator_message + " "; //$NON-NLS-1$ //$NON-NLS-2$
			fMessageLength = gc.textExtent(fMessage).x;
		}
	}

	private static class ImageManager {
		private Map fImages = new HashMap(20);

		public Image get(ImageDescriptor descriptor) {
			if (descriptor == null)
				descriptor = ImageDescriptor.getMissingImageDescriptor();

			Image result = (Image) fImages.get(descriptor);
			if (result != null)
				return result;
			result = descriptor.createImage();
			if (result != null)
				fImages.put(descriptor, result);
			return result;
		}

		public void dispose() {
			for (Iterator iter = fImages.values().iterator(); iter.hasNext();) {
				Image image = (Image) iter.next();
				image.dispose();
			}
			fImages.clear();
		}
	}

	private Display fDisplay;

	private String fProgressMessage;
	private Label fProgressLabel;
	private int fProgressCounter;
	private ProgressUpdateJob fProgressUpdateJob;

	private OpenTypeHistory fHistory;

	/* non virtual table */
	private int fNextElement;
	private List fItems;

	/* virtual table */
	private TypeNameMatch[] fHistoryMatches;
	private TypeNameMatch[] fSearchMatches;

	private int fNumberOfVisibleItems;
	private int fExpectedItemCount;
	private Color fDashLineColor;
	private int fScrollbarWidth;
	private int fTableWidthDelta;
	private int fDashLineIndex = -1;
	private Image fSeparatorIcon;
	private DashLine fDashLine = new DashLine();

	private boolean fFullyQualifySelection;
	/* remembers the last selection to restore unqualified labels */
	private TableItem[] fLastSelection;
	private String[] fLastLabels;

	private TypeInfoLabelProvider fLabelProvider;
	private ImageManager fImageManager;

	private Table fTable;

	private SyncJob fSyncJob;

	private TypeInfoFilter fTypeInfoFilter;
	private ITypeInfoFilterExtension fFilterExtension;
	private TypeNameMatch[] fLastCompletedResult;
	private TypeInfoFilter fLastCompletedFilter;

	private int fSearchJobTicket;
	protected int fElementKind;
	protected static IDLTKSearchScope fSearchScope;

	private AbstractSearchJob fSearchJob;

	private static final int HISTORY = 1;
	private static final int INDEX = 2;
	private static final int FULL = HISTORY | INDEX;

	private static final char SEPARATOR = '-';

	private static final boolean DEBUG = false;
	private static final boolean VIRTUAL = false;

	private static final TypeNameMatch[] EMTPY_TYPE_INFO_ARRAY = new TypeNameMatch[0];
	// only needed when in virtual table mode

	private static final TypeNameMatch DASH_LINE = SearchEngine
			.createTypeNameMatch(null, 0);

	public VjoTypeInfoViewer(Composite parent, int flags, Label progressLabel,
			IDLTKSearchScope scope, int elementKind, String initialFilter,
			ITypeInfoFilterExtension filterExtension,
			ITypeInfoImageProvider imageExtension,
			IDLTKUILanguageToolkit toolkit) {
		Assert.isNotNull(scope);

		fToolkit = toolkit;
		fDisplay = parent.getDisplay();
		fProgressLabel = progressLabel;
		fSearchScope = scope;
		fElementKind = elementKind;
		fFilterExtension = filterExtension;
		fFullyQualifySelection = (flags & SWT.MULTI) != 0;
		fTable = new Table(parent, SWT.V_SCROLL | SWT.H_SCROLL | SWT.BORDER
				| SWT.FLAT | flags | (VIRTUAL ? SWT.VIRTUAL : SWT.NONE));
		fTable.setFont(parent.getFont());
		fLabelProvider = new TypeInfoLabelProvider(imageExtension);
		fItems = new ArrayList(500);
		fTable.setHeaderVisible(false);
		addPopupMenu();
		fTable.addControlListener(new ControlAdapter() {
			public void controlResized(ControlEvent event) {
				int itemHeight = fTable.getItemHeight();
				Rectangle clientArea = fTable.getClientArea();
				fNumberOfVisibleItems = (clientArea.height / itemHeight) + 1;
			}
		});
		fTable.addKeyListener(new KeyAdapter() {
			public void keyPressed(KeyEvent e) {
				if (e.keyCode == SWT.DEL) {
					deleteHistoryEntry();
				} else if (e.keyCode == SWT.ARROW_DOWN) {
					int index = fTable.getSelectionIndex();
					if (index == fDashLineIndex - 1) {
						e.doit = false;
						setTableSelection(index + 2);
					}
				} else if (e.keyCode == SWT.ARROW_UP) {
					int index = fTable.getSelectionIndex();
					if (fDashLineIndex != -1 && index == fDashLineIndex + 1) {
						e.doit = false;
						setTableSelection(index - 2);
					}
				}
			}
		});
		fTable.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				if (fLastSelection != null) {
					for (int i = 0; i < fLastSelection.length; i++) {
						TableItem item = fLastSelection[i];
						// could be disposed by deleting element from
						// type info history
						if (!item.isDisposed())
							item.setText(fLastLabels[i]);
					}
				}
				TableItem[] items = fTable.getSelection();
				fLastSelection = new TableItem[items.length];
				fLastLabels = new String[items.length];
				for (int i = 0; i < items.length; i++) {
					TableItem item = items[i];
					fLastSelection[i] = item;
					fLastLabels[i] = item.getText();
					Object data = item.getData();
					if (data instanceof TypeNameMatch) {
						String qualifiedText = getQualifiedText((TypeNameMatch) data);
						if (qualifiedText.length() > fLastLabels[i].length())
							item.setText(qualifiedText);
					}
				}
			}
		});
		fTable.addDisposeListener(new DisposeListener() {
			public void widgetDisposed(DisposeEvent e) {
				stop(true, true);
				fDashLineColor.dispose();
				fSeparatorIcon.dispose();
				fImageManager.dispose();
				if (fProgressUpdateJob != null) {
					fProgressUpdateJob.stop();
					fProgressUpdateJob = null;
				}
			}
		});
		if (VIRTUAL) {
			fHistoryMatches = EMTPY_TYPE_INFO_ARRAY;
			fSearchMatches = EMTPY_TYPE_INFO_ARRAY;
			fTable.addListener(SWT.SetData, new Listener() {
				public void handleEvent(Event event) {
					TableItem item = (TableItem) event.item;
					setData(item);
				}
			});
		}

		fDashLineColor = computeDashLineColor();
		fScrollbarWidth = computeScrollBarWidth();
		fTableWidthDelta = fTable.computeTrim(0, 0, 0, 0).width
				- fScrollbarWidth;
		fSeparatorIcon = DLTKPluginImages.DESC_OBJS_TYPE_SEPARATOR
				.createImage(fTable.getDisplay());
		// Use a new image manager since an extension can provide its own
		// image descriptors. To avoid thread problems with SWT the registry
		// must be created in the UI thread.
		fImageManager = new ImageManager();

		fHistory = OpenTypeHistory.getInstance(this.fToolkit);
		if (initialFilter != null && initialFilter.length() > 0)
			fTypeInfoFilter = createTypeInfoFilter(initialFilter);
		GC gc = null;
		try {
			gc = new GC(fTable);
			gc.setFont(fTable.getFont());
			fDashLine.initialize(gc);
		} finally {
			gc.dispose();
		}
		// If we do have a type info filter then we are
		// scheduling a search job in startup. So no
		// need to sync the search indices.
		if (fTypeInfoFilter == null) {
			scheduleSyncJob();
		}
	}

	/* package */void startup() {
		if (fTypeInfoFilter == null) {
			reset();
		} else {
			scheduleSearchJob(FULL);
		}
	}

	public Table getTable() {
		return fTable;
	}

	/* package */TypeInfoLabelProvider getLabelProvider() {
		return fLabelProvider;
	}

	private int getNumberOfVisibleItems() {
		return fNumberOfVisibleItems;
	}

	public void setFocus() {
		fTable.setFocus();
	}

	public void setQualificationStyle(boolean value) {
		if (fFullyQualifySelection == value)
			return;
		fFullyQualifySelection = value;
		if (fLastSelection != null) {
			for (int i = 0; i < fLastSelection.length; i++) {
				TableItem item = fLastSelection[i];
				Object data = item.getData();
				if (data instanceof TypeNameMatch) {
					item.setText(getQualifiedText((TypeNameMatch) data));
				}
			}
		}
	}

	public TypeNameMatch[] getSelection() {
		TableItem[] items = fTable.getSelection();
		List result = new ArrayList(items.length);
		for (int i = 0; i < items.length; i++) {
			Object data = items[i].getData();
			if (data instanceof TypeNameMatch) {
				result.add(data);
			}
		}
		return (TypeNameMatch[]) result
				.toArray(new TypeNameMatch[result.size()]);
	}

	public void stop() {
		stop(true, false);
	}

	public void stop(boolean stopSyncJob, boolean dispose) {
		if (fSyncJob != null && stopSyncJob) {
			fSyncJob.stop();
			fSyncJob = null;
		}
		if (fSearchJob != null) {
			fSearchJob.stop();
			fSearchJob = null;
		}
	}

	public void forceSearch() {
		stop(false, false);
		if (fTypeInfoFilter == null) {
			reset();
		} else {
			// clear last results
			fLastCompletedFilter = null;
			fLastCompletedResult = null;
			scheduleSearchJob(isSyncJobRunning() ? HISTORY : FULL);
		}
	}

	public void setSearchPattern(String text) {
		stop(false, false);
		if (text.length() == 0) { //$NON-NLS-1$
			fTypeInfoFilter = null;
			reset();
		} else {
			// reset();
			deleteHistoryEntry();
			fTypeInfoFilter = createTypeInfoFilter(text);
			scheduleSearchJob(isSyncJobRunning() ? HISTORY : FULL);
		}
	}

	public void setSearchScope(IDLTKSearchScope scope, boolean refresh) {
		setStaticSearchScope(scope);
		if (!refresh)
			return;
		stop(false, false);
		fLastCompletedFilter = null;
		fLastCompletedResult = null;
		if (fTypeInfoFilter == null) {
			reset();
			// if (refresh) {
			// scheduleSearchJob(isSyncJobRunning() ? HISTORY : FULL);
			// }
		} else {
			scheduleSearchJob(isSyncJobRunning() ? HISTORY : FULL);
		}
	}

	private static void setStaticSearchScope(IDLTKSearchScope scope) {
		fSearchScope = scope;
	}

	public void setFullyQualifyDuplicates(boolean value, boolean refresh) {
		fLabelProvider.setFullyQualifyDuplicates(value);
		if (!refresh)
			return;
		stop(false, false);
		if (fTypeInfoFilter == null) {
			reset();
		} else {
			scheduleSearchJob(isSyncJobRunning() ? HISTORY : FULL);
		}
	}

	public void reset() {
		fLastSelection = null;
		fLastLabels = null;
		fExpectedItemCount = 0;
		fDashLineIndex = -1;
		TypeInfoFilter filter = (fTypeInfoFilter != null) ? fTypeInfoFilter
				: new TypeInfoFilter(
						"*", fSearchScope, fElementKind, fFilterExtension); //$NON-NLS-1$
		if (VIRTUAL) {
			fHistoryMatches = fHistory.getFilteredTypeInfos(filter);
			fExpectedItemCount = fHistoryMatches.length;
			fTable.setItemCount(fHistoryMatches.length);
			// bug under windows.
			if (fHistoryMatches.length == 0) {
				fTable.redraw();
			}
			fTable.clear(0, fHistoryMatches.length - 1);
		} else {
			fNextElement = 0;
			TypeNameMatch[] historyItems = fHistory
					.getFilteredTypeInfos(filter);

			// Add by Oliver. Filter the history result when we switch between
			// ctype and itype.
			historyItems = filterTypeHistory(historyItems);

			if (historyItems.length == 0) {
				shortenTable();
				return;
			}
			fExpectedItemCount = historyItems.length;
			int lastIndex = historyItems.length - 1;
			TypeNameMatch last = null;
			TypeNameMatch type = historyItems[0];
			for (int i = 0; i < historyItems.length; i++) {
				TypeNameMatch next = i == lastIndex ? null
						: historyItems[i + 1];
				addSingleElement(type, fLabelProvider.getImageDescriptor(type),
						fLabelProvider.getText(last, type, next));
				last = type;
				type = next;
			}
			shortenTable();
		}
	}

	/**
	 * @param historyItems
	 * @return
	 */
	private static TypeNameMatch[] filterTypeHistory(
			TypeNameMatch[] historyItems) {
		List<TypeNameMatch> result = new ArrayList<TypeNameMatch>();
		for (TypeNameMatch typeNameMatch : historyItems) {
			IType type = typeNameMatch.getType();
			IJstType jstType = org.ebayopensource.vjet.eclipse.internal.core.util.Util
					.toJstType(type);
			// Add by Oliver. Filter the type only for interface.
			if (VjoOpenTypeSelectionDialog.SHOWINTERFACEONLY == VjoTypeSelectionDialog
					.getShownTypeFlag()) {
				if (jstType.isInterface()
						&& !CodeassistUtils.isNativeType(jstType)) {
					result.add(typeNameMatch);
				}
			}
			// Filter the type only for ctype.
			else if (VjoOpenTypeSelectionDialog.SHOWCTYPEONLY == VjoTypeSelectionDialog
					.getShownTypeFlag()) {
				if (!jstType.isInterface()
						&& !CodeassistUtils.isNativeType(jstType)) {
					result.add(typeNameMatch);
				}
			} else {
				result.add(typeNameMatch);
			}
		}
		return result.toArray(new TypeNameMatch[] {});
	}

	protected TypeInfoFilter createTypeInfoFilter(String text) {
		if ("**".equals(text)) //$NON-NLS-1$
			text = "*"; //$NON-NLS-1$
		return new TypeInfoFilter(text, fSearchScope, fElementKind,
				fFilterExtension);
	}

	private void addPopupMenu() {
		Menu menu = new Menu(fTable.getShell(), SWT.POP_UP);
		fTable.setMenu(menu);
		final MenuItem remove = new MenuItem(menu, SWT.NONE);
		remove.setText(DLTKUIMessages.TypeInfoViewer_remove_from_history);
		menu.addMenuListener(new MenuAdapter() {
			public void menuShown(MenuEvent e) {
				TableItem[] selection = fTable.getSelection();
				remove.setEnabled(canEnable(selection));
			}
		});
		remove.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				deleteHistoryEntry();
			}
		});
	}

	private boolean canEnable(TableItem[] selection) {
		if (selection.length == 0)
			return false;
		for (int i = 0; i < selection.length; i++) {
			TableItem item = selection[i];
			Object data = item.getData();
			if (!(data instanceof TypeNameMatch))
				return false;
			if (!(fHistory.contains((TypeNameMatch) data)))
				return false;
		}
		return true;
	}

	// ---- History management
	// -------------------------------------------------------

	private void deleteHistoryEntry() {
		int index = fTable.getSelectionIndex();
		if (index == -1)
			return;
		TableItem item = fTable.getItem(index);
		Object element = item.getData();
		if (!(element instanceof TypeNameMatch))
			return;
		if (fHistory.remove(element) != null) {
			item.dispose();
			fItems.remove(index);
			int count = fTable.getItemCount();
			if (count > 0) {
				item = fTable.getItem(0);
				if (item.getData() instanceof DashLine) {
					item.dispose();
					fItems.remove(0);
					fDashLineIndex = -1;
					if (count > 1) {
						setTableSelection(0);
					}
				} else {
					if (index >= count) {
						index = count - 1;
					}
					setTableSelection(index);
				}
			} else {
				// send dummy selection
				fTable.notifyListeners(SWT.Selection, new Event());
			}
		}
	}

	// -- Search result updating
	// ----------------------------------------------------

	private void clear(int ticket) {
		syncExec(ticket, new Runnable() {
			public void run() {
				fNextElement = 0;
				fDashLineIndex = -1;
				fLastSelection = null;
				fLastLabels = null;
				fExpectedItemCount = 0;
			}
		});
	}

	private void rememberResult(int ticket, final TypeNameMatch[] result) {
		syncExec(ticket, new Runnable() {
			public void run() {
				if (fLastCompletedResult == null) {
					fLastCompletedFilter = fTypeInfoFilter;
					fLastCompletedResult = result;
				}
			}
		});
	}

	private void addHistory(int ticket, final List elements,
			final List imageDescriptors, final List labels) {
		addAll(ticket, elements, imageDescriptors, labels);
	}

	private void addAll(int ticket, final List elements,
			final List imageDescriptors, final List labels) {
		syncExec(ticket, new Runnable() {
			public void run() {
				int size = elements.size();
				for (int i = 0; i < size; i++) {
					addSingleElement(elements.get(i),
							(ImageDescriptor) imageDescriptors.get(i),
							(String) labels.get(i));
				}
			}
		});
	}

	private void addDashLineAndUpdateLastHistoryEntry(int ticket,
			final TypeNameMatch next) {
		syncExec(ticket, new Runnable() {
			public void run() {
				if (fNextElement > 0) {
					TableItem item = fTable.getItem(fNextElement - 1);
					String label = item.getText();
					String newLabel = fLabelProvider.getText(null,
							(TypeNameMatch) item.getData(), next);
					if (newLabel.length() != label.length())
						item.setText(newLabel);
					if (fLastSelection != null && fLastSelection.length > 0) {
						TableItem last = fLastSelection[fLastSelection.length - 1];
						if (last == item) {
							fLastLabels[fLastLabels.length - 1] = newLabel;
						}
					}
				}
				fDashLineIndex = fNextElement;
				addDashLine();
			}
		});
	}

	private void addDashLine() {
		TableItem item = null;
		if (fItems.size() > fNextElement) {
			item = (TableItem) fItems.get(fNextElement);
		} else {
			item = new TableItem(fTable, SWT.NONE);
			fItems.add(item);
		}
		fillDashLine(item);
		fNextElement++;
	}

	private void addSingleElement(Object element,
			ImageDescriptor imageDescriptor, String label) {
		TableItem item = null;
		Object old = null;
		if (fItems.size() > fNextElement) {
			item = (TableItem) fItems.get(fNextElement);
			old = item.getData();
			item.setForeground(null);
		} else {
			item = new TableItem(fTable, SWT.NONE);
			fItems.add(item);
		}
		item.setData(element);
		item.setImage(fImageManager.get(imageDescriptor));
		if (fNextElement == 0) {
			if (needsSelectionChange(old, element) || fLastSelection != null) {
				item.setText(label);
				fTable.setSelection(0);
				fTable.notifyListeners(SWT.Selection, new Event());
			} else {
				fLastSelection = new TableItem[] { item };
				fLastLabels = new String[] { label };
			}
		} else {
			item.setText(label);
		}
		fNextElement++;
	}

	private boolean needsSelectionChange(Object oldElement, Object newElement) {
		int[] selected = fTable.getSelectionIndices();
		if (selected.length != 1)
			return true;
		if (selected[0] != 0)
			return true;
		if (oldElement == null)
			return true;
		return !oldElement.equals(newElement);
	}

	private void scheduleSearchJob(int mode) {
		fSearchJobTicket++;
		if (fLastCompletedFilter != null
				&& fTypeInfoFilter.isSubFilter(fLastCompletedFilter.getText())) {
			fSearchJob = new CachedResultJob(fSearchJobTicket,
					fLastCompletedResult, this, fTypeInfoFilter, fHistory,
					fNumberOfVisibleItems, mode);
		} else {
			fLastCompletedFilter = null;
			fLastCompletedResult = null;
			fSearchJob = new SearchEngineJob(fSearchJobTicket, this,
					fTypeInfoFilter, fHistory, fNumberOfVisibleItems, mode,
					fSearchScope, fElementKind, this.fToolkit);
		}
		fSearchJob.schedule();
	}

	private void searchJobDone(int ticket) {
		syncExec(ticket, new Runnable() {
			public void run() {
				shortenTable();
				checkEmptyList();
				fSearchJob = null;
			}
		});
	}

	private void searchJobCanceled(int ticket, final boolean removePendingItems) {
		syncExec(ticket, new Runnable() {
			public void run() {
				if (removePendingItems) {
					shortenTable();
					checkEmptyList();
				}
				fSearchJob = null;
			}
		});
	}

	private synchronized void searchJobFailed(int ticket, CoreException e) {
		searchJobDone(ticket);
		DLTKUIPlugin.log(e);
	}

	// -- virtual table support
	// -------------------------------------------------------

	private void setHistoryResult(int ticket, final TypeNameMatch[] types) {
		syncExec(ticket, new Runnable() {
			public void run() {
				fExpectedItemCount = types.length;
				int lastHistoryLength = fHistoryMatches.length;
				fHistoryMatches = types;
				int length = fHistoryMatches.length + fSearchMatches.length;
				int dash = (fHistoryMatches.length > 0 && fSearchMatches.length > 0) ? 1
						: 0;
				fTable.setItemCount(length + dash);
				if (length == 0) {
					// bug under windows.
					fTable.redraw();
					return;
				}
				int update = Math
						.max(lastHistoryLength, fHistoryMatches.length);
				if (update > 0) {
					fTable.clear(0, update + dash - 1);
				}
			}
		});
	}

	private void setSearchResult(int ticket, final TypeNameMatch[] types) {
		syncExec(ticket, new Runnable() {
			public void run() {
				fExpectedItemCount += types.length;
				fSearchMatches = types;
				int length = fHistoryMatches.length + fSearchMatches.length;
				int dash = (fHistoryMatches.length > 0 && fSearchMatches.length > 0) ? 1
						: 0;
				fTable.setItemCount(length + dash);
				if (length == 0) {
					// bug under windows.
					fTable.redraw();
					return;
				}
				if (fHistoryMatches.length == 0) {
					fTable.clear(0, length + dash - 1);
				} else {
					fTable.clear(fHistoryMatches.length - 1, length + dash - 1);
				}
			}
		});
	}

	private void setData(TableItem item) {
		int index = fTable.indexOf(item);
		TypeNameMatch type = getTypeInfo(index);
		if (type == DASH_LINE) {
			item.setData(fDashLine);
			fillDashLine(item);
		} else {
			item.setData(type);
			item.setImage(fImageManager.get(fLabelProvider
					.getImageDescriptor(type)));
			item.setText(fLabelProvider.getText(getTypeInfo(index - 1), type,
					getTypeInfo(index + 1)));
			item.setForeground(null);
		}
	}

	private TypeNameMatch getTypeInfo(int index) {
		if (index < 0)
			return null;
		if (index < fHistoryMatches.length) {
			return fHistoryMatches[index];
		}
		int dash = (fHistoryMatches.length > 0 && fSearchMatches.length > 0) ? 1
				: 0;
		if (index == fHistoryMatches.length && dash == 1) {
			return DASH_LINE;
		}
		index = index - fHistoryMatches.length - dash;
		if (index >= fSearchMatches.length)
			return null;
		return fSearchMatches[index];
	}

	// -- Sync Job updates
	// ------------------------------------------------------------

	private void scheduleSyncJob() {
		fSyncJob = new SyncJob(this, this.fToolkit.getCoreToolkit());
		fSyncJob.schedule();
	}

	private void syncJobDone() {
		syncExec(new Runnable() {
			public void run() {
				fSyncJob = null;
				if (fTypeInfoFilter != null) {
					scheduleSearchJob(FULL);
				}
			}
		});
	}

	private boolean isSyncJobRunning() {
		return fSyncJob != null;
	}

	// -- progress monitor updates
	// -----------------------------------------------------

	private void scheduleProgressUpdateJob() {
		syncExec(new Runnable() {
			public void run() {
				if (fProgressCounter == 0) {
					clearProgressMessage();
					fProgressUpdateJob = new ProgressUpdateJob(fDisplay,
							VjoTypeInfoViewer.this);
					fProgressUpdateJob.schedule(300);
				}
				fProgressCounter++;
			}
		});
	}

	private void stopProgressUpdateJob() {
		syncExec(new Runnable() {
			public void run() {
				fProgressCounter--;
				if (fProgressCounter == 0 && fProgressUpdateJob != null) {
					fProgressUpdateJob.stop();
					fProgressUpdateJob = null;
					clearProgressMessage();
				}
			}
		});
	}

	private void setProgressMessage(String message) {
		fProgressMessage = message;
	}

	private void clearProgressMessage() {
		fProgressMessage = ""; //$NON-NLS-1$
		fProgressLabel.setText(fProgressMessage);
	}

	private void updateProgressMessage() {
		fProgressLabel.setText(fProgressMessage);
	}

	// -- Helper methods
	// --------------------------------------------------------------

	private void syncExec(final Runnable runnable) {
		if (fDisplay.isDisposed())
			return;
		fDisplay.syncExec(new Runnable() {
			public void run() {
				if (fTable.isDisposed())
					return;
				runnable.run();
			}
		});
	}

	private void syncExec(final int ticket, final Runnable runnable) {
		if (fDisplay.isDisposed())
			return;
		fDisplay.syncExec(new Runnable() {
			public void run() {
				if (fTable.isDisposed() || ticket != fSearchJobTicket)
					return;
				runnable.run();
			}
		});
	}

	private void fillDashLine(TableItem item) {
		Rectangle bounds = item.getImageBounds(0);
		Rectangle area = fTable.getBounds();
		boolean willHaveScrollBar = fExpectedItemCount + 1 > fNumberOfVisibleItems;
		item
				.setText(fDashLine.getText(area.width - bounds.x - bounds.width
						- fTableWidthDelta
						- (willHaveScrollBar ? fScrollbarWidth : 0)));
		item.setImage(fSeparatorIcon);
		item.setForeground(fDashLineColor);
		item.setData(fDashLine);
	}

	private void shortenTable() {
		if (VIRTUAL)
			return;
		if (fNextElement < fItems.size()) {
			fTable.setRedraw(false);
			fTable.remove(fNextElement, fItems.size() - 1);
			fTable.setRedraw(true);
		}
		for (int i = fItems.size() - 1; i >= fNextElement; i--) {
			fItems.remove(i);
		}
	}

	private void checkEmptyList() {
		if (fTable.getItemCount() == 0) {
			fTable.notifyListeners(SWT.Selection, new Event());
		}
	}

	private void setTableSelection(int index) {
		fTable.setSelection(index);
		fTable.notifyListeners(SWT.Selection, new Event());
	}

	private Color computeDashLineColor() {
		Color fg = fTable.getForeground();
		int fGray = (int) (0.3 * fg.getRed() + 0.59 * fg.getGreen() + 0.11 * fg
				.getBlue());
		Color bg = fTable.getBackground();
		int bGray = (int) (0.3 * bg.getRed() + 0.59 * bg.getGreen() + 0.11 * bg
				.getBlue());
		int gray = (int) ((fGray + bGray) * 0.66);
		return new Color(fDisplay, gray, gray, gray);
	}

	private int computeScrollBarWidth() {
		Composite t = new Composite(fTable.getShell(), SWT.V_SCROLL);
		int result = t.computeTrim(0, 0, 0, 0).width;
		t.dispose();
		return result;
	}

	private String getQualifiedText(TypeNameMatch type) {
		return fFullyQualifySelection ? fLabelProvider
				.getFullyQualifiedText(type) : fLabelProvider
				.getQualifiedText(type);
	}

}
