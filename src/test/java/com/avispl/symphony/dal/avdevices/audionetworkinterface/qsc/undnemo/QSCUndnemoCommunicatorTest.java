/*
 * Copyright (c) 2022 AVI-SPL Inc. All Rights Reserved.
 */
package com.avispl.symphony.dal.avdevices.audionetworkinterface.qsc.undnemo;

import java.util.Map;

import junit.framework.TestCase;
import org.junit.Assert;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.avispl.symphony.api.dal.dto.monitor.ExtendedStatistics;

/**
 * Unit test for {@link QSCUndnemoCommunicator}.
 * Test monitoring data and control
 *
 * @author Duy Nguyen
 * @version 1.0
 * @since 1.0.0
 */
class QSCUndnemoCommunicatorTest extends TestCase {

	private QSCUndnemoCommunicator qscUndnemoCommunicator;

	private static final String REAL_DEVICE_HOST_NAME = "10.8.40.120";
	private static final int PORT = 49494;

	@BeforeEach
	public void init() throws Exception {
		qscUndnemoCommunicator = new QSCUndnemoCommunicator();
		qscUndnemoCommunicator.setHost(REAL_DEVICE_HOST_NAME);
		qscUndnemoCommunicator.setBufferLength(100);
		qscUndnemoCommunicator.setPort(PORT);
		qscUndnemoCommunicator.init();
		qscUndnemoCommunicator.connect();
	}

	@AfterEach
	public void destroy() throws InterruptedException {
		qscUndnemoCommunicator.disconnect();
		Thread.sleep(20000);
	}

	/**
	 * Test getMultipleStatistics get all current system
	 * Expect getMultipleStatistics successfully with three systems
	 */
	@Tag("RealDevice")
	@Test
	void testGetMultipleStatisticsWithRealDevice() throws Exception {
		qscUndnemoCommunicator.getMultipleStatistics();
		Thread.sleep(30000);
		ExtendedStatistics extendedStatistics = (ExtendedStatistics) qscUndnemoCommunicator.getMultipleStatistics().get(0);
		Map<String, String> stats = extendedStatistics.getStatistics();
		Assert.assertEquals(262, stats.size());
		Assert.assertEquals("1", stats.get("SpeakerMute"));
		Assert.assertEquals("MXA910-MillPark1-d", stats.get("(01)Channel 1" + "#" + "DeviceName"));
		Assert.assertEquals("1", stats.get("(01)Channel 1" + "#" + "EnableState"));
		Assert.assertEquals("Automix Out", stats.get("(01)Channel 1" + "#" + "ChannelName"));
		Assert.assertEquals("Automix Out", stats.get("(01)Channel 1" + "#" + "DisplayName"));

		Assert.assertEquals("MXA910-MillPark2-d", stats.get("(02)Channel 2" + "#" + "DeviceName"));
		Assert.assertEquals("1", stats.get("(02)Channel 2" + "#" + "EnableState"));
		Assert.assertEquals("Automix Out", stats.get("(02)Channel 2" + "#" + "ChannelName"));
		Assert.assertEquals("Automix Out", stats.get("(02)Channel 2" + "#" + "DisplayName"));

		Assert.assertEquals("3", stats.get("(00)ActiveChannel" + "#" + "ChannelIndex"));
		Assert.assertEquals("1", stats.get("(00)ActiveChannel" + "#" + "EnableState"));
		Assert.assertEquals("Automix Out", stats.get("(00)ActiveChannel" + "#" + "ChannelName"));
		Assert.assertEquals("Automix Out", stats.get("(00)ActiveChannel" + "#" + "DisplayName"));

		Assert.assertEquals("", stats.get("(16)Channel 16" + "#" + "DeviceName"));
		Assert.assertEquals("0", stats.get("(16)Channel 16" + "#" + "EnableState"));
		Assert.assertEquals("", stats.get("(16)Channel 16" + "#" + "ChannelName"));
		Assert.assertEquals("No Channel Assigned", stats.get("(16)Channel 16" + "#" + "DisplayName"));
	}

