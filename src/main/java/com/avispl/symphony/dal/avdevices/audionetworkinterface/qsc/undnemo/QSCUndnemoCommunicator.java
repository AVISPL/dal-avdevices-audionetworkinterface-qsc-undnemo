/*
 * Copyright (c) 2022 AVI-SPL Inc. All Rights Reserved.
 */
package com.avispl.symphony.dal.avdevices.audionetworkinterface.qsc.undnemo;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import com.avispl.symphony.api.dal.control.Controller;
import com.avispl.symphony.api.dal.dto.control.AdvancedControllableProperty;
import com.avispl.symphony.api.dal.dto.control.ControllableProperty;
import com.avispl.symphony.api.dal.dto.monitor.ExtendedStatistics;
import com.avispl.symphony.api.dal.dto.monitor.Statistics;
import com.avispl.symphony.api.dal.error.ResourceNotReachableException;
import com.avispl.symphony.api.dal.monitor.Monitorable;
import com.avispl.symphony.dal.avdevices.audionetworkinterface.qsc.undnemo.dto.ChannelInfo;
import com.avispl.symphony.dal.avdevices.audionetworkinterface.qsc.undnemo.utils.QSCUndnemoConstant;
import com.avispl.symphony.dal.avdevices.audionetworkinterface.qsc.undnemo.utils.QSCUndnemoMetric;
import com.avispl.symphony.dal.avdevices.audionetworkinterface.qsc.undnemo.utils.QSCUndnemoUDPCommand;
import com.avispl.symphony.dal.util.StringUtils;

/**
 * QSC Attero Tech unDNEMO Adapter
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
 * Controlling:
 * <ul>
 * 	<li>Set Active Channel Index (1-64)</li>
 * 	<li>Set Speaker Mute (On/Off)</li>
 * 	<li>Set Volume (1-10)</li>
 * 	<li>Set Button Brightness (0-10)</li>
 * 	<li>Set Display Brightness (0-10)</li>
 * 	</ul
 *
 * @author Duy Nguyen
 * @version 1.0
 * @since 1.0
 */
public class QSCUndnemoCommunicator extends UDPCommunicator implements Monitorable, Controller {

	/**
	 * Process that is running whenever {@link QSCUndnemoCommunicator#getMultipleStatistics()} is called to fetch all 64 channel information.
	 * - The worker thread will be destroyed when all 64 channels are fetched successfully or {@link QSCUndnemoCommunicator#internalDestroy()} is called.
	 *
	 * @author Maksym.Rossiytsev, Duy Nguyen
	 * @since 1.0.0
	 */
	class QSCChannelDataLoader implements Runnable {

		private volatile boolean inProgress;

		private List<Integer> listIndexes;

		/**
		 * QSCChannelDataLoader with args constructor
		 *
		 * @param indexes list of index
		 */
		public QSCChannelDataLoader(List<Integer> indexes) {
			inProgress = true;
			listIndexes = indexes;
		}

		@Override
		public void run() {
			mainloop:
			while (inProgress) {
				if (!inProgress) {
					break mainloop;
				}
				try {
					retrieveChannelInfo(listIndexes);
				} catch (Exception e) {
					String errorMessage = String.format("Channel Info Data Retrieval-Error: %s with cause: %s", e.getMessage(), e.getCause().getMessage());
					channelErrorMessagesList.add(errorMessage);
					logger.error(errorMessage);
				}
				if (logger.isDebugEnabled()) {
					logger.debug("Finished collecting channel info statistics cycle at " + new Date());
				}
				// Finished collecting, stop the thread. It will be called again when getMultipleStatistics is called
				stop();
			}
		}

		/**
		 * Triggers main loop to stop
		 */
		public void stop() {
			inProgress = false;
		}
	}

	/**
	 * Executor that runs all the async operations, that {@link #channelDataLoader} is posting
	 */
	private static ExecutorService executorService;

	/**
	 * Runner service responsible for collecting data
	 */
	private QSCChannelDataLoader channelDataLoader;

