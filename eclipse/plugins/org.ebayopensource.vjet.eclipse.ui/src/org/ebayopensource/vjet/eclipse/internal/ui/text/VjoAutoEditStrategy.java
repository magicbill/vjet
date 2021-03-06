/*******************************************************************************
 * Copyright (c) 2005-2011 eBay Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 *******************************************************************************/
/*******************************************************************************
 * Copyright (c) 2005, 2007 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 
 *******************************************************************************/
package org.ebayopensource.vjet.eclipse.internal.ui.text;

import java.util.regex.Pattern;

import org.ebayopensource.vjet.eclipse.core.VjetPlugin;
import org.ebayopensource.vjet.eclipse.ui.VjetPreferenceConstants;
import org.ebayopensource.vjet.eclipse.ui.VjetUIPlugin;
import org.ebayopensource.vjo.meta.VjoKeywords;
import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.dltk.mod.core.IScriptProject;
import org.eclipse.dltk.mod.javascript.internal.ui.text.JavascriptPartitionScanner;
import org.eclipse.dltk.mod.javascript.internal.ui.text.JsPreferenceInterpreter;
import org.eclipse.dltk.mod.javascript.internal.ui.text.Symbols;
import org.eclipse.dltk.mod.javascript.scriptdoc.IScanner;
import org.eclipse.dltk.mod.javascript.scriptdoc.JavaHeuristicScanner;
import org.eclipse.dltk.mod.javascript.scriptdoc.JavaIndenter;
import org.eclipse.dltk.mod.javascript.scriptdoc.PublicScanner;
import org.eclipse.dltk.mod.javascript.ui.text.IJavaScriptPartitions;
import org.eclipse.dltk.mod.ui.DLTKUIPlugin;
import org.eclipse.dltk.mod.ui.PreferenceConstants;
import org.eclipse.dltk.mod.ui.text.util.AutoEditUtils;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.DefaultIndentLineAutoEditStrategy;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.DocumentCommand;
import org.eclipse.jface.text.DocumentRewriteSession;
import org.eclipse.jface.text.DocumentRewriteSessionType;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.ITypedRegion;
import org.eclipse.jface.text.TextUtilities;
import org.eclipse.jface.text.rules.FastPartitioner;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.texteditor.ITextEditorExtension3;

/**
 * Auto indent strategy sensitive to brackets. Copied from
 * JavascriptAutoEditStrategy, extend to support vjo's special syntax
 */
public class VjoAutoEditStrategy extends DefaultIndentLineAutoEditStrategy {

	private static final String LINE_SEPARATOR = System
			.getProperty("line.separator");

	/** The line comment introducer. Value is "{@value} " */
	private static final String LINE_COMMENT = "//"; //$NON-NLS-1$

	private static final char FORWARD_SLASH = '/';

	// private static final String START_MULTILINE_COMMENT = "/**";
	//
	// private static final String END_MULTILINE_COMMENT = "*/";

	private static final char CONTINUE_MULTILINE_COMMENT = '*';

	// private static class CompilationUnitInfo {
	//
	// char[] buffer;
	// int delta;
	//
	// CompilationUnitInfo(char[] buffer, int delta) {
	// this.buffer = buffer;
	// this.delta = delta;
	// }
	// }

	private boolean fCloseBrace;
	// private boolean fCloseComment;
	private boolean fIsSmartMode;

	private String fPartitioning;
	final IScriptProject fProject;
	private static IScanner fgScanner = new PublicScanner(false, false, false,
			3, null, null, false);

	/**
	 * Creates a new Java auto indent strategy for the given document
	 * partitioning.
	 * 
	 * @param partitioning
	 *            the document partitioning
	 * @param project
	 *            the project to get formatting preferences from, or null to use
	 *            default preferences
	 */
	public VjoAutoEditStrategy(String partitioning, IScriptProject project) {
		fPartitioning = partitioning;
		fProject = project;

		// fix 2537
		this.prefs = new JsPreferenceInterpreter(VjetUIPlugin.getDefault()
				.getPreferenceStore());
	}

	private int getBracketCount(IDocument d, int startOffset, int endOffset,
			boolean ignoreCloseBrackets) throws BadLocationException {

		int bracketCount = 0;
		while (startOffset < endOffset) {
			char curr = d.getChar(startOffset);
			startOffset++;
			switch (curr) {
			case FORWARD_SLASH:
				if (startOffset < endOffset) {
					char next = d.getChar(startOffset);
					if (next == CONTINUE_MULTILINE_COMMENT) {
						// a comment starts, advance to the comment end
						startOffset = getMultiLineCommentEnd(d,
								startOffset + 1, endOffset);
					} else if (next == FORWARD_SLASH) {
						// '//'-comment: nothing to do anymore on this line
						startOffset = getLineCommentEnd(d, startOffset + 1,
								endOffset);
					}
				}
				break;
			case CONTINUE_MULTILINE_COMMENT:
				if (startOffset < endOffset) {
					char next = d.getChar(startOffset);
					if (next == FORWARD_SLASH) {
						// we have been in a comment: forget what we read before
						bracketCount = 0;
						startOffset++;
					}
				}
				break;
			case '{':
				bracketCount++;
				ignoreCloseBrackets = false;
				break;
			case '}':
				if (!ignoreCloseBrackets) {
					bracketCount--;
				}
				break;
			case '"':
			case '\'':
				startOffset = getStringEnd(d, startOffset, endOffset, curr);
				break;
			default:
			}
		}
		return bracketCount;
	}

	// ----------- bracket counting
	// ------------------------------------------------------

	private int getMultiLineCommentEnd(IDocument d, int offset, int endOffset)
			throws BadLocationException {

		while (offset < endOffset) {
			char curr = d.getChar(offset);
			offset++;
			if (curr == CONTINUE_MULTILINE_COMMENT) {
				if (offset < endOffset && d.getChar(offset) == FORWARD_SLASH) {
					return offset + 1;
				}
			}
		}
		return endOffset;
	}

	private int getLineCommentEnd(IDocument d, int offset, int endOffset)
			throws BadLocationException {

		while (offset < endOffset) {
			char curr = d.getChar(offset);
			offset++;
			if (curr == '\n') {
				return offset;
			}
		}
		return endOffset;
	}

	private String getIndentOfLine(IDocument d, int line)
			throws BadLocationException {
		if (line > -1) {
			int start = d.getLineOffset(line);
			int end = start + d.getLineLength(line) - 1;
			int whiteEnd = findEndOfWhiteSpace(d, start, end);
			return d.get(start, whiteEnd - start);
		} else {
			return ""; //$NON-NLS-1$
		}
	}

	private int getStringEnd(IDocument d, int offset, int endOffset, char ch)
			throws BadLocationException {
		while (offset < endOffset) {
			char curr = d.getChar(offset);
			offset++;
			if (curr == '\\') {
				// ignore escaped characters
				offset++;
			} else if (curr == ch) {
				return offset;
			}
		}
		return endOffset;
	}