	/**
	 * Test getMultipleStatistics get all current system
	 * Expect getMultipleStatistics successfully with three systems
	 */
	@Tag("RealDevice")
	@Test
	void testGetMultipleStatisticsWithInvalidFilter() throws Exception {
		qscUndnemoCommunicator.setChannelIndex("!,#,$,@,a");
		qscUndnemoCommunicator.getMultipleStatistics();
		Thread.sleep(30000);
		ExtendedStatistics extendedStatistics = (ExtendedStatistics) qscUndnemoCommunicator.getMultipleStatistics().get(0);
		Map<String, String> stats = extendedStatistics.getStatistics();
		Assert.assertEquals(262, stats.size());
		Assert.assertEquals("1", stats.get("SpeakerMute"));
		Assert.assertEquals("MXA910-MillPark1-d", stats.get("(01)Channel 1" + "#" + "DeviceName"));
		Assert.assertEquals("1", stats.get("(01)Channel 1" + "#" + "EnableState"));
		Assert.assertEquals("Automix Out", stats.get("(01)Channel 1" + "#" + "ChannelName"));
		Assert.assertEquals("Automix Out", stats.get("(01)Channel 1" + "#" + "DisplayName"));

		Assert.assertEquals("MXA910-MillPark2-d", stats.get("(02)Channel 2" + "#" + "DeviceName"));
		Assert.assertEquals("1", stats.get("(02)Channel 2" + "#" + "EnableState"));
		Assert.assertEquals("Automix Out", stats.get("(02)Channel 2" + "#" + "ChannelName"));
		Assert.assertEquals("Automix Out", stats.get("(02)Channel 2" + "#" + "DisplayName"));

		Assert.assertEquals("3", stats.get("(00)ActiveChannel" + "#" + "ChannelIndex"));
		Assert.assertEquals("1", stats.get("(00)ActiveChannel" + "#" + "EnableState"));
		Assert.assertEquals("Automix Out", stats.get("(00)ActiveChannel" + "#" + "ChannelName"));
		Assert.assertEquals("Automix Out", stats.get("(00)ActiveChannel" + "#" + "DisplayName"));
	}

	/**
	 * Test getMultipleStatistics get all current system
	 * Expect getMultipleStatistics successfully with three systems
	 */
	@Tag("RealDevice")
	@Test
	void testGetMultipleStatisticsWithFilter() throws Exception {
		qscUndnemoCommunicator.setChannelIndex("1,2,3,@");
		qscUndnemoCommunicator.getMultipleStatistics();
		Thread.sleep(30000);
		ExtendedStatistics extendedStatistics = (ExtendedStatistics) qscUndnemoCommunicator.getMultipleStatistics().get(0);
		Map<String, String> stats = extendedStatistics.getStatistics();
		Assert.assertEquals(18, stats.size());
		Assert.assertEquals("1", stats.get("SpeakerMute"));
		Assert.assertEquals("MXA910-MillPark1-d", stats.get("(01)Channel 1" + "#" + "DeviceName"));
		Assert.assertEquals("1", stats.get("(01)Channel 1" + "#" + "EnableState"));
		Assert.assertEquals("Automix Out", stats.get("(01)Channel 1" + "#" + "ChannelName"));
		Assert.assertEquals("Automix Out", stats.get("(01)Channel 1" + "#" + "DisplayName"));

		Assert.assertEquals("MXA910-MillPark2-d", stats.get("(02)Channel 2" + "#" + "DeviceName"));
		Assert.assertEquals("1", stats.get("(02)Channel 2" + "#" + "EnableState"));
		Assert.assertEquals("Automix Out", stats.get("(02)Channel 2" + "#" + "ChannelName"));
		Assert.assertEquals("Automix Out", stats.get("(02)Channel 2" + "#" + "DisplayName"));

		Assert.assertEquals("3", stats.get("(00)ActiveChannel" + "#" + "ChannelIndex"));
		Assert.assertEquals("1", stats.get("(00)ActiveChannel" + "#" + "EnableState"));
		Assert.assertEquals("Automix Out", stats.get("(00)ActiveChannel" + "#" + "ChannelName"));
		Assert.assertEquals("Automix Out", stats.get("(00)ActiveChannel" + "#" + "DisplayName"));
	}



}