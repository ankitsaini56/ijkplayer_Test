/**
 * Region.java
 *
 * Copyright (c) by TUTK Co.LTD. All Rights Reserved.
 */
package com.tutk.IOTC;

/**
* Enum the master region type that IOTCAPI supports.
*/
public enum Region {
    REGION_ALL(0),
    REGION_CN(1),
    REGION_EU(2),
    REGION_US(3);

    private int value;

    private Region(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }
}
