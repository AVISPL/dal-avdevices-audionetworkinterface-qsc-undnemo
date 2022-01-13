/*
 * Copyright (c) 2021 AVI-SPL Inc. All Rights Reserved.
 */
package com.avispl.symphony.dal.avdevices.audionetworkinterface.qsc.undnemo;

import java.util.List;

import jdk.internal.net.http.common.SSLFlowDelegate.Monitorable;

import com.avispl.symphony.api.dal.control.Controller;
import com.avispl.symphony.api.dal.dto.control.ControllableProperty;

/**
 * QSC Attero Tech unDNEMO Adapter
 *
 * Controlling:
 * <ul>
 *	<li>Set Active Channel Index (1-64)</li>
 *	<li>Set Speaker Mute (On/Off)</li>
 *	<li>Set Volume (1-10)</li>
 *	<li>Set Button Brightness (0-10)</li>
 *	<li>Set Display Brightness (0-10)</li>
 *	</ul
 *
 * Monitoring:
 * <ul>
 *	<li>Online / Offline Status</li>
 *	<li>Version Info</li>
 *	<li>Channel Info (channels 1-64)</li>
 *	<li>Active Channel Index (1-64)</li>
 *	<li>Speaker Mute (On/Off)</li>
 *	<li>Volume (1-10)</li>
 *	<li>Button Brightness (0-10)</li>
 *	<li>Display Brightness (0-10)</li>
 * </ul>
 * @author Duy Nguyen
 * @version 1.0
 * @since 1.0
 */
public class QSCUndnemoCommunicator extends UDPCommunicator implements Monitorable, Controller {
	@Override
	public String getInfo() {
		UDPCommunicator udpCommunicator = new UDPCommunicator();
		return null;
	}

	@Override
	public void controlProperty(ControllableProperty controllableProperty) throws Exception {

	}

	@Override
	public void controlProperties(List<ControllableProperty> list) throws Exception {

	}
}
