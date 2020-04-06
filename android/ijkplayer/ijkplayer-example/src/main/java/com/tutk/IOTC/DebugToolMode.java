/**
 * DebugToolMode.java
 *
 * Copyright (c) by TUTK Co.LTD. All Rights Reserved.
 */
package com.tutk.IOTC;

/**
* Enum the debug tool mode
*/
public enum DebugToolMode {
    ENABLE_LOCAL(0),
    ENABLE_REMOTE(1);

    private int value;
    private DebugToolMode(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }
}