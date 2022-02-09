/*
 * Copyright (c) 2022 AVI-SPL Inc. All Rights Reserved.
 */
package com.avispl.symphony.dal.avdevices.audionetworkinterface.qsc.undnemo.utils;

/**
 * Metric for QSC Undnemo Monitoring Properties
 *
 * @author Duy Nguyen
 * @version 1.0.0
 * @since 1.0.0
 */
public enum QSCUndnemoMetric {

	SOFTWARE_VERSION_INFO("SoftwareVersionInfo"),
	ACTIVE_CHANNEL_INDEX("(00)ActiveChannel#ChannelIndex"),
	CHANNEL_INFO_ENABLE_STATE("EnableState"),
	CHANNEL_INFO_DEVICE_NAME("DeviceName"),
	CHANNEL_INFO_CHANNEL_NAME("ChannelName"),
	CHANNEL_INFO_DISPLAY_NAME("DisplayName"),
	SPEAKER_MUTE("SpeakerMute"),
	VOLUME("Volume"),
	BUTTON_BRIGHTNESS("ButtonBrightness"),
	DISPLAY_BRIGHTNESS("DisplayBrightness");

	private final String name;

	/**
	 * QSCUndnemoMetric constructor
	 *
	 * @param name {@code {@link #name}}
	 */
	QSCUndnemoMetric(String name) {
		this.name = name;
	}

	/**
	 * Retrieves {@code {@link #name}}
	 *
	 * @return value of {@link #name}
	 */
	public String getName() {
		return name;
	}

	/**
	 * Get name of metric from QSCUndnemoMetric
	 *
	 * @param name name of metric
	 * @return Enum of QSCUndnemoMonitoringMetric
	 */
	public static QSCUndnemoMetric getByName(String name) {
		for (QSCUndnemoMetric metric: QSCUndnemoMetric.values()) {
			if (metric.getName().equals(name)) {
				return metric;
			}
		}
		throw new IllegalArgumentException("Cannot find the enum with name: " + name);
	}
}