
typedef struct NebulaAPI {
    int     size;
    int     (*Client_New)(const char *udid, const char *credential, long *ctx);
    int     (*Send_Command)(long ctx, const char *reqJson, char **response, int timeoutMS);
} NebulaAPI;
