package com.fuguang.assistant;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.text.InputType;
import android.view.*;
import android.widget.*;

public class MainActivity extends Activity {
    EditText apiKey;TextView status;
    @Override public void onCreate(Bundle state){super.onCreate(state);getWindow().setStatusBarColor(Ui.BG);getWindow().setNavigationBarColor(Ui.BG);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(Ui.BG);
        LinearLayout root=Ui.column(this);root.setPadding(Ui.dp(this,24),Ui.dp(this,36),Ui.dp(this,24),Ui.dp(this,32));scroll.addView(root);setContentView(scroll);
        root.setOnApplyWindowInsetsListener((v,insets)->{v.setPadding(Ui.dp(this,24)+insets.getSystemWindowInsetLeft(),Ui.dp(this,20)+insets.getSystemWindowInsetTop(),Ui.dp(this,24)+insets.getSystemWindowInsetRight(),Ui.dp(this,24)+insets.getSystemWindowInsetBottom());return insets;});
        root.addView(Ui.text(this,"✦   浮光   /   FUGUANG",15,Ui.BLUE));root.addView(Ui.title(this,"看见，就能问。",32));root.addView(Ui.text(this,"不用切换 App，让 DeepSeek 帮你读懂当前屏幕。",16,Ui.MUTED));
        LinearLayout hero=Ui.card(this,root);hero.addView(Ui.text(this,"屏幕上的灵感，随手解答",19,Ui.INK));hero.addView(Ui.text(this,"01  点悬浮球，截取当前画面\n02  预览截图，输入你的问题\n03  阅读回答，还可以继续追问",15,Ui.MUTED));
        LinearLayout config=Ui.card(this,root);config.addView(Ui.title(this,"连接 DeepSeek",19));config.addView(Ui.text(this,"模型：deepseek-flash · 支持图片输入",13,Ui.MUTED));
        apiKey=Ui.input(this,"填写你自己的 API Key");apiKey.setSingleLine(true);apiKey.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);apiKey.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);config.addView(apiKey);
        try{apiKey.setText(Secrets.load(this));}catch(Exception e){toast("原密钥无法读取，请重新填写。");}
        Button save=Ui.button(this,"保存连接设置",false);config.addView(save);save.setOnClickListener(v->save());
        config.addView(Ui.text(this,"密钥经 Android Keystore 加密保存在本机。截图只在你点击发送后上传到 DeepSeek，API 调用按平台规则计费。",12,Ui.MUTED));
        LinearLayout launch=Ui.card(this,root);launch.addView(Ui.title(this,"开启屏幕助手",19));status=Ui.text(this,"",14,Ui.MUTED);launch.addView(status);
        Button permission=Ui.button(this,"①  允许显示悬浮窗",false);launch.addView(permission);permission.setOnClickListener(v->{try{startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:"+getPackageName())));}catch(Exception e){toast("请在系统设置中为浮光开启悬浮窗权限。");}});
        Button start=Ui.button(this,"②  启动浮光",true);launch.addView(start);start.setOnClickListener(v->startAssistant());
        Button stop=Ui.button(this,"停止助手",false);launch.addView(stop);stop.setOnClickListener(v->{stopService(new Intent(this,ScreenService.class));toast("已停止");});
        root.addView(Ui.text(this,"OPPO 使用提示：在 ColorOS 设置中允许“浮光”显示悬浮窗。若后台经常被清理，可在应用电池管理中允许后台活动。\n\n启动时请授权共享整个屏幕。锁屏或系统结束共享后，需要重新启动。受保护的页面可能无法截取。",13,Ui.MUTED));
    }
    @Override protected void onResume(){super.onResume();if(status!=null)status.setText(Settings.canDrawOverlays(this)?"悬浮窗权限已开启 ✓":"请先允许悬浮窗，再启动助手。");}
    boolean save(){try{Secrets.save(this,apiKey.getText().toString().trim());toast("设置已保存");return true;}catch(Exception e){toast("密钥保存失败，请重试。");return false;}}
    void startAssistant(){
        if(!Settings.canDrawOverlays(this)){toast("请先允许显示悬浮窗。");return;}
        if(apiKey.getText().toString().trim().isEmpty()){toast("请先填写 API Key。");return;}if(!save())return;
        if(ScreenService.running){moveTaskToBack(true);return;}
        if(Build.VERSION.SDK_INT>=33 && checkSelfPermission("android.permission.POST_NOTIFICATIONS")!=android.content.pm.PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"},13);return;}
        requestCapture();
    }
    void requestCapture(){
        MediaProjectionManager m=(MediaProjectionManager)getSystemService(MEDIA_PROJECTION_SERVICE);
        Intent captureIntent=Build.VERSION.SDK_INT>=34
            ?m.createScreenCaptureIntent(android.media.projection.MediaProjectionConfig.createConfigForDefaultDisplay())
            :m.createScreenCaptureIntent();
        startActivityForResult(captureIntent,40);
    }
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] grants){super.onRequestPermissionsResult(request,permissions,grants);if(request==13)requestCapture();}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==40&&result==RESULT_OK&&data!=null){Intent i=new Intent(this,ScreenService.class).putExtra("code",result).putExtra("data",data);startForegroundService(i);moveTaskToBack(true);}else if(request==40)toast("未授权截屏，助手尚未启动。");}
    void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
}
