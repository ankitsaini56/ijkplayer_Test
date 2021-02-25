package tv.danmaku.ijk.media.example.webrtc

import com.tutk.IOTC.NebulaAPIs
import com.tutk.IOTC.NebulaClientInfo
import org.json.JSONObject
import tv.danmaku.ijk.webrtc.NebulaInterface

class NebulaImp : NebulaInterface {
    override fun Client_New(udid: String, credential: String, ctx: LongArray): Int {
        return NebulaAPIs.Nebula_Client_New_From_String(udid, credential, ctx)
    }

    override fun Send_Command(ctx: Long, reqJson: String, response: Array<String?>, timeoutMS: Int): Int {
        return NebulaAPIs.Nebula_Client_Send_Command(
                ctx,
                reqJson,
                response,
                timeoutMS,
                null)
    }
}
