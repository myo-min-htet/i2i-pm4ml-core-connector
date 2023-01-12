package com.modusbox.client.utility;

import org.json.JSONArray;
import org.json.JSONObject;

public class util {
    //get specific value of content field by key(here phone no.) from json array
    public static String getMapData(String key,String content,String data){
        String value="";

        JSONArray arrJson = new JSONArray(data);
        for (int i = 0; i < arrJson.length(); i++) {
            if(arrJson.getJSONObject(i).getString("phone").equals(key))
            {
                value = arrJson.getJSONObject(i).getString(content);
            }
        }
        return value;
    }
}
