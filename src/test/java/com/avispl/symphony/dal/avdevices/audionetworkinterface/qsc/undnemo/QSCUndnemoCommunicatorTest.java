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

import com.avispl.symphony.api.dal.dto.control.ControllableProperty;
import com.avispl.symphony.api.dal.dto.monitor.ExtendedStatistics;
import com.avispl.symphony.dal.avdevices.audionetworkinterface.qsc.undnemo.utils.QSCUndnemoMetric;

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
		Assertions.assertEquals("3", stats.get("ActiveChannel#ChannelIndex"));
		Assertions.assertEquals("MXA910-MillPark1-d", stats.get("Channel 01" + "#" + "DeviceName"));
		Assertions.assertEquals("1", stats.get("Channel 01" + "#" + "EnableState"));
		Assertions.assertEquals("Automix Out", stats.get("Channel 01" + "#" + "ChannelName"));
		Assertions.assertEquals("Automix Out", stats.get("Channel 01" + "#" + "DisplayName"));

		Assertions.assertEquals("MXA910-MillPark2-d", stats.get("Channel 02" + "#" + "DeviceName"));
		Assertions.assertEquals("1", stats.get("Channel 02" + "#" + "EnableState"));
		Assertions.assertEquals("Automix Out", stats.get("Channel 02" + "#" + "ChannelName"));
		Assertions.assertEquals("Automix Out", stats.get("Channel 02" + "#" + "DisplayName"));

		Assertions.assertEquals("3", stats.get("ActiveChannel" + "#" + "ChannelIndex"));
		Assertions.assertEquals("1", stats.get("ActiveChannel" + "#" + "EnableState"));
		Assertions.assertEquals("Automix Out", stats.get("ActiveChannel" + "#" + "ChannelName"));
		Assertions.assertEquals("Automix Out", stats.get("ActiveChannel" + "#" + "DisplayName"));

		Assertions.assertEquals("", stats.get("Channel 16" + "#" + "DeviceName"));
		Assertions.assertEquals("0", stats.get("Channel 16" + "#" + "EnableState"));
		Assertions.assertEquals("", stats.get("Channel 16" + "#" + "ChannelName"));
		Assertions.assertEquals("No Channel Assigned", stats.get("Channel 16" + "#" + "DisplayName"));
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
		Assertions.assertEquals("3", stats.get("ActiveChannel#ChannelIndex"));

		Assertions.assertEquals("MXA910-MillPark1-d", stats.get("Channel 01" + "#" + "DeviceName"));
		Assertions.assertEquals("1", stats.get("Channel 01" + "#" + "EnableState"));
		Assertions.assertEquals("Automix Out", stats.get("Channel 01" + "#" + "ChannelName"));
		Assertions.assertEquals("Automix Out", stats.get("Channel 01" + "#" + "DisplayName"));

		Assertions.assertEquals("MXA910-MillPark2-d", stats.get("Channel 02" + "#" + "DeviceName"));
		Assertions.assertEquals("1", stats.get("Channel 02" + "#" + "EnableState"));
		Assertions.assertEquals("Automix Out", stats.get("Channel 02" + "#" + "ChannelName"));
		Assertions.assertEquals("Automix Out", stats.get("Channel 02" + "#" + "DisplayName"));

		Assertions.assertEquals("3", stats.get("ActiveChannel" + "#" + "ChannelIndex"));
		Assertions.assertEquals("1", stats.get("ActiveChannel" + "#" + "EnableState"));
		Assertions.assertEquals("Automix Out", stats.get("ActiveChannel" + "#" + "ChannelName"));
		Assertions.assertEquals("Automix Out", stats.get("ActiveChannel" + "#" + "DisplayName"));
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
		Assertions.assertEquals("3", stats.get("ActiveChannel#ChannelIndex"));
		Assertions.assertEquals("MXA910-MillPark1-d", stats.get("Channel 01" + "#" + "DeviceName"));
		Assertions.assertEquals("1", stats.get("Channel 01" + "#" + "EnableState"));
		Assertions.assertEquals("Automix Out", stats.get("Channel 01" + "#" + "ChannelName"));
		Assertions.assertEquals("Automix Out", stats.get("Channel 01" + "#" + "DisplayName"));

		Assertions.assertEquals("MXA910-MillPark2-d", stats.get("Channel 02" + "#" + "DeviceName"));
		Assertions.assertEquals("1", stats.get("Channel 02" + "#" + "EnableState"));
		Assertions.assertEquals("Automix Out", stats.get("Channel 02" + "#" + "ChannelName"));
		Assertions.assertEquals("Automix Out", stats.get("Channel 02" + "#" + "DisplayName"));

		Assertions.assertEquals("3", stats.get("ActiveChannel" + "#" + "ChannelIndex"));
		Assertions.assertEquals("1", stats.get("ActiveChannel" + "#" + "EnableState"));
		Assertions.assertEquals("Automix Out", stats.get("ActiveChannel" + "#" + "ChannelName"));
		Assertions.assertEquals("Automix Out", stats.get("ActiveChannel" + "#" + "DisplayName"));
	}

	/**
	 * Test controlProperty set button brightness
	 * @throws Exception when fail to control
	 */
	@Tag("RealDevice")
	@Test
	void testControlPropertyButtonBrightness() throws Exception {
		ControllableProperty property = new ControllableProperty();
		property.setValue(1);
		property.setProperty(QSCUndnemoMetric.BUTTON_BRIGHTNESS.getName());
		qscUndnemoCommunicator.controlProperty(property);
		// getMultipleStatistics after control
		qscUndnemoCommunicator.getMultipleStatistics();
		// Sleep here to wait for worker thread to fetch channel info
		Thread.sleep(30000);
		ExtendedStatistics extendedStatistics = (ExtendedStatistics) qscUndnemoCommunicator.getMultipleStatistics().get(0);
		Map<String, String> stats = extendedStatistics.getStatistics();
		Assertions.assertEquals("1", stats.get("ButtonBrightness"));
	}

	/**
	 * Test controlProperty set display brightness
	 * @throws Exception when fail to control
	 */
	@Tag("RealDevice")
	@Test
	void testControlPropertyDisplayBrightness() throws Exception {
		ControllableProperty property = new ControllableProperty();
		property.setValue(1);
		property.setProperty(QSCUndnemoMetric.DISPLAY_BRIGHTNESS.getName());
		qscUndnemoCommunicator.controlProperty(property);
		// getMultipleStatistics after control
		qscUndnemoCommunicator.getMultipleStatistics();
		// Sleep here to wait for worker thread to fetch channel info
		Thread.sleep(30000);
		ExtendedStatistics extendedStatistics = (ExtendedStatistics) qscUndnemoCommunicator.getMultipleStatistics().get(0);
		Map<String, String> stats = extendedStatistics.getStatistics();
		Assertions.assertEquals("1", stats.get("DisplayBrightness"));
	}

	/**
	 * Test controlProperty set volume
	 * @throws Exception when fail to control
	 */
	@Tag("RealDevice")
	@Test
	void testControlPropertyVolume() throws Exception {
		ControllableProperty property = new ControllableProperty();
		property.setValue(1);
		property.setProperty(QSCUndnemoMetric.VOLUME.getName());
		qscUndnemoCommunicator.controlProperty(property);
		// getMultipleStatistics after control
		qscUndnemoCommunicator.getMultipleStatistics();
		// Sleep here to wait for worker thread to fetch channel info
		Thread.sleep(30000);
		ExtendedStatistics extendedStatistics = (ExtendedStatistics) qscUndnemoCommunicator.getMultipleStatistics().get(0);
		Map<String, String> stats = extendedStatistics.getStatistics();
		Assertions.assertEquals("1", stats.get("Volume"));
	}

	/**
	 * Test controlProperty set speaker mute
	 * @throws Exception when fail to control
	 */
	@Tag("RealDevice")
	@Test
	void testControlPropertySpeakerMute() throws Exception {
		ControllableProperty property = new ControllableProperty();
		property.setValue(1);
		property.setProperty(QSCUndnemoMetric.SPEAKER_MUTE.getName());
		qscUndnemoCommunicator.controlProperty(property);
		// getMultipleStatistics after control
		qscUndnemoCommunicator.getMultipleStatistics();
		// Sleep here to wait for worker thread to fetch channel info
		Thread.sleep(30000);
		ExtendedStatistics extendedStatistics = (ExtendedStatistics) qscUndnemoCommunicator.getMultipleStatistics().get(0);
		Map<String, String> stats = extendedStatistics.getStatistics();
		Assertions.assertEquals("1", stats.get("SpeakerMute"));
	}

	/**
	 * Test controlProperty set active channel index
	 * - Case one: given index is the same as current active channel index.
	 * @throws Exception when fail to control
	 */
	@Tag("RealDevice")
	@Test
	void testControlPropertyActiveChannelCaseOne() throws Exception {
		qscUndnemoCommunicator.getMultipleStatistics();
		// Sleep here to wait for worker thread to fetch channel info
		Thread.sleep(30000);
		qscUndnemoCommunicator.getMultipleStatistics();
		ControllableProperty property = new ControllableProperty();
		property.setValue(3);
		property.setProperty(QSCUndnemoMetric.ACTIVE_CHANNEL_INDEX.getName());
		qscUndnemoCommunicator.controlProperty(property);
		ExtendedStatistics extendedStatistics = (ExtendedStatistics) qscUndnemoCommunicator.getMultipleStatistics().get(0);
		Map<String, String> stats = extendedStatistics.getStatistics();
		Assertions.assertEquals("3", stats.get("ActiveChannel#ChannelIndex"));
	}

	/**
	 * Test controlProperty set active channel index
	 * - Case two: given index is different from current active channel index.
	 * @throws Exception when fail to control
	 */
	@Tag("RealDevice")
	@Test
	void testControlPropertyActiveChannelCaseTwo() throws Exception {
		qscUndnemoCommunicator.getMultipleStatistics();
		// Sleep here to wait for worker thread to fetch channel info
		Thread.sleep(30000);
		qscUndnemoCommunicator.getMultipleStatistics();
		ControllableProperty property = new ControllableProperty();
		property.setValue(4);
		property.setProperty(QSCUndnemoMetric.ACTIVE_CHANNEL_INDEX.getName());
		qscUndnemoCommunicator.controlProperty(property);
		ExtendedStatistics extendedStatistics = (ExtendedStatistics) qscUndnemoCommunicator.getMultipleStatistics().get(0);
		Map<String, String> stats = extendedStatistics.getStatistics();
		Assertions.assertEquals("4", stats.get("ActiveChannel#ChannelIndex"));
		// Set it back to 3, so that it won't conflict other tests.
		property.setValue(3);
		property.setProperty(QSCUndnemoMetric.ACTIVE_CHANNEL_INDEX.getName());
		qscUndnemoCommunicator.controlProperty(property);
	}

	/**
	 * Test controlProperty get all stats
	 * Expect controlProperty successfully channel index: 1,2
	 */
	@Tag("RealDevice")
	@Test
	void testControlPropertyActiveChannelCaseThree() throws Exception {
		// Special character will be ignored, only valid index will be put to statistics
		qscUndnemoCommunicator.setChannelIndex("1,2");
		qscUndnemoCommunicator.getMultipleStatistics();
		// Sleep here to wait for worker thread to fetch channel info
		Thread.sleep(30000);
		qscUndnemoCommunicator.getMultipleStatistics();
		ControllableProperty property = new ControllableProperty();
		property.setValue(2);
		property.setProperty(QSCUndnemoMetric.ACTIVE_CHANNEL_INDEX.getName());
		qscUndnemoCommunicator.controlProperty(property);
		ExtendedStatistics extendedStatistics = (ExtendedStatistics) qscUndnemoCommunicator.getMultipleStatistics().get(0);
		Map<String, String> stats = extendedStatistics.getStatistics();
		Assertions.assertEquals("2", stats.get("ActiveChannel#ChannelIndex"));
		// Set it back to 3, so that it won't conflict other tests.
		property.setValue(3);
		property.setProperty(QSCUndnemoMetric.ACTIVE_CHANNEL_INDEX.getName());
		qscUndnemoCommunicator.controlProperty(property);
	}
}