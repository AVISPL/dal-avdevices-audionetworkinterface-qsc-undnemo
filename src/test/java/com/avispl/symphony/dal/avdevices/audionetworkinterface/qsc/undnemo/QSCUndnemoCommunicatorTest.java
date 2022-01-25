/*
 * Copyright (c) 2022 AVI-SPL Inc. All Rights Reserved.
 */
package com.avispl.symphony.dal.avdevices.audionetworkinterface.qsc.undnemo;

import junit.framework.TestCase;
import org.junit.After;
import org.junit.Before;

public class QSCUndnemoCommunicatorTest extends TestCase {
	private QSCUndnemoCommunicator qscUndnemoCommunicator;
	@Before
	public void setUp() throws Exception {
		qscUndnemoCommunicator = new QSCUndnemoCommunicator();
		qscUndnemoCommunicator.setHost("10.8.40.120");
		qscUndnemoCommunicator.setPort(49494);
		qscUndnemoCommunicator.setBufferLength(50);
		qscUndnemoCommunicator.init();
		qscUndnemoCommunicator.connect();
	}
	@After
	public void destroy() {
		qscUndnemoCommunicator.disconnect();
	}
	public void testGetMultipleStatistics() throws Exception {
	}

	public void testControlProperty() throws Exception {
	}

	public void testControlProperties() {
	}
}