/*
 * Copyright (c) 2022 AVI-SPL Inc. All Rights Reserved.
 */
package com.avispl.symphony.dal.avdevices.audionetworkinterface.qsc.undnemo;

import java.nio.charset.StandardCharsets;
import java.time.temporal.ValueRange;
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
import java.util.stream.IntStream;

import com.avispl.symphony.dal.avdevices.audionetworkinterface.qsc.undnemo.dto.ChannelInfo;
import com.avispl.symphony.dal.avdevices.audionetworkinterface.qsc.undnemo.utils.QSCUndnemoConstant;
import com.avispl.symphony.dal.avdevices.audionetworkinterface.qsc.undnemo.utils.QSCUndnemoMetric;
import com.avispl.symphony.dal.avdevices.audionetworkinterface.qsc.undnemo.utils.QSCUndnemoUDPCommand;

import org.springframework.util.CollectionUtils;

import com.avispl.symphony.api.dal.control.Controller;
import com.avispl.symphony.api.dal.dto.control.AdvancedControllableProperty;
import com.avispl.symphony.api.dal.dto.control.ControllableProperty;
import com.avispl.symphony.api.dal.dto.monitor.ExtendedStatistics;
import com.avispl.symphony.api.dal.dto.monitor.Statistics;
import com.avispl.symphony.api.dal.error.CommandFailureException;
import com.avispl.symphony.api.dal.error.ResourceNotReachableException;
import com.avispl.symphony.api.dal.monitor.Monitorable;
import com.avispl.symphony.dal.util.StringUtils;

