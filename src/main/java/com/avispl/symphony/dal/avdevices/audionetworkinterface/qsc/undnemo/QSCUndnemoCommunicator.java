/*
 * Copyright (c) 2022 AVI-SPL Inc. All Rights Reserved.
 */
package com.avispl.symphony.dal.avdevices.audionetworkinterface.qsc.undnemo;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.util.CollectionUtils;

import com.avispl.symphony.api.dal.control.Controller;
import com.avispl.symphony.api.dal.dto.control.AdvancedControllableProperty;
import com.avispl.symphony.api.dal.dto.control.ControllableProperty;
import com.avispl.symphony.api.dal.dto.monitor.ExtendedStatistics;
import com.avispl.symphony.api.dal.dto.monitor.Statistics;
import com.avispl.symphony.api.dal.monitor.Monitorable;
import com.avispl.symphony.dal.avdevices.audionetworkinterface.qsc.undnemo.utils.QSCUndnemoConstant;
import com.avispl.symphony.dal.avdevices.audionetworkinterface.qsc.undnemo.utils.QSCUndnemoMetric;
import com.avispl.symphony.dal.avdevices.audionetworkinterface.qsc.undnemo.utils.QSCUndnemoUDPCommand;

/**
 * QSC Attero Tech unDNEMO Adapter
 *
 * Controlling:
 * <ul>
 * 	<li>Set Active Channel Index (1-64)</li>
 * 	<li>Set Speaker Mute (On/Off)</li>
 * 	<li>Set Volume (1-10)</li>
 * 	<li>Set Button Brightness (0-10)</li>
 * 	<li>Set Display Brightness (0-10)</li>
 * 	</ul
 *
 * Monitoring:
 * <ul>
 * 	<li>Online / Offline Status</li>
 * 	<li>Version Info</li>
 * 	<li>Channel Info (channels 1-64)</li>
 * 	<li>Active Channel Index (1-64)</li>
 * 	<li>Speaker Mute (On/Off)</li>
 * 	<li>Volume (1-10)</li>
 * 	<li>Button Brightness (0-10)</li>
 * 	<li>Display Brightness (0-10)</li>
 * </ul>
 *
 * @author Duy Nguyen
 * @version 1.0
 * @since 1.0
 */
public class QSCUndnemoCommunicator extends UDPCommunicator implements Monitorable, Controller {

	private ExtendedStatistics localExtendedStatistics;

