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

import java.util.stream.Collectors;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.obd.metrics.api.model.Init;
import org.obd.metrics.api.model.Init.Header;
import org.obd.metrics.command.ATCommand;

public class InitHeaderCommandsTest {

	@Test
	public void extendedAddressSetsUpAddressFilterAndFlowControl() {
		final Header header = Header.builder().mode("22").header("6F1").extendedAddress("18").receiveAddress("618")
				.build();
		Assertions.assertThat(header.isExtendedAddressing()).isTrue();
		Assertions.assertThat(queries(header, false))
				.isEqualTo("ATSH6F1 ATCEA18 ATCRA618 ATFCSH6F1 ATFCSD18300000 ATFCSM1");
		// Already an extended header: nothing to clear first.
		Assertions.assertThat(queries(header, true)).isEqualTo(queries(header, false));
	}

	@Test
	public void receiveAddressIsOptional() {
		final Header header = Header.builder().mode("22").header("6F1").extendedAddress("12").build();
		Assertions.assertThat(queries(header, false)).isEqualTo("ATSH6F1 ATCEA12 ATFCSH6F1 ATFCSD12300000 ATFCSM1");
	}

	@Test
	public void normalHeaderClearsExtendedAddressingOnRequest() {
		final Header header = Header.builder().mode("01").header("7DF").build();
		Assertions.assertThat(header.isExtendedAddressing()).isFalse();
		Assertions.assertThat(queries(header, false)).isEqualTo("ATSH7DF");
		Assertions.assertThat(queries(header, true)).isEqualTo("ATCEA ATCRA ATFCSM0 ATSH7DF");
	}

	@Test
	public void initTellsWhetherAnyHeaderIsExtended() {
		Assertions.assertThat(Init.builder().header(Header.builder().mode("01").header("7DF").build()).build()
				.hasExtendedAddressing()).isFalse();
		Assertions.assertThat(Init.builder().header(Header.builder().mode("01").header("7DF").build())
				.header(Header.builder().mode("22").header("6F1").extendedAddress("12").build()).build()
				.hasExtendedAddressing()).isTrue();
	}

	private static String queries(Header header, boolean clear) {
		return header.commands(clear).stream().map(ATCommand::getQuery).collect(Collectors.joining(" "));
	}
}
