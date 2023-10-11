/*! \file NebulaCommon.h
This file describes common APIs of the Nebula module in IOTC platform.

\copyright Copyright (c) 2010 by Throughtek Co., Ltd. All Rights Reserved.
 */

#ifndef __NEBULACOMMON_H__
#define __NEBULACOMMON_H__

#include "NebulaError.h"
#include "NebulaJsonAPIs.h"
#include "TUTKGlobalAPIs.h"

#ifdef _WIN32
    #ifdef IOTC_STATIC_LIB
        #define NEBULA_API
    #elif defined P2PAPI_EXPORTS
        #define NEBULA_API __declspec(dllexport)
    #else
        #define NEBULA_API __declspec(dllimport)
    #endif
#else
    #define NEBULA_API
    #define __stdcall
#endif

#if defined(__GNUC__) || defined(__clang__)
    #define NEBULA_API_DEPRECATED __attribute__((deprecated))
#elif defined(_MSC_VER)
    #ifdef IOTC_STATIC_LIB
        #define NEBULA_API_DEPRECATED __declspec(deprecated)
    #elif defined P2PAPI_EXPORTS
        #define NEBULA_API_DEPRECATED __declspec(deprecated, dllexport)
    #else
        #define NEBULA_API_DEPRECATED __declspec(deprecated, dllimport)
    #endif
#else
    #define NEBULA_API_DEPRECATED
#endif

#ifdef __cplusplus
extern "C" {
#endif

/* ============================================================================
 * Generic Macro Definition
 * ============================================================================
 */

#define MAX_PUBLIC_UDID_LENGTH 40
#define MAX_PIN_CODE_LENGTH 9
#define MAX_UDID_LENGTH 106
#define MAX_REALM_LENGTH 128
#define MAX_NEBULA_PSK_LENGTH 1024
#define MAX_NEBULA_IDENTITY_LENGTH 119
#define MAX_NEBULA_SECRETID_LENGTH 128


/* ============================================================================
 * Function Declaration
 * ============================================================================
 */

/**
 * \brief Get the version of Nebula module
 *
 * \details This function returns the version of Nebula module.
 *
 */
NEBULA_API const char* Nebula_Get_Version_String(void);

/**
 * \brief Initialize Nebula module
 *
 * \details This function is used by devices to initialize Nebula module
 *			and shall be called before any Nebula module related function
 *			is invoked.
 *
 * \return #NEBULA_ER_NoERROR if initializing successfully
 * \return Error code if return value < 0
 *          - #NEBULA_ER_RESOURCE_ERROR Getting system resource fail
 *
 * \see Nebula_DeInitialize()
 *
 * \attention   This function must be the first function to call, and
 *              this call MUST have a corresponding call to Nebula_DeInitialize
 *              when the operation is complete.
 */
NEBULA_API int Nebula_Initialize();


/**
 * \brief Deinitialize Nebula module
 *
 * \details This function will deinitialize Nebula module and
 *          must be the last function to call for Nebula module
 *          This would release all the resource allocated in this module.
 *
 * \return #NEBULA_ER_NoERROR if deinitialize successfully
 * \return Error code if return value < 0
 *			- #NEBULA_ER_NOT_INITIALIZE  The Nebula module is not initialized yet
 *
 * \see Nebula_Initialize()
 *
 */
NEBULA_API int Nebula_DeInitialize();


/**
 * \brief Set Attribute of log file
 *
 * \param logAttr [in] See #LogAttr
 *
 * \return #NEBULA_ER_NoERROR on success.
 * \return The value < 0
 *			- #NEBULA_ER_INVALID_ARG   Invalid input argument.
 */
NEBULA_API int Nebula_Set_Log_Attr(LogAttr logAttr);


#ifdef __cplusplus
}
#endif

#endif /* _NEBULACOMMON_ */
