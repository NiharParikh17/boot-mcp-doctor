package com.nhparikh.repo.bootmcpdoctor.tools;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ToolCallsTests {

    @AfterEach
    void finish() {
        ToolCalls.finish();
    }

    @Test
    void returnsTheWorkWhenItSucceeds() {
        ToolCalls.start();
        assertThat(ToolCalls.run("get_health", () -> "healthy", (failure) -> failure)).isEqualTo("healthy");
        assertThat(ToolCalls.finish()).isEqualTo(new ToolCalls.Call(null, null));
    }

    @Test
    void reportsAndRecordsExceptions() {
        IllegalStateException failure = new IllegalStateException("secret");
        ToolCalls.start();
        String result = ToolCalls.run("get_health", () -> {
            throw failure;
        }, (message) -> message);
        assertThat(result).isEqualTo("get_health failed with IllegalStateException. The application's log has the "
                + "details.");
        assertThat(ToolCalls.finish().failure()).isSameAs(failure);
    }

    // Such as a method that is missing from the application's Spring Boot version.
    @Test
    void reportsAndRecordsLinkageErrors() {
        NoSuchMethodError failure = new NoSuchMethodError("StatusAggregator.of");
        ToolCalls.start();
        String result = ToolCalls.run("get_health", () -> {
            throw failure;
        }, (message) -> message);
        assertThat(result).startsWith("get_health failed with NoSuchMethodError.");
        assertThat(ToolCalls.finish().failure()).isSameAs(failure);
    }

    @Test
    void keepsNotesAlongsideAFailure() {
        ToolCalls.start();
        ToolCalls.note("Set ROOT from INFO to DEBUG.");
        ToolCalls.run("set_log_level", () -> {
            throw new IllegalStateException();
        }, (message) -> message);
        ToolCalls.Call call = ToolCalls.finish();
        assertThat(call.note()).isEqualTo("Set ROOT from INFO to DEBUG.");
        assertThat(call.failure()).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void startsEachCallAfresh() {
        ToolCalls.note("left over");
        ToolCalls.start();
        assertThat(ToolCalls.finish()).isEqualTo(new ToolCalls.Call(null, null));
        assertThat(ToolCalls.finish()).isEqualTo(new ToolCalls.Call(null, null));
    }
}
