
typedef struct NebulaAPI {
    long    ctx;
    int     size;
    int     (*Send_Command)(long ctx, const char *reqJson, char **response, int timeoutMS);
} NebulaAPI;
