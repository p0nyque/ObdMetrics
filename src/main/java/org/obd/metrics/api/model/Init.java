 /**
 * Copyright 2019-2026, Tomasz Żebrowski
 *
 * <p>Licensed to the Apache Software Foundation (ASF) under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to You under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 *
 * <p>Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.obd.metrics.api.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.obd.metrics.api.CANNetworkRegistry;
import org.obd.metrics.api.DefaultCanNetworkRegistry;
import org.obd.metrics.command.ATCommand;
import org.obd.metrics.command.group.CommandGroup;
import org.obd.metrics.command.group.DefaultCommandGroup;

import lombok.Builder;
import lombok.Builder.Default;
import lombok.Getter;
import lombok.NonNull;
import lombok.Singular;
import lombok.ToString;

@Builder
public class Init {
	
	@Builder
	@ToString
	public static class Header {
		@Getter
		@Default
		private String header = "";

		@Getter
		@Default
		private String mode = "";

		/**
		 * Which trouble code requests the module takes in a DTC action: the {@code dtcRead} / {@code dtcClear} PIDs
		 * (UDS) or the {@code dtcObdRead} / {@code dtcObdClear} PIDs (OBD-II services 03 / 07 / 0A and 04). Set by
		 * the caller per module: neither the header nor the vehicle tells which services an ECU answers.
		 */
		@Getter
		@Default
		private DtcProtocol dtcProtocol = DtcProtocol.UDS;

		/**
		 * CAN extended address of the target module (ISO 15765-2 extended addressing), e.g. {@code 12} for the
		 * BMW DDE: the module's address travels as the first data byte, the CAN id ({@link #header}, {@code 6F1}
		 * on BMW) addresses the gateway. Empty for normal addressing.
		 */
		@Getter
		@Default
		private String extendedAddress = "";

		/**
		 * CAN id the module answers on ({@code ATCRA}), e.g. {@code 612} for the BMW DDE. Only used with
		 * {@link #extendedAddress}; empty leaves the adapter's receive filter at its default.
		 */
		@Getter
		@Default
		private String receiveAddress = "";

		public boolean isExtendedAddressing() {
			return extendedAddress != null && !extendedAddress.isEmpty();
		}

		/**
		 * The AT commands that point the adapter at this module. With an extended address: {@code ATSH}, the
		 * extended address, the receive filter and a flow control frame that carries the extended address too
		 * (multi-frame replies need it). {@code clearExtendedAddressing} first turns all of that off again, for a
		 * normal header sent while the adapter may still be set up for an extended one.
		 */
		public List<ATCommand> commands(final boolean clearExtendedAddressing) {
			final List<ATCommand> commands = new ArrayList<>();
			if (isExtendedAddressing()) {
				commands.add(new ATCommand("SH" + header));
				commands.add(new ATCommand("CEA" + extendedAddress));
				if (receiveAddress != null && !receiveAddress.isEmpty()) {
					commands.add(new ATCommand("CRA" + receiveAddress));
				}
				commands.add(new ATCommand("FCSH" + header));
				commands.add(new ATCommand("FCSD" + extendedAddress + "300000"));
				commands.add(new ATCommand("FCSM1"));
			} else {
				if (clearExtendedAddressing) {
					commands.addAll(CLEAR_EXTENDED_ADDRESSING);
				}
				commands.add(new ATCommand("SH" + header));
			}
			return commands;
		}
	}

	/** Turns extended addressing, the receive filter and the custom flow control off (ELM327 defaults). */
	public static final List<ATCommand> CLEAR_EXTENDED_ADDRESSING = Collections.unmodifiableList(
			Arrays.asList(new ATCommand("CEA"), new ATCommand("CRA"), new ATCommand("FCSM0")));

	public enum DtcProtocol {
		UDS, OBD
	}
	
	public static final Init DEFAULT = Init.builder()
	        .delayAfterInit(0)
	        .protocol(Protocol.AUTO)
	        .sequence(DefaultCommandGroup.INIT)
	        .build();

	public enum Protocol {
		AUTO("0"), CAN_11("6"), CAN_29("7"), CAN_USER1("B");

		@Getter
		private String type;

		Protocol(String type) {
			this.type = type;
		}
	}
	
	@Getter
	@Default
	private long delayAfterReset = 0l;
	
	@Getter
	@Default
	private long delayAfterInit = 0l;

	@Getter
	@NonNull
	@Default
	private CommandGroup<?> sequence = DefaultCommandGroup.INIT;
	
	@Getter
	@Default
	private Protocol protocol = Protocol.AUTO;

	@Getter
	@Singular
	private List<Header> headers;
	
	/** True when any header of the table uses CAN extended addressing (see {@link Header#getExtendedAddress()}). */
	public boolean hasExtendedAddressing() {
		return headers != null && headers.stream().anyMatch(Header::isExtendedAddressing);
	}

	@Getter
	@Builder.Default
	private CANNetworkRegistry networkRegistry = new DefaultCanNetworkRegistry();
}