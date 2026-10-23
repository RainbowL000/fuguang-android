package com.fuguang.assistant;

import org.json.*;

final class Payload {
    static JSONObject user(String question,String image) throws JSONException {
        if(image==null)return new JSONObject().put("role","user").put("content",question);
        return new JSONObject().put("role","user").put("content",new JSONArray()
            .put(new JSONObject().put("type","text").put("text",question))
            .put(new JSONObject().put("type","image_url").put("image_url",new JSONObject().put("url","data:image/jpeg;base64,"+image))));
    }
    static String request(JSONArray history,JSONObject next) throws JSONException {
        JSONArray messages=new JSONArray(history.toString());messages.put(next);
        return new JSONObject().put("model","deepseek-flash").put("messages",messages).put("stream",false)
            .put("thinking",new JSONObject().put("type","disabled")).put("max_tokens",8192).toString();
    }
    static String answer(String raw,int status) throws Exception {
        JSONObject body;
        try{body=new JSONObject(raw);}catch(JSONException e){throw new Exception("服务返回了无法解析的内容（HTTP "+status+"），请稍后重试。");}
        if(status<200||status>=300||body.has("error")){
            String tip=switch(status){case 401 -> "API Key 无效，请在首页重新填写。";case 402 -> "API 余额不足，请检查 DeepSeek 开放平台账户。";case 429 -> "请求过于频繁，请稍后重试。";default -> "DeepSeek 请求失败（HTTP "+status+"），请稍后重试。";};
            throw new Exception(tip);
        }
        JSONArray choices=body.optJSONArray("choices");
        JSONObject choice=choices==null?null:choices.optJSONObject(0);
        JSONObject message=choice==null?null:choice.optJSONObject("message");
        if(message==null)throw new Exception("接口返回结构异常：缺少 choices[0].message，请更新应用或稍后重试。");
        Object content=message.opt("content");String result=content instanceof String?(String)content:"";
        String finish=choice.optString("finish_reason","");
        if("content_filter".equals(finish))throw new Exception("本次请求被平台内容过滤，请调整问题后重试。");
        if(result.trim().isEmpty()){
            if("length".equals(finish))throw new Exception("输出额度已用尽，尚未生成最终回答。请只问一道题，或缩小提问范围。（finish_reason=length）");
            Object reasoning=message.opt("reasoning_content");
            if(reasoning instanceof String&&!((String)reasoning).trim().isEmpty())throw new Exception("接口仅返回了思考内容，没有最终回答。请重试或缩小提问范围。（reasoning_only）");
            throw new Exception("接口返回空回答，请稍后重试。（empty_content）");
        }
        if("length".equals(finish))return result+"\n\n【回答达到输出上限，以上内容可能不完整。请缩小范围或继续追问。】";
        return result;
    }
}
