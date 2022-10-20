
package tv.danmaku.ijk.media.player.misc;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

public class ObjectTrackingInfo {
    public Rect rect;
    public String category;
    public Vector<Double> vector;

    public static List<ObjectTrackingInfo> parse(String jsonStr) {
        ArrayList info = new ArrayList();

        if (jsonStr == null) {
            return info;
        }

        try {
            JSONObject json = new JSONObject(jsonStr);
            JSONObject jsonRoot = getJsonRoot(json);
            if (jsonRoot == null) {
                return info;
            }
            JSONObject jsonIVA = jsonRoot.optJSONObject("iva");
            if (jsonIVA == null) {
                return info;
            }

            JSONArray jsonOD = jsonIVA.optJSONArray("od");
            if (jsonOD != null && jsonOD.length() > 0) {
                for (int i = 0; i < jsonOD.length(); i++) {
                    JSONObject jsonItem = jsonOD.optJSONObject(i);
                    if (jsonItem == null) {
                        continue;
                    }
                    info.add(new ObjectTrackingInfo(jsonItem));
                }
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

    private static JSONObject getJsonRoot(JSONObject json) {
        JSONObject root = json.optJSONObject("AGTX");
        if (root != null) {
            return root;
        }
        root = json.optJSONObject("agtx");
        if (root != null) {
            return root;
        }
        root = json.optJSONObject("result");
        return root;
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
            category = json.optString("cat");
            JSONArray jsonRect = json.getJSONArray("rect");
            if (jsonRect.length() < 4) {
                return false;
            }
            rect = new Rect();
            rect.x = jsonRect.getInt(0);
            rect.y = jsonRect.getInt(1);
            rect.width = jsonRect.getInt(2) - rect.x;
            rect.height = jsonRect.getInt(3) - rect.y;

            JSONArray jsonVector = json.optJSONArray("vector");
            if (jsonVector != null) {
                vector = new Vector<>();
                for (int i = 0; i < jsonVector.length(); i++) {
                    double v = jsonVector.getDouble(i);
                    vector.add(v);
                }
            }
        } catch (JSONException e) {
            e.printStackTrace();
            return false;
        }

        return true;
    }

    private boolean parseROI(JSONObject jsonRoot) {
        try {
            JSONObject json = jsonRoot.optJSONObject("roi");
            if (json == null) {
                return false;
            }
            category = "";
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
