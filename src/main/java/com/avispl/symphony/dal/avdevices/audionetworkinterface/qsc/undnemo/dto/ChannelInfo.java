/*
 * Copyright (c) 2022 AVI-SPL Inc. All Rights Reserved.
 */
package com.avispl.symphony.dal.avdevices.audionetworkinterface.qsc.undnemo.dto;

/**
 * Channel info DTO
 *
 * @author Duy Nguyen
 * @version 1.0.0
 * @since 1.0.0
 */
public class ChannelInfo {

	private String channelInfoIndex;

	private String enableState;

	private String deviceName;

	private String channelName;

	private String displayName;

	/**
	 * Channel info with args-constructor
	 *
	 * @param channelIndex index of the channel
	 * @param enableState enable state (0/1)
	 * @param deviceName name of the device
	 * @param channelName channel name
	 * @param displayName display name
	 */
	public ChannelInfo(String channelIndex, String enableState, String deviceName, String channelName, String displayName) {
		this.channelInfoIndex = channelIndex;
		this.enableState = enableState;
		this.deviceName = deviceName;
		this.channelName = channelName;
		this.displayName = displayName;
	}

	/**
	 * Retrieves {@code {@link #channelInfoIndex}}
	 *
	 * @return value of {@link #channelInfoIndex}
	 */
	public String getChannelInfoIndex() {
		return channelInfoIndex;
	}

	/**
	 * Sets {@code channelIndex}
	 *
	 * @param channelIndex the {@code java.lang.String} field
	 */
	public void setChannelInfoIndex(String channelIndex) {
		this.channelInfoIndex = channelIndex;
	}

	/**
	 * Retrieves {@code {@link #enableState}}
	 *
	 * @return value of {@link #enableState}
	 */
	public String getEnableState() {
		return enableState;
	}

	/**
	 * Sets {@code enableState}
	 *
	 * @param enableState the {@code java.lang.String} field
	 */
	public void setEnableState(String enableState) {
		this.enableState = enableState;
	}

	/**
	 * Retrieves {@code {@link #deviceName}}
	 *
	 * @return value of {@link #deviceName}
	 */
	public String getDeviceName() {
		return deviceName;
	}

	/**
	 * Sets {@code deviceName}
	 *
	 * @param deviceName the {@code java.lang.String} field
	 */
	public void setDeviceName(String deviceName) {
		this.deviceName = deviceName;
	}

	/**
	 * Retrieves {@code {@link #channelName}}
	 *
	 * @return value of {@link #channelName}
	 */
	public String getChannelName() {
		return channelName;
	}

	/**
	 * Sets {@code channelName}
	 *
	 * @param channelName the {@code java.lang.String} field
	 */
	public void setChannelName(String channelName) {
		this.channelName = channelName;
	}

	/**
	 * Retrieves {@code {@link #displayName}}
	 *
	 * @return value of {@link #displayName}
	 */
	public String getDisplayName() {
		return displayName;
	}

	/**
	 * Sets {@code displayName}
	 *
	 * @param displayName the {@code java.lang.String} field
	 */
	public void setDisplayName(String displayName) {
		this.displayName = displayName;
	}

	@Override
	public String toString() {
		return "ChannelInfo{" +
				"channelIndex='" + channelInfoIndex + '\'' +
				", enableState='" + enableState + '\'' +
				", deviceName='" + deviceName + '\'' +
				", channelName='" + channelName + '\'' +
				", displayName='" + displayName + '\'' +
				'}';
	}
}