	/**
	 * List of channel info
	 */
	private List<ChannelInfo> channelInfoList = Collections.synchronizedList(new ArrayList<>());

	/**
	 * List error message occur while fetching channel infos
	 */
	private Set<String> channelErrorMessagesList = Collections.synchronizedSet(new LinkedHashSet<>());

	/**
	 * This field is used to prevent fetching unnecessary data when perform {@link QSCUndnemoCommunicator#controlProperty(ControllableProperty)}
	 */
	private boolean isActiveChannelControl = false;

	/**
	 * This field is used to check if the {@link QSCUndnemoCommunicator#getMultipleStatistics()} -
	 * is called after {@link QSCUndnemoCommunicator#controlProperty(ControllableProperty)}
	 */
	private boolean isGetMultipleStatsAfterControl = false;

	/**
	 * Local extended statistics
	 */
	private ExtendedStatistics localExtendedStatistics;

	/**
	 * Adapter Properties - (Optional) filter option: string of channel indexes (separated by commas)
	 */
	private String channelIndex;

	/**
	 * Retrieves {@code {@link #channelIndex}}
	 *
	 * @return value of {@link #channelIndex}
	 */
	public String getChannelIndex() {
		return channelIndex;
	}

	/**
	 * Sets {@code channelIndex}
	 *
	 * @param channelIndex the {@code java.lang.String} field
	 */
	public void setChannelIndex(String channelIndex) {
		this.channelIndex = channelIndex;
	}