	private void smartIndentAfterClosingBracket(IDocument d, DocumentCommand c) {
		if (c.offset == -1 || d.getLength() == 0)
			return;

		try {
			int p = (c.offset == d.getLength() ? c.offset - 1 : c.offset);
			int line = d.getLineOfOffset(p);
			int start = d.getLineOffset(line);
			int whiteend = findEndOfWhiteSpace(d, start, c.offset);

			JavaHeuristicScanner scanner = new JavaHeuristicScanner(d);
			VjoIndenter indenter = new VjoIndenter(d, scanner, fProject);

			// shift only when line does not contain any text up to the closing
			// bracket
			if (whiteend == c.offset) {
				// evaluate the line with the opening bracket that matches out
				// closing bracket
				int reference = indenter.findReferencePosition(c.offset, false,
						true, false, false);

				// Add by Oliver. Begin. 2009-10-31. Because the reference value
				// is wrong, I recompute the match "{" line information.

				// TODO indenter.findReferencePosition is incorrect, need to
				// investigate in the future.
				String beforeCursor = d.get(0, c.offset).trim();
				int lastMatch = beforeCursor.lastIndexOf("{");
				if (lastMatch > reference) {
					reference = lastMatch;
				}
				// Add by Oliver. End.

				int indLine = d.getLineOfOffset(reference);
				if (indLine != -1 && indLine != line) {
					// take the indent of the found line
					StringBuffer replaceText = new StringBuffer(
							getIndentOfLine(d, indLine));
					// add the rest of the current line including the just added
					// close bracket
					replaceText.append(d.get(whiteend, c.offset - whiteend));
					replaceText.append(c.text);
					// modify document command
					c.length += c.offset - start;
					c.offset = start;
					c.text = replaceText.toString();
				}
			}
		} catch (BadLocationException e) {
			DLTKUIPlugin.log(e);
		}
	}

	private void smartIndentAfterOpeningBracket(IDocument d, DocumentCommand c) {
		if (c.offset < 1 || d.getLength() == 0)
			return;

		JavaHeuristicScanner scanner = new JavaHeuristicScanner(d);

		int p = (c.offset == d.getLength() ? c.offset - 1 : c.offset);

		try {
			// current line
			int line = d.getLineOfOffset(p);
			int lineOffset = d.getLineOffset(line);

			// make sure we don't have any leading comments etc.
			if (d.get(lineOffset, p - lineOffset).trim().length() != 0)
				return;

			// line of last Java code
			int pos = scanner.findNonWhitespaceBackward(p,
					JavaHeuristicScanner.UNBOUND);
			if (pos == -1)
				return;
			int lastLine = d.getLineOfOffset(pos);

			// only shift if the last java line is further up and is a braceless
			// block candidate
			if (lastLine < line) {

				VjoIndenter indenter = new VjoIndenter(d, scanner, fProject);
				StringBuffer indent = indenter.computeIndentation(p, true);
				String toDelete = d.get(lineOffset, c.offset - lineOffset);
				if (indent != null && !indent.toString().equals(toDelete)) {
					c.text = indent.append(c.text).toString();
					c.length += c.offset - lineOffset;
					c.offset = lineOffset;
				}
			}

		} catch (BadLocationException e) {
			DLTKUIPlugin.log(e);
		}

	}

	private void smartIndentAfterNewLine(IDocument d, DocumentCommand c) {
		int indexOf = c.text.indexOf('\t');
		if (indexOf != -1) {
			c.text = c.text.substring(0, indexOf);
		}
		indexOf = c.text.indexOf(' ');
		if (indexOf != -1) {
			c.text = c.text.substring(0, indexOf);
		}
		JavaHeuristicScanner scanner = new JavaHeuristicScanner(d);
		VjoIndenter indenter = new VjoIndenter(d, scanner, fProject);

		// Add by oliver begin. 2009-10-10. Recalculate the offset value, if the
		// current offset lies at the new line. We will use the nearest line's
		// indentation as the new line indentation.
//		int offset = c.offset;
//		if (offset < d.getLength()) {
//			try {
//				if (d.get(0, offset).endsWith(LINE_SEPARATOR)
//						|| d.get(0, offset).endsWith("\t")) {
//					String beforeCursor = d.get(0, offset).trim();
//					offset = beforeCursor.length();
//				}
//			} catch (BadLocationException e) {
//				DLTKUIPlugin.log(e);
//			}
//		}
		// Add by oliver end.

		StringBuffer indent = indenter.computeIndentation(c.offset);
		if (indent == null)
			indent = new StringBuffer();

		int docLength = d.getLength();
		if (c.offset == -1 || docLength == 0)
			return;

		try {
			int p = (c.offset == docLength ? c.offset - 1 : c.offset);
			int line = d.getLineOfOffset(p);

			StringBuffer buf = new StringBuffer(c.text + indent);

			IRegion reg = d.getLineInformation(line);
			int lineEnd = reg.getOffset() + reg.getLength();

			int contentStart = findEndOfWhiteSpace(d, c.offset, lineEnd);
			c.length = Math.max(contentStart - c.offset, 0);

			int start = reg.getOffset();
			ITypedRegion region = TextUtilities.getPartition(d, fPartitioning,
					start, true);
			if (IJavaScriptPartitions.JS_DOC.equals(region.getType()))
				start = d.getLineInformationOfOffset(region.getOffset())
						.getOffset();

			// insert closing brace on new line after an unclosed opening brace
			if (closeBrace() && !isBlockBalanced(d)
					&&( isAfterOpenBrace(d, c.offset) || !isClosedBrace(d, c.offset))) {
				// Comment by Oliver. 2009-09-29. This is an extra indentation.
				// buf.append('\t');
				c.caretOffset = c.offset + buf.length();
				c.shiftsCaret = false;

				// copy old content of line behind insertion point to new line
				// unless we think we are inserting an anonymous type definition
				buf.append('}');
				copyContent(d, c, buf, lineEnd, contentStart);

				appendReference(d, c, indenter, buf, lineEnd, start);
				
			}

			// deleted below code, use VjoDocIndentStrategy instead.
			// continue multi-line comment
			// else if (isContinueComment(d, c.offset) && closeComment()) {
			// copyContent(d, c, buf, lineEnd, contentStart);
			// buf.append(CONTINUE_MULTILINE_COMMENT);
			// c.caretOffset = c.offset + buf.length();
			// c.shiftsCaret = false;
			// }
			// // close multi-line and single-line comment
			// else if (getCountMultiLineComments(d) > 0 && closeComment()) {
			// // Modify by Oliver. 2009-03-27. Before the offset of line end
			// // is actual position of this line. Now only use the cursor
			// // position.
			// copyContent(d, c, buf, c.offset, contentStart);
			// buf.append(" " + CONTINUE_MULTILINE_COMMENT);
			// c.caretOffset = c.offset + buf.length();
			// appendReference(d, c, indenter, buf, lineEnd, start);
			// buf.append(" " + END_MULTILINE_COMMENT);
			//
			// // Add by Oliver. 2009-03-27. Add new line after the end of
			// // comment block.
			// buf.append(TextUtilities.getDefaultLineDelimiter(d));
			//
			// c.shiftsCaret = false;
			// }
			// insert extra line upon new line between two braces
			else if (c.offset > start && contentStart < lineEnd
					&& d.getChar(contentStart) == '}') {
				int firstCharPos = scanner.findNonWhitespaceBackward(
						c.offset - 1, start);
				if (firstCharPos != JavaHeuristicScanner.NOT_FOUND
						&& d.getChar(firstCharPos) == '{') {
					c.caretOffset = c.offset + buf.length();
					c.shiftsCaret = false;
					appendReference(d, c, indenter, buf, lineEnd, start);
				}
			}
			c.text = buf.toString();

		} catch (BadLocationException e) {
			DLTKUIPlugin.log(e);
		}
	}

