package tv.danmaku.ijk.media.example.webrtc

import com.tutk.IOTC.NebulaAPIs
import com.tutk.IOTC.NebulaClientInfo
import org.json.JSONObject
import tv.danmaku.ijk.webrtc.NebulaInterface

class NebulaImp(val mCtx: Long) : NebulaInterface {
    override fun Send_Command(reqJson: String, response: Array<String?>, timeoutMS: Int): Int {
        return NebulaAPIs.Nebula_Client_Send_Command(
            mCtx,
            reqJson,
            response,
            timeoutMS,
            null)
    }
}
