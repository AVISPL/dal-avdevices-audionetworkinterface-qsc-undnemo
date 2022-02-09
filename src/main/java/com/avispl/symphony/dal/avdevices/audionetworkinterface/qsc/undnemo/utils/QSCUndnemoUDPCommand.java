/*
 * Copyright (c) 2022 AVI-SPL Inc. All Rights Reserved.
 */
package com.avispl.symphony.dal.avdevices.audionetworkinterface.qsc.undnemo.utils;

/**
 * Command enum for QSC Undnemo UDP Commands
 *
 * @author Duy Nguyen
 * @version 1.0.0
 * @since 1.0.0
 */
public enum QSCUndnemoUDPCommand {

	GET_CMD_VERSION("VERSION"),
	GET_CMD_ACT_CH_IDX("ACT_CH_IDX"),
	GET_CMD_CH_INFO("CH_INFO"),
	GET_CMD_SPKR_MUTE("SPKR_MUTE"),
	GET_CMD_VOLUME("VOLUME"),
	GET_CMD_GBB("GBB"),
	GET_CMD_GDB("GDB"),
	SET_ACT_CH_IDX("SET_ACT_CH_IDX"),
	SET_SBB("SBB"),
	SET_SDB("SDB"),
	SET_SPKR_MUTE("SET_SPKR_MUTE"),
	SET_VOLUME("SET_VOLUME");

	private final String command;

	/**
	 * QSCUndnemoUDPCommand constructor
	 *
	 * @param command {@code {@link #command}}
	 */
	QSCUndnemoUDPCommand(String command) {
		this.command = command;
	}

	/**
	 * Retrieves {@code {@link #command}}
	 *
	 * @return value of {@link #command}
	 */
	public String getCommand() {
		return command;
	}
}