package com.nhparikh.repo.bootmcpdoctor.tools;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import com.nhparikh.repo.bootmcpdoctor.model.LogLevelChange;
import com.nhparikh.repo.bootmcpdoctor.model.LoggersReport;
import com.nhparikh.repo.bootmcpdoctor.model.LoggersReport.LoggerGroupLevel;
import com.nhparikh.repo.bootmcpdoctor.model.LoggersReport.LoggerLevel;
import com.nhparikh.repo.bootmcpdoctor.safety.ResponseSanitizer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.logging.LogLevel;
import org.springframework.boot.logging.LoggerConfiguration;
import org.springframework.boot.logging.LoggerGroups;
import org.springframework.boot.logging.LoggingSystem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

class LoggerToolsTests {

    private final LoggingSystem loggingSystem = mock(LoggingSystem.class);

    private final LoggerGroups loggerGroups = new LoggerGroups(
            Map.of("web", List.of("org.springframework.web", "org.springframework.http")));

    @BeforeEach
    void setUp() {
        given(this.loggingSystem.getSupportedLogLevels()).willReturn(EnumSet.allOf(LogLevel.class));
        given(this.loggingSystem.getLoggerConfigurations()).willReturn(List.of(
                new LoggerConfiguration("ROOT", LogLevel.INFO, LogLevel.INFO),
                new LoggerConfiguration("com.example", LogLevel.DEBUG, LogLevel.DEBUG),
                new LoggerConfiguration("com.example.orders", null, LogLevel.DEBUG),
                new LoggerConfiguration("org.springframework", null, LogLevel.INFO)));
    }

    @Test
    void withoutPrefixReturnsRootConfiguredLoggersAndGroups() {
        LoggersReport report = loggerTools(50).getLoggers(null);
        assertThat(report.loggers()).containsExactly(new LoggerLevel("ROOT", "INFO", "INFO"),
                new LoggerLevel("com.example", "DEBUG", "DEBUG"));
        assertThat(report.groups()).containsExactly(
                new LoggerGroupLevel("web", null, List.of("org.springframework.web", "org.springframework.http")));
        assertThat(report.levels()).contains("TRACE", "OFF");
        assertThat(report.summary()).isEqualTo("2 loggers are ROOT or have a configured level; 1 groups.");
    }

    @Test
    void prefixReturnsAllLoggersUnderIt() {
        LoggersReport report = loggerTools(50).getLoggers("com.example");
        assertThat(report.loggers()).extracting(LoggerLevel::name).containsExactly("com.example", "com.example.orders");
        assertThat(report.groups()).isEmpty();
    }

    @Test
    void capsLoggersAtMaxItems() {
        LoggersReport report = loggerTools(1).getLoggers(null);
        assertThat(report.loggers()).extracting(LoggerLevel::name).containsExactly("ROOT");
        assertThat(report.omittedLoggers()).isEqualTo(1);
    }

    @Test
    void reportsUnavailableWithoutLoggingSystem() {
        LoggerTools tools = new LoggerTools(provider(null), provider(null), new ResponseSanitizer(List.of(), 50));
        assertThat(tools.getLoggers(null).summary()).isEqualTo(LoggerTools.UNAVAILABLE);
    }

    @Test
    void setsLogLevel() {
        given(this.loggingSystem.getLoggerConfiguration("com.example.orders")).willReturn(
                new LoggerConfiguration("com.example.orders", null, LogLevel.DEBUG),
                new LoggerConfiguration("com.example.orders", LogLevel.TRACE, LogLevel.TRACE));
        LogLevelChange change = logLevelTools().setLogLevel("com.example.orders", "trace");
        then(this.loggingSystem).should().setLogLevel("com.example.orders", LogLevel.TRACE);
        assertThat(change).isEqualTo(new LogLevelChange(
                "Set com.example.orders from inherited to TRACE (effective TRACE).", "com.example.orders", true,
                null, "TRACE", "TRACE"));
    }

    @Test
    void resetClearsTheConfiguredLevel() {
        given(this.loggingSystem.getLoggerConfiguration("com.example")).willReturn(
                new LoggerConfiguration("com.example", LogLevel.DEBUG, LogLevel.DEBUG),
                new LoggerConfiguration("com.example", null, LogLevel.INFO));
        LogLevelChange change = logLevelTools().setLogLevel("com.example", "RESET");
        then(this.loggingSystem).should().setLogLevel("com.example", null);
        assertThat(change.previousLevel()).isEqualTo("DEBUG");
        assertThat(change.newLevel()).isNull();
        assertThat(change.summary()).isEqualTo("Set com.example from DEBUG to inherited (effective INFO).");
    }

    @Test
    void setsGroupLevelOnEveryMember() {
        LogLevelChange change = logLevelTools().setLogLevel("web", "DEBUG");
        then(this.loggingSystem).should().setLogLevel("org.springframework.web", LogLevel.DEBUG);
        then(this.loggingSystem).should().setLogLevel("org.springframework.http", LogLevel.DEBUG);
        assertThat(change.newLevel()).isEqualTo("DEBUG");
        assertThat(change.effectiveLevel()).isNull();
    }

    @Test
    void rejectsUnknownLevel() {
        LogLevelChange change = logLevelTools().setLogLevel("com.example", "LOUD");
        assertThat(change.changed()).isFalse();
        assertThat(change.summary()).startsWith("Unknown level 'LOUD'.");
        then(this.loggingSystem).should(never()).setLogLevel(anyString(), any());
    }

    @Test
    void rejectsMissingLoggerName() {
        LogLevelChange change = logLevelTools().setLogLevel(" ", "DEBUG");
        assertThat(change.changed()).isFalse();
        then(this.loggingSystem).should(never()).setLogLevel(anyString(), any());
    }

    private LoggerTools loggerTools(int maxItems) {
        return new LoggerTools(provider(this.loggingSystem), provider(this.loggerGroups),
                new ResponseSanitizer(List.of(), maxItems));
    }

    private LogLevelTools logLevelTools() {
        return new LogLevelTools(provider(this.loggingSystem), provider(this.loggerGroups));
    }

    @SuppressWarnings("unchecked")
    private static <T> ObjectProvider<T> provider(T value) {
        ObjectProvider<T> provider = mock(ObjectProvider.class);
        given(provider.getIfAvailable()).willReturn(value);
        given(provider.getIfAvailable(any())).willAnswer((invocation) -> (value != null) ? value
                : ((Supplier<T>) invocation.getArgument(0)).get());
        return provider;
    }
}
