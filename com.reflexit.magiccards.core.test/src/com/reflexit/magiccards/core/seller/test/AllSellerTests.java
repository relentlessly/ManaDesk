/*******************************************************************************
 * Copyright (c) 2008 Alena Laskavaia.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Alena Laskavaia - initial API and implementation
 *******************************************************************************/

/*
 * Contributors:
 *     Rémi Dutil (2026) - updated for ManaDesk creation and Eclipse 2.0 migration
 */

package com.reflexit.magiccards.core.seller.test;

import junit.framework.Test;
import junit.framework.TestSuite;


public class AllSellerTests {
	public static Test suite() {
		TestSuite suite = new TestSuite("Test for com.reflexit.magiccards.core.seller.test");
		// $JUnit-BEGIN$
		// prive providers
// !!! RD Not now		suite.addTestSuite(ParseTcgPlayerPricesTest.class);
		// !!! RD Not now		suite.addTestSuite(ParseMOTLPricesTest.class);
		// !!! RD Not now		suite.addTestSuite(ParseMagicCardMarketPricesTest.class);
		// $JUnit-END$
		return suite;
	}
}
