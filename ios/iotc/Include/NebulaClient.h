/*! \file NebulaClient.h
This file describes Nebula module APIs for client.

\copyright Copyright (c) 2010 by Throughtek Co., Ltd. All Rights Reserved.
*/

#ifndef _NEBULACLIENT_H_
#define _NEBULACLIENT_H_

#ifdef __cplusplus
extern "C" {
#endif

#include "NebulaCommon.h"

typedef struct NebulaClientCtx {
    void *info; // Extra info can be attached to NebulaClientCtx
} NebulaClientCtx;

typedef struct NebulaClientInfo {
    int version;
    char public_udid[MAX_PUBLIC_UDID_LENGTH + 1];    //need to include \0
    char psk[MAX_NEBULA_PSK_LENGTH + 1];             //need to include \0
    char identity[MAX_NEBULA_IDENTITY_LENGTH + 1];   //need to include \0
    char secret_id[MAX_NEBULA_SECRETID_LENGTH + 1];  //need to include \0
} NebulaClientInfo;

typedef enum {
    NEBULA_CLILOGIN_ST_CONNECTED       = 1 << 0, // get the login response from Nebula server
    NEBULA_CLILOGIN_ST_DISCONNECTED    = 1 << 1, // disconnected from Nebula server, please check the network status
    NEBULA_CLILOGIN_ST_RETRYLOGIN      = 1 << 2, // retry login to Nebula server
} NebulaClientLoginState;

/**
 * \brief The prototype of client connect state callback function.
 *
 * \details This callback function is called when client connect state changed.
 *
 * \param client [in] client's context generate by Nebula_Client_New()
 * \param state [in] the connect state of Nebula client
 *
 * \see Nebula_Client_Connect()
 */
typedef void(__stdcall *NebulaClientConnectStateFn)(NebulaClientCtx *client, NebulaClientLoginState state);

/**
 * \brief Generat client context for Nebula module at first time.
 *
 * \details This function will generate client context for Nebula module.
 *          Client API for Nebula module need this context to work.
 *          Client need to call this api if never bind to device before.
 *          User can call Nebula_Client_New_From_String replace this api
 *          if user already bind to device.
 *
 * \param public_udid [in] device's public_udid for Nebula module
 * \param ctx[out]  client's context generate by Nebula_Client_New or Nebula_Client_New_From_String;
 *
 * \return #NEBULA_ER_NoERROR if generate client context successfully
 * \return Error code if return value < 0
 *            - #NEBULA_ER_INVALID_ARG The ctx or public_udid is null
 *            - #NEBULA_ER_NOT_INITIALIZE  The Nebula module is not initialized yet
 *            - #NEBULA_ER_MEM_INSUFFICIENT Insufficient memory for allocation
 *
 * \see Nebula_Device_Delete()
 *
 */
NEBULA_API int Nebula_Client_New(const char* public_udid, NebulaClientCtx** ctx);

/**
 * \brief Generate client context with device's information for Nebula module
 *
 * \details This function will generate client context for Nebula module.
 *          Client API for Nebula module need this context to work.
 *          Client call this api if have bind to device and get device'information before.
 *
 * \param NebulaClientInfo [in] the necessary informations from Nebula device
 * \param ctx[out]  client's context generate by Nebula_Client_New or Nebula_Client_New_From_String;
 *
 * \return #NEBULA_ER_NoERROR if generate client context successfully
 * \return Error code if return value < 0
 *            - #NEBULA_ER_INVALID_ARG The ctx or client_info is null or invalid UDID
 *            - #NEBULA_ER_NOT_INITIALIZE  The Nebula module is not initialized yet
 *            - #NEBULA_ER_MEM_INSUFFICIENT Insufficient memory for allocation
 *
 */
NEBULA_API int Nebula_Client_New_From_Struct(NebulaClientInfo *client_info, NebulaClientCtx **ctx);

/**
 * \brief Generate client context with device's information for Nebula module
 *
 * \details This function will generate client context for Nebula module.
 *          Client API for Nebula module need this context to work.
 *          Client call this api if have bind to device and get device'information before.
 *
 * \param public_udid [in] device's public_udid for Nebula module
 * \param string_data [in] client's information that return by Nebula_Client_To_String.
 * \param ctx[out]  client's context generate by Nebula_Client_New or Nebula_Client_New_From_String;
 *
 * \return #NEBULA_ER_NoERROR if generate client context successfully
 * \return Error code if return value < 0
 *            - #NEBULA_ER_INVALID_ARG The ctx or string_data is null or parsing string_data return null
 *            - #NEBULA_ER_NOT_INITIALIZE  The Nebula module is not initialized yet
 *            - #NEBULA_ER_MEM_INSUFFICIENT Insufficient memory for allocation
 *
 * \see Nebula_Client_To_String()
 *
 */
NEBULA_API int Nebula_Client_New_From_String(const char* public_udid, const char* string_data, NebulaClientCtx** ctx);


/**
 * \brief Generating device's information for client which used to create client's context.
 *
 * \details This function will generate device's information in json string after bind to device successfully.
 *          This api must be used after calling Nebula_Client_Bind.
 *
 * \param ctx[in]  client's context generate by Nebula_Client_New or Nebula_Client_New_From_String;
 *
 * \return null if ctx's psk, secret_id or identity is null
 *
 * \see Nebula_Device_Delete()
 *
 */
NEBULA_API char *Nebula_Client_To_String(NebulaClientCtx* ctx);


/**
 * \brief Pairing Nebula Client and Device
 *
 * \details This function is used when Nebula client want to bind a Nebula device.
 *          Device and client need to use same pin code when doing binding process,
 *          after bind success, client will get infomations that is needed for establish
 *          a connection to device.
 *
 * \param client_ctx [in] Nebula context of client, it's from Nebula_Client_New() or Nebula_Client_New_From_String()
 * \param pin_code [in] Same PIN code of Nebula_Device_Bind()
 * \param bind_response [out] The bind response json object,this object has identity, avToken and authKey
 * \param timeout_msec [in] The timeout for this function in unit of millisecond, give 0 means block forever
 * \param abort_flag [in] set *abort_flag to 1 if you need to abort this function
 *
 * \return #NEBULA_ER_NoERROR if bind to a device successfully
 * \return Error code if return value < 0
 *            - #NEBULA_ER_INVALID_ARG The ctx, pin_code or json_response is null
 *            - #NEBULA_ER_NOT_INITIALIZE  The Nebula module is not initialized yet
 *            - #NEBULA_ER_BIND_SERVER_LOGIN_FAIL Failed to login to binding server
 *            - #NEBULA_ER_DEVICE_NOT_READY Device not ready for binding
 *            - #NEBULA_ER_EXCEED_BUFFER_SIZE If generate json string size larger than response_buf_size
 *            - #NEBULA_ER_TIMEOUT Bind or query bind server timeout
 *            - #NEBULA_ER_HTTP_ERROR Bind or query bind Server response error
 *
 * \see Nebula_Device_Bind(), Nebula_Client_New(), Nebula_Client_New_From_String()
 *
 * \attention (1) Recommended value of timeout: 1000 millisecond ~ 30000 millisecond
 *            (2) Once the client bind to a sepecific device successfully, there is no need to do binding again
 *                when client want to connect to the device next time.
 */
NEBULA_API int Nebula_Client_Bind(NebulaClientCtx* ctx, const char *pin_code,
                                  NebulaJsonObject **bind_response,
                                  unsigned int timeout_msec, unsigned int *abort_flag);


/**
 * \brief Release response data of Nebula_Client_Bind
 *
 * \details  This function is used to release json response data from Nebula_Client_Bind,
 *           you must call it after you got the json response data from Nebula_Client_Bind
 *
 * \param bind_response [in] This json response data pointer
 *
 * \return #Nebula_ER_NoERROR if free json response data pointer successfully
 * \return Error code if return value < 0
 *            - #Nebula_ER_INVALID_ARG The bind_response in null
 */
NEBULA_API int Nebula_Client_Free_Bind_Response(NebulaJsonObject *bind_response);

/**
 * \brief Client connect to Bridge server.
 *
 * \details This function is used by client to connect to bridge server.
 *          Client will create persistent connection with bridge server for reduce delay of send command
 *          This function is unnecessary before Nebula_Client_Send_Command()
 *
 * \param ctx[in] client's context generate by Nebula_Client_New()
 * \param connect_state_handler[in] this function will be call when connect status change
 * \param timeout_msec [in] The timeout for this function in unit of millisecond, give 0 means block forever
 * \param abort_flag [in] set *abort_flag to 1 if you need to abort this function
 *
 * \return #NEBULA_ER_NoERROR if login to Nebula server successfully
 * \return Error code if return value < 0
 *            - #NEBULA_ER_INVALID_ARG The ctx is null
 *            - #NEBULA_ER_NOT_INITIALIZE  The Nebula module is not initialized yet
 *            - #NEBULA_ER_LOGIN_ALREADY_CALLED This function already called
 *            - #NEBULA_ER_INVALID_UDID The UDID is incorrect
 *            - #NEBULA_ER_UDID_EXPIRED The UDID is expired
 *            - #NEBULA_ER_FAIL_CREATE_THREAD Fails to create threads
 *
 * \see Nebula_Client_New() ,Nebula_Client_New_From_Struct(), Nebula_Client_New_From_String(), Nebula_Client_Send_Command()
 */
NEBULA_API int Nebula_Client_Connect(NebulaClientCtx *ctx, NebulaClientConnectStateFn connect_state_handler, unsigned int timeout_msec, unsigned int *abort_flag);

/**
 * \brief Send Nubula command message to Device
 *
 * \details This function is used by Nebula client to send a command in json format.
 *          User can get response from Nebula device through the output response buffer.
 *          This function is not support for Nebula device currently.
 *
 * \param client_ctx [in] Nebula context of client, it's from Nebula_Client_New() or Nebula_Client_New_From_String()
 * \param request [in] JSON string of Nebula request
 * \param response [out] The bind response json object
 * \param timeout_msec [in] The timeout for this function in unit of millisecond, give 0 means block forever
 * \param abort_flag [in] set *abort_flag to 1 if you need to abort this function
 *
 * \return #NEBULA_ER_NoERROR if exchange successfully
 * \return Error code if return value < 0
 *            - #NEBULA_ER_INVALID_ARG The ctx, request or response is null.Or parsing request return null
 *            - #NEBULA_ER_NOT_INITIALIZE The Nebula module is not initialized yet
 *            - #NEBULA_ER_CLIENT_NOT_BIND_TO_DEVICE Client is not bind to device yet
 *            - #NEBULA_ER_TIMEOUT Query or connect bridge server timeout
 *            - #NEBULA_ER_RESOURCE_ERROR Getting system resource fail
 *            - #NEBULA_ER_BRIDGE_SERVER_LOGIN_FAIL Login to bridge server fail
 *            - #NEBULA_ER_DEVICE_OFFLINE Device offline
 *            - #NEBULA_ER_DEVICE_SLEEPING Device sleeping
 *            - #NEBULA_ER_DEVICE_AWAKENING Device awakening
 *            - #NEBULA_ER_INVALID_UDID The UDID is incorrect
 *            - #NEBULA_ER_UDID_EXPIRED The UDID is expired
 *
 * \attention (1) Recommended value of timeout: 1000 millisecond ~ 30000 millisecond
 */
NEBULA_API int Nebula_Client_Send_Command(NebulaClientCtx* ctx, const char *request, NebulaJsonObject **response,  unsigned int timeout_msec, unsigned int *abort_flag);

/**
 * \brief Release response data of Nebula_Client_Send_Command
 * \details  This function is used to release json response data from Nebula_Client_Send_Command,
 *           you must call it after you got the json response data from Nebula_Client_Send_Command
 *
 * \param response [in] This json response data pointer
 *
 * \return #Nebula_ER_NoERROR if free json response data pointer successfully
 * \return Error code if return value < 0
 *            - #Nebula_ER_INVALID_ARG The response in null
 *
 */
NEBULA_API int Nebula_Client_Free_Send_Command_Response(NebulaJsonObject *response);

/**
 * \brief Wake up sleep device
 *
 * \details This function will wakeup sleeping device by sending wakeup pattern in
 *          #Nebula_Device_Get_Sleep_Packet()
 *
 * \param ctx [in] Nebula context of client, it's from Nebula_Client_New() or Nebula_Client_New_From_String()
 * \param timeout_msec [in] The timeout for this function in unit of millisecond, give 0 means block forever
 * \param abort_flag [in] set *abort_flag to 1 if you need to abort this function
 *
 * \return #NEBULA_ER_NoERROR if wakeup device successfully
 * \return Error code if return value < 0
 *            - #NEBULA_ER_INVALID_ARG The ctx is null
 *            - #NEBULA_ER_NOT_INITIALIZE The Nebula module is not initialized yet
 *            - #NEBULA_ER_CLIENT_NOT_BIND_TO_DEVICE Client is not bind to device yet
 *            - #NEBULA_ER_TIMEOUT Wakeup deivce timeout
 *            - #NEBULA_ER_DEVICE_ONLINE Device already awakened
 *            - #NEBULA_ER_DEVICE_OFFLINE Device offline
 *            - #NEBULA_ER_SERVICE_UNAVAILABLE Nebula Server not in service
 *            - #NEBULA_ER_INVALID_UDID The UDID is incorrect
 *            - #NEBULA_ER_UDID_EXPIRED The UDID is expired
 *
 */
NEBULA_API int Nebula_Client_Wakeup_Device(NebulaClientCtx* ctx, unsigned int timeout_msec, unsigned int *abort_flag);

/**
 * \brief Check device online
 *
 * \details This function will check if device login to bridge server
 *
 * \param ctx [in] Nebula context of client, it's from Nebula_Client_New() or Nebula_Client_New_From_String()
 * \param timeout_msec [in] The timeout for this function in unit of millisecond, give 0 means block forever
 * \param abort_flag [in] set *abort_flag to 1 if you need to abort this function
 *
 * \return #NEBULA_ER_NoERROR if device login to bridge server
 * \return Error code if return value < 0
 *            - #NEBULA_ER_INVALID_ARG The ctx is null
 *            - #NEBULA_ER_NOT_INITIALIZE The Nebula module is not initialized yet
 *            - #NEBULA_ER_CLIENT_NOT_BIND_TO_DEVICE Client is not bind to device yet
 *            - #NEBULA_ER_INVALID_UDID The UDID is incorrect
 *            - #NEBULA_ER_UDID_EXPIRED The UDID is expired
 *            - #NEBULA_ER_TIMEOUT Wakeup deivce timeout
 *            - #NEBULA_ER_DEVICE_OFFLINE Device offline
 *            - #NEBULA_ER_DEVICE_SLEEPING Device sleeping
 *            - #NEBULA_ER_DEVICE_AWAKENING Device awakening
 *
 */
NEBULA_API int Nebula_Client_Check_Device_Online(NebulaClientCtx* client, unsigned int timeout_msec, unsigned int *abort_flag);

/**
 * \brief Release client context
 *
 * \details This function will free client context that created by Nebula_Client_New()
 *
 * \param client_ctx [in] Nebula context of client, it's from Nebula_Client_New() or Nebula_Client_New_From_String()
 *
 * \return #NEBULA_ER_NoERROR if delete client context successfully
 * \return Error code if return value < 0
 *            - #NEBULA_ER_INVALID_ARG The ctx is null
 *            - #NEBULA_ER_NOT_INITIALIZE The Nebula module is not initialized yet
 *
 * \see Nebula_Client_New(), Nebula_Client_New_From_String()
 */
NEBULA_API int Nebula_Client_Delete(NebulaClientCtx* ctx);

#ifdef __cplusplus
}
#endif

#endif /* _NEBULACLIENT_H_ */