	private boolean isAfterOpenBrace(IDocument d, int offset) {
		while(true){
			
			try {
				char ch = d.getChar(offset);
				if(ch=='{'){
					return true;
				}else if(!Character.isWhitespace(ch)){
					return false;
				}
				offset--;
			} catch (BadLocationException e) {
				return false;
			}
		}

	}

	private void copyContent(IDocument d, DocumentCommand c, StringBuffer buf,
			int lineEnd, int contentStart) throws BadLocationException {
		if (c.offset == 0
				|| !((computeAnonymousPosition(d, c.offset - 1, fPartitioning,
						lineEnd) != -1) || isBraceInParenthis(d, c.offset))) {
			if (lineEnd - contentStart > 0) {
				c.length = lineEnd - c.offset;
				buf.append(d.get(contentStart, lineEnd - contentStart)
						.toCharArray());
			}
		}
	}

	private boolean isBraceInParenthis(IDocument d, int offset) {
		String sm = d.get();
		String lsm = sm.substring(offset, sm.length());
		if (lsm.length() == 0) {
			return false;
		}
		char c = lsm.charAt(0);
		return c == ')' || c == '}';
	}

	private void appendReference(IDocument d, DocumentCommand c,
			VjoIndenter indenter, StringBuffer buf, int lineEnd, int start)
			throws BadLocationException {
		StringBuffer reference = null;
		int nonWS = findEndOfWhiteSpace(d, start, lineEnd);
		if (nonWS < c.offset && d.getChar(nonWS) == '{')
			reference = new StringBuffer(d.get(start, nonWS - start));
		else
			reference = indenter.getReferenceIndentation(c.offset);

		buf.append(TextUtilities.getDefaultLineDelimiter(d));

		if (reference != null)
			buf.append(reference);
	}

	private boolean isClosedBrace(IDocument d, int offset) {
		int start, startCurlyBrace, end, endBraceCurly;
		String sm = d.get();
		String lsm = sm.substring(offset, sm.length());

		// Don't consider the situation that cursor is at the end of .props or
		// .protos.
		String tsm = sm.substring(0, offset).trim();
		if (tsm.endsWith("({")) {
			tsm = tsm.substring(0, tsm.length() - 2).trim();
			if (tsm.endsWith(VjoKeywords.PROPS)
					|| tsm.endsWith(VjoKeywords.PROTOS)) {
				return true;
			}
		}

		// Add by Oliver. for bug:4901
		try {
			IRegion line = d.getLineInformationOfOffset(offset);
			// modify by patrick, fix for JIRA task VFOUR-199
			int position = lsm.indexOf(LINE_SEPARATOR);
			String afterOffset = position != -1 ? sm.substring(offset, offset
					+ position) : lsm;
			// end modify
			if (afterOffset.trim().startsWith("})")) {
				return true;
			}
		} catch (BadLocationException e1) {
			VjetUIPlugin.log(e1);
		}

		// gets the block and counts braces
		start = sm.substring(0, offset).lastIndexOf("({");
		// Add by Oliver begin,2009-10-12. if inner type is above with this
		// {, the range needs to end with "})" of inner type.
		startCurlyBrace = sm.substring(0, offset).lastIndexOf("})");
		// if (startCurlyBrace != -1 && startCurlyBrace > start) {
		// start = Math.max(startCurlyBrace, start);
		// }
		// Add by Oliver end.

		// Add by Oliver begin,2009-10-12. if inner type is following with this
		// {, the range needs to end with "({" of inner type.
		endBraceCurly = sm.indexOf("({", offset);

		// end = sm.indexOf("})", offset);
		// Add by Oliver. Bug:8748
		end = getLegalEnd(sm, offset);
		if (endBraceCurly != -1 && endBraceCurly > start) {
			end = Math.min(endBraceCurly, end);
		}
		// Add by Oliver end.

		if (start < 0 || end < 0) {
			// if the next char is "}" will return true, otherwise return false
			end = lsm.indexOf("}");
			if (end < 0)
				return false;
			return !(lsm.substring(0, end).trim().length() > 0);

		} else {
			// counts braces in the block
			try {
				int count = getBracketCount(d, start + "({".length(), end,
						false);
				if (count <= 0)
					return true;

			} catch (BadLocationException e) {
				VjetPlugin.getDefault().getLog().log(
						new Status(IStatus.ERROR, VjetPlugin.PLUGIN_ID,
								IStatus.ERROR,
								"Error during counting of braces", e));
			}
		}

		return false;
		// String s = lsm.substring(end);
		//		
		// int start = sm.lastIndexOf("function ", offset);
		// int lastOpen = sm.lastIndexOf("{", start);
		// if (lastOpen == -1)
		// lastOpen = 0;
		// int lastClosed = sm.lastIndexOf("}", start);
		// if (lastClosed == -1)
		// lastClosed = 0;
		// while (lastOpen > lastClosed) {
		// start = sm.lastIndexOf("function ", lastOpen);
		// lastOpen = sm.lastIndexOf("{", start);
		// if (lastOpen == -1)
		// lastOpen = 0;
		// lastClosed = sm.lastIndexOf("}", start);
		// if (lastClosed == -1)
		// lastClosed = 0;
		//
		// }
		//
		// int end = sm.indexOf("function ", offset);
		// if (end == -1) {
		// end = sm.length();
		// } else {
		// lastOpen = sm.lastIndexOf("{", end);
		// if (lastOpen == -1)
		// lastOpen = 0;
		// lastClosed = sm.lastIndexOf("}", end);
		// if (lastClosed == -1)
		// lastClosed = 0;
		// while (lastOpen > lastClosed) {
		// int end2 = sm.indexOf("function ", lastOpen);
		// if (end == end2)
		// break;
		// end = end2;
		// lastOpen = sm.lastIndexOf("{", end);
		// if (lastOpen == -1)
		// lastOpen = 0;
		// lastClosed = sm.lastIndexOf("}", end);
		// if (lastClosed == -1)
		// lastClosed = 0;
		//
		// }
		// }
		// int level = 0;
		// boolean qm = false;
		// char charp = 0;
		// for (int a = start; a < end; a++) {
		// char charAt = sm.charAt(a);
		// if (!qm) {
		// if (charAt == '{')
		// level++;
		// if (charAt == '}')
		// level--;
		// }
		// if (charAt == '"' || charAt == '\'') {
		// if (charp != '\\')
		// qm = !qm;
		// }
		// charp = charAt;
		// }
		// return level <= 0;
	}

