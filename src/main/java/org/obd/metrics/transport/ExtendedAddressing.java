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
package org.obd.metrics.transport;

/**
 * CAN extended addressing (ISO 15765-2, BMW style: {@code ATCEA12}) on the receive side. The module puts the
 * tester address ({@code F1}) as the first data byte of every frame it answers with, ahead of the PCI byte. Whether
 * an ELM327 / STN shows that byte depends on the chip, so a reply that carries it is reduced to what normal
 * addressing would give ({@code F1 62 40 02 ..} to {@code 62 40 02 ..}); a reply without it is left alone.
 */
final class ExtendedAddressing {

	private static final String CEA_COMMAND = "ATCEA";
	private static final byte TESTER_ADDRESS_HI = 'F';
	private static final byte TESTER_ADDRESS_LO = '1';
	private static final byte COLON = ':';

	private volatile boolean active = false;

	boolean isActive() {
		return active;
	}

	// Follows what was sent: ATCEA<hh> switches extended addressing on, a bare ATCEA (or an adapter reset) off.
	void onTransmit(final String query) {
		if (query == null) {
			return;
		}
		if (query.startsWith(CEA_COMMAND)) {
			active = query.length() > CEA_COMMAND.length();
		} else if ("ATZ".equals(query) || "ATD".equals(query) || "ATWS".equals(query)) {
			active = false;
		}
	}

	/**
	 * Removes the tester address from a reply held in {@code buffer[from, from + length)}, in place. Single frame:
	 * a leading {@code F1} (with the PCI byte and padding, when the chip shows them: {@code F1 05 62 40 02 0A 0B AA}). Multi-frame ({@code 01A0:F162..1:F1..}): the {@code F1} after every frame index, only
	 * when the first frame carries it. Spaces and line breaks are already gone at this point.
	 *
	 * @return the reply's new length
	 */
	static int strip(final byte[] buffer, final int from, final int length) {
		if (length < 2) {
			return length;
		}

		if (isTesterAddress(buffer, from, from + length)) {
			// A chip that shows the tester address may show the single frame's PCI byte (0N, N data bytes) and
			// its padding too: keep only the N data bytes then.
			final int dataBytes = singleFramePciLength(buffer, from + 2, from + length);
			final int skip = dataBytes > 0 ? 4 : 2;
			final int newLength = dataBytes > 0 ? dataBytes * 2 : length - 2;
			System.arraycopy(buffer, from + skip, buffer, from, newLength);
			return newLength;
		}

		final int end = from + length;
		final int firstColon = indexOf(buffer, COLON, from, end);
		if (firstColon < 0 || !isTesterAddress(buffer, firstColon + 1, end)) {
			return length;
		}

		int write = from;
		for (int read = from; read < end; read++) {
			buffer[write++] = buffer[read];
			if (buffer[read] == COLON && isTesterAddress(buffer, read + 1, end)) {
				read += 2;
			}
		}
		return write - from;
	}

	// N of a single frame PCI byte "0N" at buffer[at] that is followed by at least N data bytes, else 0. A reply
	// with CAN auto formatting starts with the service's answer (4x, 5x, 6x, 7F), never with 0x.
	private static int singleFramePciLength(final byte[] buffer, final int at, final int end) {
		if (at + 1 >= end || buffer[at] != '0') {
			return 0;
		}
		final int n = buffer[at + 1] - '0';
		if (n < 1 || n > 7 || end - (at + 2) < n * 2) {
			return 0;
		}
		return n;
	}

	private static boolean isTesterAddress(final byte[] buffer, final int at, final int end) {
		return at + 1 < end && buffer[at] == TESTER_ADDRESS_HI && buffer[at + 1] == TESTER_ADDRESS_LO;
	}

	private static int indexOf(final byte[] buffer, final byte value, final int from, final int end) {
		for (int i = from; i < end; i++) {
			if (buffer[i] == value) {
				return i;
			}
		}
		return -1;
	}
}
