/*! \file NebulaLANAPIs.h
This file describes all the APIs of the IOTC module in IOTC platform.
IOTC module is a kind of data communication modules to provide basic data
transfer among devices and clients.

\copyright Copyright (c) 2010 by Throughtek Co., Ltd. All Rights Reserved.

Revision Table

Version     | Name             |Date           |Description
------------|------------------|---------------|-------------------
4.0.0.0     |Terry Liu         |2019-08-07     |+ Add Nebula_WiFi_Setup_Start_On_LAN, Nebula_Device_Listen_On_LAN
4.0.0.0     |Terry Liu         |2019-08-07     |+ Add Nebula_App_Search_UDID_On_LAN, Nebula_App_Request_TCP_Connect_On_LAN
4.0.0.0     |Terry Liu         |2019-08-07     |+ Add Nebula_WiFi_Setup_Stop_On_LAN, Nebula_Send_IOCtrl_On_LAN
4.0.0.0     |Terry Liu         |2019-08-07     |+ Add Nebula_Recv_IOCtrl_From_LAN
 */
 
#ifndef _NEBULA_LANAPIs_H_
#define _NEBULA_LANAPIs_H_

#include <NebulaWiFiConfig.h>
#include "NebulaAPIs.h"
#include "NebulaError.h"

/* ============================================================================
 * Platform Dependant Macro Definition
 * ============================================================================
 */

#ifdef _WIN32
    #ifdef IOTC_STATIC_LIB
        #define NEBULA_LAN_API
    #elif defined P2PAPI_EXPORTS
        #define NEBULA_LAN_API __declspec(dllexport)
    #else
        #define NEBULA_LAN_API __declspec(dllimport)
    #endif
#else
    #define NEBULA_LAN_API
    #define __stdcall
#endif

#if defined(__GNUC__) || defined(__clang__)
    #define NEBULA_LAN_API_DEPRECATED __attribute__((deprecated))
#elif defined(_MSC_VER)
    #ifdef IOTC_STATIC_LIB
        #define NEBULA_LAN_API_DEPRECATED __declspec(deprecated)
    #elif defined P2PAPI_EXPORTS
        #define NEBULA_LAN_API_DEPRECATED __declspec(deprecated, dllexport)
    #else
        #define NEBULA_LAN_API_DEPRECATED __declspec(deprecated, dllimport)
    #endif
#else
    #define NEBULA_LAN_API_DEPRECATED
#endif

