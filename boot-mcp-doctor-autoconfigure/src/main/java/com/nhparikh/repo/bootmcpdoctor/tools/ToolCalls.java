package com.nhparikh.repo.bootmcpdoctor.tools;

import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Records what happens during the tool call that runs on the current thread, for the line boot-mcp-doctor logs
 * about each call. Each tool runs its work through it, so that an unexpected failure reaches the caller as a clear
 * message instead of the exception's own message, which can contain configuration values. The failure itself, with
 * its stack trace, is logged with the call.
 * <p>
 * Internal to boot-mcp-doctor: public only so that the auto-configuration can read what the tools recorded, and may
 * change in any release.
 */
public final class ToolCalls {

    private static final ThreadLocal<Call> current = new ThreadLocal<>();

    private ToolCalls() {
    }

    /**
     * Starts recording the tool call that is about to run on this thread.
     */
    public static void start() {
        current.set(new Call(null, null));
    }

    /**
     * Stops recording the tool call that ran on this thread.
     * @return what happened during it
     */
    public static Call finish() {
        Call call = current.get();
        current.remove();
        return (call != null) ? call : new Call(null, null);
    }

    /**
     * Describes a failure to the caller without its message.
     * @param tool the tool that failed
     * @param failure what it failed with
     * @return the message
     */
    public static String failureMessage(String tool, Throwable failure) {
        return tool + " failed with " + failure.getClass().getSimpleName()
                + ". The application's log has the details.";
    }

    /**
     * Runs a tool's work. An exception, or a class or method missing from the application, is recorded for the log,
     * and the tool reports it through {@code failed} instead.
     */
    static <T> T run(String tool, Supplier<T> work, Function<String, T> failed) {
        try {
            return work.get();
        }
        catch (RuntimeException | LinkageError ex) {
            Call call = current.get();
            current.set(new Call(ex, (call != null) ? call.note() : null));
            return failed.apply(failureMessage(tool, ex));
        }
    }

    /**
     * Adds a note to the logged line, such as what a tool that changes the application changed.
     */
    static void note(String note) {
        Call call = current.get();
        current.set(new Call((call != null) ? call.failure() : null, note));
    }

    /**
     * What happened during a tool call.
     *
     * @param failure what the tool's work failed with, or {@code null}
     * @param note what the tool noted for the log, or {@code null}
     */
    public record Call(Throwable failure, String note) {
    }
}
