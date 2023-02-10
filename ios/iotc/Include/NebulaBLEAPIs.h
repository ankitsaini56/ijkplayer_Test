/*! \file NebulaBLEAPIs.h
This file describes all the APIs of the IOTC module in IOTC platform.
IOTC module is a kind of data communication modules to provide basic data
transfer among devices and clients.

\copyright Copyright (c) 2010 by Throughtek Co., Ltd. All Rights Reserved.

Revision Table

Version     | Name             |Date           |Description
------------|------------------|---------------|-------------------
4.0.0.0     |Terry Liu         |2019-08-07     |+ Add Nebula_BLE_Get_Service_Info, Nebula_BLE_Get_Characteristic_Net_Status_Info
4.0.0.0     |Terry Liu         |2019-08-07     |+ Add Nebula_BLE_Get_Characteristic_IOCtrl_Info, Nebula_BLE_Client_Restore_IOCtrl_Message
4.0.0.0     |Terry Liu         |2019-08-07     |+ Add Nebula_BLE_Client_Generate_IOCtrl_Message, Nebula_BLE_Device_Initialize
4.0.0.0     |Terry Liu         |2019-08-07     |+ Add Nebula_BLE_Device_DeInitialize, Nebula_BLE_Device_Service_Start
4.0.0.0     |Terry Liu         |2019-08-07     |+ Add Nebula_BLE_Device_Service_Stop, Nebula_BLE_Device_Send_User_IOCtrl_Message
4.0.0.0     |Terry Liu         |2019-08-07     |+ Add Nebula_BLE_Device_Receive_IOCtrl_Message
 */
 
#ifndef _NEBULA_BLEAPIs_H_
#define _NEBULA_BLEAPIs_H_

#include "NebulaWiFiConfig.h"
#include "NebulaError.h"
#include "TUTKGlobalAPIs.h"

/* ============================================================================
 * Platform Dependant Macro Definition
 * ============================================================================
 */

#define NEBULA_BLE_API

