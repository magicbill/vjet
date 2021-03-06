/*******************************************************************************
 * Copyright (c) 2005-2011 eBay Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 *******************************************************************************/
/* 
 * $Id: ImageAnimateImage.java.java, July 27, 2009, 12:20:41 AM, liama. Exp$:
 * Copyright (c) 2006-2009 Ebay Technologies. All Rights Reserved.
 * This software program and documentation are copyrighted by Ebay
 * Technologies.
 */
package org.ebayopensource.dsf.jst.validation.vjo.samples.dox.ebay.vjoPro.vjoPro4javadev.samples.vjlib;




import java.util.List;

import org.ebayopensource.dsf.jsgen.shared.ids.FieldProbIds;
import org.ebayopensource.dsf.jsgen.shared.validation.vjo.VjoSemanticProblem;
import org.ebayopensource.dsf.jst.validation.vjo.VjoValidationBaseTester;
import org.junit.Before;
import org.junit.Test;




/**
 * ImageAnimateImage.java
 * 
 * @author <a href="mailto:liama@ebay.com">liama</a>
 * @since JDK 1.5
 */
//@Category( { P3, FAST, UNIT })
//@ModuleInfo(value="DsfPrebuild",subModuleId="JsToJava")
public class ImageAnimateImage extends VjoValidationBaseTester {

    @Before
    public void setUp() {
        expectProblems.clear();
        expectProblems
                .add(createNewProblem(FieldProbIds.UndefinedField, 26, 0));
        expectProblems.add(createNewProblem(FieldProbIds.UndefinedField, 9, 0));
        expectProblems
                .add(createNewProblem(FieldProbIds.UndefinedField, 15, 0));
        expectProblems
                .add(createNewProblem(FieldProbIds.UndefinedField, 23, 0));
        expectProblems
                .add(createNewProblem(FieldProbIds.UndefinedField, 11, 0));
        expectProblems
                .add(createNewProblem(FieldProbIds.UndefinedField, 26, 0));
        expectProblems
                .add(createNewProblem(FieldProbIds.UndefinedField, 14, 0));
    }

    @Test
    //@Category( { P3, FAST, UNIT })
    //@Description("Test VJO Sample project, To validate false positive ")
    // The bug 6110 still exist. now we update the test js
    // file via alias. if use alias it's work.
    public void testImageAnimateImage() {
        List<VjoSemanticProblem> problems = getVjoSemanticProblem(
                VjoValidationBaseTester.VJLIB_FOLDER,
                "dox.ebay.vjoPro.vjoPro4javadev.samples.vjlib.",
                "ImageAnimateImage.js", this.getClass());
        assertProblemEquals(expectProblems, problems);
    }
}