	// private boolean isContinueComment(IDocument d, int offset) {
	// String sm = d.get();
	// IRegion pos = null;
	// try {
	// pos = d.getLineInformationOfOffset(offset);
	// } catch (BadLocationException e) {
	// // TODO Auto-generated catch block
	// e.printStackTrace();
	// }
	// // gets position of start of the line where cursor present
	// if (pos == null)
	// return false;
	//
	// sm = sm.substring(pos.getOffset(), pos.getOffset() + pos.getLength())
	// .trim();
	// if (sm.length() < 0 || sm.startsWith(END_MULTILINE_COMMENT))
	// return false;
	// if (sm.startsWith(CONTINUE_MULTILINE_COMMENT + ""))
	// return true;
	//
	// return false;
	// }
	//
	// private int getCountMultiLineComments(IDocument d) {
	// int count = 0;
	// int offset = 0;
	// String sm = d.get();
	//
	// while (offset < sm.length() - START_MULTILINE_COMMENT.length() - 1) {
	// if (sm.substring(offset, offset + START_MULTILINE_COMMENT.length())
	// .equals(START_MULTILINE_COMMENT))
	// count++;
	// else if (sm.substring(offset,
	// offset + END_MULTILINE_COMMENT.length()).equals(
	// END_MULTILINE_COMMENT))
	// count--;
	// offset++;
	// }
	//
	// return count;
	// }
	public int getLegalEnd(String sm, int offset) {
		int end = sm.indexOf("})", offset);
		if (end != -1 && end < sm.length()) {
			if (sm.substring(end).startsWith("});")) {
				end = getLegalEnd(sm, end + "});".length());
			} else {
				return end;
			}
		}
		return end;
	}

	/**
	 * Computes an insert position for an opening brace if <code>offset</code>
	 * maps to a position in <code>document</code> with a expression in
	 * parenthesis that will take a block after the closing parenthesis.
	 * 
	 * @param document
	 *            the document being modified
	 * @param offset
	 *            the offset of the caret position, relative to the line start.
	 * @param partitioning
	 *            the document partitioning
	 * @param max
	 *            the max position
	 * @return an insert position relative to the line start if
	 *         <code>line</code> contains a parenthesized expression that can be
	 *         followed by a block, -1 otherwise
	 */
	private static int computeAnonymousPosition(IDocument document, int offset,
			String partitioning, int max) {
		// find the opening parenthesis for every closing parenthesis on the
		// current line after offset
		// return the position behind the closing parenthesis if it looks like a
		// method declaration
		// or an expression for an if, while, for, catch statement

		JavaHeuristicScanner scanner = new JavaHeuristicScanner(document);
		int pos = offset;
		int length = max;
		int scanTo = scanner.scanForward(pos, length, '}');
		if (scanTo == -1)
			scanTo = length;

		int closingParen = findClosingParenToLeft(scanner, pos) - 1;

		while (true) {
			int startScan = closingParen + 1;
			closingParen = scanner.scanForward(startScan, scanTo, ')');
			if (closingParen == -1)
				break;

			int openingParen = scanner.findOpeningPeer(closingParen - 1, '(',
					')');

			// no way an expression at the beginning of the document can mean
			// anything
			if (openingParen < 1)
				break;

			// only select insert positions for parenthesis currently embracing
			// the caret
			if (openingParen > pos)
				continue;

			if (looksLikeAnonymousClassDef(document, partitioning, scanner,
					openingParen - 1))
				return closingParen + 1;

		}

		return -1;
	}

	/**
	 * Finds a closing parenthesis to the left of <code>position</code> in
	 * document, where that parenthesis is only separated by whitespace from
	 * <code>position</code>. If no such parenthesis can be found,
	 * <code>position</code> is returned.
	 * 
	 * @param scanner
	 *            the java heuristic scanner set up on the document
	 * @param position
	 *            the first character position in <code>document</code> to be
	 *            considered
	 * @return the position of a closing parenthesis left to
	 *         <code>position</code> separated only by whitespace, or
	 *         <code>position</code> if no parenthesis can be found
	 */
	private static int findClosingParenToLeft(JavaHeuristicScanner scanner,
			int position) {
		if (position < 1)
			return position;

		if (scanner.previousToken(position - 1, JavaHeuristicScanner.UNBOUND) == Symbols.TokenRPAREN)
			return scanner.getPosition() + 1;
		return position;
	}

	/**
	 * Checks whether the content of <code>document</code> in the range (
	 * <code>offset</code>, <code>length</code>) contains the <code>new</code>
	 * keyword.
	 * 
	 * @param document
	 *            the document being modified
	 * @param offset
	 *            the first character position in <code>document</code> to be
	 *            considered
	 * @param length
	 *            the length of the character range to be considered
	 * @param partitioning
	 *            the document partitioning
	 * @return <code>true</code> if the specified character range contains a
	 *         <code>new</code> keyword, <code>false</code> otherwise.
	 */
	private static boolean isNewMatch(IDocument document, int offset,
			int length, String partitioning) {
		Assert.isTrue(length >= 0);
		Assert.isTrue(offset >= 0);
		Assert.isTrue(offset + length < document.getLength() + 1);

		try {
			String text = document.get(offset, length);
			int pos = text.indexOf("new"); //$NON-NLS-1$

			while (pos != -1
					&& !isDefaultPartition(document, pos + offset, partitioning))
				pos = text.indexOf("new", pos + 2); //$NON-NLS-1$

			if (pos < 0)
				return false;

			if (pos != 0
					&& Character.isJavaIdentifierPart(text.charAt(pos - 1)))
				return false;

			if (pos + 3 < length
					&& Character.isJavaIdentifierPart(text.charAt(pos + 3)))
				return false;

			return true;

		} catch (BadLocationException e) {
		}
		return false;
	}

	/**
	 * Checks whether the content of <code>document</code> at
	 * <code>position</code> looks like an anonymous class definition.
	 * <code>position</code> must be to the left of the opening parenthesis of
	 * the definition's parameter list.
	 * 
	 * @param document
	 *            the document being modified
	 * @param position
	 *            the first character position in <code>document</code> to be
	 *            considered
	 * @param partitioning
	 *            the document partitioning
	 * @return <code>true</code> if the content of <code>document</code> looks
	 *         like an anonymous class definition, <code>false</code> otherwise
	 */
	private static boolean looksLikeAnonymousClassDef(IDocument document,
			String partitioning, JavaHeuristicScanner scanner, int position) {
		int previousCommaParenEqual = scanner.scanBackward(position - 1,
				JavaHeuristicScanner.UNBOUND, new char[] { ',', '(', '=' });
		if (previousCommaParenEqual == -1
				|| position < previousCommaParenEqual + 5) // 2 for borders, 3
			// for "new"
			return false;

		if (isNewMatch(document, previousCommaParenEqual + 1, position
				- previousCommaParenEqual - 2, partitioning))
			return true;

		return false;
	}

	/**
	 * Checks whether <code>position</code> resides in a default (Java)
	 * partition of <code>document</code>.
	 * 
	 * @param document
	 *            the document being modified
	 * @param position
	 *            the position to be checked
	 * @param partitioning
	 *            the document partitioning
	 * @return <code>true</code> if <code>position</code> is in the default
	 *         partition of <code>document</code>, <code>false</code> otherwise
	 */
	private static boolean isDefaultPartition(IDocument document, int position,
			String partitioning) {
		Assert.isTrue(position >= 0);
		Assert.isTrue(position <= document.getLength());

		try {
			ITypedRegion region = TextUtilities.getPartition(document,
					partitioning, position, false);
			return region.getType().equals(IDocument.DEFAULT_CONTENT_TYPE);

		} catch (BadLocationException e) {
		}

		return false;
	}