	/**
	 * Constructor set command error and success list that is required by {@link UDPCommunicator}
	 */
	public QSCUndnemoCommunicator() {
		// set buffer length because the response may exceed the default value.
		this.setBufferLength(100);

		this.setCommandSuccessList(Collections.singletonList(UDPCommunicator.getHexByteString(new byte[] { (byte) 0x00, 0x00, (byte) 0x00 })));
		this.setCommandErrorList(Collections.singletonList(
				UDPCommunicator.getHexByteString(new byte[] { (byte) 0x00, 0x00, (byte) 0x00 })
		));
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void internalInit() throws Exception {
		if (logger.isDebugEnabled()) {
			logger.debug("Internal init is called.");
		}
		super.internalInit();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void internalDestroy() {
		if (logger.isDebugEnabled()) {
			logger.debug("Internal destroy is called.");
		}

		if (channelDataLoader != null) {
			channelDataLoader.stop();
			channelDataLoader = null;
		}

		if (executorService != null) {
			executorService.shutdownNow();
			executorService = null;
		}
		channelInfoList.clear();
		channelErrorMessagesList.clear();
		super.internalDestroy();
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

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public List<Statistics> getMultipleStatistics() throws Exception {
		if (logger.isDebugEnabled()) {
			logger.debug(String.format("Perform retrieving statistics at host: %s with port: %s."
					+ " Is getMultipleStatistics after controlProperty: %s", this.host, this.port, isGetMultipleStatsAfterControl));
		}
		if (!channelErrorMessagesList.isEmpty()) {
			synchronized (channelErrorMessagesList) {
				String errorMessage = channelErrorMessagesList.stream().map(Object::toString)
						.collect(Collectors.joining("\n"));
				channelErrorMessagesList.clear();
				throw new ResourceNotReachableException(errorMessage);
			}
		}
		// Prevent fetching all 64 channels when performing control other properties and vice versa.
		if (localExtendedStatistics != null && isGetMultipleStatsAfterControl) {
			Map<String, String> localStatistics = localExtendedStatistics.getStatistics();
			List<AdvancedControllableProperty> localControls = localExtendedStatistics.getControllableProperties();
			if (!isActiveChannelControl) {
				// Update properties that are not relating to channel info
				populateOtherMonitoringAndControllingProperties(localStatistics, localControls);
			} else {
				isGetMultipleStatsAfterControl = false;
				return Collections.singletonList(localExtendedStatistics);
			}
			localExtendedStatistics.setStatistics(localStatistics);
			localExtendedStatistics.setControllableProperties(localControls);
			isGetMultipleStatsAfterControl = false;
			return Collections.singletonList(localExtendedStatistics);
		}
		ExtendedStatistics extendedStatistics = new ExtendedStatistics();
		Map<String, String> statistics = new HashMap<>();
		List<AdvancedControllableProperty> controls = new ArrayList<>();
		populateMonitoringAnControllingProperties(statistics, controls);
		extendedStatistics.setStatistics(statistics);
		extendedStatistics.setControllableProperties(controls);
		localExtendedStatistics = extendedStatistics;
		// Submit 4 threads to start fetching the channel info, each thread is responsible for 16 channels.
		List<Integer> filterChannelIndexValues = handleListChannelIndex();
		executorService = Executors.newFixedThreadPool(4);
		if (filterChannelIndexValues.isEmpty()) {
			submitThread(1, 16);
			submitThread(17, 32);
			submitThread(33, 48);
			submitThread(49, 64);
		} else {
			filterChannelInfo(filterChannelIndexValues);
		}
		return Collections.singletonList(localExtendedStatistics);
	}

	/**
	 * Prepare list of index before submitting.
	 *
	 * @param x begin index
	 * @param y end index
	 */
	private void submitThread(int x, int y) {
		List<Integer> listIndexes = new ArrayList<>();
		for (int i = x; i <= y; i++) {
			listIndexes.add(i);
		}
		executorService.submit(channelDataLoader = new QSCChannelDataLoader(listIndexes));
	}

	/**
	 * Get list of channel every 30 seconds
	 * UDP Command: CH_INFO + index of the channel
	 * Total request every 30 seconds: 64 requests
	 * Success: populate data for {@link #channelInfoList}
	 *
	 * @param listIndexes list of indexes
	 * @throws Exception if fail to get response
	 */
	private void retrieveChannelInfo(List<Integer> listIndexes) throws Exception {
		for (int i = 0; i <= listIndexes.size(); i++) {
			String rawChannelInfos = getUDPResponse(QSCUndnemoUDPCommand.GET_CMD_CH_INFO.getCommand() + QSCUndnemoConstant.SPACE + listIndexes.get(i));
			if (rawChannelInfos.contains(QSCUndnemoConstant.ACK)) {
				String[] channelInfos = parseUDPResponse(rawChannelInfos);
				String channelInfoIndex = channelInfos[0];
				String enableState = channelInfos[1];
				String deviceName = channelInfos[2];
				deviceName = deviceName.replace(QSCUndnemoConstant.QUOTE, QSCUndnemoConstant.EMPTY);
				String channelName = channelInfos[3];
				channelName = channelName.replace(QSCUndnemoConstant.QUOTE, QSCUndnemoConstant.EMPTY);
				String displayName = channelInfos[4];
				displayName = displayName.replace(QSCUndnemoConstant.QUOTE, QSCUndnemoConstant.EMPTY);
				ChannelInfo channelInfo = new ChannelInfo(channelInfoIndex, enableState, deviceName, channelName, displayName);
				channelInfoList.add(channelInfo);
			}
		}
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
		populateOtherMonitoringAndControllingProperties(stats, controls);
		populateChannelInfoMonitoringAndControllingProperties(stats, controls);
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
		// Only populate data to stats if channelInfoList is full of 64 channels data
		if (channelInfoList.size() == 64 || !handleListChannelIndex().isEmpty()) {
			String rawCurrentActiveChannelIndex = getUDPResponse(QSCUndnemoUDPCommand.GET_CMD_ACT_CH_IDX.getCommand());
			if (rawCurrentActiveChannelIndex.contains(QSCUndnemoConstant.NACK)) {
				if (logger.isDebugEnabled()) {
					logger.debug("Fail to get active channel index");
				}
				return;
			}
			String currentActiveChannelIndex = parseUDPResponse(rawCurrentActiveChannelIndex)[0];
			stats.put(QSCUndnemoMetric.ACTIVE_CHANNEL_INDEX.getName(), currentActiveChannelIndex);
			List<String> values = new ArrayList<>();
			for (int i = 1; i <= 64; i++) {
				values.add(String.valueOf(i));
			}
			controls.add(createDropdown(QSCUndnemoMetric.ACTIVE_CHANNEL_INDEX.getName(), values, currentActiveChannelIndex));
			synchronized (channelInfoList) {
				for (ChannelInfo channelInfo : channelInfoList
				) {
					String activeChannelIndex = stats.get(QSCUndnemoMetric.ACTIVE_CHANNEL_INDEX.getName());
					String groupName;
					if (channelInfo.getChannelInfoIndex().equals(activeChannelIndex)) {
						groupName = String.format("(00)%s", QSCUndnemoConstant.ACTIVE_CHANNEL);
					} else {
						if (Integer.parseInt(channelInfo.getChannelInfoIndex()) <= 9) {
							groupName = String.format("(0%s)Channel %s", channelInfo.getChannelInfoIndex(), channelInfo.getChannelInfoIndex());
						} else {
							groupName = String.format("(%s)Channel %s", channelInfo.getChannelInfoIndex(), channelInfo.getChannelInfoIndex());
						}
					}
					stats.put(String.format("%s#%s", groupName, QSCUndnemoMetric.CHANNEL_INFO_ENABLE_STATE.getName()), channelInfo.getEnableState());
					stats.put(String.format("%s#%s", groupName, QSCUndnemoMetric.CHANNEL_INFO_DEVICE_NAME.getName()), channelInfo.getDeviceName());
					stats.put(String.format("%s#%s", groupName, QSCUndnemoMetric.CHANNEL_INFO_CHANNEL_NAME.getName()), channelInfo.getChannelName());
					stats.put(String.format("%s#%s", groupName, QSCUndnemoMetric.CHANNEL_INFO_DISPLAY_NAME.getName()), channelInfo.getDisplayName());
				}
			}
			if (logger.isDebugEnabled()) {
				logger.debug("Clearing channel info list");
			}
			channelInfoList.clear();
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
	 * Split channelIndex (separated by commas) to array of indexes
	 *
	 * @return list int of channel indexes
	 */
	private List<Integer> handleListChannelIndex() {
		if (!StringUtils.isNullOrEmpty(channelIndex) && !QSCUndnemoConstant.DOUBLE_QUOTES.equals(channelIndex)) {
			try {
				List<Integer> resultList = new ArrayList<>();
				String[] listIndex = this.getChannelIndex().split(QSCUndnemoConstant.COMMA);
				for (String index : listIndex) {
					String trimIndex = index.trim();
					if (trimIndex.matches(QSCUndnemoConstant.REGEX_IS_INTEGER)) {
						resultList.add(Integer.parseInt(trimIndex));
					}
				}
				return resultList;
			} catch (Exception e) {
				throw new IllegalArgumentException("Fail to split string, input from adapter properties is wrong", e);
			}
		}
		return Collections.emptyList();
	}

	/**
	 * Filter list of channel info based on channel indexes
	 *
	 * @param filterChannelIndexValues list of indexes
	 * @throws Exception when fail to get active channel index
	 */
	private void filterChannelInfo(List<Integer> filterChannelIndexValues) throws Exception {
		if (logger.isDebugEnabled()) {
			logger.debug(String.format("Applying channel index filter with values(s): %s", channelIndex));
		}
		String rawCurrentActiveChannelIndex = getUDPResponse(QSCUndnemoUDPCommand.GET_CMD_ACT_CH_IDX.getCommand());
		if (rawCurrentActiveChannelIndex.contains(QSCUndnemoConstant.ACK)) {
			int activeChannelIndex = Integer.parseInt(parseUDPResponse(rawCurrentActiveChannelIndex)[0]);
			if (!filterChannelIndexValues.contains(activeChannelIndex)) {
				filterChannelIndexValues.add(activeChannelIndex);
			}
		}
		executorService.submit(channelDataLoader = new QSCChannelDataLoader(filterChannelIndexValues));
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