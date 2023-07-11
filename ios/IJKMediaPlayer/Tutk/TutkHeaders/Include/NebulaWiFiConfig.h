
#ifndef _WIFI_CONFIG_H_
#define _WIFI_CONFIG_H_

#include <stdint.h>
#include "NebulaAPIs.h"
#include "TUTKGlobalAPIs.h"


/* ============================================================================
 * Platform Dependant Macro Definition
 * ============================================================================
 */

#define WIFI_CONFIG_API

#ifdef __cplusplus
extern "C" {
#endif /* __cplusplus */


/* ============================================================================
 * Generic Macro Definition
 * ============================================================================
 */

#define MAX_WIFI_SSID_LENGTH 32
#define MAX_WIFI_PWD_LENGTH 32

#define MAX_TIMEZONE_LENGTH 50

/* ============================================================================
 * Error Code Declaration
 * ============================================================================
 */


/* ============================================================================
 * Enumeration Declaration
 * ============================================================================
 */

typedef enum
{
    WIFIAPENC_INVALID,
    WIFIAPENC_NONE,
    WIFIAPENC_WEP,  //WEP, for no password
    WIFIAPENC_WPA_TKIP,
    WIFIAPENC_WPA_AES,
    WIFIAPENC_WPA2_TKIP,
    WIFIAPENC_WPA2_AES,

    WIFIAPENC_WPA_PSK_TKIP,
    WIFIAPENC_WPA_PSK_AES,
    WIFIAPENC_WPA2_PSK_TKIP,
    WIFIAPENC_WPA2_PSK_AES,
} NebulaAPEncTypeEnum;

typedef enum
{
    WIFICONN_FAIL,
    WIFICONN_OK,        //device get message,but not connect success
    WIFICONN_SUCCESS,
} NebulaWifiConncetResult;

typedef enum
{
    CONFIG_SET_FAIL,
    CONFIG_SET_SUCCESS,
} NebulaSetConfigResult;

typedef enum
{
    IOCTRL_RESERVE,
    IOCTRL_UDID_REQ,
    IOCTRL_UDID_RESP,
    IOCTRL_SSIDLIST_REQ,
    IOCTRL_SSIDLIST_RESP,
    IOCTRL_SETWIFI_REQ,
    IOCTRL_SETWIFI_RESP,
    IOCTRL_SETREGION_REQ,
    IOCTRL_SETREGION_RESP,
    IOCTRL_NEBULA_BIND_REQ,
    IOCTRL_NEBULA_BIND_RESP,
    IOTYPE_SETTIMEZONE_REQ,
    IOTYPE_SETTIMEZONE_RESP,
    IOTYPE_GETFWVERSION_REQ,
    IOTYPE_GETFWVERSION_RESP,
    IOTYPE_LAN_RESTART_REQ,
    IOTYPE_LAN_RESTART_RESP,
    IOCTRL_NEBULA_SECRET_ID_REQ,
    IOCTRL_NEBULA_SECRET_ID_RESP,
    IOCTRL_MSG_MAX_COUNT,              //not a msg, just count tutk defined msg count
    IOCTRL_NOT_SUPPORT_MSG = 0x0FF,
    IOCTRL_USER_DEFINED_START = 0x100,
    //user defined
    IOCTRL_USER_DEFINED_END = 0xFFFF
} NebulaIOCtrlType;

//IOCTRL_UDID_REQ
typedef struct
{
    char reserve[4];
} NebulaIOCtrlMsgUDIDReq;

//IOCTRL_UDID_RESP
typedef struct
{
    char udid[MAX_PUBLIC_UDID_LENGTH];      // exclude the Null character '\0'
    char pin_code[MAX_PIN_CODE_LENGTH + 1];
} NebulaIOCtrlMsgUDIDResp;

//IOCTRL_SSIDLIST_REQ
typedef struct
{
    uint8_t max_ap_count;
} NebulaIOCtrlMsgSSIDListReq;

//IOCTRL_SSIDLIST_RESP
typedef struct
{
    char ssid[MAX_WIFI_SSID_LENGTH + 1];       //WiFi ssid
    uint8_t enctype;                       //refer to NebulaAPEncTypeEnum
} NebulaIOCtrlMsgSSIDListResp;

//IOCTRL_SETWIFI_REQ
typedef struct
{
    char ssid[MAX_WIFI_SSID_LENGTH + 1];       //WiFi ssid
    char password[MAX_WIFI_PWD_LENGTH + 1];    //if exist, WiFi password
    uint8_t enctype;                       //refer to NebulaAPEncTypeEnum
} NebulaIOCtrlMsgSetWifiReq;

//IOCTRL_SETWIFI_RESP
typedef struct
{
    char ssid[MAX_WIFI_SSID_LENGTH + 1];       //WiFi ssid
    uint8_t result;                        //refer to NebulaWifiConncetResult
} NebulaIOCtrlMsgSetWifiResp;

//IOCTRL_SETREGION_REQ
typedef struct
{
    uint8_t tutk_region;                   //refer to enum TUTKRegion in TUTKGlobalAPIs.h
} NebulaIOCtrlMsgSetRegionReq;

//IOCTRL_SETREGION_RESP
typedef struct
{
    uint8_t result;                        //refer to NebulaSetConfigResult
} NebulaIOCtrlMsgSetRegionResp;

//IOCTRL_NEBULA_BIND_REQ
typedef struct
{
    char reserve[4];
} NebulaIOCtrlMsgNebulaBindReq;

//IOCTRL_NEBULA_BIND_RESP
//NebulaIOCtrlMsgNebulaBindResp is a string buffer ,need to include the Null character '\0'

//IOTYPE_SETTIMEZONE_REQ
typedef struct
{
    char timezone_str[MAX_TIMEZONE_LENGTH+1];
}NebulaIOCtrlMsgTimeZoneReq;

//IOTYPE_SETTIMEZONE_RESP
typedef struct
{
    uint8_t result;                        //refer to NebulaSetConfigResult
}NebulaIOCtrlMsgTimeZoneResp;

//IOTYPE_GETFWVERSION_REQ
typedef struct
{
    char reserve[4];
} NebulaIOCtrlMsgGetFWVersionReq;

//IOTYPE_GETFWVERSION_RESP
//NebulaIOCtrlMsgGetFWVersionResp is a string buffer ,need to include the Null character '\0'

//IOTYPE_LAN_RESTART_REQ
typedef struct
{
    char reserve[4];
} NebulaIOCtrlMsgLanRestartReq;

//IOTYPE_LAN_RESTART_RESP
typedef struct
{
    uint8_t result;                        //refer to NebulaSetConfigResult
}NebulaIOCtrlMsgLanRestartResp;

//IOCTRL_NEBULA_SECRET_ID_REQ
typedef struct
{
    char reserve[4];
} NebulaIOCtrlMsgNebulaSecretIdReq;

//IOCTRL_NEBULA_SECRET_ID_RESP
//NebulaIOCtrlMsgNebulaSecretIdResp is a string buffer ,need to include the Null character '\0'


//IOCTRL_NOT_SUPPORT_MSG
typedef struct
{
    uint16_t type;
} NebulaIOCtrlNotSupportMsg;

#ifdef __cplusplus
}
#endif /* __cplusplus */

#endif /* _WIFI_CONFIG_H_ */