	/**
	 * Installs a java partitioner with <code>document</code>.
	 * 
	 * @param document
	 *            the document
	 */
	private static void installJavaStuff(Document document) {
		String[] types = new String[] { IJavaScriptPartitions.JS_DOC,
				IJavaScriptPartitions.JS_SINGLE_COMMENT,
				IJavaScriptPartitions.JS_MULTI_COMMENT,
				IJavaScriptPartitions.JS_PARTITIONING,
				IJavaScriptPartitions.JS_STRING, IDocument.DEFAULT_CONTENT_TYPE };
		FastPartitioner partitioner = new FastPartitioner(
				new JavascriptPartitionScanner(), types);
		partitioner.connect(document);
		document.setDocumentPartitioner(IJavaScriptPartitions.JS_PARTITIONING,
				partitioner);
	}

	/**
	 * Installs a java partitioner with <code>document</code>.
	 * 
	 * @param document
	 *            the document
	 */
	private static void removeJavaStuff(Document document) {
		document.setDocumentPartitioner(IJavaScriptPartitions.JS_PARTITIONING,
				null);
	}

	private void smartPaste(IDocument document, DocumentCommand command) {
		int newOffset = command.offset;
		int newLength = command.length;
		String newText = command.text;

		try {
			JavaHeuristicScanner scanner = new JavaHeuristicScanner(document);
			VjoIndenter indenter = new VjoIndenter(document, scanner, fProject);
			int offset = newOffset;

			// reference position to get the indent from
			int refOffset = indenter.findReferencePosition(offset);
			if (refOffset == JavaHeuristicScanner.NOT_FOUND)
				return;
			int peerOffset = getPeerPosition(document, command);
			peerOffset = indenter.findReferencePosition(peerOffset);
			refOffset = Math.min(refOffset, peerOffset);

			// eat any WS before the insertion to the beginning of the line
			int firstLine = 1; // don't format the first line per default, as
			// it has other content before it
			IRegion line = document.getLineInformationOfOffset(offset);
			String notSelected = document.get(line.getOffset(), offset
					- line.getOffset());
			if (notSelected.trim().length() == 0) {
				newLength += notSelected.length();
				newOffset = line.getOffset();
				firstLine = 0;
			}

			// prefix: the part we need for formatting but won't paste
			IRegion refLine = document.getLineInformationOfOffset(refOffset);
			String prefix = document.get(refLine.getOffset(), newOffset
					- refLine.getOffset());

			/**
			 * modify by kevin, 2009-10-31. the modification(2009-10-09) break
			 * some features, refine to fix bug 2537
			 */
			// Add by Oliver. 2009-10-09. We remove the new line if we paste the
			// text after new line. Ignore the scenario that last line end with
			// comma.
			if (prefix.endsWith(LINE_SEPARATOR)
					&& !prefix.endsWith("{" + LINE_SEPARATOR)
					&& !prefix.endsWith(";" + LINE_SEPARATOR)
					&& !prefix.trim().endsWith(",")) {
				offset = document.get(0, offset).trim().length();
				refLine = document.getLineInformationOfOffset(offset);
				prefix = document.get(refLine.getOffset(), offset
						- refLine.getOffset());
			}

			// handle the indentation computation inside a temporary document
			Document temp = new Document(prefix + newText);
			DocumentRewriteSession session = temp
					.startRewriteSession(DocumentRewriteSessionType.STRICTLY_SEQUENTIAL);
			scanner = new JavaHeuristicScanner(temp);
			indenter = new VjoIndenter(temp, scanner, fProject);
			installJavaStuff(temp);

			// indent the first and second line
			// compute the relative indentation difference from the second line
			// (as the first might be partially selected) and use the value to
			// indent all other lines.
			boolean isIndentDetected = false;
			StringBuffer addition = new StringBuffer();
			int insertLength = 0;
			int first = document.computeNumberOfLines(prefix) + firstLine; // don't
			// format
			// first
			// line
			int lines = temp.getNumberOfLines();
			int tabLength = getVisualTabLengthPreference();
			boolean changed = false;
			for (int l = first; l < lines; l++) { // we don't change the
				// number of lines while
				// adding indents

				IRegion r = temp.getLineInformation(l);
				int lineOffset = r.getOffset();
				int lineLength = r.getLength();

				if (lineLength == 0) // don't modify empty lines
					continue;

				if (!isIndentDetected) {

					// indent the first pasted line
					String current = getCurrentIndent(temp, l);
					StringBuffer correct = indenter
							.computeIndentation(lineOffset);
					if (correct == null)
						return; // bail out

					insertLength = subtractIndent(correct, current, addition,
							tabLength);
					if (l != first
							&& temp.get(lineOffset, lineLength).trim().length() != 0) {
						isIndentDetected = true;
						if (insertLength == 0) {
							// no adjustment needed, bail out
							if (firstLine == 0) {
								// but we still need to adjust the first line
								command.offset = newOffset;
								command.length = newLength;
								if (changed)
									break; // still need to get the leading
								// indent of the first line
							}
							return;
						}
						removeJavaStuff(temp);
					} else {
						changed = insertLength != 0;
					}
				}

				// relatively indent all pasted lines
				if (insertLength > 0)
					addIndent(temp, l, addition, tabLength);
				else if (insertLength < 0)
					cutIndent(temp, l, -insertLength, tabLength);

			}

			temp.stopRewriteSession(session);
			newText = temp.get(prefix.length(), temp.getLength()
					- prefix.length());

			command.offset = newOffset;
			command.length = newLength;
			command.text = newText;

		} catch (BadLocationException e) {
			DLTKUIPlugin.log(e);
		}

	}

	/**
	 * Returns the indentation of the line <code>line</code> in
	 * <code>document</code>. The returned string may contain pairs of leading
	 * slashes that are considered part of the indentation. The space before the
	 * asterisk in a javadoc-like comment is not considered part of the
	 * indentation.
	 * 
	 * @param document
	 *            the document
	 * @param line
	 *            the line
	 * @return the indentation of <code>line</code> in <code>document</code>
	 * @throws BadLocationException
	 *             if the document is changed concurrently
	 */
	private static String getCurrentIndent(Document document, int line)
			throws BadLocationException {
		IRegion region = document.getLineInformation(line);
		int from = region.getOffset();
		int endOffset = region.getOffset() + region.getLength();

		// go behind line comments
		int to = from;
		while (to < endOffset - 2 && document.get(to, 2).equals(LINE_COMMENT))
			to += 2;

		while (to < endOffset) {
			char ch = document.getChar(to);
			if (!Character.isWhitespace(ch))
				break;
			to++;
		}

		// don't count the space before javadoc like, asterisk-style comment
		// lines
		if (to > from && to < endOffset - 1
				&& document.get(to - 1, 2).equals(" *")) { //$NON-NLS-1$
			String type = TextUtilities.getContentType(document,
					IJavaScriptPartitions.JS_PARTITIONING, to, true);
			if (type.equals(IJavaScriptPartitions.JS_DOC)
					|| type.equals(IJavaScriptPartitions.JS_SINGLE_COMMENT)
					|| type.equals(IJavaScriptPartitions.JS_MULTI_COMMENT))
				to--;
		}

		return document.get(from, to - from);
	}

