package com.fuguang.assistant;

import org.json.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class PayloadTest {
    @Test public void screenQuestionsDisableThinkingWithRoomForFinalAnswer() throws Exception {
        JSONObject request=new JSONObject(Payload.request(new JSONArray(),Payload.user("题目","YWJj")));
        assertEquals("disabled",request.getJSONObject("thinking").getString("type"));
        assertEquals(8192,request.getInt("max_tokens"));
    }
    private String response(Object content,String finish,String reasoning) throws Exception {
        return new JSONObject().put("choices",new JSONArray().put(new JSONObject().put("finish_reason",finish)
            .put("message",new JSONObject().put("content",content).put("reasoning_content",reasoning)))).toString();
    }
    @Test public void reasoningBudgetExhaustionHasSpecificError() throws Exception {
        try{Payload.answer(response(JSONObject.NULL,"length","private reasoning"),200);fail("expected failure");}
        catch(Exception e){assertTrue(e.getMessage().contains("finish_reason=length"));assertFalse(e.getMessage().contains("private reasoning"));}
    }
    @Test public void reasoningOnlyIsNotPresentedAsFinalAnswer() throws Exception {
        try{Payload.answer(response("","stop","private reasoning"),200);fail("expected failure");}
        catch(Exception e){assertTrue(e.getMessage().contains("reasoning_only"));assertFalse(e.getMessage().contains("private reasoning"));}
    }
    @Test public void nullContentDoesNotBecomeLiteralNullAnswer() throws Exception {
        try{Payload.answer(response(JSONObject.NULL,"stop",""),200);fail("expected failure");}
        catch(Exception e){assertTrue(e.getMessage().contains("empty_content"));}
    }
    @Test public void truncatedAnswerIsMarked() throws Exception {assertTrue(Payload.answer(response("partial","length",""),200).contains("可能不完整"));}
    @Test public void malformedSuccessResponseHasSpecificError() throws Exception {
        try{Payload.answer("{\"choices\":[]}",200);fail("expected failure");}
        catch(Exception e){assertTrue(e.getMessage().contains("choices[0].message"));}
    }
    @Test public void escapesQuestionAndKeepsImage() throws Exception {
        String q="解释 \"hello\"\n路径 C:\\notes 😀";
        JSONObject body=new JSONObject(Payload.request(new JSONArray(),Payload.user(q,"YWJj")));
        JSONArray content=body.getJSONArray("messages").getJSONObject(0).getJSONArray("content");
        assertEquals(q,content.getJSONObject(0).getString("text"));
        assertEquals("data:image/jpeg;base64,YWJj",content.getJSONObject(1).getJSONObject("image_url").getString("url"));
    }
    @Test public void followupPreservesHistoryWithoutMutatingIt() throws Exception {
        JSONArray h=new JSONArray().put(Payload.user("first","YWJj")).put(new JSONObject().put("role","assistant").put("content","answer"));
        JSONObject b=new JSONObject(Payload.request(h,Payload.user("followup",null)));
        assertEquals(2,h.length());assertEquals(3,b.getJSONArray("messages").length());
        assertEquals("followup",b.getJSONArray("messages").getJSONObject(2).getString("content"));
    }
    @Test public void parsesSuccessfulAnswer() throws Exception {assertEquals("你好",Payload.answer("{\"choices\":[{\"message\":{\"content\":\"你好\"}}]}",200));}
    @Test public void failedResponseDoesNotLookLikeBlankSuccess() throws Exception {
        try{Payload.answer("{\"error\":{\"message\":\"bad key\"}}",401);fail("expected failure");}catch(Exception e){assertTrue(e.getMessage().contains("API Key"));}
    }
    @Test public void invalidJsonFailsClearly() throws Exception {try{Payload.answer("<html>bad gateway</html>",502);fail("expected failure");}catch(Exception e){assertTrue(e.getMessage().contains("502"));}}
}
