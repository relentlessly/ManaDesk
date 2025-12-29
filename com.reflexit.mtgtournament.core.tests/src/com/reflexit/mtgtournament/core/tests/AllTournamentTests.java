package com.reflexit.mtgtournament.core.tests;

import org.junit.runner.RunWith;
import org.junit.runners.AllTests;

import com.reflexit.mtgtournament.core.model.ScheduleTest;

import junit.framework.Test;
import junit.framework.TestSuite;

@RunWith(AllTests.class)
public class AllTournamentTests {
	public static Test suite() {
		TestSuite suite = new TestSuite("Test for com.reflexit.mtgtournament.core.model");
		// $JUnit-BEGIN$
		// juni4
// !!! RD Not now		suite.addTest(new JUnit4TestAdapter(CmdAddTableTest.class));
// !!! RD Not now		suite.addTest(new JUnit4TestAdapter(XmlRegressionTest.class));
		// core
		suite.addTestSuite(ScheduleTest.class);
		return suite;
	}
}