	/**
	 * Computes the difference of two indentations and returns the difference in
	 * length of current and correct. If the return value is positive,
	 * <code>addition</code> is initialized with a substring of that length of
	 * <code>correct</code>.
	 * 
	 * @param correct
	 *            the correct indentation
	 * @param current
	 *            the current indentation (might contain non-whitespace)
	 * @param difference
	 *            a string buffer - if the return value is positive, it will be
	 *            cleared and set to the substring of <code>current</code> of
	 *            that length
	 * @param tabLength
	 *            the length of a tab
	 * @return the difference in length of <code>correct</code> and
	 *         <code>current</code>
	 */
	private int subtractIndent(CharSequence correct, CharSequence current,
			StringBuffer difference, int tabLength) {
		int c1 = computeVisualLength(correct, tabLength);
		int c2 = computeVisualLength(current, tabLength);
		int diff = c1 - c2;
		if (diff <= 0)
			return diff;

		difference.setLength(0);
		int len = 0, i = 0;
		while (len < diff) {
			char c = correct.charAt(i++);
			difference.append(c);
			len += computeVisualLength(c, tabLength);
		}

		return diff;
	}

	/**
	 * Indents line <code>line</code> in <code>document</code> with
	 * <code>indent</code>. Leaves leading comment signs alone.
	 * 
	 * @param document
	 *            the document
	 * @param line
	 *            the line
	 * @param indent
	 *            the indentation to insert
	 * @param tabLength
	 *            the length of a tab
	 * @throws BadLocationException
	 *             on concurrent document modification
	 */
	private void addIndent(Document document, int line, CharSequence indent,
			int tabLength) throws BadLocationException {
		IRegion region = document.getLineInformation(line);
		int insert = region.getOffset();
		int endOffset = region.getOffset() + region.getLength();

		// Compute insert after all leading line comment markers
		int newInsert = insert;
		while (newInsert < endOffset - 2
				&& document.get(newInsert, 2).equals(LINE_COMMENT))
			newInsert += 2;

		// Heuristic to check whether it is commented code or just a comment
		if (newInsert > insert) {
			int whitespaceCount = 0;
			int i = newInsert;
			while (i < endOffset - 1) {
				char ch = document.get(i, 1).charAt(0);
				if (!Character.isWhitespace(ch))
					break;
				whitespaceCount = whitespaceCount
						+ computeVisualLength(ch, tabLength);
				i++;
			}

			if (whitespaceCount != 0 && whitespaceCount >= 4)
				insert = newInsert;
		}

		// Insert indent
		document.replace(insert, 0, indent.toString());
	}

	/**
	 * Cuts the visual equivalent of <code>toDelete</code> characters out of the
	 * indentation of line <code>line</code> in <code>document</code>. Leaves
	 * leading comment signs alone.
	 * 
	 * @param document
	 *            the document
	 * @param line
	 *            the line
	 * @param toDelete
	 *            the number of space equivalents to delete
	 * @param tabLength
	 *            the length of a tab
	 * @throws BadLocationException
	 *             on concurrent document modification
	 */
	private void cutIndent(Document document, int line, int toDelete,
			int tabLength) throws BadLocationException {
		IRegion region = document.getLineInformation(line);
		int from = region.getOffset();
		int endOffset = region.getOffset() + region.getLength();

		// go behind line comments
		while (from < endOffset - 2
				&& document.get(from, 2).equals(LINE_COMMENT))
			from += 2;

		int to = from;
		while (toDelete > 0 && to < endOffset) {
			char ch = document.getChar(to);
			if (!Character.isWhitespace(ch))
				break;
			toDelete -= computeVisualLength(ch, tabLength);
			if (toDelete >= 0)
				to++;
			else
				break;
		}

		document.replace(from, to - from, ""); //$NON-NLS-1$
	}

	/**
	 * Returns the visual length of a given <code>CharSequence</code> taking
	 * into account the visual tabulator length.
	 * 
	 * @param seq
	 *            the string to measure
	 * @param tabLength
	 *            the length of a tab
	 * @return the visual length of <code>seq</code>
	 */
	private int computeVisualLength(CharSequence seq, int tabLength) {
		int size = 0;

		for (int i = 0; i < seq.length(); i++) {
			char ch = seq.charAt(i);
			if (ch == '\t') {
				if (tabLength != 0)
					size += tabLength - size % tabLength;
				// else: size stays the same
			} else {
				size++;
			}
		}
		return size;
	}

	/**
	 * Returns the visual length of a given character taking into account the
	 * visual tabulator length.
	 * 
	 * @param ch
	 *            the character to measure
	 * @param tabLength
	 *            the length of a tab
	 * @return the visual length of <code>ch</code>
	 */
	private int computeVisualLength(char ch, int tabLength) {
		if (ch == '\t')
			return tabLength;
		else
			return 1;
	}

	/**
	 * The preference setting for the visual tabulator display.
	 * 
	 * @return the number of spaces displayed for a tabulator in the editor
	 */
	private int getVisualTabLengthPreference() {
		return 4;
	}

	private int getPeerPosition(IDocument document, DocumentCommand command) {
		if (document.getLength() == 0)
			return 0;
		/*
		 * Search for scope closers in the pasted text and find their opening
		 * peers in the document.
		 */
		Document pasted = new Document(command.text);
		installJavaStuff(pasted);
		int firstPeer = command.offset;

		JavaHeuristicScanner pScanner = new JavaHeuristicScanner(pasted);
		JavaHeuristicScanner dScanner = new JavaHeuristicScanner(document);

		// add scope relevant after context to peer search
		int afterToken = dScanner.nextToken(command.offset + command.length,
				JavaHeuristicScanner.UNBOUND);
		try {
			switch (afterToken) {
			case Symbols.TokenRBRACE:
				pasted.replace(pasted.getLength(), 0, "}"); //$NON-NLS-1$
				break;
			case Symbols.TokenRPAREN:
				pasted.replace(pasted.getLength(), 0, ")"); //$NON-NLS-1$
				break;
			case Symbols.TokenRBRACKET:
				pasted.replace(pasted.getLength(), 0, "]"); //$NON-NLS-1$
				break;
			}
		} catch (BadLocationException e) {
			// cannot happen
			Assert.isTrue(false);
		}

		int pPos = 0; // paste text position (increasing from 0)
		int dPos = Math.max(0, command.offset - 1); // document position
		// (decreasing from paste
		// offset)
		while (true) {
			int token = pScanner.nextToken(pPos, JavaHeuristicScanner.UNBOUND);
			pPos = pScanner.getPosition();
			switch (token) {
			case Symbols.TokenLBRACE:
			case Symbols.TokenLBRACKET:
			case Symbols.TokenLPAREN:
				pPos = skipScope(pScanner, pPos, token);
				if (pPos == JavaHeuristicScanner.NOT_FOUND)
					return firstPeer;
				break; // closed scope -> keep searching
			case Symbols.TokenRBRACE:
				int peer = dScanner.findOpeningPeer(dPos, '{', '}');
				dPos = peer - 1;
				if (peer == JavaHeuristicScanner.NOT_FOUND)
					return firstPeer;
				firstPeer = peer;
				break; // keep searching
			case Symbols.TokenRBRACKET:
				peer = dScanner.findOpeningPeer(dPos, '[', ']');
				dPos = peer - 1;
				if (peer == JavaHeuristicScanner.NOT_FOUND)
					return firstPeer;
				firstPeer = peer;
				break; // keep searching
			case Symbols.TokenRPAREN:
				peer = dScanner.findOpeningPeer(dPos, '(', ')');
				dPos = peer - 1;
				if (peer == JavaHeuristicScanner.NOT_FOUND)
					return firstPeer;
				firstPeer = peer;
				break; // keep searching
			case Symbols.TokenCASE:
			case Symbols.TokenDEFAULT:
				VjoIndenter indenter = new VjoIndenter(document, dScanner,
						fProject);
				peer = indenter.findReferencePosition(dPos, false, false,
						false, true);
				if (peer == JavaHeuristicScanner.NOT_FOUND)
					return firstPeer;
				firstPeer = peer;
				break; // keep searching

			case Symbols.TokenEOF:
				return firstPeer;
			default:
				// keep searching
			}
		}
	}

