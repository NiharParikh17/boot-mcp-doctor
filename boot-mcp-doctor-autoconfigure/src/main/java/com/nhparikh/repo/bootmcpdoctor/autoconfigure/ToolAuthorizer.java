package com.nhparikh.repo.bootmcpdoctor.autoconfigure;

/**
 * Decides, when a tool is called, whether the current caller may use it.
 */
interface ToolAuthorizer {

    /**
     * @param tool the tool name
     * @param write whether the tool changes application state
     * @return why the call is denied, or {@code null} if it is allowed
     */
    String denyReason(String tool, boolean write);
}