	/**
	 * Constructor set command error and success list that is required by {@link UDPCommunicator}
	 */
	public QSCUndnemoCommunicator() {
		// set buffer length because the response may exceed the default value.
		this.setBufferLength(100);
		this.setCommandSuccessList(Collections.singletonList(UDPCommunicator.getHexByteString(new byte[] { (byte) 0x00, 0x00, (byte) 0x00 })));

		// set list of error response strings (included at the end of response when command fails, typically ending with command prompt)
		this.setCommandErrorList(Collections.singletonList(
				UDPCommunicator.getHexByteString(new byte[] { (byte) 0x00, 0x00, (byte) 0x00 })
		));
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void controlProperty(ControllableProperty controllableProperty) {

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void controlProperties(List<ControllableProperty> list) {
		if (CollectionUtils.isEmpty(list)) {
			throw new IllegalArgumentException("Controllable properties cannot be null or empty");
		}
		for (ControllableProperty controllableProperty : list) {
			controlProperty(controllableProperty);
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public List<Statistics> getMultipleStatistics() throws Exception {
		if (logger.isDebugEnabled()) {
			logger.debug(String.format("Perform retrieving statistics at host: %s with port: %s", this.host, this.port));
		}
		ExtendedStatistics extendedStatistics = new ExtendedStatistics();
		Map<String, String> statistics = new HashMap<>();
		List<AdvancedControllableProperty> controls = new ArrayList<>();
		populateMonitoringAnControllingProperties(statistics, controls);
		extendedStatistics.setStatistics(statistics);
		extendedStatistics.setControllableProperties(controls);
		localExtendedStatistics = extendedStatistics;
		return Collections.singletonList(localExtendedStatistics);
	}

	/**
	 * Populate monitoring and controlling properties
	 * Number of request per monitoring cycle: 70 requests
	 *
	 * @param stats Map of statistics
	 * @param controls List of AdvancedControllableProperty
	 * @throws Exception when fail to get UDP response {@link QSCUndnemoCommunicator#getUDPResponse(String)}
	 */
	private void populateMonitoringAnControllingProperties(Map<String, String> stats, List<AdvancedControllableProperty> controls) throws Exception {
		populateChannelInfoMonitoringAndControllingProperties(stats, controls);
		populateOtherMonitoringAndControllingProperties(stats, controls);
	}

	/**
	 * Populate other properties: Version Info, Speaker Mute, Volume, Button Brightness, Display Brightness
	 * Number of request per monitoring cycle: 5
	 *
	 * @param stats Map of statistics
	 * @param controls list of AdvancedControllableProperty
	 * @throws Exception when fail to get UDP response
	 */
	private void populateOtherMonitoringAndControllingProperties(Map<String, String> stats, List<AdvancedControllableProperty> controls) throws Exception {
		if (logger.isDebugEnabled()) {
			logger.debug("Populating data for Version Info, Speaker Mute, Volume, Button Brightness, Display Brightness");
		}
		String rawVersionInfoUDPResponse = getUDPResponse(QSCUndnemoUDPCommand.GET_CMD_VERSION.getCommand());
		if (rawVersionInfoUDPResponse.contains(QSCUndnemoConstant.ACK)) {
			stats.put(QSCUndnemoMetric.SOFTWARE_VERSION_INFO.getName(), parseUDPResponse(rawVersionInfoUDPResponse)[0]);
		}

		String rawCurrentSpeakerMuteStatus = getUDPResponse(QSCUndnemoUDPCommand.GET_CMD_SPKR_MUTE.getCommand());
		if (rawCurrentSpeakerMuteStatus.contains(QSCUndnemoConstant.ACK)) {
			String currentSpeakerMuteStatus = parseUDPResponse(rawCurrentSpeakerMuteStatus)[0].trim();
			stats.put(QSCUndnemoMetric.SPEAKER_MUTE.getName(), currentSpeakerMuteStatus);
			controls.add(createSwitch(QSCUndnemoMetric.SPEAKER_MUTE.getName(), Integer.parseInt(currentSpeakerMuteStatus), "Off", "On"));
		}

		String rawCurrentVolume = getUDPResponse(QSCUndnemoUDPCommand.GET_CMD_VOLUME.getCommand());
		if (rawCurrentVolume.contains(QSCUndnemoConstant.ACK)) {
			String currentVolume = parseUDPResponse(rawCurrentVolume)[0];
			stats.put(QSCUndnemoMetric.VOLUME.getName(), currentVolume);
			controls.add(createSlider(QSCUndnemoMetric.VOLUME.getName(), "1", "10", 1f, 10f, Float.valueOf(currentVolume)));
		}

		String rawCurrentButtonBrightnessValue = getUDPResponse(QSCUndnemoUDPCommand.GET_CMD_GBB.getCommand());
		if (rawCurrentButtonBrightnessValue.contains(QSCUndnemoConstant.ACK)) {
			String currentButtonBrightnessValue = parseUDPResponse(rawCurrentButtonBrightnessValue)[0];
			stats.put(QSCUndnemoMetric.BUTTON_BRIGHTNESS.getName(), currentButtonBrightnessValue);
			controls.add(createSlider(QSCUndnemoMetric.BUTTON_BRIGHTNESS.getName(), "0", "10", 0f, 10f, Float.valueOf(currentButtonBrightnessValue)));
		}

		String rawCurrentDisplayBrightnessValue = getUDPResponse(QSCUndnemoUDPCommand.GET_CMD_GDB.getCommand());
		if (rawCurrentDisplayBrightnessValue.contains(QSCUndnemoConstant.ACK)) {
			String currentDisplayBrightnessValue = parseUDPResponse(rawCurrentDisplayBrightnessValue)[0];
			stats.put(QSCUndnemoMetric.DISPLAY_BRIGHTNESS.getName(), currentDisplayBrightnessValue);
			controls.add(createSlider(QSCUndnemoMetric.DISPLAY_BRIGHTNESS.getName(), "0", "10", 0f, 10f, Float.valueOf(currentDisplayBrightnessValue)));
		}
	}

	/**
	 * Populate channel info properties: Active channel index, channel info
	 * Number of request per monitoring cycle: 65 (64 requests for channel info and 1 for getting active channel index)
	 *
	 * @param stats Map of statistics
	 * @param controls list of AdvancedControllableProperty
	 * @throws Exception when fail to get UDP response
	 */
	private void populateChannelInfoMonitoringAndControllingProperties(Map<String, String> stats, List<AdvancedControllableProperty> controls) throws Exception {
		if (logger.isDebugEnabled()) {
			logger.debug("Populating data for channel info and active channel index");
		}
		String rawCurrentActiveChannelIndex = getUDPResponse(QSCUndnemoUDPCommand.GET_CMD_ACT_CH_IDX.getCommand());
		if (rawCurrentActiveChannelIndex.contains(QSCUndnemoConstant.ACK)) {
			String currentActiveChannelIndex = parseUDPResponse(rawCurrentActiveChannelIndex)[0];
			stats.put(QSCUndnemoMetric.ACTIVE_CHANNEL_INDEX.getName(), currentActiveChannelIndex);
			List<String> values = new ArrayList<>();
			for (int i = 1; i < 65; i++) {
				values.add(String.valueOf(i));
			}
			controls.add(createDropdown(QSCUndnemoMetric.ACTIVE_CHANNEL_INDEX.getName(), values, currentActiveChannelIndex));
		}
		// TODO: need uniFYControlPanel to set up the channel (channel name, channel states, ...)
		for (int i = 1; i <= 64; i++) {
			String rawChannelInfos = getUDPResponse(QSCUndnemoUDPCommand.GET_CMD_CH_INFO.getCommand() + QSCUndnemoConstant.SPACE + i);
			if (rawChannelInfos.contains(QSCUndnemoConstant.ACK)) {
				String[] channelInfos = parseUDPResponse(rawChannelInfos);
				String channelIndex = channelInfos[0];
				String activeChannelIndex = stats.get(QSCUndnemoMetric.ACTIVE_CHANNEL_INDEX.getName());
				String groupName;
				if (channelIndex.equals(activeChannelIndex)) {
					groupName = String.format("(00)%s", QSCUndnemoConstant.ACTIVE_CHANNEL);
				} else {
					if (i <= 9) {
						groupName = String.format("(0%s)Channel %s", i, channelIndex);
					} else {
						groupName = String.format("(%s)Channel %s", i, channelIndex);
					}
				}
				String enableState = channelInfos[1];
				String deviceName = channelInfos[2];
				String channelName = channelInfos[3];
				String displayName = channelInfos[4];
				stats.put(String.format("%s#%s", groupName, QSCUndnemoMetric.CHANNEL_INFO_ENABLE_STATE.getName()), enableState);
				stats.put(String.format("%s#%s", groupName, QSCUndnemoMetric.CHANNEL_INFO_DEVICE_NAME.getName()), deviceName);
				stats.put(String.format("%s#%s", groupName, QSCUndnemoMetric.CHANNEL_INFO_CHANNEL_NAME.getName()), channelName);
				stats.put(String.format("%s#%s", groupName, QSCUndnemoMetric.CHANNEL_INFO_DISPLAY_NAME.getName()), displayName);
			}
		}
	}

	/**
	 * Send UDP Command
	 *
	 * @param command String UDP command
	 * @return String of response from the UDP server
	 * @throws Exception when fail to send UDP command
	 */
	private String getUDPResponse(String command) throws Exception {
		command += QSCUndnemoConstant.CR;
		byte[] bytes = command.getBytes();
		byte[] response = this.send(bytes);
		String result = new String(response, StandardCharsets.UTF_8);
		if (result.startsWith(QSCUndnemoConstant.ACK)) {
			return result;
		}
		return QSCUndnemoConstant.NACK;
	}

	/**
	 * Parse the response to proper format
	 *
	 * @param inputString String response from the API
	 * @return Array of String with proper format
	 */
	private String[] parseUDPResponse(String inputString) {
		String[] splitString = inputString.split(QSCUndnemoConstant.SPACE);
		List<String> resultStrings = new ArrayList<>();
		if (inputString.contains(QSCUndnemoConstant.CH_INFO)) {
			parseChannelInfo(inputString, splitString[2], resultStrings);
		} else {
			splitString[2] = splitString[2].replace(QSCUndnemoConstant.CR, QSCUndnemoConstant.EMPTY);
			resultStrings.add(splitString[2]);
		}
		return resultStrings.toArray(new String[resultStrings.size()]);
	}

	/**
	 * Parse raw response to channel info.
	 *
	 * @param inputString Raw string from UDP response
	 * @param enableState State of current channel
	 * @param resultStrings List of result channel info.
	 */
	private void parseChannelInfo(String inputString, String enableState, List<String> resultStrings) {
		int openParenIdx = inputString.indexOf(QSCUndnemoConstant.OPEN_PAREN);
		int closeParenIdx = inputString.indexOf(QSCUndnemoConstant.CLOSE_PAREN);
		String channelId = inputString.substring(openParenIdx + 1, closeParenIdx);
		resultStrings.add(channelId);
		List<Integer> indexList = new ArrayList<>();
		for (int index = inputString.indexOf(QSCUndnemoConstant.QUOTE);
				index >= 0;
				index = inputString.indexOf(QSCUndnemoConstant.QUOTE, index + 1)) {
			indexList.add(index);
		}
		if (indexList.size() == 6) {
			String deviceName = inputString.substring(indexList.get(0), indexList.get(1) + 1);
			String channelName = inputString.substring(indexList.get(2), indexList.get(3) + 1);
			String displayName = inputString.substring(indexList.get(4), indexList.get(5) + 1);
			resultStrings.add(enableState);
			resultStrings.add(deviceName);
			resultStrings.add(channelName);
			resultStrings.add(displayName);
		}
	}

	/**
	 * Create slider
	 *
	 * @param name String Name of the slider
	 * @param labelStart String start label of the slider
	 * @param labelEnd String end label of the slider
	 * @param rangeStart Float range start
	 * @param rangeEnd Float range end
	 * @param initialValue Float initial value
	 * @return Instance of AdvancedControllableProperty
	 */
	private AdvancedControllableProperty createSlider(String name, String labelStart, String labelEnd, Float rangeStart, Float rangeEnd, Float initialValue) {
		AdvancedControllableProperty.Slider slider = new AdvancedControllableProperty.Slider();
		slider.setLabelStart(labelStart);
		slider.setLabelEnd(labelEnd);
		slider.setRangeStart(rangeStart);
		slider.setRangeEnd(rangeEnd);

		return new AdvancedControllableProperty(name, new Date(), slider, initialValue);
	}

	/**
	 * Create switch
	 *
	 * @param name String name of the switch
	 * @param status status of the switch (On/Off)
	 * @param labelOff String - Off label
	 * @param labelOn String - On label
	 * @return Instance of AdvancedControllableProperty
	 */
	private AdvancedControllableProperty createSwitch(String name, int status, String labelOff, String labelOn) {
		AdvancedControllableProperty.Switch toggle = new AdvancedControllableProperty.Switch();
		toggle.setLabelOff(labelOff);
		toggle.setLabelOn(labelOn);

		return new AdvancedControllableProperty(name, new Date(), toggle, status);
	}

	/**
	 * Create drop-down
	 *
	 * @param name String name of the drop-down
	 * @param values List of values
	 * @param initialValue String initial value
	 * @return Instance of AdvancedControllableProperty
	 */
	private AdvancedControllableProperty createDropdown(String name, List<String> values, String initialValue) {
		AdvancedControllableProperty.DropDown dropDown = new AdvancedControllableProperty.DropDown();
		dropDown.setOptions(values.toArray(new String[0]));
		dropDown.setLabels(values.toArray(new String[0]));

		return new AdvancedControllableProperty(name, new Date(), dropDown, initialValue);
	}

}
