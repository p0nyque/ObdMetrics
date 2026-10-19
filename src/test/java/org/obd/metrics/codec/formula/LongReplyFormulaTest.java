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
package org.obd.metrics.codec.formula;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.obd.metrics.pid.PidDefinition;
import org.obd.metrics.pid.ValueType;
import org.obd.metrics.transport.message.ConnectorResponseFactory;

public class LongReplyFormulaTest {

	// BMW 48 V battery record (22 D08C): 28 data bytes, SOC in byte 14 (O). Formula letters stop at Z.
	private static final String REPLY = "62D08C" + "12C0FE0C0032" + "0000".repeat(4) + "48" + "64" + "1324"
			+ "00".repeat(11);

	@Test
	public void bytesPastZAreIgnored() {
		Assertions.assertThat(decode("O")).isEqualTo(72.0);
	}

	@Test
	public void firstBytesStillDecode() {
		Assertions.assertThat(decode("(A*256+B)/100")).isEqualTo(48.0);
	}

	private Number decode(String formula) {
		final PidDefinition pid = new PidDefinition(1L, 28, formula, "22", "D08C", "", "48 V record", 0, 100,
				ValueType.DOUBLE);
		return FormulaEvaluatorCodec.instance(FormulaEvaluatorConfig.builder().build(), null, null).decode(pid,
				ConnectorResponseFactory.wrap(REPLY.getBytes()));
	}
}
