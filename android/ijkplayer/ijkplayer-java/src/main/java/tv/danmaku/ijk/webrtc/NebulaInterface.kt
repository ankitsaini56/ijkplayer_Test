package tv.danmaku.ijk.webrtc

import org.json.JSONObject

interface NebulaInterface {
    fun Client_New(udid: String, credential: String, ctx: LongArray): Int
    fun Send_Command(ctx: Long, reqJson: String, response: Array<String?>, timeoutMS: Int): Int
}

data class NebulaParameter(val udid: String, val credential: String, val amToken: String,
                           val realm: String, val state: String?, val info: JSONObject?) {
    constructor(udid: String, credential: String, amToken: String, realm: String, state: String?) :
            this(udid, credential, amToken, realm, state, null)
}