#ifdef __cplusplus
extern "C" {
#endif /* __cplusplus */


/* ============================================================================
 * Generic Macro Definition
 * ============================================================================
 */

#define MAX_DEVICE_NAME_LENGTH 128
#define PWD_MAX_LENGTH 64

/* ============================================================================
 * Enumeration Declaration
 * ============================================================================
 */

  typedef enum{
    DEVICE,
    CLIENT
  } LanSearchRole;


/* ============================================================================
 * Structure Definition
 * ============================================================================
 */

  typedef struct _st_UDIDInfo
  {
    char udid[MAX_PUBLIC_UDID_LENGTH + 1];
    char device_name[MAX_DEVICE_NAME_LENGTH + 1];
  } st_UDIDInfo;


/* ============================================================================
 * Function Declaration
 * ============================================================================
 */

/**
 * \brief Start to lan search without UID for WiFi setup
 *
 * \details Start to lan search for WiFi setup. After calling this api, user can listen or
 *          request tcp connect to create tcp connection.
 *
 * \param role [in] role of lan search.
 * \param searchable [in] This device could be searched with empty search name or not.
 *
 * \return 0 if start lan search successfully.
 * \return Error code if return value < 0
 *          - #NEBULA_ER_INVALID_ARG if value of role is invalid.
 *          - #NEBULA_ER_RESOURCE_ERROR Getting system resource fail.
 *
 */

NEBULA_LAN_API int Nebula_WiFi_Setup_Start_On_LAN(LanSearchRole role, int searchable);

/**
 * \brief Device can listen for tcp request on lan or response to lan search with search id.
 *
 * \details Devcie will start listen for tcp request and create connection on LAN
 *          after calling this api.
 *
 * \param udid [in] The udid of Device.
 * \param pwd [in] The pwd of Device, max length is 64. This param could be null, if device don't want to use.
 * \param device_name [in] The name of Device, max length is 128. This param could be null, if device don't want to use.
 * \param timeout_ms [in] timeout in miliseconds for this api.
 *
 * \return 0 if tcp connection create successfully.
 * \return Error code if return value < 0
 *          - #NEBULA_ER_INVALID_ARG if input is invalid.
 *          - #NEBULA_ER_SOCKET_ERROR system socket setup failed.
 *          - #NEBULA_ER_FORCE_STOP Nebula_WiFi_Setup_Stop_On_LAN already called.
 *          - #NEBULA_ER_TIMEOUT Device listen timeout.
 *          - #NEBULA_ER_TCP_ALREADY_CONNECTED if TCP connection already created.
 *
 */

NEBULA_LAN_API int Nebula_Device_Listen_On_LAN(const char* udid, const char* pwd, const char* device_name, uint16_t timeout_ms);

/**
 * \brief Used by client for searching devices's UDID on LAN.
 *
 * \details  When clients and devices are stay in a LAN environment, client can call this function
 *      to discovery devices.
 *
 * \param udid_array [in] The array of udid to store search result.The value of udid_array could not be null.
 * \param array_cnt [in] The size of the udid array, it could not be zero.
 * \param timeout_ms [in] The timeout in miliseconds before discovery process end and could not be zero.
 *
 * \return The number of devices found.
 * \return Error code if return value < 0
 *          - #NEBULA_ER_INVALID_ARG if value of udid_array, array_cnt and timeout_ms is invalid.
 *          - #NEBULA_ER_FORCE_STOP Nebula_WiFi_Setup_Stop_On_LAN already called.
 *          - #NEBULA_ER_RESOURCE_ERROR Getting system resource fail.
 *          - #NEBULA_ER_TCP_ALREADY_CONNECTED if TCP connection already created. 
 *
 */


NEBULA_LAN_API int Nebula_App_Search_UDID_On_LAN(st_UDIDInfo *udid_array, uint16_t array_cnt, uint16_t timeout_ms);

/**
 * \brief Used by client for connect to devices with UDID on LAN.
 *
 * \details  When clients and devices are stay in a LAN environment, client can call this function
 *      to connect to device with TCP.
 *
 * \param udid [in] The udid of Device.
 * \param pwd [in] The pwd of Device, max length is 64. This param could be null, if device not support.
 * \param timeout_ms [in] The timeout in miliseconds before discovery process end.
 *
 * \return 0 if tcp connection create successfully.
 * \return Error code if return value < 0
 *          - #NEBULA_ER_INVALID_ARG if input is invalid.
 *          - #NEBULA_ER_SOCKET_ERROR system socket setup failed.
 *          - #NEBULA_ER_FORCE_STOP Nebula_WiFi_Setup_Stop_On_LAN already called.
 *          - #NEBULA_ER_TIMEOUT request TCP connection timeout.
 *          - #NEBULA_ER_TCP_ALREADY_CONNECTED if TCP connection already created.
 *
 */

NEBULA_LAN_API int Nebula_App_Request_TCP_Connect_On_LAN(const char * udid, const char* pwd, uint16_t timeout_ms);

/**
 * \brief Stop to lan search for WiFi setup
 *
 * \details Stop to lan search for WiFi setup. This api must be called, if 
 *          NEBULA_WiFi_Setup_Start_On_LAN has been called. This api will close tcp connection and 
 *          free memory.
 *
 */

NEBULA_LAN_API void Nebula_WiFi_Setup_Stop_On_LAN();

/**
 * \brief Send WiFi setup IO control
 *
 * \details This function is used by devices or clients to send a
 *      WiFi setup IO control.
 *
 * \param type [in] The type of IO control.
 * \param ioctrl_buf [in] The buffer of IO control data
 * \param ioctrl_len [in] The length of IO control data
 *
 * \return #AV_ER_NoERROR if sending successfully
 * \return Error code if return value < 0
 *
 *
 */

NEBULA_LAN_API int Nebula_Send_IOCtrl_On_LAN(NebulaIOCtrlType type, const char * ioctrl_buf, uint16_t ioctrl_len);

/**
 * \brief Receive WiFi setup IO control
 *
 * \details This function is used by devices or clients to receive a
 *      WiFi setup io control.
 *
 * \param type [out] The type of IO control.
 * \param result_buf [in] The buffer of received IO control data.
 * \param buf_size [in] The max length of buffer of received IO control data.
 * \param timeout_ms [out] The timeout_ms for this function in unit of million-second, give 0 means return immediately.
 *
 * \return If result_buf is NULL this is the required buffer size. If result_buf is non-NULL this is the data size in result_buf.
 * \return Error code if return value < 0
 *      - #AV_ER_INVALID_ARG The AV channel ID is not valid or IO control type
 *        / data is null
 *
 */

NEBULA_LAN_API int Nebula_Recv_IOCtrl_From_LAN(NebulaIOCtrlType* type, char *result_buf, uint16_t buf_size, uint16_t timeout_ms);


#ifdef __cplusplus
}
#endif /* __cplusplus */

#endif /* _NEBULA_LANAPIs_H_ */