#ifdef __cplusplus
extern "C" {
#endif /* __cplusplus */


/* ============================================================================
 * Generic Macro Definition
 * ============================================================================
 */

#define BLE_PROPERTY_BROADCAST                  (0x01)//unused
#define BLE_PROPERTY_READ                       (0x02)
#define BLE_PROPERTY_WRITE_WITHOUT_RESPONSE     (0x04)
#define BLE_PROPERTY_WRITE                      (0x08)//unused
#define BLE_PROPERTY_NOTIFY                     (0x10)
#define BLE_PROPERTY_INDICATE                   (0x20)//unused
#define BLE_PROPERTY_AUTH_WRITE                 (0x40)//unused
#define BLE_PROPERTY_EXTS                       (0x80)//unused

/* ============================================================================
 * Type Definition
 * ============================================================================
 */

typedef NebulaIOCtrlMsgUDIDResp NebulaUDIDPinInfo_t;
typedef NebulaIOCtrlMsgSSIDListResp NebulaSSIDInfo_t;
typedef NebulaIOCtrlMsgSetWifiResp NebulaNetInfo_t;

/**
 * \details The prototype of get Central connect count function.
 *			This function will be call when Nebula check Central connect count.
 *
 * \param connected_count [out] Count of Central
 *
 */
typedef void NebulaGetCentralConnectCountFn(int *connected_count);

/**
 * \details The prototype of get Wifi connect status function.
 *			This function will be call when Nebula check Wifi connect status.
 *
 * \param connected_net_info [out] SSID and connect result
 *
 */
typedef void NebulaGetNetStatusFn(NebulaNetInfo_t *connected_net_info);

/**
 * \details The prototype of get UDID and pair PIN code function.
 *			This function will be call when Peripheral get IOCTRL_UDID_REQ.
 *
 * \param udid_pin_info [out] UDID and PIN code
 *
 */
typedef void NebulaGetUDIDPinFn(NebulaUDIDPinInfo_t *udid_pin_info);

/**
 * \details The prototype of get current scanned Wifi AP list function.
 *			This function will be call when Peripheral get IOCTRL_SSIDLIST_REQ.
 *
 * \param max_array_length [in] Max element number of ssid_info_array
 * \param ssid_info_array [out] Array of SSID & encode type info
 * \param array_count [out] Actual length of ssid_info_array
 * 
 * \attention ssid_info_array will free by SDK
 *
 */
typedef void NebulaGetSSIDListFn(int max_array_length, NebulaSSIDInfo_t *ssid_info_array, int *array_count);

/**
 * \details The prototype of connect assigned Wifi AP function.
 *			This function will be call when Peripheral get IOCTRL_SETWIFI_REQ.
 *
 * \param ssid [in] SSID to connect
 * \param password [in] Password of this Wifi AP
 * \param enctype [in] Encode type of NebulaAPEncTypeEnum
 *
 */
typedef void NebulaConnectWifiApFn(const char *ssid, const char *password, NebulaAPEncTypeEnum enctype);

/**
 * \details The prototype of set region function.
 *			This function will be call when Peripheral get IOCTRL_SETREGION_REQ.
 *
 * \param region [in] region number of TUTKRegion
 * \param result [out] set result of NebulaSetConfigResult
 *
 * \see TUTK_SDK_Set_Master_Region()
 *
 */
typedef void NebulaSetRegionFn(TUTKRegion region, NebulaSetConfigResult *result);

/**
 * \details The prototype of get Nebula local bind information function.
 *			This function will be call when Peripheral get IOCTRL_NEBULA_BIND_REQ.
 *
 * \param nebula_bind_message_string [out] Nebula local bind message
 *
 * \see Nebula_Device_New_Credential()
 *
 */
typedef void NebulaGetBindMsgFn(char **nebula_bind_message_string);


/**
 * \details The prototype of get Nebula secret id.
 *			This function will be call when Peripheral get IOCTRL_NEBULA_SCRET_ID_REQ.
 *
 * \param nebula_secret_id_string [out] Nebula local bind message
 *
 * \see Nebula_Device_New_Credential()
 *
 */
typedef void NebulaGetSecretIdFn(char **nebula_secret_id_string);

/**
 * \details The prototype of set timezone function.
 *			This function will be call when Peripheral get IOTYPE_SETTIMEZONE_REQ.
 *
 * \param timezone_str [in] Timezone string
 * \param result [out] set result of NebulaSetConfigResult
 *
 */
typedef void NebulaSetTimeZoneFn(const char *timezone_str, NebulaSetConfigResult *result);

/**
 * \details The prototype of get firmware version function.
 *			This function will be call when Peripheral get IOTYPE_GETFWVERSION_REQ.
 *
 * \param fw_version_string [out] firmware version 
 *
 */
typedef void NebulaGetFWVersionMsgFn(char **fw_version_string);

/**
 * \details The prototype of Nebula BLE send function.
 *			This function will be call when Peripheral need to send response to Central.
 *
 * \param characteristic_uuid [in] Which characteristic to send message
 * \param ble_operate_type [in] Send by BLE_PROPERTY_NOTIFY or BLE_PROPERTY_INDICATE
 * \param message [in] Part of IOCtrl message
 * \message_length message [in] Length of message
 *
 */
typedef void NebulaSendMessageFn(const char *characteristic_uuid, int ble_operate_type, const char *message, int message_length);

/**
 * \details Nebula will set 4 Bytes data ( 2 Bytes NebulaIOCtrlType + 2 Bytes IOCtrl message length) in BLE READ buffer.
 *			This function will be call when Peripheral need to send response to Central.
 *
 * \param characteristic_uuid [in] Which characteristic to keep data
 * \param data [in] Type & actual message length of IOCtrl message
 * \param data_length [in] Length of data_length
 *
 */
typedef void NebulaSetReadDataFn(const char *characteristic_uuid, const char *data, int data_length);

/**
 * \details The prototype of IOCtrl handle function.
 *			This function will be call after RestoreIOCtrlFromBLE() restore IOCtrl message or Nebula_BLE_Device_Receive_IOCtrl_Message() restore user defined message.
 *
 * \param type [in] Type of NebulaIOCtrlType 
 * \param ioctrl_buf [in] Pointer of IOCtrl struct
 * \param ioctrl_len [in] Length of ioctrl_buf
 *
 */
typedef void NebulaHandleIOCtrlFn(NebulaIOCtrlType type, const char *ioctrl_buf, int ioctrl_len);

/* ============================================================================
 * Type Definition
 * ============================================================================
 */

typedef struct 
{
    unsigned int struct_length;

    /*BLE message*/
    NebulaSendMessageFn *msg_sender;
    NebulaSetReadDataFn *set_read_data;

    /*User BLE message handler (this callback can be NULL) ,This callback will be call after Device get user defined message.*/
    NebulaHandleIOCtrlFn *user_ioctrl_handler;

    /*Wifi configuration*/
    NebulaGetCentralConnectCountFn *get_central_count;
    NebulaGetNetStatusFn *get_net_status;
    NebulaGetUDIDPinFn *get_udid_pin;
    NebulaGetSSIDListFn *get_ssid_list;
    NebulaConnectWifiApFn *connect_to_wifi;

    /*Set TUTK region (this callback can be NULL)*/
    NebulaSetRegionFn *set_tutk_region;

    /*Nebula local bind (this callback can be NULL)*/
    NebulaGetBindMsgFn *get_bind_message;

    /*Set timezone (this callback can be NULL)*/
    NebulaSetTimeZoneFn *set_timezone;

    /*Get Device FW version (this callback can be NULL)*/
    NebulaGetFWVersionMsgFn *get_fw_version;
    NebulaGetSecretIdFn *get_secret_id;
} NebulaBLEDeviceCallbackGroup1;

typedef const NebulaBLEDeviceCallbackGroup1 *NebulaBLEDeviceInitializeConfig;

/* ============================================================================
 * Function Declaration
 * ============================================================================
 */

/**
 * \brief Get Nebula BLE Service UUID 
 *
 * \details This function for Central and Peripheral to get Nebula service UUID.
 *
 * \param uuid_buff [out] UUID of Service
 * \param buff_length [in] buffer length
 *
 * \return = NEBULA_ER_NoERROR for return UID success
 * \return Error code if return value < 0
 *          - #NEBULA_ER_INVALID_ARG for pointer is NULL
 *
 */
NEBULA_BLE_API int Nebula_BLE_Get_Service_Info(char *uuid_buff, int buff_length);

/**
 * \brief Get Nebula BLE Characteristic UUID and property of IOCtrl message
 *
 * \details This function for Central and Peripheral to get Nebula IOCtrl message UUID.
 *
 * \param uuid_buff [out] UUID of Characteristic
 * \param buff_length [in] buffer length
 * \param property [out] Bit field of property.
 *
 * \return = NEBULA_ER_NoERROR for return UUID and property success
 * \return Error code if return value < 0
 *          - #NEBULA_ER_INVALID_ARG for pointer is NULL
 * 
 * \see BLE_PROPERTY_BROADCAST, BLE_PROPERTY_READ, BLE_PROPERTY_WRITE_WITHOUT_RESPONSE, BLE_PROPERTY_WRITE, BLE_PROPERTY_NOTIFY, BLE_PROPERTY_INDICATE, BLE_PROPERTY_AUTH_WRITE, BLE_PROPERTY_EXTS
 *
 */
NEBULA_BLE_API int Nebula_BLE_Get_Characteristic_IOCtrl_Info(char *uuid_buff,int buff_length, uint8_t *property);

/**
 * \brief Get Nebula BLE Characteristic UUID and property of Net Status
 *
 * \details This function for Central and Peripheral to get Nebula net status UUID.
 *
 * \param uuid_buff [out] UUID of Characteristic
 * \param buff_length [in] buffer length
 * \param property [out] Bit field of property.
 *
 * \return = NEBULA_ER_NoERROR for return UUID and property success
 * \return Error code if return value < 0
 *          - #NEBULA_ER_INVALID_ARG for pointer is NULL
 * 
 * \see BLE_PROPERTY_BROADCAST, BLE_PROPERTY_READ, BLE_PROPERTY_WRITE_WITHOUT_RESPONSE, BLE_PROPERTY_WRITE, BLE_PROPERTY_NOTIFY, BLE_PROPERTY_INDICATE, BLE_PROPERTY_AUTH_WRITE, BLE_PROPERTY_EXTS
 *
 */
NEBULA_BLE_API int Nebula_BLE_Get_Characteristic_Net_Status_Info(char *uuid_buff,int buff_length, uint8_t *property);

/**
 * \brief Generate IOCtrl message for BLE client send function
 *
 * \details This function for generate IOCtrl message.
 *          Nebula BLE Central feed IOCtrl struct data , this function will generate IOCtrl message for WRITE.
 *
 * \param type [in] Type of IOCtrl
 * \param ioctrl_struct_ptr [in] Poniter of IOCtrl struct
 * \param ioctrl_struct_length [in] Length of ioctrl_struct_ptr
 * \param message_buf [out] IOCtrl message for BLE send function
 * \param message_buf_size [in] send_buf size
 *
 * \return > 0 for actual data size when generate message suucess.
 * \return Error code if return value < 0
 *          - #NEBULA_ER_INVALID_ARG type is invalid or buffer pointer is NULL
 *          - #NEBULA_ER_EXCEED_BUFFER_SIZE send_buf is too small
 *          - #NEBULA_ER_MESSAGE_CHECK_FAIL input wrong struct of type
 *
 */
NEBULA_BLE_API int Nebula_BLE_Client_Generate_IOCtrl_Message(NebulaIOCtrlType type, const char *ioctrl_struct_ptr, uint16_t ioctrl_struct_length, char *message_buf, uint16_t message_buf_size);

/**
 * \brief Handle IOCtrl message from BLE client receive function
 *
 * \details This function for Nebula BLE Central to handle IOCtrl message from Nebula BLE Peripheral and recovery to IOCtrl struct.
 *
 * \param characteristic_uuid [in] UUID of Characteristic
 * \param recv_buf [in] Pointer of receive buffer
 * \param data_len [in] Actual data length
 * \param HandleIOCtrl [in] IOCtrl callback function 
 *
 * \return > 0 for NebulaIOCtrlType and IOCtrl message receive completely.
 * \return = NEBULA_ER_NoERROR is message NOT receive completely.
 * \return Error code if return value < 0
 *          - #NEBULA_ER_INVALID_ARG buffer pointer is NULL or callback is NULL
 *          - #NEBULA_ER_EXCEED_BUFFER_SIZE send_buf is too small
 *          - #NEBULA_ER_UNKNOW_MESSAGE unknow IOCtrl message or unknow ble_uuid
 *
 */
NEBULA_BLE_API int Nebula_BLE_Client_Restore_IOCtrl_Message(const char *characteristic_uuid, const char *recv_buf, uint16_t data_len, NebulaHandleIOCtrlFn HandleIOCtrl);

/**
 * \brief Initialize callback function for Nebula Device
 *
 * \details This function for Nebula BLE Peripheral to register callback function .
 *
 * \param device_init_config [in] a pointer to structure which store all input parameters
 *
 * \return NEBULA_ER_NoERROR for initialize success
 * \return Error code if return value < 0
 *          - #NEBULA_ER_INVALID_ARG for pointer is NULL
 *
 * \see NebulaGetCentralConnectCountFn(), NebulaGetNetStatusFn(), NebulaGetUDIDPinFn(), NebulaGetSSIDListFn(), NebulaConnectWifiApFn(), NebulaSendMessageFn(), NebulaSetReadDataFn(), NebulaHandleIOCtrlFn()
 *
 */
NEBULA_BLE_API int Nebula_BLE_Device_Initialize(NebulaBLEDeviceInitializeConfig device_init_config);

/**
 * \brief DeInitialize callback function
 *
 * \details This function for Nebula BLE Peripheral to deregister callback function .
 *
 * \return NEBULA_ER_NoERROR for deInitialize success
 * \return Error code if return value < 0
 *          - #NEBULA_ER_BLE_SERVICE_NOT_STOP Service is not stop
 *
 */
NEBULA_BLE_API int Nebula_BLE_Device_DeInitialize();

/**
 * \brief Nebula Device Start BLE service
 *
 * \details This function for Nebula BLE Peripheral to start Nebula BLE service and Characteristic .
 *
 * \return NEBULA_ER_NoERROR for start service success
 * \return Error code if return value < 0
 *          - #NEBULA_ER_NOT_INITIALIZE Service is not initialize
 *          - #NEBULA_ER_FAIL_CREATE_THREAD Create thread fail
 *
 */
NEBULA_BLE_API int Nebula_BLE_Device_Service_Start();

/**
 * \brief Nebula Device Stop BLE service
 *
 * \details This function for Nebula BLE Peripheral to stop Nebula BLE service and Characteristic.
 *
 * \return NEBULA_ER_NoERROR for stop service success
 * \return Error code if return value < 0
 *          - #NEBULA_ER_NOT_INITIALIZE Service is not initialize
 *          - #NEBULA_ER_BLE_DEVICE_NOT_READY Service is not start
 *
 */
NEBULA_BLE_API int Nebula_BLE_Device_Service_Stop();

/**
 * \brief Nebula Device send user define IOCtrl message
 *
 * \details This function for Nebula BLE Peripheral to response user define IOCtrl request from Nebula BLE Central.
 *
 * \param user_define_type [in] User defined type of NebulaIOCtrlType (257~65534)
 * \param user_data [in] Data
 * \param user_data_len [in] Length of user_data
 *
 * \return NEBULA_ER_NoERROR for send message success
 * \return Error code if return value < 0
 *          - #NEBULA_ER_NOT_INITIALIZE Service is not initialize
 *          - #NEBULA_ER_BLE_DEVICE_NOT_READY Service is not start
 *          - #NEBULA_ER_INVALID_ARG user_define_type is invalid
 *          - #NEBULA_ER_BLE_CALLBACK_IS_NULL Callback function is invalid
 *          - #NEBULA_ER_BLE_UNKNOW_STATUTS Central number is invalid 
 *
 */
NEBULA_BLE_API int Nebula_BLE_Device_Send_User_IOCtrl_Message(NebulaIOCtrlType user_define_type, const char *user_data, uint16_t user_data_len);

/**
 * \brief Nebula Device handle IOCtrl message from Nebula Client
 *
 * \details This function for Nebula BLE Peripheral to handle IOCtrl message from Nebula BLE Central.
 *
 * \param recv_buf [in] Data buffer
 * \param data_len [in] Actual data length of recv_buf
 *
 * \return NEBULA_ER_NoERROR for send message success
 * \return Error code if return value < 0
 *          - #NEBULA_ER_NOT_INITIALIZE Service is not initialize
 *          - #NEBULA_ER_BLE_DEVICE_NOT_READY Service is not start
 *          - #NEBULA_ER_INVALID_ARG buffer pointer is NULL
 *          - #NEBULA_ER_UNKNOW_MESSAGE IOCtrl message length is invalid
 *
 */
NEBULA_BLE_API int Nebula_BLE_Device_Receive_IOCtrl_Message(const char *recv_buf, uint16_t data_len);

#ifdef __cplusplus
}
#endif /* __cplusplus */

#endif /* _NEBULA_BLEAPIs_H_ */


