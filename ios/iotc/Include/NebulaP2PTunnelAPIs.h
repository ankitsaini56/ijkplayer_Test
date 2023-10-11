/*! \file P2PTunnelAPIs.h
This file describes all the APIs of the P2PTunnel module in IOTC platform.
P2PTunnel module is a kind of medium module to map local service to remote can access
like port mapping among devices and clients. ex: Web, ssh etc.

\copyright Copyright (c) 2021 by Throughtek Co., Ltd. All Rights Reserved.*/


#ifndef _NebulaP2PTunnelAPIs_H_
#define _NebulaP2PTunnelAPIs_H_

#include "IOTCAPIs.h"
#include "P2PTunnelAPIs.h"


/* ============================================================================
 * Platform Dependant Macro Definition
 * ============================================================================
 */


#ifdef _WIN32
/** @cond */
#ifdef IOTC_STATIC_LIB
#define TUNNEL_API
#elif defined P2PAPI_EXPORTS
#define TUNNEL_API __declspec(dllexport)
#else
#define TUNNEL_API __declspec(dllimport)
#endif // #ifdef P2PAPI_EXPORTS
/** @endcond */
#endif // #ifdef _WIN32

#if defined(__GNUC__) || defined(__clang__)
    #define TUNNEL_API_DEPRECATED __attribute__((deprecated))
#elif defined(_MSC_VER)
    #ifdef IOTC_STATIC_LIB
        #define TUNNEL_API_DEPRECATED __declspec(deprecated)
    #elif defined P2PAPI_EXPORTS
        #define TUNNEL_API_DEPRECATED __declspec(deprecated, dllexport)
    #else
        #define TUNNEL_API_DEPRECATED __declspec(deprecated, dllimport)
    #endif
#else
    #define TUNNEL_API_DEPRECATED
#endif

#if defined(__linux__) || defined (__APPLE__)
#define TUNNEL_API
#define __stdcall
#endif // #ifdef __linux__

#ifdef __cplusplus
extern "C" {
#endif /* __cplusplus */

/* ============================================================================
 * Function Declaration
 * ============================================================================
 */

/**
 * \brief Start a tunnel server with DTLS mode by Nebula
 *
 * \details This function will start a tunnel server with given NebulaDeviceCtx
 *
 * \param pDeviceCtx [in] The pointer of NebulaDeviceCtx
 * \param pfxSessionInfoExFn [in]  The function pointer to get tunnel session information function. Can be NULL.
 * \param pArg [in] User can give data pointer to pass to pfxTunnelServerAuthFn and pfxSessionInfoExFn when this
 *             call back function is triggered. Can be NULL.
 *
 * \return #TUNNEL_ER_NoERROR if start successfully
 * \return Error code if return value < 0
 *			- #TUNNEL_ER_NOT_INITIALIZED P2PModule has not been initialized in that tunnel server
 *			- #TUNNEL_ER_FAIL_CREATE_THREAD Failed to create thread
 *			- #TUNNEL_ER_UID_NO_PERMISSION This UID not support P2PModule and TCP relay function
 *			- #TUNNEL_ER_UID_UNLICENSE This UID is not licensed or expired
 */
TUNNEL_API int32_t P2PTunnelServer_Start_By_Nebula(NebulaDeviceCtx *pDeviceCtx, tunnelSessionInfoExCB pfxSessionInfoExFn, const void *pArg);

/**
 * \brief Connect to a tunnel server with DTLS mode by Nebula
 *
 * \details This function used by a tunnel agent to connect the tunnel server
 *			with specified NebulaClientCtx
 *
 * \param pClientCtx [in] The pointer of NebulaClientCtx that try to connect tunnel server
 * \param cszAmToken [in] The rental token from AM server
 * \param cszRealm [in] The realm of rental server needed for device
 * \param nTimeoutMs [in] The timeout for this function in unit of millisecond, give 0 means return immediately
 *
 * \return Tunnel Session ID if return value >= 0
 * \return #TUNNEL_ER_NoERROR if connect successfully
 * \return Error code if return value < 0
 *			- #TUNNEL_ER_NOT_INITIALIZED P2PModule has not been initialized in that tunnel agent
 *			- #TUNNEL_ER_AUTH_FAILED The tunnel agent failed to connect to tunnel server
 *					because authentication data is illegal.
 *			- #TUNNEL_ER_UID_UNLICENSE This UID is illegal or does not support P2PTunnel function
 *			- #TUNNEL_ER_UID_NO_PERMISSION This UID not support P2PModule and TCP relay function
 *			- #TUNNEL_ER_UID_NOT_SUPPORT_RELAY This UID can't setup connection through relay
 *			- #TUNNEL_ER_DEVICE_NOT_ONLINE The specified tunnel server does not login to IOTC server yet
 *			- #TUNNEL_ER_DEVICE_NOT_LISTENING The specified tunnel server is not listening for connection,
 *					it maybe busy at establishing connection with other tunnel agent
 *			- #TUNNEL_ER_NETWORK_UNREACHABLE Internet is not available or firewall blocks connection
 *			- #TUNNEL_ER_FAILED_SETUP_CONNECTION Can't connect to the tunnel server although it is online
 *					and listening for connection, it maybe caused by internet unstable situation
 *			- #TUNNEL_ER_OPERATION_IS_INVALID Not support manual mode
 *			- #TUNNEL_ER_HANDSHAKE_FAILED Create connection fail
 *			- #TUNNEL_ER_REMOTE_NOT_SUPPORT_DTLS Remote not support DTLS
 *			- #TUNNEL_ER_TIMEOUT Connect timeout
 *
 */
TUNNEL_API int32_t P2PTunnelAgent_Connect_By_Nebula(NebulaClientCtx *pClientCtx, const char *cszAmToken, const char *cszRealm, uint32_t nTimeoutMs);

/**
 * \brief It's to stop the progressing of connection for Nebula.
 *
 * \details This API is for a client to stop connecting to a device.
 * 			We can use it to stop connecting when client blocks in P2PTunnelAgent_Connect_By_Nebula().
 *
 * \param pClientCtx [in] The NebulaClientCtx of tunnel connection
 *
 * \return 0 if success
 * \return Error code if return value < 0
 *			- #TUNNEL_ER_AGENT_NOT_CONNECTING Tunnel Agent isn't connecting.
 *
 */
TUNNEL_API int32_t P2PTunnelAgent_Connect_Stop_By_Nebula(NebulaClientCtx *pClientCtx);

#ifdef __cplusplus
}
#endif /* __cplusplus */


#endif /* _P2PTunnelAPIs_H_ */
