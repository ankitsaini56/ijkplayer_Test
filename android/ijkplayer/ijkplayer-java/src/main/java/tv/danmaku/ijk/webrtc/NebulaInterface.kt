package tv.danmaku.ijk.webrtc

import org.json.JSONObject

interface NebulaInterface {
    fun Send_Command(reqJson: String, response: Array<String?>, timeoutMS: Int): Int
}

data class NebulaParameter(val dmToken: String, val realm: String, val channelId: Int?, val streamType: String?,
                           val playbackStartTime: Int?, val playbackFileName: String?, val isQuickConnect: Boolean?, val info: JSONObject?) {
    constructor(dmToken: String, realm: String) :
            this(dmToken, realm, null, null, null, null, null)

    constructor(dmToken: String, realm: String, streamType: String?, playbackStartTime: Int?, playbackFileName: String?, isQuickConnect: Boolean?) :
            this(dmToken, realm, null, streamType, playbackStartTime, playbackFileName, isQuickConnect, null)

    constructor(dmToken: String, realm: String, channelId: Int?, streamType: String?, playbackStartTime: Int?, playbackFileName: String?, isQuickConnect: Boolean?) :
            this(dmToken, realm, channelId, streamType, playbackStartTime, playbackFileName, isQuickConnect, null)
}