	/**
	 * Skips the scope opened by <code>token</code> in <code>document</code>,
	 * returns either the position of the
	 * 
	 * @param pos
	 * @param token
	 * @return the position after the scope
	 */
	private static int skipScope(JavaHeuristicScanner scanner, int pos,
			int token) {
		int openToken = token;
		int closeToken;
		switch (token) {
		case Symbols.TokenLPAREN:
			closeToken = Symbols.TokenRPAREN;
			break;
		case Symbols.TokenLBRACKET:
			closeToken = Symbols.TokenRBRACKET;
			break;
		case Symbols.TokenLBRACE:
			closeToken = Symbols.TokenRBRACE;
			break;
		default:
			Assert.isTrue(false);
			return -1; // dummy
		}

		int depth = 1;
		int p = pos;

		while (true) {
			int tok = scanner.nextToken(p, JavaHeuristicScanner.UNBOUND);
			p = scanner.getPosition();

			if (tok == openToken) {
				depth++;
			} else if (tok == closeToken) {
				depth--;
				if (depth == 0)
					return p + 1;
			} else if (tok == Symbols.TokenEOF) {
				return JavaHeuristicScanner.NOT_FOUND;
			}
		}
	}

	private boolean isLineDelimiter(IDocument document, String text) {
		String[] delimiters = document.getLegalLineDelimiters();
		if (delimiters != null)
			return (TextUtilities.startsWith(delimiters, text) > -1)
					&& text.trim().length() == 0;
		return false;
	}

	/**
	 * Processes command in work with brackets, strings, etc
	 * 
	 * @param d
	 * @param c
	 */
	JsPreferenceInterpreter prefs;

	private void autoClose(IDocument d, DocumentCommand c) {
		if (c.offset == -1)
			return;
		try {
			if (d.getChar(c.offset - 1) == '\\')
				return;
		} catch (BadLocationException e1) {
		}
		if ('\"' == c.text.charAt(0) && !prefs.closeStrings())
			return;
		if ('\'' == c.text.charAt(0) && !prefs.closeStrings())
			return;
		if (!prefs.closeBrackets()
				&& ('[' == c.text.charAt(0) || '(' == c.text.charAt(0) || '{' == c.text
						.charAt(0)))
			return;
		try {

			switch (c.text.charAt(0)) {
			case '\"':
			case '\'':
				// if we close existing quote, do nothing
				if ('\"' == c.text.charAt(0) && c.offset > 0
						&& "\"".equals(d.get(c.offset - 1, 1)))
					return;

				if ('\'' == c.text.charAt(0) && c.offset > 0
						&& "\'".equals(d.get(c.offset - 1, 1)))
					return;

				if (c.offset != d.getLength()
						&& c.text.charAt(0) == d.get(c.offset, 1).charAt(0))
					c.text = "";
				else {
					c.text += c.text;
					// dont set the length, because of the length > 0 then a
					// selection has to be replaced
					// c.length = 0;
				}

				c.shiftsCaret = false;
				c.caretOffset = c.offset + 1;
				break;
			case '(':
			case '{':
			case '[':
				// check partition
				if (AutoEditUtils.getRegionType(d, fPartitioning, c.offset) != IDocument.DEFAULT_CONTENT_TYPE)
					return;
				if (c.offset != d.getLength()
						&& c.text.charAt(0) == d.get(c.offset, 1).charAt(0))
					return;

				try { // in class closing
					String regex = "^\\s*class\\s+.*";
					String regex2 = ".*\\(.*\\).*";
					int start = d.getLineOffset(d.getLineOfOffset(c.offset));
					String curLine = d.get(start, c.offset - start);
					if (Pattern.matches(regex, curLine)
							&& !Pattern.matches(regex2, curLine)) {
						c.text = "():";
						c.shiftsCaret = false;
						c.caretOffset = c.offset + 1;
						return;
					}
				} catch (BadLocationException e) {
				}

				// add closing peer
				c.text = c.text + AutoEditUtils.getBracePair(c.text.charAt(0));
				// dont set the length, because of the length > 0 then a
				// selection has to be replaced
				// c.length = 0;

				c.shiftsCaret = false;
				c.caretOffset = c.offset + 1;
				break;
			case '}':
			case ']':
			case ')':
				// check partition
				if (AutoEditUtils.getRegionType(d, fPartitioning, c.offset) != IDocument.DEFAULT_CONTENT_TYPE)
					return;
				if (!prefs.closeBrackets())
					return;
				// if we already have bracket we should jump over it
				if (c.offset != d.getLength()
						&& c.text.charAt(0) == d.get(c.offset, 1).charAt(0)) {
					c.text = "";
					c.shiftsCaret = false;
					c.caretOffset = c.offset + 1;
					return;
				}
				break;
			}
		} catch (BadLocationException e) {
			e.printStackTrace();
		}
	}

	private void smartIndentOnKeypress(IDocument document,
			DocumentCommand command) {
		switch (command.text.charAt(0)) {
		case '}':
			smartIndentAfterClosingBracket(document, command);
			break;
		case '{':
			smartIndentAfterOpeningBracket(document, command);
			break;
		case '\"':
		case '\'':
		case '(':
		case '[':
			autoClose(document, command);
			break;
		case 'e':
			smartIndentUponE(document, command);
			break;
		}
	}

