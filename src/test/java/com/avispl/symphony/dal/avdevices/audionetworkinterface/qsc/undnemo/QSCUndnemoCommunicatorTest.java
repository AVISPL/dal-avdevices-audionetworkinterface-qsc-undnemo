/*
 * Copyright (c) 2022 AVI-SPL Inc. All Rights Reserved.
 */
package com.avispl.symphony.dal.avdevices.audionetworkinterface.qsc.undnemo;

import java.util.Map;

import junit.framework.TestCase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
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
	public void destroy() {
		qscUndnemoCommunicator.disconnect();
	}

	/**
	 * Test getMultipleStatistics get all stats
	 * Expect getMultipleStatistics successfully
	 */
	@Tag("RealDevice")
	@Test
	void testGetMultipleStatisticsWithRealDevice() throws Exception {
		qscUndnemoCommunicator.getMultipleStatistics();
		Thread.sleep(30000);
		ExtendedStatistics extendedStatistics = (ExtendedStatistics) qscUndnemoCommunicator.getMultipleStatistics().get(0);
		Map<String, String> stats = extendedStatistics.getStatistics();
		Assertions.assertEquals(262, stats.size());
		Assertions.assertEquals("1", stats.get("SpeakerMute"));
		Assertions.assertEquals("1", stats.get("DisplayBrightness"));
		Assertions.assertEquals("1", stats.get("ButtonBrightness"));
		Assertions.assertEquals("1", stats.get("Volume"));
		Assertions.assertEquals("1", stats.get("SpeakerMute"));
		Assertions.assertEquals("3", stats.get("(00)ActiveChannel#ChannelIndex"));
		Assertions.assertEquals("MXA910-MillPark1-d", stats.get("(01)Channel 1" + "#" + "DeviceName"));
		Assertions.assertEquals("1", stats.get("(01)Channel 1" + "#" + "EnableState"));
		Assertions.assertEquals("Automix Out", stats.get("(01)Channel 1" + "#" + "ChannelName"));
		Assertions.assertEquals("Automix Out", stats.get("(01)Channel 1" + "#" + "DisplayName"));

		Assertions.assertEquals("MXA910-MillPark2-d", stats.get("(02)Channel 2" + "#" + "DeviceName"));
		Assertions.assertEquals("1", stats.get("(02)Channel 2" + "#" + "EnableState"));
		Assertions.assertEquals("Automix Out", stats.get("(02)Channel 2" + "#" + "ChannelName"));
		Assertions.assertEquals("Automix Out", stats.get("(02)Channel 2" + "#" + "DisplayName"));

		Assertions.assertEquals("3", stats.get("(00)ActiveChannel" + "#" + "ChannelIndex"));
		Assertions.assertEquals("1", stats.get("(00)ActiveChannel" + "#" + "EnableState"));
		Assertions.assertEquals("Automix Out", stats.get("(00)ActiveChannel" + "#" + "ChannelName"));
		Assertions.assertEquals("Automix Out", stats.get("(00)ActiveChannel" + "#" + "DisplayName"));

		Assertions.assertEquals("", stats.get("(16)Channel 16" + "#" + "DeviceName"));
		Assertions.assertEquals("0", stats.get("(16)Channel 16" + "#" + "EnableState"));
		Assertions.assertEquals("", stats.get("(16)Channel 16" + "#" + "ChannelName"));
		Assertions.assertEquals("No Channel Assigned", stats.get("(16)Channel 16" + "#" + "DisplayName"));
	}

	/**
	 * Test getMultipleStatistics get all stats
	 * Expect getMultipleStatistics successfully
	 */
	@Tag("RealDevice")
	@Test
	void testGetMultipleStatisticsWithInvalidFilter() throws Exception {

		qscUndnemoCommunicator.setChannelIndex("!,#,$,@,a");
		qscUndnemoCommunicator.getMultipleStatistics();
		// Sleep here to wait for worker thread to fetch channel info
		Thread.sleep(30000);
		ExtendedStatistics extendedStatistics = (ExtendedStatistics) qscUndnemoCommunicator.getMultipleStatistics().get(0);
		Map<String, String> stats = extendedStatistics.getStatistics();
		Assertions.assertEquals(262, stats.size());
		Assertions.assertEquals("1", stats.get("SpeakerMute"));
		Assertions.assertEquals("1", stats.get("DisplayBrightness"));
		Assertions.assertEquals("1", stats.get("ButtonBrightness"));
		Assertions.assertEquals("1", stats.get("Volume"));
		Assertions.assertEquals("1", stats.get("SpeakerMute"));
		Assertions.assertEquals("3", stats.get("(00)ActiveChannel#ChannelIndex"));

		Assertions.assertEquals("MXA910-MillPark1-d", stats.get("(01)Channel 1" + "#" + "DeviceName"));
		Assertions.assertEquals("1", stats.get("(01)Channel 1" + "#" + "EnableState"));
		Assertions.assertEquals("Automix Out", stats.get("(01)Channel 1" + "#" + "ChannelName"));
		Assertions.assertEquals("Automix Out", stats.get("(01)Channel 1" + "#" + "DisplayName"));

		Assertions.assertEquals("MXA910-MillPark2-d", stats.get("(02)Channel 2" + "#" + "DeviceName"));
		Assertions.assertEquals("1", stats.get("(02)Channel 2" + "#" + "EnableState"));
		Assertions.assertEquals("Automix Out", stats.get("(02)Channel 2" + "#" + "ChannelName"));
		Assertions.assertEquals("Automix Out", stats.get("(02)Channel 2" + "#" + "DisplayName"));

		Assertions.assertEquals("3", stats.get("(00)ActiveChannel" + "#" + "ChannelIndex"));
		Assertions.assertEquals("1", stats.get("(00)ActiveChannel" + "#" + "EnableState"));
		Assertions.assertEquals("Automix Out", stats.get("(00)ActiveChannel" + "#" + "ChannelName"));
		Assertions.assertEquals("Automix Out", stats.get("(00)ActiveChannel" + "#" + "DisplayName"));
	}

	/**
	 * Test getMultipleStatistics get all stats
	 * Expect getMultipleStatistics successfully channel index: 1,2,3
	 */
	@Tag("RealDevice")
	@Test
	void testGetMultipleStatisticsWithFilter() throws Exception {
		// Special character will be ignored, only valid index will be put to statistics
		qscUndnemoCommunicator.setChannelIndex("1,2,3,@");
		qscUndnemoCommunicator.getMultipleStatistics();
		// Sleep here to wait for worker thread to fetch channel info
		Thread.sleep(30000);
		ExtendedStatistics extendedStatistics = (ExtendedStatistics) qscUndnemoCommunicator.getMultipleStatistics().get(0);
		Map<String, String> stats = extendedStatistics.getStatistics();
		Assertions.assertEquals(18, stats.size());
		Assertions.assertEquals("1", stats.get("SpeakerMute"));
		Assertions.assertEquals("1", stats.get("DisplayBrightness"));
		Assertions.assertEquals("1", stats.get("ButtonBrightness"));
		Assertions.assertEquals("1", stats.get("Volume"));
		Assertions.assertEquals("1", stats.get("SpeakerMute"));
		Assertions.assertEquals("3", stats.get("(00)ActiveChannel#ChannelIndex"));
		Assertions.assertEquals("MXA910-MillPark1-d", stats.get("(01)Channel 1" + "#" + "DeviceName"));
		Assertions.assertEquals("1", stats.get("(01)Channel 1" + "#" + "EnableState"));
		Assertions.assertEquals("Automix Out", stats.get("(01)Channel 1" + "#" + "ChannelName"));
		Assertions.assertEquals("Automix Out", stats.get("(01)Channel 1" + "#" + "DisplayName"));

		Assertions.assertEquals("MXA910-MillPark2-d", stats.get("(02)Channel 2" + "#" + "DeviceName"));
		Assertions.assertEquals("1", stats.get("(02)Channel 2" + "#" + "EnableState"));
		Assertions.assertEquals("Automix Out", stats.get("(02)Channel 2" + "#" + "ChannelName"));
		Assertions.assertEquals("Automix Out", stats.get("(02)Channel 2" + "#" + "DisplayName"));

		Assertions.assertEquals("3", stats.get("(00)ActiveChannel" + "#" + "ChannelIndex"));
		Assertions.assertEquals("1", stats.get("(00)ActiveChannel" + "#" + "EnableState"));
		Assertions.assertEquals("Automix Out", stats.get("(00)ActiveChannel" + "#" + "ChannelName"));
		Assertions.assertEquals("Automix Out", stats.get("(00)ActiveChannel" + "#" + "DisplayName"));
	}
}