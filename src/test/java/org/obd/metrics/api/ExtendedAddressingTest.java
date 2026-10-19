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

import java.io.IOException;
import java.util.List;
import java.util.concurrent.BlockingDeque;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.obd.metrics.api.model.AdaptiveTimeoutPolicy;
import org.obd.metrics.api.model.Adjustments;
import org.obd.metrics.api.model.BatchPolicy;
import org.obd.metrics.api.model.CachePolicy;
import org.obd.metrics.api.model.Init;
import org.obd.metrics.api.model.Init.Header;
import org.obd.metrics.api.model.Init.Protocol;
import org.obd.metrics.api.model.ObdMetric;
import org.obd.metrics.api.model.ProducerPolicy;
import org.obd.metrics.api.model.Query;
import org.obd.metrics.command.group.DefaultCommandGroup;
import org.obd.metrics.pid.PidDefinition;
import org.obd.metrics.test.DataCollector;
import org.obd.metrics.test.MockAdapterConnection;
import org.obd.metrics.test.SimpleLifecycle;
import org.obd.metrics.test.SimpleWorkflowFactory;
import org.obd.metrics.test.WorkflowFinalizer;
import org.obd.metrics.test.WorkflowMonitor;

// BMW F/G series: ECUs sit behind the gateway, addressed with CAN id 6F1 and the ECU address (12 = DDE) as the
// first data byte; they answer on 600 + ECU with the tester address F1 as the first byte.
public class ExtendedAddressingTest {

	private static final long BMW_PID = 99001L;
	private static final long ENGINE_RPM = 13L;

	private static final Header BMW_DDE = Header.builder().mode("22").header("6F1").extendedAddress("12")
			.receiveAddress("612").build();
	private static final Header OBD = Header.builder().mode("01").header("7DF").build();

	private static final String BMW_DDE_SETUP = "ATSH6F1, ATCEA12, ATCRA612, ATFCSH6F1, ATFCSD12300000, ATFCSM1, 224205";

	// The DDE's reply as different chips may show it: without the tester address, with it, with the single
	// frame's PCI byte too, and with the frame's padding. (0x0A * 256 + 0x0B) / 100 = 25.71 in every case.
	@ParameterizedTest
	@ValueSource(strings = { "6242050A0B", "F16242050A0B", "F1056242050A0B", "F1056242050A0BAAAA" })
	public void readsMode22ValueBehindExtendedAddress(String adapterResponse) throws IOException, InterruptedException {
		final DataCollector collector = new DataCollector();
		final Workflow workflow = SimpleWorkflowFactory.getWorkflow(new SimpleLifecycle(), collector,
				"bmw_extended_addressing.json");

		final MockAdapterConnection connection = MockAdapterConnection.builder()
				.requestResponse("224205", adapterResponse)
				.build();

		final Init init = init().header(BMW_DDE).build();

		workflow.start(connection, Query.builder().pid(BMW_PID).build(), init, adjustments());
		WorkflowMonitor.waitUntilRunning(workflow);
		WorkflowFinalizer.finalizeAfter(workflow, 500);

		final BlockingDeque<String> recordedQueries = connection.recordedQueries();
		Assertions.assertThat(recordedQueries.toString()).contains(BMW_DDE_SETUP);
		// Only one module in the table: extended addressing is never switched off in between.
		Assertions.assertThat(recordedQueries).doesNotContain("ATCEA");

		assertMetricValue(workflow, collector, BMW_PID, 25.71);
	}

	@Test
	public void switchesBetweenExtendedAndNormalHeader() throws IOException, InterruptedException {
		final DataCollector collector = new DataCollector();
		final Workflow workflow = SimpleWorkflowFactory.getWorkflow(new SimpleLifecycle(), collector, "mode01.json",
				"bmw_extended_addressing.json");

		final MockAdapterConnection connection = MockAdapterConnection.builder()
				.requestResponse("224205", "F16242050A0B")
				.requestResponse("010C", "410C541B")
				.build();

		final Init init = init().header(BMW_DDE).header(OBD).build();

		workflow.start(connection, Query.builder().pid(BMW_PID).pid(ENGINE_RPM).build(), init, adjustments());
		WorkflowMonitor.waitUntilRunning(workflow);
		WorkflowFinalizer.finalizeAfter(workflow, 500);

		final String recordedQueries = connection.recordedQueries().toString();
		Assertions.assertThat(recordedQueries).contains(BMW_DDE_SETUP);
		// Back to the plain OBD header: extended addressing, receive filter and custom flow control off first.
		Assertions.assertThat(recordedQueries).contains("ATCEA, ATCRA, ATFCSM0, ATSH7DF, 010C");

		assertMetricValue(workflow, collector, BMW_PID, 25.71);
		assertMetricValue(workflow, collector, ENGINE_RPM, 5382.0);
	}

	@Test
	public void normalHeadersOnlySendNoExtendedAddressingCommands() throws IOException, InterruptedException {
		final DataCollector collector = new DataCollector();
		final Workflow workflow = SimpleWorkflowFactory.getWorkflow(new SimpleLifecycle(), collector, "mode01.json",
				"bmw_extended_addressing.json");

		final MockAdapterConnection connection = MockAdapterConnection.builder()
				.requestResponse("224205", "6242050A0B")
				.requestResponse("010C", "410C541B")
				.build();

		final Init init = init().header(Header.builder().mode("22").header("7E0").build()).header(OBD).build();

		workflow.start(connection, Query.builder().pid(BMW_PID).pid(ENGINE_RPM).build(), init, adjustments());
		WorkflowMonitor.waitUntilRunning(workflow);
		WorkflowFinalizer.finalizeAfter(workflow, 500);

		final String recordedQueries = connection.recordedQueries().toString();
		Assertions.assertThat(recordedQueries).contains("ATSH7E0, 224205").contains("ATSH7DF, 010C");
		Assertions.assertThat(recordedQueries).doesNotContain("ATCEA").doesNotContain("ATFCS").doesNotContain("ATCRA");
	}

	private static Init.InitBuilder init() {
		return Init.builder().delayAfterInit(0).protocol(Protocol.CAN_11).sequence(DefaultCommandGroup.INIT);
	}

	private static Adjustments adjustments() {
		return Adjustments.builder()
				.vehicleDtcReadingEnabled(false)
				.vehicleMetadataReadingEnabled(false)
				.vehicleCapabilitiesReadingEnabled(false)
				.cachePolicy(CachePolicy.builder().storeResultCacheOnDisk(false).resultCacheEnabled(false).build())
				.adaptiveTimeoutPolicy(AdaptiveTimeoutPolicy.builder().enabled(false).commandFrequency(6).build())
				.producerPolicy(ProducerPolicy.builder().priorityQueueEnabled(true).build())
				.batchPolicy(BatchPolicy.builder().enabled(false).build())
				.build();
	}

	private static void assertMetricValue(Workflow workflow, DataCollector collector, long pidId, double expected) {
		final PidDefinition pid = workflow.getPidRegistry().findBy(pidId);
		Assertions.assertThat(pid).isNotNull();
		final List<ObdMetric> metrics = collector.findMetricsBy(pid);
		Assertions.assertThat(metrics).isNotEmpty();
		Assertions.assertThat(((Number) metrics.get(0).getValue()).doubleValue()).isEqualTo(expected);
	}
}
