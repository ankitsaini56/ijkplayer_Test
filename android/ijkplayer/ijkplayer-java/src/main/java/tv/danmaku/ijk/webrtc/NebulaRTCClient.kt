package tv.danmaku.ijk.media.example.webrtc

import android.util.Log
import tv.danmaku.ijk.webrtc.AppRTCClient.*
import org.json.JSONArray
import org.json.JSONObject
import org.webrtc.IceCandidate
import org.webrtc.PeerConnection
import org.webrtc.PeerConnection.IceServer
import org.webrtc.SessionDescription
import tv.danmaku.ijk.webrtc.AppRTCClient
import tv.danmaku.ijk.webrtc.NebulaInterface
import java.lang.Thread.sleep
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class NebulaRTCClient : AppRTCClient {
    private val TAG = "NebulaRTCClient"
    private val TIMEOUT_IN_MS = 5000
    private var mConnectionParameters: RoomConnectionParameters? = null
    private var mEvents: SignalingEvents? = null
    private var mContext: Long? = null
    private var mOfferSdp: SessionDescription? = null
    private val mIceServers: ArrayList<IceServer> = ArrayList()
    private var mCandidate: IceCandidate? = null
    private val mExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var mAnswerSdp: SessionDescription? = null
    private var mOfferCandidates: ArrayList<IceCandidate> = ArrayList()
    private var mAnswerCandidates: ArrayList<IceCandidate> = ArrayList()
    private var mNebulaAPIs: NebulaInterface? = null
    private var mUsingAmazonKVS = false
    private var mRtcId = 0
    private var mIceGatheringState: PeerConnection.IceGatheringState = PeerConnection.IceGatheringState.NEW;
    private val DEBUG = false


    constructor(events: SignalingEvents, nebulaAPIs: NebulaInterface) {
        mEvents = events
        mNebulaAPIs = nebulaAPIs
    }

    private fun parseAnswerCandidate(sdp: String) {
        var lines = sdp.split("\r\n")
        var candidateList: ArrayList<String> = ArrayList()
        var sdpmid = ""
        for(line in lines) {
            if(line.startsWith("a=mid:")) {
                var sdpmid = line.split(":")[1]
                for(candidate in candidateList) {
                    //mOfferCandidates.add(IceCandidate(sdpmid, 0, candidate))
                    mEvents?.onRemoteIceCandidate(IceCandidate(sdpmid, 0, candidate))
                }
                candidateList.clear()
            }
            else if(line.startsWith("a=candidate")) {
                var candidates = line.split("=")
                candidateList.add(candidates[1])
            }
        }
    }

    override fun connectToRoom(connectionParameters: RoomConnectionParameters) {
        mConnectionParameters = connectionParameters
        mExecutor.execute {
            startClient(connectionParameters.nebulaParameters.udid, connectionParameters.nebulaParameters.credential)
        }
    }

    val ICE_SERVERS = """{
      "lifetimeDuration": "86400s",
      "iceServers": [
        {
          "urls": [
            "stun:64.233.188.127:19302",
            "stun:[2404:6800:4008:c06::7f]:19302"
          ]
        },
        {
          "urls": [
            "turn:172.253.117.127:19305?transport=udp",
            "turn:[2607:f8b0:400e:c0a::7f]:19305?transport=udp",
            "turn:172.253.117.127:19305?transport=tcp",
            "turn:[2607:f8b0:400e:c0a::7f]:19305?transport=tcp"
          ],
          "username": "CJmukfQFEgaF6vEDwuIYzc/s6OMTIICjBQ",
          "credential": "XE+YlZDCoTHYxinn+yZhntLs3SM=",
          "maxRateKbps": "8000"
        }
      ],
      "blockStatus": "NOT_BLOCKED",
      "iceTransportPolicy": "all"
    }"""

    fun buildIceServer() {
        val json = JSONObject(ICE_SERVERS)
        val iceServers: JSONArray = json.getJSONArray("iceServers")
        for (i in 0 until iceServers.length()) {
            val server = iceServers.getJSONObject(i)
            val turnUrls = server.getJSONArray("urls")
            val username = if (server.has("username")) server.getString("username") else ""
            val credential = if (server.has("credential")) server.getString("credential") else ""
            var urls: MutableList<String> = mutableListOf()
            for (j in 0 until turnUrls.length()) {
                urls.add(turnUrls.getString(j))
                //val turnUrl = turnUrls.getString(j)
            }
            val turnServer = IceServer.builder(urls)
                    .setUsername(username)
                    .setPassword(credential)
                    .createIceServer()
            mIceServers.add(turnServer)
        }
    }

    private fun buildIceServer(json: JSONObject) {
        /**
         *  {
                "RTC_ID": <RTC_ID>,
                "username": "<USERNAME>",
                "password": "<PASSWORD>",
                "ttl": <TIME_TO_LIVE_SEC>,
                "uris": [
                    "<TURN_URI>"
                ]
            }
         */
        Log.e(TAG, "json=$json")
        var urls: MutableList<String> = mutableListOf()
        val username = json.optString("username")
        val password = json.optString("password")
        val uris = json.getJSONArray("uris")
        Log.e(TAG, "username=$username, password=$password")
        Log.e(TAG, "uris=$uris")
        Log.e(TAG, "length=${uris.length()}")

        for(i in 0 until uris.length()) {
            Log.e(TAG, "url=${uris.optString(i)}")
            urls.add(uris.optString(i))
        }
        val turnServer = IceServer.builder(urls)
                .setUsername(username)
                .setPassword(password)
                .createIceServer()
        mIceServers.add(turnServer)
    }

    private fun createOffer() {
        val parameters = SignalingParameters( // Ice servers are not needed for direct connections.
                mIceServers,
                true,  // Server side acts as the initiator on direct connections.
                null,  // clientId
                null,  // wssUrl
                null,  // wwsPostUrl
                null,  // offerSdp
                null // iceCandidates
        )
        mEvents?.onConnectedToRoom(parameters)
    }

    private fun logLongString(str: String) {
        if (str.length > 4000) {
            Log.v(TAG, "sb.length = " + str.length)
            val chunkCount: Int = str.length / 4000 // integer division
            for (i in 0..chunkCount) {
                val max = 4000 * (i + 1)
                if (max >= str.length) {
                    Log.v(TAG, "chunk " + i + " of " + chunkCount + ":" + str.substring(4000 * i))
                } else {
                    Log.v(TAG, "chunk " + i + " of " + chunkCount + ":" + str.substring(4000 * i, max))
                }
            }
        } else {
            Log.v(TAG, str)
        }
    }

    private fun genStartWebRTCJson() :JSONObject{
        val json = JSONObject()
        val args = JSONObject()
        json.put("func", "startWebRTC")
        args.put("am_token", mConnectionParameters?.nebulaParameters?.amToken)
        args.put("realm", mConnectionParameters?.nebulaParameters?.realm)
        //args.put("state", mConnectionParameters?.nebulaParameters?.state)
        if(mConnectionParameters?.nebulaParameters?.state != null) {
            args.put("info", mConnectionParameters?.nebulaParameters?.state)
        }
        json.put("args", args)
        return json
    }

    private fun genStopWebRTCJson() :JSONObject{
        val json = JSONObject()
        val args = JSONObject()
        json.put("func", "stopWebRTC")
        args.put("RTC_ID", mRtcId)
        json.put("args", args)
        return json
    }

    fun startClient(udid: String, credential: String) {
        val ctx = LongArray(1)
        val ret = mNebulaAPIs?.Client_New(udid, credential, ctx)
        if(ret != 0) {
            Log.e(TAG, "client new failed")
            return
        }
        mContext = ctx[0]
        //amazon kvs device
        if(mConnectionParameters?.nebulaParameters?.amToken != null) {
            mUsingAmazonKVS = true
            var startResponse: String? = clientSend(genStartWebRTCJson().toString()) ?: return
            var startResJson = JSONObject(startResponse)
            if(startResJson.optInt("statusCode") != 200) {
                Log.e(TAG, "startWebRTC failed")
                return
            }else {
                val content = startResJson.optJSONObject("content")
                mRtcId = content.optInt("RTC_ID")
                if(DEBUG) {
                    Log.e(TAG, "startWebRTC success rtcid: $mRtcId")
                }
                buildIceServer(content)
            }
        }
        createOffer()
        while(mOfferSdp == null) {
            sleep(200)
        }
        sleep(2000)
        /*while(mIceGatheringState != PeerConnection.IceGatheringState.COMPLETE) {
            sleep(1000)
        }*/
        val json = buildOfferSDPResponseJson(mOfferSdp)
        val response = clientSend(json.toString())
        if(DEBUG) {
            Log.e(TAG, "response = $response")
        }
        if(response != null) {
            val resJson = JSONObject(response)
            if(resJson.optInt("statusCode") != 200) {
                clientSend(genStopWebRTCJson().toString())
                return
            }
            var content = resJson.optJSONObject("content")
            if(content != null) {
                val type = content.optString("type")
                val sdp = content.optString("sdp").trim()
                if(DEBUG) {
                    logLongString("answer = $sdp")
                }
                val s = SessionDescription(
                        SessionDescription.Type.fromCanonicalForm(type), "$sdp\r\n")
                mEvents?.onRemoteDescription(s)
                parseAnswerCandidate(s.description)
            }
        }
    }

    override fun sendOfferSdp(sdp: SessionDescription?) {
        mOfferSdp = sdp
    }

    private fun buildOfferSDPResponseJson(sdp: SessionDescription?): JSONObject {
        val json = JSONObject()
        val offerObj = JSONObject()
        json.put("func", "exchangeSdp")
        var offer = sdp?.description
        if(offer == null) {
            offerObj.put("type", "offer")
            offerObj.put("sdp", offer)
            if(mRtcId != 0) {
                offerObj.put("RTC_ID", mRtcId)
            }
            json.put("args", offerObj)
            return json
        }
        val removeStr = "a=ice-options:trickle renomination"
        var preIdx = offer.indexOf(removeStr)
        Log.d(TAG, "preIdx = $preIdx")
        while(preIdx != -1) {
            offer = offer?.removeRange(preIdx, preIdx+removeStr.length+2)
            if(offer != null)
                preIdx = offer.indexOf(removeStr)
        }
        var candidates = ""
        if(!mAnswerCandidates.isEmpty()) {
            for(candi in mAnswerCandidates) {
                candidates += "a=" + candi.sdp + "\r\n"
            }
        }
        if(offer != null) {
            var preidx = offer.indexOf("m=")
            var postidx = offer.indexOf("\r\n", preidx) + 2
            offer = offer.substring(0, postidx) + candidates + offer.substring(postidx)
            preidx = offer.indexOf("m=", postidx)
            postidx = offer.indexOf("\r\n", preidx) + 2
            offer = offer.substring(0, postidx) + candidates + offer.substring(postidx)
            if(DEBUG) {
                Log.e(TAG, "offer = $offer")
            }
            offerObj.put("type", "offer")
            offerObj.put("sdp", offer)
            if(mRtcId != 0) {
                offerObj.put("RTC_ID", mRtcId)
            }
            json.put("args", offerObj)
        }
        return json
    }

    private fun clientSend(reqJson: String): String? {
        val response = arrayOfNulls<String>(1)
        if(DEBUG) {
            logLongString("send $reqJson")
        }
        mContext?.let {
            val ret = mNebulaAPIs?.Send_Command(it, reqJson, response, TIMEOUT_IN_MS)
            if(DEBUG) {
                Log.d(TAG, "send ret = $ret")
            }
            return response[0]
        }

        return null
    }

    override fun sendAnswerSdp(sdp: SessionDescription?) {
        mAnswerSdp = sdp
        if(DEBUG) {
            Log.e(TAG, "answer sdp: ${mAnswerSdp?.description}")
        }
    }

    override fun sendIceGatheringState(newState: PeerConnection.IceGatheringState?) {
        newState?.let {
            Log.e(TAG, "ice gathering state change to $newState")
            mIceGatheringState = newState
        }
    }

    override fun sendLocalIceCandidate(candidate: IceCandidate?) {
        if (candidate != null) {
            mAnswerCandidates.add(candidate)
        }
        mExecutor.execute {
            var i = 0
            while(mCandidate != null && i < 15) {
                sleep(200)
                i++
            }
            mCandidate = candidate
        }
    }

    override fun sendLocalIceCandidateRemovals(candidates: Array<IceCandidate?>?) {
        Log.d(TAG, "sendLocalIceCandidateRemovals")
    }

    override fun disconnectFromRoom() {
        Log.e(TAG, "disconnectFromRoom")
        if(mUsingAmazonKVS) {
            var stopResponse: String? = clientSend(genStopWebRTCJson().toString())
            if(stopResponse != null) {
                var stopResJson = JSONObject(stopResponse)
                if(stopResJson.optInt("statusCode") != 200) {
                    Log.e(TAG, "stopWebRTC failed")
                }
            }
        }
    }
}