/**
 * QSC Attero Tech unDNEMO Adapter
 * <p>
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
 * <p>
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

		private final List<Integer> listIndexes;

		/**
		 * QSCChannelDataLoader with args constructor
		 *
		 * @param indexes list of index
		 */
		public QSCChannelDataLoader(List<Integer> indexes) {
			listIndexes = indexes;
		}

		@Override
		public void run() {
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
	private final List<ChannelInfo> channelInfoList = Collections.synchronizedList(new ArrayList<>());

	/**
	 * List error message occur while fetching channel infos
	 */
	private final Set<String> channelErrorMessagesList = Collections.synchronizedSet(new LinkedHashSet<>());

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
		this.setBufferLength(200);

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
			channelDataLoader = null;
		}

		if (executorService != null) {
			executorService.shutdownNow();
		}
		channelInfoList.clear();
		channelErrorMessagesList.clear();
		localExtendedStatistics = null;
		super.internalDestroy();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void controlProperty(ControllableProperty controllableProperty) throws Exception {
		isGetMultipleStatsAfterControl = true;
		String property = controllableProperty.getProperty();
		String value = String.valueOf(controllableProperty.getValue());
		if (logger.isDebugEnabled()) {
			logger.debug(String.format("Perform control operation with property: %s and value: %s", property, value));
		}
		QSCUndnemoMetric qscUndnemoMetric = getQSCUndnemoControllingMetric(property);
		String response;
		switch (qscUndnemoMetric) {
			case ACTIVE_CHANNEL_INDEX:
				isActiveChannelControl = true;
				if (value.equals(QSCUndnemoConstant.NONE)) {
					return;
				}
				String rawCurrentActiveChannelIndex = getUDPResponse(QSCUndnemoUDPCommand.GET_CMD_ACT_CH_IDX.getCommand());
				String currentActiveChannelIndex = getValidActiveChannelIndex(rawCurrentActiveChannelIndex);
				if (value.equals(currentActiveChannelIndex)) {
					return;
				}
				List<Integer> indexList = handleListChannelIndex();
				if (indexList.isEmpty()) {
					if (currentActiveChannelIndex.equals("0")) {
						handleActiveChannelControlWithNoneCurrentIndex(value);
					} else {
						handleActiveChannelControl(value, currentActiveChannelIndex);
					}
				} else {
					handleActiveChannelControlWithFilter(value, indexList);
				}
				break;
			case BUTTON_BRIGHTNESS:
				isActiveChannelControl = false;
				float flValue = Float.parseFloat(value);
				int intValue = Math.round(flValue);
				response = getUDPResponse(QSCUndnemoUDPCommand.SET_SBB.getCommand() + QSCUndnemoConstant.SPACE + intValue);
				if (QSCUndnemoConstant.NACK.equals(response)) {
					throw new CommandFailureException(this.getAddress(), QSCUndnemoUDPCommand.SET_SBB.getCommand(), String.format("Fail to set button brightness with value: %s", value));
				}
				break;
			case DISPLAY_BRIGHTNESS:
				isActiveChannelControl = false;
				float flValue1 = Float.parseFloat(value);
				int intValue1 = Math.round(flValue1);
				response = getUDPResponse(QSCUndnemoUDPCommand.SET_SDB.getCommand() + QSCUndnemoConstant.SPACE + intValue1);
				if (QSCUndnemoConstant.NACK.equals(response)) {
					throw new CommandFailureException(this.getAddress(), QSCUndnemoUDPCommand.SET_SDB.getCommand(), String.format("Fail to set display brightness with value: %s", value));
				}
				break;
			case SPEAKER_MUTE:
				isActiveChannelControl = false;
				response = getUDPResponse(QSCUndnemoUDPCommand.SET_SPKR_MUTE.getCommand() + QSCUndnemoConstant.SPACE + value);
				if (QSCUndnemoConstant.NACK.equals(response)) {
					throw new CommandFailureException(this.getAddress(), QSCUndnemoUDPCommand.SET_SPKR_MUTE.getCommand(), String.format("Fail to set speaker mute with value: %s", value));
				}
				break;
			case VOLUME:
				isActiveChannelControl = false;
				float flValue2 = Float.parseFloat(value);
				int intValue2 = Math.round(flValue2);
				response = getUDPResponse(QSCUndnemoUDPCommand.SET_VOLUME.getCommand() + QSCUndnemoConstant.SPACE + intValue2);
				if (QSCUndnemoConstant.NACK.equals(response)) {
					throw new CommandFailureException(this.getAddress(), QSCUndnemoUDPCommand.SET_VOLUME.getCommand(), String.format("Fail to set volume with value: %s", value));
				}
				break;
			default:
				if (logger.isWarnEnabled()) {
					logger.warn(String.format("Operation %s with value %s is not supported.", property, value));
				}
				throw new IllegalArgumentException(String.format("Operation %s with value %s is not supported.", property, value));
		}
		if (logger.isDebugEnabled()) {
			logger.debug("End control operation");
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void controlProperties(List<ControllableProperty> list) throws Exception {
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
				localExtendedStatistics.setStatistics(localStatistics);
				localExtendedStatistics.setControllableProperties(localControls);
			}
			// else condition: data already be handled in handleActiveChannelControl/handleActiveChannelControlWithFilter
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
	 * Handle control active channel index. This method is used to handle every statistics locally -
	 * to save the number of requests made to the device.
	 *
	 * @param value value of the new index
	 * @param currentActiveChannelIndex current active channel index
	 * @throws Exception if fail to get UDP response
	 */
	private void handleActiveChannelControl(String value, String currentActiveChannelIndex) throws Exception {
		int intValue = Integer.parseInt(value);
		String response = getUDPResponse(QSCUndnemoUDPCommand.SET_ACT_CH_IDX.getCommand() + QSCUndnemoConstant.SPACE + intValue);
		if (QSCUndnemoConstant.NACK.equals(response)) {
			throw new CommandFailureException(this.getAddress(), QSCUndnemoUDPCommand.SET_ACT_CH_IDX.getCommand(), String.format("Fail to set active channel index with value: %s", value));
		}
		// From this line to the end of the method is used to switch places between two groups of stat/control "old active channel" and "new active channel" -
		// This is because we want to prevent unnecessary fetch all 64 channels.
		Map<String, String> stats = localExtendedStatistics.getStatistics();
		String newActiveIndexKey = String.format("Channel %02d", intValue);
		String oldActiveIndexKey = QSCUndnemoConstant.ACTIVE_CHANNEL;
		String oldActiveIndexEnableState = stats.get(String.format("%s#%s", oldActiveIndexKey, QSCUndnemoMetric.CHANNEL_INFO_ENABLE_STATE.getName()));
		String oldActiveIndexDeviceName = stats.get(String.format("%s#%s", oldActiveIndexKey, QSCUndnemoMetric.CHANNEL_INFO_DEVICE_NAME.getName()));
		String oldActiveIndexChannelName = stats.get(String.format("%s#%s", oldActiveIndexKey, QSCUndnemoMetric.CHANNEL_INFO_CHANNEL_NAME.getName()));
		String oldActiveIndexDisplayName = stats.get(String.format("%s#%s", oldActiveIndexKey, QSCUndnemoMetric.CHANNEL_INFO_DISPLAY_NAME.getName()));
		String oldActiveIndexGroupName;
		int intCurrentActiveChannelIndex = Integer.parseInt(currentActiveChannelIndex);
		oldActiveIndexGroupName = String.format("Channel %02d", intCurrentActiveChannelIndex);
		String newActiveIndexEnableStatKey = String.format("%s#%s", newActiveIndexKey, QSCUndnemoMetric.CHANNEL_INFO_ENABLE_STATE.getName());
		String newActiveIndexDeviceNameKey = String.format("%s#%s", newActiveIndexKey, QSCUndnemoMetric.CHANNEL_INFO_DEVICE_NAME.getName());
		String newActiveIndexChannelNameKey = String.format("%s#%s", newActiveIndexKey, QSCUndnemoMetric.CHANNEL_INFO_CHANNEL_NAME.getName());
		String newActiveIndexDisplayNameKey = String.format("%s#%s", newActiveIndexKey, QSCUndnemoMetric.CHANNEL_INFO_DISPLAY_NAME.getName());

		String newActiveIndexEnableState = stats.get(newActiveIndexEnableStatKey);
		String newActiveIndexDeviceName = stats.get(newActiveIndexDeviceNameKey);
		String newActiveIndexChannelName = stats.get(newActiveIndexChannelNameKey);
		String newActiveIndexDisplayName = stats.get(newActiveIndexDisplayNameKey);

		String groupName = QSCUndnemoConstant.ACTIVE_CHANNEL;
		// Remove previous non-active group.
		stats.remove(newActiveIndexEnableStatKey);
		stats.remove(newActiveIndexDeviceNameKey);
		stats.remove(newActiveIndexChannelNameKey);
		stats.remove(newActiveIndexDisplayNameKey);

		String formattedValue = String.format("%02d", intValue);
		List<AdvancedControllableProperty> controllableProperties = localExtendedStatistics.getControllableProperties();
		// Update dropdown list without None value
		controllableProperties.removeIf(control -> control.getName().equals(QSCUndnemoMetric.ACTIVE_CHANNEL_INDEX.getName()));
		List<String> values = new ArrayList<>();
		int[] intArray = IntStream.rangeClosed(1, 64).toArray();
		for (int j : intArray) {
			values.add(String.format("%02d", j));
		}
		controllableProperties.add(createDropdown(QSCUndnemoMetric.ACTIVE_CHANNEL_INDEX.getName(), values, formattedValue));
		// Put statistics for ActiveChannel group
		stats.put(QSCUndnemoMetric.ACTIVE_CHANNEL_INDEX.getName(), formattedValue);
		stats.put(String.format("%s#%s", groupName, QSCUndnemoMetric.CHANNEL_INFO_ENABLE_STATE.getName()), newActiveIndexEnableState);
		stats.put(String.format("%s#%s", groupName, QSCUndnemoMetric.CHANNEL_INFO_DEVICE_NAME.getName()), newActiveIndexDeviceName);
		stats.put(String.format("%s#%s", groupName, QSCUndnemoMetric.CHANNEL_INFO_CHANNEL_NAME.getName()), newActiveIndexChannelName);
		stats.put(String.format("%s#%s", groupName, QSCUndnemoMetric.CHANNEL_INFO_DISPLAY_NAME.getName()), newActiveIndexDisplayName);

		// Put statistics for new Channel group
		stats.put(String.format("%s#%s", oldActiveIndexGroupName, QSCUndnemoMetric.CHANNEL_INFO_ENABLE_STATE.getName()), oldActiveIndexEnableState);
		stats.put(String.format("%s#%s", oldActiveIndexGroupName, QSCUndnemoMetric.CHANNEL_INFO_DEVICE_NAME.getName()), oldActiveIndexDeviceName);
		stats.put(String.format("%s#%s", oldActiveIndexGroupName, QSCUndnemoMetric.CHANNEL_INFO_CHANNEL_NAME.getName()), oldActiveIndexChannelName);
		stats.put(String.format("%s#%s", oldActiveIndexGroupName, QSCUndnemoMetric.CHANNEL_INFO_DISPLAY_NAME.getName()), oldActiveIndexDisplayName);

		localExtendedStatistics.setStatistics(stats);
		localExtendedStatistics.setControllableProperties(controllableProperties);
	}

	/**
	 * Handle control active channel index when currentActiveChannelIndex = 0. In this case, ActiveChannel group only -
	 * contains a dropdown list with default value = None.
	 * to save the number of requests made to the device.
	 *
	 * @param value value of the new index
	 * @throws Exception if fail to get UDP response
	 */
	private void handleActiveChannelControlWithNoneCurrentIndex(String value) throws Exception {
		int intValue = Integer.parseInt(value);
		String response = getUDPResponse(QSCUndnemoUDPCommand.SET_ACT_CH_IDX.getCommand() + QSCUndnemoConstant.SPACE + intValue);
		if (QSCUndnemoConstant.NACK.equals(response)) {
			throw new CommandFailureException(this.getAddress(), QSCUndnemoUDPCommand.SET_ACT_CH_IDX.getCommand(), String.format("Fail to set active channel index with value: %s", value));
		}
		Map<String, String> stats = localExtendedStatistics.getStatistics();
		List<AdvancedControllableProperty> controls = localExtendedStatistics.getControllableProperties();
		String formattedValue = String.format("%02d", intValue);
		// Update dropdown list without None value
		controls.removeIf(control -> control.getName().equals(QSCUndnemoMetric.ACTIVE_CHANNEL_INDEX.getName()));
		List<String> values = new ArrayList<>();
		int[] intArray = IntStream.rangeClosed(1, 64).toArray();
		for (int j : intArray) {
			values.add(String.format("%02d", j));
		}
		controls.add(createDropdown(QSCUndnemoMetric.ACTIVE_CHANNEL_INDEX.getName(), values, formattedValue));
		// Switch places between ActiveChannel and Channel group.
		String newActiveIndexKey = String.format("Channel %02d", intValue);
		String newActiveIndexEnableStatKey = String.format("%s#%s", newActiveIndexKey, QSCUndnemoMetric.CHANNEL_INFO_ENABLE_STATE.getName());
		String newActiveIndexDeviceNameKey = String.format("%s#%s", newActiveIndexKey, QSCUndnemoMetric.CHANNEL_INFO_DEVICE_NAME.getName());
		String newActiveIndexChannelNameKey = String.format("%s#%s", newActiveIndexKey, QSCUndnemoMetric.CHANNEL_INFO_CHANNEL_NAME.getName());
		String newActiveIndexDisplayNameKey = String.format("%s#%s", newActiveIndexKey, QSCUndnemoMetric.CHANNEL_INFO_DISPLAY_NAME.getName());

		String newActiveIndexEnableState = stats.get(newActiveIndexEnableStatKey);
		String newActiveIndexDeviceName = stats.get(newActiveIndexDeviceNameKey);
		String newActiveIndexChannelName = stats.get(newActiveIndexChannelNameKey);
		String newActiveIndexDisplayName = stats.get(newActiveIndexDisplayNameKey);

		// Remove previous non-active group.
		stats.remove(newActiveIndexEnableStatKey);
		stats.remove(newActiveIndexDeviceNameKey);
		stats.remove(newActiveIndexChannelNameKey);
		stats.remove(newActiveIndexDisplayNameKey);

		String groupName = QSCUndnemoConstant.ACTIVE_CHANNEL;
		stats.put(QSCUndnemoMetric.ACTIVE_CHANNEL_INDEX.getName(), formattedValue);
		stats.put(String.format("%s#%s", groupName, QSCUndnemoMetric.CHANNEL_INFO_ENABLE_STATE.getName()), newActiveIndexEnableState);
		stats.put(String.format("%s#%s", groupName, QSCUndnemoMetric.CHANNEL_INFO_DEVICE_NAME.getName()), newActiveIndexDeviceName);
		stats.put(String.format("%s#%s", groupName, QSCUndnemoMetric.CHANNEL_INFO_CHANNEL_NAME.getName()), newActiveIndexChannelName);
		stats.put(String.format("%s#%s", groupName, QSCUndnemoMetric.CHANNEL_INFO_DISPLAY_NAME.getName()), newActiveIndexDisplayName);

		localExtendedStatistics.setStatistics(stats);
		localExtendedStatistics.setControllableProperties(controls);
	}

	/**
	 * Handle control active channel index with filter option. This method is used to handle every statistics locally -
	 * to save the number of requests made to the device.
	 *
	 * @param value value of new active channel
	 * @param indexList list of filter indexes
	 * @throws Exception if fail to get UDP response
	 */
	private void handleActiveChannelControlWithFilter(String value, List<Integer> indexList) throws Exception {
		int intValue = Integer.parseInt(value);
		String response = getUDPResponse(QSCUndnemoUDPCommand.SET_ACT_CH_IDX.getCommand() + QSCUndnemoConstant.SPACE + intValue);
		if (QSCUndnemoConstant.NACK.equals(response)) {
			throw new CommandFailureException(this.getAddress(), QSCUndnemoUDPCommand.SET_ACT_CH_IDX.getCommand(), String.format("Fail to set active channel index with value: %s", value));
		}
		Map<String, String> stats = localExtendedStatistics.getStatistics();
		List<AdvancedControllableProperty> controls = localExtendedStatistics.getControllableProperties();
		if (indexList.contains(intValue)) {
			String newActiveIndexKey = String.format("Channel %02d", intValue);
			String oldNonActiveEnableStatKey = String.format("%s#%s", newActiveIndexKey, QSCUndnemoMetric.CHANNEL_INFO_ENABLE_STATE.getName());
			String oldNonActiveDeviceNameKey = String.format("%s#%s", newActiveIndexKey, QSCUndnemoMetric.CHANNEL_INFO_DEVICE_NAME.getName());
			String oldNonActiveChannelNameKey = String.format("%s#%s", newActiveIndexKey, QSCUndnemoMetric.CHANNEL_INFO_CHANNEL_NAME.getName());
			String oldNonActiveDisplayNameKey = String.format("%s#%s", newActiveIndexKey, QSCUndnemoMetric.CHANNEL_INFO_DISPLAY_NAME.getName());
			// Remove previous non-active group.
			stats.remove(oldNonActiveEnableStatKey);
			stats.remove(oldNonActiveDeviceNameKey);
			stats.remove(oldNonActiveChannelNameKey);
			stats.remove(oldNonActiveDisplayNameKey);
		} else {
			List<Integer> listOfChannelIndex = new ArrayList<>();
			listOfChannelIndex.add(intValue);
			try {
				retrieveChannelInfo(listOfChannelIndex);
			} catch (Exception e) {
				throw new CommandFailureException(this.getAddress(), QSCUndnemoUDPCommand.GET_CMD_CH_INFO.getCommand(), e.getMessage(), e);
			}
		}
		// Remove old dropdown list.
		controls.removeIf(control -> control.getName().equals(QSCUndnemoMetric.ACTIVE_CHANNEL_INDEX.getName()));
		populateChannelInfoMonitoringAndControllingProperties(stats, controls);
		localExtendedStatistics.setStatistics(stats);
		localExtendedStatistics.setControllableProperties(controls);
	}


	/**
	 * Prepare list of index before submitting.
	 *
	 * @param beginIndex begin index of channel info
	 * @param endIndex end index of channel info
	 */
	private void submitThread(int beginIndex, int endIndex) {
		List<Integer> listIndexes = new ArrayList<>();
		for (int i = beginIndex; i <= endIndex; i++) {
			listIndexes.add(i);
		}
		channelDataLoader = new QSCChannelDataLoader(listIndexes);
		executorService.submit(channelDataLoader);
	}

	/**
	 * Get Controlling metric based on preset/ routing metric
	 *
	 * @param property String of the property from controlProperty
	 * @return Return instance of {@link QSCUndnemoMetric} that based on preset/ routing metric
	 */
	private QSCUndnemoMetric getQSCUndnemoControllingMetric(String property) {
		return QSCUndnemoMetric.getByName(property);
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
		for (Integer listIndex : listIndexes) {
			String rawChannelInfos = getUDPResponse(QSCUndnemoUDPCommand.GET_CMD_CH_INFO.getCommand() + QSCUndnemoConstant.SPACE + listIndex);
			if (rawChannelInfos.contains(QSCUndnemoConstant.ACK)) {
				String[] channelInfos = parseUDPResponse(rawChannelInfos);
				if (channelInfos.length != 5) {
					throw new ResourceNotReachableException(String.format("Fail to get channel info at index: %s", listIndex));
				}
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
		List<Integer> filterChannelIndexValues = handleListChannelIndex();
		// Only populate data to stats if channelInfoList is full of 64 channels data
		if (channelInfoList.size() == 64 || (!filterChannelIndexValues.isEmpty() && !channelInfoList.isEmpty())) {
			String rawCurrentActiveChannelIndex = getUDPResponse(QSCUndnemoUDPCommand.GET_CMD_ACT_CH_IDX.getCommand());
			String currentActiveChannelIndex = getValidActiveChannelIndex(rawCurrentActiveChannelIndex);
			int activeChannelIndex = Integer.parseInt(currentActiveChannelIndex);
			String formattedCurrentActiveChannelIndex;
			if (activeChannelIndex == 0) {
				formattedCurrentActiveChannelIndex = QSCUndnemoConstant.NONE;
			} else {
				formattedCurrentActiveChannelIndex = String.format("%02d", activeChannelIndex);
			}
			// This block of codes are used to check whether active channel information is in channelInfoList when filter channel index contains validate data.
			// When it is on normal behaviour (fetch all 64 channels) without filtering this block of code won't be applied.
			if (!filterChannelIndexValues.isEmpty() && !channelInfoList.isEmpty()) {
				// Make sure active channel information always in the channelInfoList. If there isn't active channel information, we only call -
				// 1 request. So this won't slow getMultipleStatistics() down.
				if (!filterChannelIndexValues.contains(activeChannelIndex) && activeChannelIndex != 0) {
					List<Integer> activeChannelInformation = new ArrayList<>();
					activeChannelInformation.add(activeChannelIndex);
					try {
						retrieveChannelInfo(activeChannelInformation);
					} catch (Exception e) {
						throw new ResourceNotReachableException(String.format("Cannot get active channel information with index: %s", activeChannelIndex), e);
					}
				}
				List<ChannelInfo> channelInfoListToBeRemoved = new ArrayList<>();
				synchronized (channelInfoList) {
					for (ChannelInfo channelInfo : channelInfoList) {
						// Remove unnecessary ChannelInfo in channelInfoList
						int currentIndex = Integer.parseInt(channelInfo.getChannelInfoIndex());
						if (!filterChannelIndexValues.contains(currentIndex) && currentIndex != activeChannelIndex) {
							channelInfoListToBeRemoved.add(channelInfo);
						}
					}
				}
				for (ChannelInfo channelInfo : channelInfoListToBeRemoved) {
					channelInfoList.remove(channelInfo);
				}
			}
			synchronized (channelInfoList) {
				stats.put(QSCUndnemoMetric.ACTIVE_CHANNEL_INDEX.getName(), formattedCurrentActiveChannelIndex);
				List<String> values = new ArrayList<>();
				if (activeChannelIndex == 0) {
					values.add(QSCUndnemoConstant.NONE);
				}
				if (filterChannelIndexValues.isEmpty()) {
					int[] intArray = IntStream.rangeClosed(1, 64).toArray();
					for (int j : intArray) {
						values.add(String.format("%02d", j));
					}
				} else {
					for (Integer filterChannelIndexValue : filterChannelIndexValues) {
						values.add(String.format("%02d", filterChannelIndexValue));
					}
					if (!filterChannelIndexValues.contains(activeChannelIndex) && activeChannelIndex != 0) {
						values.add(formattedCurrentActiveChannelIndex);
					}
				}
				controls.add(createDropdown(QSCUndnemoMetric.ACTIVE_CHANNEL_INDEX.getName(), values, formattedCurrentActiveChannelIndex));
				for (ChannelInfo channelInfo : channelInfoList) {
					String groupName;
					if (channelInfo.getChannelInfoIndex().equals(currentActiveChannelIndex)) {
						groupName = QSCUndnemoConstant.ACTIVE_CHANNEL;
					} else {
						int intCurrentChannelInfoIndex = Integer.parseInt(channelInfo.getChannelInfoIndex());
						groupName = String.format("Channel %02d", intCurrentChannelInfoIndex);
					}
					stats.put(String.format("%s#%s", groupName, QSCUndnemoMetric.CHANNEL_INFO_ENABLE_STATE.getName()), channelInfo.getEnableState());
					stats.put(String.format("%s#%s", groupName, QSCUndnemoMetric.CHANNEL_INFO_DEVICE_NAME.getName()), channelInfo.getDeviceName());
					stats.put(String.format("%s#%s", groupName, QSCUndnemoMetric.CHANNEL_INFO_CHANNEL_NAME.getName()), channelInfo.getChannelName());
					stats.put(String.format("%s#%s", groupName, QSCUndnemoMetric.CHANNEL_INFO_DISPLAY_NAME.getName()), channelInfo.getDisplayName());
				}
			}
			if (!isGetMultipleStatsAfterControl) {
				// Only clear when this is in normal getMultipleStatistics() not after controlProperty()
				if (logger.isDebugEnabled()) {
					logger.debug("Clearing channel info list");
				}
				channelInfoList.clear();
			}
		}
	}

	/**
	 * Get valid active channel index
	 *
	 * @param rawCurrentActiveChannelIndex raw response from the UDP command
	 * @return valid active channel index
	 */
	private String getValidActiveChannelIndex(String rawCurrentActiveChannelIndex) {
		String currentActiveChannelIndex;
		if (rawCurrentActiveChannelIndex.contains(QSCUndnemoConstant.NACK)) {
			currentActiveChannelIndex = "0";
		} else {
			currentActiveChannelIndex = parseUDPResponse(rawCurrentActiveChannelIndex)[0];
			// In case of the response contains ACK but don't have the required value.
			if (currentActiveChannelIndex.equals(QSCUndnemoConstant.NACK) || !currentActiveChannelIndex.matches(QSCUndnemoConstant.REGEX_IS_INTEGER)) {
				currentActiveChannelIndex = "0";
			}
			if (currentActiveChannelIndex.matches(QSCUndnemoConstant.REGEX_IS_INTEGER) && !ValueRange.of(1, 64).isValidIntValue(Integer.parseInt(currentActiveChannelIndex))) {
				currentActiveChannelIndex = "0";
			}
		}
		return currentActiveChannelIndex;
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
			// The response always contains 3 fields like: ACK CH_INFO 3
			if (splitString.length == 3) {
				splitString[2] = splitString[2].replace(QSCUndnemoConstant.CR, QSCUndnemoConstant.EMPTY);
				resultStrings.add(splitString[2]);
			} else {
				resultStrings.add(QSCUndnemoConstant.NACK);
			}
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
						int intTrimIndex = Integer.parseInt(trimIndex);
						if (intTrimIndex >= 1 && intTrimIndex <= 64) {
							resultList.add(intTrimIndex);
						}
					}
				}
				Set<Integer> noDuplicateIndex = new LinkedHashSet<>(resultList);
				resultList.clear();
				resultList.addAll(noDuplicateIndex);
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
	 */
	private void filterChannelInfo(List<Integer> filterChannelIndexValues) {
		if (logger.isDebugEnabled()) {
			logger.debug(String.format("Applying channel index filter with values(s): %s", channelIndex));
		}
		channelDataLoader = new QSCChannelDataLoader(filterChannelIndexValues);
		executorService.submit(channelDataLoader);
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
	 * @param values list of values
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