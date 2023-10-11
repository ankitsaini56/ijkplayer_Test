/*! \file NebulaNode.h
This file describes Nebula module APIs for device.

\copyright Copyright (c) 2010 by Throughtek Co., Ltd. All Rights Reserved.
*/

#ifndef _NEBULANODE_H_
#define _NEBULANODE_H_

#ifdef __cplusplus
extern "C" {
#endif

#include "NebulaDevice.h"

typedef struct NebulaNodeCtx {
    void *info; // Extra info can be attached to NebulaNodeCtx
} NebulaNodeCtx;

/**
 * \brief The prototype of node command handle callback function.
 *
 * \details This callback function is called when Nebula client send request to Nebula node.
 *          Nebula node should handle the request and send an appropriate response.
 *
 * \param node [in] Node's context generate by Nebula_Device_New()
 * \param fun [out] The json object name
 * \param args [out] The json object
 * \param response [in] The json response object
 *
 * \see Nebula_Node_New()
 */
typedef int(__stdcall *NebulaNodeCommandHandleFn)(NebulaNodeCtx *node, const char *identity, const char *func, const NebulaJsonObject *args, NebulaJsonObject **response);

/**
 * \brief Node settings change callback function.
 *
 * \details This callback function is called when settings of node changes.
 *          Node settings should be stored in non-volatile memory in order to
 *          be loaded when device restarts.
 *
 * \param device [in] node's context generate by Nebula_Node_New()
 * \param settings [in] the encrypted settings string of node
 *
 * \see Nebula_Node_New() Nebula_Node_Load_Settings()
 */
typedef int(__stdcall *NebulaNodeSettingsChangeHandleFn)(NebulaNodeCtx* node_ctx, const char* settings);

/**
 * \brief Create Nebula node
 *
 * \details Create Node context for Nebula module.
 *          Node API for Nebula module need this context to work.
 *
 * \param udid [in] node's udid for Nebula module
 * \param profile [in] node's profile for client to use.
 * \param command_handler [in] Nebula command handler callback function
 * \param node_ctx [out] node's context created
 *
 * \return #NEBULA_ER_NoERROR if node context is successfully created
 * \return Error code if return value < 0
 *            - #NEBULA_ER_INVALID_ARG udid, profile, command_handler, identity_handler is null or
 *               profile length larger than MAX_PROFILE_LENGTH
 *            - #NEBULA_ER_NOT_INITIALIZE  The Nebula module is not initialized yet
 *            - #NEBULA_ER_MEM_INSUFFICIENT Insufficient memory for allocation
 * \see Nebula_Node_Delete()
 *
 */
NEBULA_API int Nebula_Node_New(const char* udid, const char* profile, NebulaNodeCommandHandleFn command_handler,
        NebulaNodeSettingsChangeHandleFn settings_change_handler, NebulaNodeCtx** node_ctx);

/**
 *
 * \brief Nebula node load settings
 *
 * \details Restore node settings.
 *
 *
 * \param ctx[in] node's context generate by Nebula_Device_New()
 * \param settings[in] the encrypted settings string of Nebula node
 *
 * \return #NEBULA_ER_NoERROR if load settings successfully
 * \return Error code if return value < 0
 *            - #NEBULA_ER_INVALID_ARG The ctx or settings is null
 *            - #NEBULA_ER_NOT_INITIALIZE  The Nebula module is not initialized yet
 *
 * \see NebulaNodeSettingsChangeHandleFn
 *
 */
NEBULA_API int Nebula_Node_Load_Settings(NebulaNodeCtx* ctx, const char* settings);

/**
 * \brief Destroy Nebula node
 *
 * \details Free node context that created by Nebula_Node_New()
 *
 * \param node_ctx [in] Nebula context of node
 *
 * \return #NEBULA_ER_NoERROR if node context is successfully deleted
 * \return Error code if return value < 0
 *            - #NEBULA_ER_INVALID_ARG The ctx is null
 *            - #NEBULA_ER_NOT_INITIALIZE The Nebula module is not initialized yet
 *
 * \see Nebula_Node_New()
 */
NEBULA_API int Nebula_Node_Delete(NebulaNodeCtx* node_ctx);

/**
 * \brief Add node to device
 *
 * \details Add node for receiving forward commands.
 *
 * \param device_ctx[in] The device's context created by Nebula_Device_New()
 * \param node_ctx[in] The node's context created by Nebula_Node_New()
 * \param timeout_msec[in] The timeout for this function in unit of millisecond, give 0 means block forever
 * \param abort_flag [in] set *abort_flag to 1 if you need to abort this function
 *
 * \return #NEBULA_ER_NoERROR if node is successfully added to device
 * \return Error code if return value < 0
 *            - #NEBULA_ER_INVALID_ARG
 *            - #NEBULA_ER_NOT_INITIALIZE  The Nebula module is not initialized yet
 *            - #NEBULA_ER_MEM_INSUFFICIENT Insufficient memory for allocation
 *            - #NEBULA_ER_TIMEOUT Add node timeout
 *            - #NEBULA_ER_HTTP_ERROR Http error
 *            - #NEBULA_ER_DUPLICATE The udid of node already exists
 *
 * \see Nebula_Node_New(), Nebula_Device_Find_Node(), Nebula_Device_Remove_Node()
 */
NEBULA_API int Nebula_Device_Add_Node(NebulaDeviceCtx* device_ctx, NebulaNodeCtx* node_ctx, unsigned int timeout_msec, unsigned int *abort_flag);

/**
 * \brief Get node in deivce
 *
 * \details Get node's context with specified udid
 * \param device_ctx[in] The device's context created by Nebula_Device_New()
 * \param udid[in] The udid of node
 * \param node_ctx[out] The node's context with specified udid
 *
 * \return #NEBULA_ER_NoERROR if the node's context exists
 * \return Error code if return value < 0
 *            - #NEBULA_ER_INVALID_ARG
 *            - #NEBULA_ER_NOT_INITIALIZE  The Nebula module is not initialized yet
 *            - #NEBULA_ER_NO_SUCH_ENTRY  No such node's context
 *
 * \see Nebula_Node_New(), Nebula_Device_Add_Node(), Nebula_Device_Remove_Node()
 */
NEBULA_API int Nebula_Device_Find_Node(NebulaDeviceCtx* device_ctx, const char* udid, NebulaNodeCtx** node_ctx);

/**
 * \brief Remove node from device
 *
 * \details Remove node's context from device
 * \param device_ctx[in] The device's context created by Nebula_Device_New()
 * \param node_ctx[in] The node's context created by Nebula_Node_New()
 *
 * \return Error code if return value < 0
 *            - #NEBULA_ER_INVALID_ARG
 *            - #NEBULA_ER_NOT_INITIALIZE  The Nebula module is not initialized yet
 *            - #NEBULA_ER_NO_SUCH_ENTRY  No such node's context
 *
 * \see Nebula_Node_New(), Nebula_Device_Add_Node(), Nebula_Device_Find_Node()
 */
NEBULA_API int Nebula_Device_Remove_Node(NebulaDeviceCtx* device_ctx, NebulaNodeCtx* node_ctx);

/**
 * \brief Remove node from device by node udid
 *
 * \details Remove node's context from device
 * \param device_ctx[in] The device's context created by Nebula_Device_New()
 * \param udid[in] The udid of node
 *
 * \return Error code if return value < 0
 *            - #NEBULA_ER_INVALID_ARG
 *            - #NEBULA_ER_NOT_INITIALIZE  The Nebula module is not initialized yet
 *            - #NEBULA_ER_NO_SUCH_ENTRY  No such node udid is added to device
 *
 * \see Nebula_Node_New(), Nebula_Device_Add_Node()
 */
NEBULA_API int Nebula_Device_Remove_Node_By_Udid(NebulaDeviceCtx* device_ctx, const char* udid);

/**
 * \brief Nebula node push notification to server
 *
 * \details This function is used by node to push notification to server
 *          in order to notify Nebula client.
 *
 * \param ctx[in] node's context generate by Nebula_Node_New()
 * \param notification_obj[in] The json object contains a list of key value pair for push server to generate the push message.
 *                             The key and value should be a string.
 * \param timeout_msec[in] The timeout for this function in unit of millisecond, give 0 means block forever
 * \param abort_flag [in] set *abort_flag to 1 if you need to abort this function
 *
 * \return #NEBULA_ER_NoERROR if push to server successfully
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
NEBULA_API int Nebula_Node_Push_Notification(NebulaNodeCtx* ctx, NebulaJsonObject *notification_obj, unsigned int timeout_msec, unsigned int *abort_flag);

#ifdef __cplusplus
}
#endif

#endif /* _NEBULANODE_H_ */
