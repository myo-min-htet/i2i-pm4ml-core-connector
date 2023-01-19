package com.modusbox.client.processor;

import com.google.gson.Gson;
import com.modusbox.client.customexception.CCCustomException;
import com.modusbox.client.enums.ErrorCode;
import com.modusbox.log4j2.message.CustomJsonMessage;
import com.modusbox.log4j2.message.CustomJsonMessageImpl;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
@Component("CheckCBSError")
public class CheckCBSError implements Processor {

    public void process(Exchange exchange) throws Exception {
        Gson gson = new Gson();
        String s = gson.toJson(exchange.getIn().getBody(), LinkedHashMap.class);
        JSONObject respObject = new JSONObject(s);
        int errorCode;
        String errorMessage;
        try {
            if (respObject.has("message")) {
                errorMessage = respObject.getString("message");
                switch (errorMessage) {
                    case "External Id already exist":
                        throw new CCCustomException(ErrorCode.getErrorResponse(ErrorCode.GENERIC_PAYEE_REJECTION, "External Id already exist"));
                    case "Invalid token":
                        throw new CCCustomException(ErrorCode.getErrorResponse(ErrorCode.PAYEE_PERMISSION_ERROR, "Invalid token"));
                    case "Invalid amount.":
                        throw new CCCustomException(ErrorCode.getErrorResponse(ErrorCode.PAYEE_LIMIT_ERROR, "Invalid amount."));
                    case "Account Number can't be null.":
                        throw new CCCustomException(ErrorCode.getErrorResponse(ErrorCode.MISSING_MANDATORY_ELEMENT, "Account Number can't be null."));
                    case "Your reference id is already processed.":
                        throw new CCCustomException(ErrorCode.getErrorResponse(ErrorCode.DUPLICATE_REFERENCE_ID, "Your reference id is already processed."));
                    default:
                        throw new CCCustomException(ErrorCode.getErrorResponse(ErrorCode.GENERIC_DOWNSTREAM_ERROR_PAYEE, "Failure at payee CBS"));
                }
            }
            else if (respObject.has("error"))
            {
                errorMessage = respObject.getJSONObject("error").getString("message");
                throw new CCCustomException(ErrorCode.getErrorResponse(ErrorCode.GENERIC_DOWNSTREAM_ERROR_PAYEE, errorMessage));
            }
            else if (respObject.has("errors"))
            {
                JSONObject objJson = (JSONObject) respObject.getJSONArray("errors").get(0);
                errorMessage = objJson.getString("message");
                throw new CCCustomException(ErrorCode.getErrorResponse(ErrorCode.GENERIC_DOWNSTREAM_ERROR_PAYEE, errorMessage));
            }
            else {
                CustomJsonMessage objCustomMessage = new CustomJsonMessageImpl();
                objCustomMessage.logJsonMessage("info",null,"General error ",null,null,respObject.toString());
                throw new CCCustomException(ErrorCode.getErrorResponse(ErrorCode.GENERIC_DOWNSTREAM_ERROR_PAYEE, "Payee CBS Failure"));
            }
        } catch (JSONException e) {
            System.out.println(e.getMessage());
        }

    }

}
