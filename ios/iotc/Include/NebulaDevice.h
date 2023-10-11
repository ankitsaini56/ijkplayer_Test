/*! \file NebulaDevice.h
This file describes Nebula module APIs for device.

\copyright Copyright (c) 2010 by Throughtek Co., Ltd. All Rights Reserved.
*/

#ifndef _NEBULADEVICE_H_
#define _NEBULADEVICE_H_

#ifdef __cplusplus
extern "C" {
#endif

#include "NebulaCommon.h"

#define MAX_PROFILE_LENGTH 45000
#define NEBULA_MAX_SLEEP_ALIVE_PACKET_SIZE 256
#define MAX_IP_STRING_LENGTH 46
#define MAX_LONG_SLEEP_TCP_ALIVE_SEC (3*60*60)

typedef struct NebulaDeviceCtx {
    void *info; // Extra info can be attached to NebulaDeviceCtx
} NebulaDeviceCtx;

typedef enum NebulaSocketProtocol {
    NEBULA_PROTO_TCP,
    NEBULA_PROTO_UDP,
} NebulaSocketProtocol;

typedef struct NebulaSleepConfig {
    unsigned int cb;                    // Size of NebulaSleepConfig
    unsigned char *wake_up_pattern;     // Device's wake up pattern
    unsigned int pattern_size;          // Pattern length
    NebulaSocketProtocol protocol;      // The protocol to send alive packet
    unsigned int alive_interval_sec;    // Expect send alive packet interval, max 6*60*60 second, 0 means default value
    uint8_t disable_tcp_keep_alive;     // Set 0 to receive tcp keep alive packet, 1 to disable
    unsigned int tcp_keep_alive_sec;    // Expect receive tcp keep alive packet interval, 0 means default value, max acceptable value is MAX_LONG_SLEEP_TCP_ALIVE_SEC
    uint8_t enable_tcp_reconnect;       // Set 0 to disabl, 1 to enable tcp reconnect, which allows device stay in sleep mode after tcp reconnected
} NebulaSleepConfig;

typedef struct NebulaWakeUpData {
    char ip[MAX_IP_STRING_LENGTH + 1];                              // IP of receive alive packet server
    unsigned short port;                                            // Port of receive alive packet server
    unsigned int packet_size;                                       // Alive packet length
    char sleep_alive_packet[NEBULA_MAX_SLEEP_ALIVE_PACKET_SIZE];    // Alive packet data buffer
    unsigned int login_interval_sec;                                // Recommended send alive packet interval
} NebulaWakeUpData;

typedef enum {
	NEBULA_DEVLOGIN_ST_CONNECTED       = 1 << 0, // get the login response from Nebula server
	NEBULA_DEVLOGIN_ST_DISCONNECTED    = 1 << 1, // disconnected from Nebula server, please check the network status
	NEBULA_DEVLOGIN_ST_RETRYLOGIN      = 1 << 2, // retry login to Nebula server
} NebulaDeviceLoginState;

/**
 * \brief The prototype of identity handle callback function.
 *
 * \details This callback function is called when Nebula module need to decrypt the data from Nebula client.
 *          Nebula device is required to provide the correspond psk when this callback is called.
 *
 * \param device [in] device's context generate by Nebula_Device_New()
 * \param identity [in] the identity from Nebula client
 * \param psk [out] the pre share key keep in device that is correspond with the specific identity
 * \param psk_size [in] size of psk.
 *
 * \see Nebula_Device_New()
 */
typedef void(__stdcall *NebulaIdentityHandleFn)(NebulaDeviceCtx *device, const char *identity, char *psk, unsigned int psk_size);


/**
 * \brief The prototype of command handle callback function.
 *
 * \details This callback function is called when Nebula client send request to Nebula device.
 *          Nebula device should handle the request and send an appropriate response.
 *          The profile or document might describe getNightMode as
 *          {
 *            "func":"getNightMode",
 *            "return": {
 *              "value":"Int"
 *            }
 *          }
 *          When the value of night mode is 10, please make a JSON response as { "value": 10 }
 *          There is no need to add key "content" here
 *
 * \param device [in] Device's context generate by Nebula_Device_New()
 * \param identity [in] The identity from Nebula client
 * \param fun [out] The json object name
 * \param args [out] The json object
 * \param response [in] The json response object
 *
 * \see Nebula_Device_New()
 */
typedef int(__stdcall *NebulaCommandHandleFn)(NebulaDeviceCtx *device, const char *identity, const char *func, const NebulaJsonObject *args, NebulaJsonObject **response);


/**
 * \brief The prototype of deivce setting change handle callback function.
 *
 * \details This callback function is called when settings of Nebula device changed.
 *          Nebula device should have a safekeeping of settings and should use
 *          Nebula_Device_Load_Settings() to load the settings when Nebula device restart next time.
 *
 * \param device [in] device's context generate by Nebula_Device_New()
 * \param settings [in] the encrypted settings string of Nebula device
 *
 * \see Nebula_Device_New() Nebula_Device_Load_Settings()
 */
typedef int(__stdcall *NebulaSettingsChangeHandleFn)(NebulaDeviceCtx* device, const char* settings);

/**
 * \brief The prototype of deivce login state callback function.
 *
 * \details This callback function is called when device login state changed.
 *
 * \param device [in] device's context generate by Nebula_Device_New()
 * \param state [in] the login state of Nebula device
 *
 * \see Nebula_Device_Login()
 */
typedef int(__stdcall *NebulaDeviceLoginStateFn)(NebulaDeviceCtx* device, NebulaDeviceLoginState state);

/**
 * \brief Generat Device context for Nebula module
 *
 * \details This function will generate Device context for Nebula module.
 *          Device API for Nebula module need this context to work.
 *
 * \param udid [in] device's udid for Nebula module
 * \param secret_id [in] device's secret_id for Nebula module
 * \param profile [in] device's profile for client to use.
 * \param command_handler [in] Nebula command handler callback function
 * \param identity_handler [in] identity handler callback function
 * \param settings_change_handler [in] setting change handler callback function
 * \param ctx[out]  device's context generate by Nebula_Device_New;
 *
 * \return #NEBULA_ER_NoERROR if generate device context successfully
 * \return Error code if return value < 0
 *            - #NEBULA_ER_INVALID_ARG The udid, secret_id, profile, command_handler, identity_handler, settings_change_handler or ctx is null or
 *               profile length larger than MAX_PROFILE_LENGTH
 *            - #NEBULA_ER_NOT_INITIALIZE  The Nebula module is not initialized yet
 *            - #NEBULA_ER_MEM_INSUFFICIENT Insufficient memory for allocation
 *            - #NEBULA_ER_FAIL_CREATE_THREAD Fails to create threads
 * \see Nebula_Device_Delete()
 *
 */
NEBULA_API int Nebula_Device_New(const char* udid, const char* secret_id, const char* profile,
								 NebulaCommandHandleFn command_handler, NebulaIdentityHandleFn identity_handler,
                                 NebulaSettingsChangeHandleFn settings_change_handler, NebulaDeviceCtx** ctx);

/**
 * \brief Device login to Bridge server.
 *
 * \details This function is used by devices to login to bridge server.
 *          Device can receive data from client that is binded by nebula module after logining
 *          to Bridge server. Device need to call this api before binding to client.
 *
 *
 * \param ctx[in] device's context generate by Nebula_Device_New;
 *
 * \return #NEBULA_ER_NoERROR if login to Nebula server successfully
 * \return Error code if return value < 0
 *            - #NEBULA_ER_INVALID_ARG The ctx is null
 *            - #NEBULA_ER_NOT_INITIALIZE  The Nebula module is not initialized yet
 *            - #NEBULA_ER_RESOURCE_ERROR Getting system resource fail
 *            - #NEBULA_ER_BRIDGE_SERVER_LOGIN_FAIL Login to bridge server fail
 *            - #NEBULA_ER_TIMEOUT Query bridge server timeout
 *            - #NEBULA_ER_HTTP_ERROR Query bridge server response error
 *            - #NEBULA_ER_SERVICE_UNAVAILABLE Nebula Server not in service
 *            - #NEBULA_ER_INVALID_UDID The UDID is incorrect
 *            - #NEBULA_ER_UDID_EXPIRED The UDID is expired
 *
 * \see Nebula_Device_Bind()
 */
NEBULA_API int Nebula_Device_Login(NebulaDeviceCtx* ctx, NebulaDeviceLoginStateFn login_state_handler);


/**
 * \brief Device bind to a client
 *
 * \details This function is used by devices to binding client.
 *          Device bind to client with identity, pin code, avtoken
 *          and psk. This api can only being called once.
 *          Device need call Nebula_Device_Login before binding to client.
 *
 * \param ctx[in] device's context generate by Nebula_Device_New()
 * \param pin_code[in] device's pin code which for authenticating with client.
 * \param psk[in] device's psk to encode/decode data in Nebula module
 * \param timeout_msec[in] The timeout for this function in unit of millisecond, give 0 means block forever
 * \param abort_flag [in] set *abort_flag to 1 if you need to abort this function
 *
 * \return #NEBULA_ER_NoERROR if bind to a client successfully
 * \return Error code if return value < 0
 *            - #NEBULA_ER_INVALID_ARG The ctx, pin_code, av_token or psk is null
 *            - #NEBULA_ER_NOT_INITIALIZE  The Nebula module is not initialized yet
 *            - #NEBULA_ER_BRIDGE_SERVER_NOT_LOGIN Device not login to bridge server yet
 *            - #NEBULA_ER_TIMEOUT Query bind server timeout
 *            - #NEBULA_ER_HTTP_ERROR Query or login bind Server response error
 *
 * \see Nebula_Client_Bind()
 *
 * \attention (1) Recommended value of timeout: 1000 millisecond ~ 30000 millisecond
 *            (2) Once the device bind to a sepecific client successfully, there is no need to do binding again
 *                when device restart.
 */
NEBULA_API int Nebula_Device_Bind(NebulaDeviceCtx* ctx, const char *pin_code, const char *psk, unsigned int timeout_msec, unsigned int *abort_flag);

/**
 * \brief Device generate bind message for client
 *
 * \details This function is used by devices to generate bind message.
 *          When Device get Nebula bind request from local client (BLE or AP mode LAN),
 *          this function can provide bind message to reaopnse.
 *
 * \param udid[in] device's udid for Nebula module
 * \param identity[in] an owner of the device for the Nebula module
 * \param psk[in] device's psk to encode/decode data in Nebula module
 * \param secret_id[in] device's secret_id for Nebula module
 * \param credential[out] Nebula bind string for client
 *
 * \return #NEBULA_ER_NoERROR if generate message success
 * \return Error code if return value < 0
 *            - #NEBULA_ER_INVALID_ARG The udid, psk , secret_id, identity is null or length invalid
 *            - #NEBULA_ER_MEM_INSUFFICIENT Insufficient memory for allocation
 *
 * \see Nebula_Client_New_From_String()
 *
 * \attention bind_message_string shall be free after use it
 *
 */
NEBULA_API int Nebula_Device_New_Credential(const char *udid, const char *identity, const char *psk, const char *secret_id, char **credential);

/**
 *
 * \brief Device generate bind message for client
 *
 * \details This function is used by devices to generate bind message.
 *          When Device get Nebula bind request from local client (BLE or AP mode LAN),
 *          this function can provide bind message to reaopnse.
 *
 * \param udid[in] device's udid for Nebula module
 * \param psk[in] device's psk to encode/decode data in Nebula module
 * \param secret_id[in] device's secret_id for Nebula module
 * \param bind_message_string[out] Nebula bind string for client
 *
 * \return #NEBULA_ER_NoERROR if generate message success
 * \return Error code if return value < 0
 *            - #NEBULA_ER_INVALID_ARG The udid, psk , secret_id is null or length invalid
 *            - #NEBULA_ER_MEM_INSUFFICIENT Insufficient memory for allocation
 *
 * \see Nebula_Client_New_From_String()
 *
 * \attention bind_message_string shall be free after use it, the Nebula_Device_New_Local_Bind_Message API had been deprecated, please use Nebula_Device_New_Credential API.
 *
 */
NEBULA_API_DEPRECATED int Nebula_Device_New_Local_Bind_Message(const char *udid, const char *psk, const char *secret_id, char **bind_message_string);

/**
 *
 * \brief Nebula device load settings
 *
 * \details This function is used by device to load settings.
 *
 *
 * \param ctx[in] device's context generate by Nebula_Device_New()
 * \param settings[in] the encrypted settings string of Nebula device
 *
 * \return #NEBULA_ER_NoERROR if load settings successfully
 * \return Error code if return value < 0
 *            - #NEBULA_ER_INVALID_ARG The ctx or settings is null
 *            - #NEBULA_ER_NOT_INITIALIZE  The Nebula module is not initialized yet
 *
 * \see NebulaSettingsChangeHandleFn
 *
 */
NEBULA_API int Nebula_Device_Load_Settings(NebulaDeviceCtx* ctx, const char* settings);


/**
 * \brief Nebula device push a notification to server
 *
 * \details This function is used by device to push a notification to server
 *          when some event happened and device want to notify Nebula clients
 *          with event messages.
 *
 * \param ctx[in] device's context generate by Nebula_Device_New()
 * \param notification_obj[in] The json object contains a list of key value pair for push server to generate the push message.
 *                             The key and value should be a string.
 * \param timeout_msec[in] The timeout for this function in unit of millisecond, give 0 means block forever
 * \param abort_flag [in] set *abort_flag to 1 if you need to abort this function
 *
 * \return #NEBULA_ER_NoERROR if bind to a client successfully
 * \return Error code if return value < 0
 *            - #NEBULA_ER_INVALID_ARG The ctx or notification_obj is null
 *            - #NEBULA_ER_NOT_INITIALIZE  The Nebula module is not initialized yet
 *            - #NEBULA_ER_BRIDGE_SERVER_NOT_LOGIN Device not login to bridge server yet
 *            - #NEBULA_ER_TIMEOUT Push notification to server timeout
 *            - #NEBULA_ER_HTTP_ERROR Http error occurred when push notification to server
 *
 *
 * \attention (1) Recommended value of timeout: 1000 millisecond ~ 30000 millisecond
 */
NEBULA_API int Nebula_Device_Push_Notification(NebulaDeviceCtx* ctx, NebulaJsonObject *notification_obj,
                                               unsigned int timeout_msec, unsigned int *abort_flag);


/**
 * \brief Release device context
 *
 * \details This function will free device context that created by Nebula_Device_New()
 *
 * \param ctx[in] device's context generate by Nebula_Device_New;
 *
 * \return #NEBULA_ER_NoERROR if delete device context successfully
 * \return Error code if return value < 0
 *            - #NEBULA_ER_INVALID_ARG  The input ctx is invalid.
 *            - #NEBULA_ER_NOT_INITIALIZE  The Nebula module is not initialized yet
 *
 * \see Nebula_Device_New()
 *
 */
NEBULA_API int Nebula_Device_Delete(NebulaDeviceCtx* ctx);


/**
 * \brief Get packet to keep alive when device sleeping
 * \details  This function is used to get keep alive packet information when device sleeping
 *
 * \param ctx [in] device's context generate by Nebula_Device_New
 * \param pattern [in] The wakeup pattern of device, see #Nebula_Client_Wakeup_Device()
 * \param pattern_size [in] The size fo wakeup pattern
 * \param protocol [in] The protocol to send sleep packet
 * \param data [out] The keep alive packet information, see #NebulaWakeUpData
 * \param timeout_msec [in] The timeout for this function in unit of millisecond, give 0 means block forever
 *
 * \return #NEBULA_ER_NoERROR if get the sleep packet successfully
 * \return Error code if return value < 0
 *            - #NEBULA_ER_INVALID_ARG The ctx is null or protocol invalid
 *            - #NEBULA_ER_NOT_INITIALIZE The Nebula module is not initialized yet
 *            - #NEBULA_ER_TIMEOUT Get sleep packet timeout
 *
 */
NEBULA_API_DEPRECATED int Nebula_Device_Get_Sleep_Packet(NebulaDeviceCtx* ctx, const unsigned char* pattern,
        unsigned int pattern_size, NebulaSocketProtocol protocol, NebulaWakeUpData** data,
        unsigned int* data_count, unsigned int timeout_ms);

/**
 * \brief Get packet to keep alive when device sleeping
 * \details  This function is used to get keep alive packet information when device sleeping
 *
 * \param ctx [in] device's context generate by Nebula_Device_New
 * \param config [in] device's sleep information, see #NebulaSleepConfig
 * \param data [out] The keep alive packet information, see #NebulaWakeUpData
 * \param data_count [out] Information count of data
 * \param timeout_msec [in] The timeout for this function in unit of millisecond, give 0 means block forever
 *
 * \return #NEBULA_ER_NoERROR if get the sleep packet successfully
 * \return Error code if return value < 0
 *            - #NEBULA_ER_INVALID_ARG The ctx is null or protocol invalid
 *            - #NEBULA_ER_NOT_INITIALIZE The Nebula module is not initialized yet
 *            - #NEBULA_ER_TIMEOUT Get sleep packet timeout
 *
 */
NEBULA_API int Nebula_Device_Get_Sleep_PacketEx(NebulaDeviceCtx* ctx, NebulaSleepConfig *config,
        NebulaWakeUpData** data, unsigned int* data_count, unsigned int timeout_ms);

/**
 * \brief Release wake up data from Nebula_Device_Get_Sleep_Packet
 *
 * \details  This function is used to release wake up data from Nebula_Device_Get_Sleep_Packet,
 *           you must call it after you got the wake up data from Nebula_Device_Get_Sleep_Packet
 *
 * \param data [in] The wake up data pointer
 *
 * \return #Nebula_ER_NoERROR if free wake up data successfully
 * \return Error code if return value < 0
 *            - #Nebula_ER_INVALID_ARG The data is null pointer
 */
NEBULA_API int Nebula_Device_Free_Sleep_Packet(NebulaWakeUpData* data);


#ifdef __cplusplus
}
#endif

#endif /* _NEBULADEVICE_H_ */