	private void smartIndentUponE(IDocument d, DocumentCommand c) {
		if (c.offset < 4 || d.getLength() == 0)
			return;

		try {
			String content = d.get(c.offset - 3, 3);
			if (content.equals("els")) { //$NON-NLS-1$
				JavaHeuristicScanner scanner = new JavaHeuristicScanner(d);
				int p = c.offset - 3;

				// current line
				int line = d.getLineOfOffset(p);
				int lineOffset = d.getLineOffset(line);

				// make sure we don't have any leading comments etc.
				if (d.get(lineOffset, p - lineOffset).trim().length() != 0)
					return;

				// line of last Java code
				int pos = scanner.findNonWhitespaceBackward(p - 1,
						JavaHeuristicScanner.UNBOUND);
				if (pos == -1)
					return;
				int lastLine = d.getLineOfOffset(pos);

				// only shift if the last java line is further up and is a
				// braceless block candidate
				if (lastLine < line) {

					VjoIndenter indenter = new VjoIndenter(d, scanner, fProject);
					int ref = indenter.findReferencePosition(p, true, false,
							false, false);
					if (ref == JavaHeuristicScanner.NOT_FOUND)
						return;
					int refLine = d.getLineOfOffset(ref);
					String indent = getIndentOfLine(d, refLine);

					if (indent != null) {
						c.text = indent.toString() + "else"; //$NON-NLS-1$
						c.length += c.offset - lineOffset;
						c.offset = lineOffset;
					}
				}

				return;
			}

			if (content.equals("cas")) { //$NON-NLS-1$
				JavaHeuristicScanner scanner = new JavaHeuristicScanner(d);
				int p = c.offset - 3;

				// current line
				int line = d.getLineOfOffset(p);
				int lineOffset = d.getLineOffset(line);

				// make sure we don't have any leading comments etc.
				if (d.get(lineOffset, p - lineOffset).trim().length() != 0)
					return;

				// line of last Java code
				int pos = scanner.findNonWhitespaceBackward(p - 1,
						JavaHeuristicScanner.UNBOUND);
				if (pos == -1)
					return;
				int lastLine = d.getLineOfOffset(pos);

				// only shift if the last java line is further up and is a
				// braceless block candidate
				if (lastLine < line) {

					JavaIndenter indenter = new JavaIndenter(d, scanner,
							fProject);
					int ref = indenter.findReferencePosition(p, false, false,
							false, true);
					if (ref == JavaHeuristicScanner.NOT_FOUND)
						return;
					int refLine = d.getLineOfOffset(ref);
					int nextToken = scanner.nextToken(ref,
							JavaHeuristicScanner.UNBOUND);
					String indent;
					if (nextToken == Symbols.TokenCASE
							|| nextToken == Symbols.TokenDEFAULT)
						indent = getIndentOfLine(d, refLine);
					else
						// at the brace of the switch
						indent = indenter.computeIndentation(p).toString();

					if (indent != null) {
						c.text = indent.toString() + "case"; //$NON-NLS-1$
						c.length += c.offset - lineOffset;
						c.offset = lineOffset;
					}
				}

				return;
			}

		} catch (BadLocationException e) {
			DLTKUIPlugin.log(e);
		}
	}

	/*
	 * @see
	 * org.eclipse.jface.text.IAutoIndentStrategy#customizeDocumentCommand(org
	 * .eclipse.jface.text.IDocument, org.eclipse.jface.text.DocumentCommand)
	 */
	public void customizeDocumentCommand(IDocument d, DocumentCommand c) {
		if (c.doit == false)
			return;
		clearCachedValues();
		if (!isSmartMode()) {
			super.customizeDocumentCommand(d, c);
			return;
		}
		if (c.length == 0 && c.text != null && isLineDelimiter(d, c.text))
			smartIndentAfterNewLine(d, c);
		else if (c.text.length() == 1)
			smartIndentOnKeypress(d, c);
		else if (c.text.length() > 1
				&& getPreferenceStore().getBoolean(
						PreferenceConstants.EDITOR_SMART_PASTE))
			smartPaste(d, c); // no smart backspace for paste
	}

	private static IPreferenceStore getPreferenceStore() {
		return VjetUIPlugin.getDefault().getPreferenceStore();
	}

	private boolean closeBrace() {
		return fCloseBrace;
	}

	private boolean isSmartMode() {
		return fIsSmartMode;
	}

	// private boolean closeComment() {
	// return fCloseComment;
	// }

	private void clearCachedValues() {
		IPreferenceStore preferenceStore = getPreferenceStore();
		fCloseBrace = preferenceStore
				.getBoolean(VjetPreferenceConstants.EDITOR_CLOSE_BRACES);
		// fCloseComment = preferenceStore
		// .getBoolean(VjetPreferenceConstants.EDITOR_CLOSE_COMMENTS);
		fIsSmartMode = computeSmartMode();
	}

	private boolean computeSmartMode() {
		IWorkbenchPage page = DLTKUIPlugin.getActivePage();
		if (page != null) {
			IEditorPart part = page.getActiveEditor();
			if (part instanceof ITextEditorExtension3) {
				ITextEditorExtension3 extension = (ITextEditorExtension3) part;
				return extension.getInsertMode() == ITextEditorExtension3.SMART_INSERT;
			}
		}
		return false;
	}

	// private static CompilationUnitInfo getCompilationUnitForMethod(
	// IDocument document, int offset, String partitioning) {
	// try {
	// JavaHeuristicScanner scanner = new JavaHeuristicScanner(document);
	//
	// IRegion sourceRange = scanner.findSurroundingBlock(offset);
	// if (sourceRange == null)
	// return null;
	// String source = document.get(sourceRange.getOffset(), sourceRange
	// .getLength());
	//
	// StringBuffer contents = new StringBuffer();
	// contents.append("class ____C{void ____m()"); //$NON-NLS-1$
	// final int methodOffset = contents.length();
	// contents.append(source);
	// contents.append('}');
	//
	// char[] buffer = contents.toString().toCharArray();
	//
	// return new CompilationUnitInfo(buffer, sourceRange.getOffset()
	// - methodOffset);
	//
	// } catch (BadLocationException e) {
	// DLTKUIPlugin.log(e);
	// }
	//
	// return null;
	// }

	/**
	 * Returns true if all blocks are balanced, 
	 * 
	 * @param document
	 * @return true if blocks are balanced
	 */
	private static boolean isBlockBalanced(IDocument document) {
		JavaHeuristicScanner scanner = new JavaHeuristicScanner(document);
		int start = 0;
		int end = document.getLength();
		int numOfBlocks = 0;
		while (true) {
			int token = scanner.nextToken(start, end);
			if(token==JavaHeuristicScanner.NOT_FOUND){
				break;
			}
			switch (token) {
			case JavaHeuristicScanner.TokenLBRACE:
				numOfBlocks++;
				break;

			case JavaHeuristicScanner.TokenRBRACE:
				numOfBlocks--;
				break;
			}
			start = scanner.getPosition();
		}
		return numOfBlocks==0;

	}

	// private static IRegion getToken(IDocument document, IRegion scanRegion,
	// int tokenId) {
	//
	// try {
	//
	// final String source = document.get(scanRegion.getOffset(),
	// scanRegion.getLength());
	//
	// fgScanner.setSource(source.toCharArray());
	//
	// int id = fgScanner.getNextToken();
	// while (id != ITerminalSymbols.TokenNameEOF && id != tokenId)
	// id = fgScanner.getNextToken();
	//
	// if (id == ITerminalSymbols.TokenNameEOF)
	// return null;
	//
	// int tokenOffset = fgScanner.getCurrentTokenStartPosition();
	// int tokenLength = fgScanner.getCurrentTokenEndPosition() + 1
	// - tokenOffset; // inclusive end
	// return new Region(tokenOffset + scanRegion.getOffset(), tokenLength);
	//
	// } catch (InvalidInputException x) {
	// return null;
	// } catch (BadLocationException x) {
	// return null;
	// }
	// }
}
