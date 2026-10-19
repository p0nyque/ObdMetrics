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
package org.obd.metrics.api;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import org.obd.metrics.api.model.Init;
import org.obd.metrics.buffer.CommandsBuffer;
import org.obd.metrics.command.ATCommand;
import org.obd.metrics.command.Command;

import lombok.extern.slf4j.Slf4j;

@Slf4j
final class CANMessageHeaderMultiplexer {

	private static final String AT_COMMAND = "AT";

	private final Map<String, Init.Header> canHeaders = new HashMap<>();

	// Whether any header of the table uses CAN extended addressing: only then can the adapter be left set up for
	// it, and a normal header has to switch it off first.
	private final boolean extendedAddressingInUse;

	// What this multiplexer last set the adapter to; null = unknown (after reset(), or before the first header).
	private volatile Boolean extendedAddressingActive;

	private final AtomicBoolean singleModeTest = new AtomicBoolean(false);
	private final AtomicBoolean addedSingleModeHeaderTest = new AtomicBoolean(false);

	private volatile boolean isSingleMode = false;
	private volatile String currentMode;
	private volatile CANNetwork currentNetwork;

	private final CommandsBuffer commandsBuffer;

	CANMessageHeaderMultiplexer(final Init init, final CommandsBuffer commandsBuffer) {
		this.commandsBuffer = commandsBuffer;

		init.getHeaders().forEach(h -> {
			if (h.getMode() != null && h.getHeader() != null) {
				log.info("Found CAN header={} for mode={}, extended address={}", h.getHeader(), h.getMode(),
						h.getExtendedAddress());
				canHeaders.put(h.getMode(), h);
			}
		});
		this.extendedAddressingInUse = canHeaders.values().stream().anyMatch(Init.Header::isExtendedAddressing);
	}

	<T extends Command> void testSingleMode(List<T> commands) {
		if (commands == null || commands.isEmpty()) {
			return;
		}

		if (singleModeTest.compareAndSet(false, true)) {
			final Set<String> groupedByMode = new HashSet<>();

			commands.forEach(p -> {
				if (p != null) {
					final String canMode = p.getCanMode();
					if (canMode != null && !canMode.isEmpty()) {
						groupedByMode.add(canMode);
					}

					final String mode = p.getMode();
					if (mode != null && !mode.isEmpty()) {
						groupedByMode.add(mode);
					}
				}
			});

			if (groupedByMode.size() == 1) {
				isSingleMode = true;
			}

			log.info("Determined single mode={}, available modes={}", isSingleMode, groupedByMode);
		}
	}

	// The AT commands (ATSH, plus the extended addressing set-up when the module needs it) that point the adapter at
	// the command's module; empty when the init table has no header for it. Unlike switchHeader it never skips:
	// for commands queued straight into the buffer, whose header is not tracked. With extended addressing in the
	// table a normal header always clears it, since the adapter's state is unknown there.
	List<ATCommand> headerFor(final Command command) {
		String mode = command.getCanMode();
		if (mode == null || mode.isEmpty()) {
			mode = command.getMode();
		}
		final Init.Header header = mode == null ? null : canHeaders.get(mode);
		return header == null ? Collections.emptyList() : header.commands(extendedAddressingInUse);
	}

	boolean isExtendedAddressingInUse() {
		return extendedAddressingInUse;
	}

	// Forgets the header it last set. Commands queued straight into the buffer (DTC actions, routines, module
	// discovery) send their own ATSH and never pass through switchHeader, so afterwards the adapter points at
	// another module while this still believes the live header is set - and would skip the ATSH.
	void reset() {
		currentMode = null;
		currentNetwork = null;
		extendedAddressingActive = null;
		addedSingleModeHeaderTest.set(false);
	}

	void switchHeader(Command nextCommand) {
		if (nextCommand == null) {
			return;
		}

		String nextMode = nextCommand.getCanMode();
		if (nextMode == null || nextMode.isEmpty()) {
			nextMode = nextCommand.getMode();
		}

		if (nextMode == null || AT_COMMAND.equals(nextMode)) {
			return;
		}

		final CANNetwork nextNetwork = nextCommand.getCanNetwork();

		if (nextMode.equals(currentMode) && nextNetwork == currentNetwork) {
			if (log.isTraceEnabled()) {
				log.trace("Do not change CAN message header, previous header is the same. "
						+ "Current mode={}, next mode={}", currentMode, nextMode);
			}

		} else {

			currentMode = nextMode;
			currentNetwork = nextNetwork;

			final Init.Header nextHeader = canHeaders.get(nextMode);

			if (nextHeader != null) {
				if (log.isTraceEnabled()) {
					log.trace("Setting CAN message header={} for the mode={}", nextHeader.getHeader(), nextMode);
				}

				if (isSingleMode) {
					if (addedSingleModeHeaderTest.compareAndSet(false, true)) {
						log.info("Injecting CAN message header={} for the mode to={}", nextHeader.getHeader(),
								nextMode);
						queue(nextHeader);
					}
				} else {
					queue(nextHeader);
				}
			}
		}
	}

	private void queue(final Init.Header header) {
		// Clear only when the adapter may still be set up for extended addressing: unknown (null) or known to be on.
		final boolean clear = extendedAddressingInUse && !Boolean.FALSE.equals(extendedAddressingActive);
		header.commands(clear).forEach(commandsBuffer::addLast);
		extendedAddressingActive = header.isExtendedAddressing();
	}
}
