
package tv.danmaku.ijk.media.player.misc;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class ObjectTrackingInfo {
    public Rect rect;

    public static List<ObjectTrackingInfo> parse(String jsonStr) {
        ArrayList info = new ArrayList();

        if (jsonStr == null) {
            return info;
        }

        try {
            JSONObject json = new JSONObject(jsonStr);
            JSONObject jsonAGTX = json.optJSONObject("AGTX");
            if (jsonAGTX == null) {
                jsonAGTX = json.optJSONObject("agtx");
                if (jsonAGTX == null) {
                    return info;
                }
            }
            JSONObject jsonIVA = jsonAGTX.optJSONObject("iva");
            if (jsonIVA == null) {
                return info;
            }

            JSONArray jsonOD = jsonIVA.optJSONArray("od");
            if (jsonOD != null && jsonOD.length() > 0) {
                JSONObject jsonItem = jsonOD.optJSONObject(0);
                if (jsonItem == null) {
                    return info;
                }
                info.add(new ObjectTrackingInfo(jsonItem));
            } else {
                JSONObject jsonAROI = jsonIVA.optJSONObject("aroi");
                if (jsonAROI == null) {
                    return info;
                }
                info.add(new ObjectTrackingInfo(jsonAROI));
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }

        return info;
    }

    ObjectTrackingInfo(JSONObject jsonRoot) {
        if (!parseOD(jsonRoot)) {
            parseROI(jsonRoot);
        }
    }

    private boolean parseOD(JSONObject jsonRoot) {
        try {
            JSONObject json = jsonRoot.optJSONObject("obj");
            if (json == null) {
                return false;
            }
            JSONArray jsonRect = json.getJSONArray("rect");
            if (jsonRect.length() >= 4) {
                rect = new Rect();
                rect.x = jsonRect.getInt(0);
                rect.y = jsonRect.getInt(1);
                rect.width = jsonRect.getInt(2) - rect.x;
                rect.height = jsonRect.getInt(3) - rect.y;
                return true;
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }

        return false;
    }

    private boolean parseROI(JSONObject jsonRoot) {
        try {
            JSONObject json = jsonRoot.optJSONObject("roi");
            if (json == null) {
                return false;
            }
            JSONArray jsonRect = json.getJSONArray("rect");
            if (jsonRect.length() >= 4) {
                rect = new Rect();
                rect.x = jsonRect.getInt(0);
                rect.y = jsonRect.getInt(1);
                rect.width = jsonRect.getInt(2) - rect.x;
                rect.height = jsonRect.getInt(3) - rect.y;
                return true;
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }

        return false;
    }

}
