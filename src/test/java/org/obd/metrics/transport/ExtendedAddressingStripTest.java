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

import java.nio.charset.StandardCharsets;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

public class ExtendedAddressingStripTest {

	@ParameterizedTest
	@CsvSource(value = {
			// no tester address: left alone
			"6242050A0B=6242050A0B",
			"7F2231=7F2231",
			"NODATA=NODATA",
			// single frame, tester address only
			"F16242050A0B=6242050A0B",
			// single frame with PCI byte, and with padding
			"F1056242050A0B=6242050A0B",
			"F1056242050A0BAAAA=6242050A0B",
			// negative response
			"F1037F2231=7F2231",
			// multi-frame: tester address after every frame index
			"00C0:F162F1905A41521:F1454145424E39=00C0:62F1905A41521:454145424E39",
			// multi-frame shown without the tester address: left alone
			"00C0:62F1905A41521:454145424E39=00C0:62F1905A41521:454145424E39",
	}, delimiter = '=')
	public void strip(String reply, String expected) {
		final byte[] buffer = reply.getBytes(StandardCharsets.ISO_8859_1);
		final int length = ExtendedAddressing.strip(buffer, 0, buffer.length);
		Assertions.assertThat(new String(buffer, 0, length, StandardCharsets.ISO_8859_1)).isEqualTo(expected);
	}

	@Test
	public void stripHonoursOffset() {
		final byte[] buffer = "SEARCHING...F16242050A0B".getBytes(StandardCharsets.ISO_8859_1);
		final int length = ExtendedAddressing.strip(buffer, 12, buffer.length - 12);
		Assertions.assertThat(new String(buffer, 12, length, StandardCharsets.ISO_8859_1)).isEqualTo("6242050A0B");
	}

	@Test
	public void followsTransmittedCommands() {
		final ExtendedAddressing extendedAddressing = new ExtendedAddressing();
		Assertions.assertThat(extendedAddressing.isActive()).isFalse();

		extendedAddressing.onTransmit("ATCEA12");
		Assertions.assertThat(extendedAddressing.isActive()).isTrue();

		extendedAddressing.onTransmit("224205");
		Assertions.assertThat(extendedAddressing.isActive()).isTrue();

		extendedAddressing.onTransmit("ATCEA");
		Assertions.assertThat(extendedAddressing.isActive()).isFalse();

		extendedAddressing.onTransmit("ATCEA18");
		extendedAddressing.onTransmit("ATZ");
		Assertions.assertThat(extendedAddressing.isActive()).isFalse();
	}
}
