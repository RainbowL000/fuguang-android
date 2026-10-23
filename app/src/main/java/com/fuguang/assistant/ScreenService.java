package com.fuguang.assistant;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.content.res.Configuration;
import android.graphics.*;
import android.hardware.display.*;
import android.media.*;
import android.media.projection.*;
import android.os.*;
import android.provider.Settings;
import android.util.Base64;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import org.json.*;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.*;

public class ScreenService extends Service {
    static volatile boolean running=false;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService network=Executors.newSingleThreadExecutor();
    private WindowManager windows;private MediaProjection projection;private VirtualDisplay display;private ImageReader reader;
    private View bubble,panel;private WindowManager.LayoutParams bubbleParams;private TextView answer;private EditText question;private Button send;
    private String imageData;private Bitmap preview;private JSONArray history=new JSONArray();
    private boolean awaiting=false,busy=false,dead=false;private int width,height,rotation,session=0;
    private final Runnable captureTimeout=()->{if(awaiting){awaiting=false;releaseReader();showBubble();toast("截屏超时。请返回浮光停止后重新启动，并授权共享整个屏幕。此提示不代表页面受保护。");}};
    private final MediaProjection.Callback projectionCallback=new MediaProjection.Callback(){@Override public void onStop(){if(!dead){toast("屏幕共享已结束，请打开浮光重新启动。");stopSelf();}}};
    @Override public void onCreate(){super.onCreate();windows=(WindowManager)getSystemService(WINDOW_SERVICE);}
    @Override public int onStartCommand(Intent intent,int flags,int id){
        if(intent==null||"stop".equals(intent.getAction())){stopSelf();return START_NOT_STICKY;}
        if(running)return START_NOT_STICKY;
        try{
            NotificationManager nm=getSystemService(NotificationManager.class);nm.createNotificationChannel(new NotificationChannel("screen","屏幕助手",NotificationManager.IMPORTANCE_LOW));
            PendingIntent open=PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
            PendingIntent stop=PendingIntent.getService(this,1,new Intent(this,ScreenService.class).setAction("stop"),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
            Notification n=new Notification.Builder(this,"screen").setSmallIcon(com.fuguang.assistant.R.drawable.ic_logo).setContentTitle("浮光正在待命").setContentText("点击悬浮球提问 · 截图仅在发送时上传").setContentIntent(open).setOngoing(true).addAction(new Notification.Action.Builder(null,"停止助手",stop).build()).build();
            startForeground(7,n,ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
            Intent data=intent.getParcelableExtra("data");if(data==null)throw new IllegalStateException();
            projection=((MediaProjectionManager)getSystemService(MEDIA_PROJECTION_SERVICE)).getMediaProjection(intent.getIntExtra("code",Activity.RESULT_CANCELED),data);
            projection.registerCallback(projectionCallback,main);
            android.util.DisplayMetrics metrics=new android.util.DisplayMetrics();windows.getDefaultDisplay().getRealMetrics(metrics);width=metrics.widthPixels;height=metrics.heightPixels;rotation=windows.getDefaultDisplay().getRotation();
            // Create exactly one virtual display per consent token. Attach a fresh surface only when capturing.
            display=projection.createVirtualDisplay("FuguangScreen",width,height,metrics.densityDpi,DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,null,null,main);
            running=true;showBubble();
        }catch(Exception e){toast("启动失败，请确认悬浮窗和屏幕共享权限后重试。");stopSelf();}
        return START_NOT_STICKY;
    }
    @android.annotation.SuppressLint({"ClickableViewAccessibility", "RtlHardcoded"}) // Raw screen coordinates deliberately use LEFT; touch handler invokes performClick / performLongClick.
    private void showBubble(){
        if(dead||!Settings.canDrawOverlays(this))return;
        if(bubble!=null){bubble.setVisibility(View.VISIBLE);return;}
        TextView b=Ui.title(this,"✦",28);b.setTextColor(Color.WHITE);b.setGravity(Gravity.CENTER);b.setBackground(Ui.bg(Ui.BLUE,28,this));b.setElevation(Ui.dp(this,8));b.setContentDescription("浮光：点击截屏提问，拖动调整位置，长按停止");bubble=b;
        bubbleParams=new WindowManager.LayoutParams(Ui.dp(this,54),Ui.dp(this,54),WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,PixelFormat.TRANSLUCENT);bubbleParams.gravity=Gravity.TOP|Gravity.LEFT;bubbleParams.x=width-Ui.dp(this,70);bubbleParams.y=height/3;
        b.setOnClickListener(v->{if(imageData!=null)showPanel();else beginCapture();});b.setOnLongClickListener(v->{stopSelf();return true;});
        b.setOnTouchListener(new View.OnTouchListener(){float x,y;int ox,oy;boolean moved;long down;
            public boolean onTouch(View v,android.view.MotionEvent e){switch(e.getActionMasked()){
                case MotionEvent.ACTION_DOWN:x=e.getRawX();y=e.getRawY();ox=bubbleParams.x;oy=bubbleParams.y;moved=false;down=System.currentTimeMillis();return true;
                case MotionEvent.ACTION_MOVE:float dx=e.getRawX()-x,dy=e.getRawY()-y;if(Math.abs(dx)+Math.abs(dy)>Ui.dp(ScreenService.this,8))moved=true;if(moved){bubbleParams.x=Math.max(0,Math.min(width-Ui.dp(ScreenService.this,54),ox+(int)dx));bubbleParams.y=Math.max(Ui.dp(ScreenService.this,32),Math.min(height-Ui.dp(ScreenService.this,90),oy+(int)dy));windows.updateViewLayout(bubble,bubbleParams);}return true;
                case MotionEvent.ACTION_UP:if(!moved){if(System.currentTimeMillis()-down>650)v.performLongClick();else v.performClick();}return true;
                case MotionEvent.ACTION_CANCEL:return true;
            }return false;}});
        try{windows.addView(b,bubbleParams);}catch(Exception e){toast("悬浮窗权限不可用，请重新授权。");stopSelf();}
    }
    private void beginCapture(){
        if(awaiting)return;
        if(busy){toast("正在等待回答，请稍后。");return;}
        if(windows.getDefaultDisplay().getRotation()!=rotation){toast("屏幕方向已变化，请重新启动浮光。");stopSelf();return;}
        removePanel();clearConversation();if(bubble!=null)bubble.setVisibility(View.GONE);
        // A new surface requests an initial frame even when the underlying screen is static.
        // Attach after the overlay has disappeared, so no previously queued overlay frame can be used.
        awaiting=true;
        main.postDelayed(()->{
            if(dead||!awaiting)return;
            try{
                releaseReader();
                reader=ImageReader.newInstance(width,height,PixelFormat.RGBA_8888,2);
                reader.setOnImageAvailableListener(this::readFrame,main);
                display.setSurface(reader.getSurface());
                main.postDelayed(captureTimeout,8000);
            }catch(Exception e){awaiting=false;releaseReader();showBubble();toast("截屏连接失败，请返回首页停止后重新启动助手。");}
        },400);
    }
    private void readFrame(ImageReader source){
        if(dead||source!=reader||!awaiting)return;
        boolean finished=false;
        try(Image frame=source.acquireLatestImage()){
            if(frame!=null){awaiting=false;finished=true;main.removeCallbacks(captureTimeout);capture(frame);}
        }catch(Exception e){awaiting=false;finished=true;main.removeCallbacks(captureTimeout);showBubble();toast("读取截图失败，请返回首页停止后重新启动助手。");}
        finally{if(finished)releaseReader();}
    }
    private void releaseReader(){
        if(display!=null){try{display.setSurface(null);}catch(Exception ignored){}}
        if(reader!=null){reader.setOnImageAvailableListener(null,null);reader.close();reader=null;}
    }
    private void capture(Image frame){
        Image.Plane plane=frame.getPlanes()[0];int pixel=plane.getPixelStride(),stride=plane.getRowStride();ByteBuffer buffer=plane.getBuffer();
        Bitmap padded=Bitmap.createBitmap(stride/pixel,height,Bitmap.Config.ARGB_8888);padded.copyPixelsFromBuffer(buffer);
        Bitmap cropped=Bitmap.createBitmap(padded,0,0,width,height);if(cropped!=padded)padded.recycle();
        float scale=Math.min(1f,1600f/Math.max(width,height));preview=Bitmap.createScaledBitmap(cropped,Math.max(1,Math.round(width*scale)),Math.max(1,Math.round(height*scale)),true);if(preview!=cropped)cropped.recycle();
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();preview.compress(Bitmap.CompressFormat.JPEG,85,bytes);imageData=Base64.encodeToString(bytes.toByteArray(),Base64.NO_WRAP);
        showPanel();
    }
    private void showPanel(){
        LinearLayout root=Ui.column(this);root.setPadding(Ui.dp(this,20),Ui.dp(this,14),Ui.dp(this,20),Ui.dp(this,20));root.setBackground(Ui.bg(Color.WHITE,26,this));panel=root;
        LinearLayout bar=new LinearLayout(this);bar.setGravity(Gravity.CENTER_VERTICAL);TextView name=Ui.title(this,"✦ 浮光",21);bar.addView(name,new LinearLayout.LayoutParams(0,-2,1));Button close=Ui.button(this,"收起",false);bar.addView(close,new LinearLayout.LayoutParams(Ui.dp(this,76),Ui.dp(this,44)));root.addView(bar);close.setOnClickListener(v->{removePanel();showBubble();});
        root.addView(Ui.text(this,"DeepSeek · 基于本次截图回答",12,Ui.MUTED));
        ImageView thumb=new ImageView(this);thumb.setImageBitmap(preview);thumb.setScaleType(ImageView.ScaleType.FIT_CENTER);thumb.setContentDescription("本次准备发送的屏幕截图，点击放大预览");root.addView(thumb,new LinearLayout.LayoutParams(-1,Ui.dp(this,100)));
        thumb.setOnClickListener(v->{if(thumb.getLayoutParams().height==Ui.dp(this,100))thumb.getLayoutParams().height=Ui.dp(this,260);else thumb.getLayoutParams().height=Ui.dp(this,100);thumb.requestLayout();});
        ScrollView scroller=new ScrollView(this);answer=Ui.text(this,busy?"正在分析截图，请稍候…":history.length()>0?history.optJSONObject(history.length()-1).optString("content"):"你想了解这张屏幕上的什么？\n点击发送后，截图和问题才会上传到 DeepSeek。",15,Ui.INK);answer.setTextIsSelectable(true);answer.setLineSpacing(Ui.dp(this,4),1f);scroller.addView(answer);root.addView(scroller,new LinearLayout.LayoutParams(-1,0,1));
        question=Ui.input(this,"输入问题，例如：解释这页的内容");question.setMinLines(1);question.setMaxLines(3);root.addView(question,new LinearLayout.LayoutParams(-1,-2));
        send=Ui.button(this,busy?"正在思考…":history.length()>0?"继续追问":"发送问题",true);root.addView(send);send.setEnabled(!busy);question.setEnabled(!busy);send.setOnClickListener(v->ask());
        LinearLayout actions=new LinearLayout(this);Button copy=Ui.button(this,"复制回答",false),recapture=Ui.button(this,"换一张屏幕",false);LinearLayout.LayoutParams half=new LinearLayout.LayoutParams(0,Ui.dp(this,44),1);half.setMargins(0,Ui.dp(this,10),Ui.dp(this,6),0);actions.addView(copy,half);LinearLayout.LayoutParams right=new LinearLayout.LayoutParams(0,Ui.dp(this,44),1);right.setMargins(Ui.dp(this,6),Ui.dp(this,10),0,0);actions.addView(recapture,right);root.addView(actions);
        copy.setOnClickListener(v->{((android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("浮光回答",answer.getText()));toast("已复制");});recapture.setOnClickListener(v->beginCapture());
        WindowManager.LayoutParams p=new WindowManager.LayoutParams(Math.min(width-Ui.dp(this,24),Ui.dp(this,440)),Math.min(height-Ui.dp(this,100),Ui.dp(this,640)),WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,PixelFormat.TRANSLUCENT);p.gravity=Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL;p.y=Ui.dp(this,28);p.softInputMode=WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE;
        try{windows.addView(root,p);}catch(Exception e){toast("无法显示问答面板，请重新授权悬浮窗。");stopSelf();}
        if(bubble!=null)bubble.setVisibility(View.GONE);
    }
    private void ask(){
        String q=question.getText().toString().trim();if(q.isEmpty()){toast("先写下你的问题。");return;}if(busy)return;if(history.length()>=12){toast("本轮已达 6 次提问，请换一张屏幕开始新对话。");return;}
        final JSONObject user;final String payload,key;try{key=Secrets.load(this);if(key.isEmpty()){toast("请在首页填写 API Key。");return;}user=Payload.user(q,history.length()==0?imageData:null);payload=Payload.request(history,user);}catch(Exception e){toast("读取连接设置失败，请返回首页重新保存。");return;}
        busy=true;int token=session;send.setEnabled(false);send.setText("正在思考…");question.setEnabled(false);answer.setText("正在分析截图，请稍候…");((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(question.getWindowToken(),0);
        network.submit(()->{String result=null,error=null;try{result=DeepSeek.ask(key,payload);}catch(java.net.SocketTimeoutException e){error="连接超时，请重试。";}catch(java.io.IOException e){error="网络连接失败，请检查网络后重试。";}catch(Exception e){error=e.getMessage()==null?"请求失败，请重试。":e.getMessage();}final String text=result,problem=error;main.post(()->{if(dead||token!=session)return;busy=false;if(text!=null){history.put(user);try{history.put(new JSONObject().put("role","assistant").put("content",text));}catch(JSONException ignored){}answer.setText(text);question.setText("");question.setHint("继续追问这张截图…");}else answer.setText(problem);send.setEnabled(true);send.setText(history.length()==0?"发送问题":"继续追问");question.setEnabled(true);});});
    }
    private void removePanel(){if(panel!=null){((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(panel.getWindowToken(),0);try{windows.removeView(panel);}catch(Exception ignored){}panel=null;}}
    private void clearConversation(){session++;history=new JSONArray();imageData=null;if(preview!=null){preview.recycle();preview=null;}}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
    @Override public void onConfigurationChanged(Configuration config){super.onConfigurationChanged(config);if(windows!=null&&windows.getDefaultDisplay().getRotation()!=rotation){toast("屏幕方向已变化，请打开浮光重新启动。");stopSelf();}}
    @Override public void onDestroy(){dead=true;running=false;main.removeCallbacksAndMessages(null);removePanel();if(bubble!=null){try{windows.removeView(bubble);}catch(Exception ignored){}bubble=null;}releaseReader();if(display!=null)display.release();if(projection!=null){projection.unregisterCallback(projectionCallback);projection.stop();}clearConversation();network.shutdownNow();stopForeground(STOP_FOREGROUND_REMOVE);super.onDestroy();}
    @Override public IBinder onBind(Intent i){return null;}
}
