/*******************************************************************************
 * Copyright (c) 2005-2011 eBay Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 *******************************************************************************/
package org.ebayopensource.dsf.css.dom;

/**
 *  The <code>RGBColor</code> interface is used to represent any RGB color 
 * value. This interface reflects the values in the underlying style 
 * property. Hence, modifications made to the <code>CSSPrimitiveValue</code> 
 * objects modify the style property. 
 * <p> A specified RGB color is not clipped (even if the number is outside the 
 * range 0-255 or 0%-100%). A computed RGB color is clipped depending on the 
 * device. 
 * <p> Even if a style sheet can only contain an integer for a color value, 
 * the internal storage of this integer is a float, and this can be used as 
 * a float in the specified or the computed style. 
 * <p> A color percentage value can always be converted to a number and vice 
 * versa. 
 * <p>See also the <a href='http://www.w3.org/TR/2000/REC-DOM-Level-2-Style-20001113'>Document Object Model (DOM) Level 2 Style Specification</a>.
 * @since DOM Level 2
 */
public interface IRgbColor {
    /**
     *  This attribute is used for the red value of the RGB color. 
     */
    ICssPrimitiveValue getRed();

    /**
     *  This attribute is used for the green value of the RGB color. 
     */
    ICssPrimitiveValue getGreen();

    /**
     *  This attribute is used for the blue value of the RGB color. 
     */
    ICssPrimitiveValue getBlue();
}