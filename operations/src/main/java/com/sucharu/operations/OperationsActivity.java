package com.sucharu.operations;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class OperationsActivity extends AppCompatActivity {
    private static final int GREEN = Color.rgb(23, 107, 85), DARK = Color.rgb(16, 63, 53);
    private static final int CREAM = Color.rgb(248, 247, 240), INK = Color.rgb(23, 59, 53);
    private static final int MUTED = Color.rgb(102, 117, 110), LINE = Color.rgb(226, 233, 225);
    private static final String[] BOOKING_STATUSES = {"booking_received", "confirmed", "assigned", "on_the_way", "service_started", "completed", "cancelled"};
    private static final String[] VERIFY_STATUSES = {"pending", "in_review", "verified", "rejected"};
    private static final String[] PARTNER_STATUSES = {"inactive", "active", "suspended"};
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private final String[] adminTabs = {"Dashboard", "Bookings", "Partners", "Services"};
    private SharedPreferences prefs;
    private LinearLayout root, pageContent, nav;
    private String role = "admin", token = "", displayName = "";
    private String currentTab = "Dashboard";
    private EditText loginIdentifier, loginPassword;
    private TextView loginError;
    private JSONArray partnerCache = new JSONArray();

    @Override protected void onCreate(@Nullable Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences("sucharu_ops", MODE_PRIVATE);
        token = prefs.getString("token", "");
        role = prefs.getString("role", "admin");
        displayName = prefs.getString("name", "");
        if (!token.isEmpty()) showConsole(); else showLogin();
    }

    private void showLogin() {
        root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(CREAM);
        LinearLayout hero = new LinearLayout(this); hero.setOrientation(LinearLayout.VERTICAL); hero.setPadding(dp(24), dp(34), dp(24), dp(28)); hero.setBackgroundColor(DARK);
        hero.addView(text("সুচারু", 15, Color.rgb(181, 214, 108), true));
        hero.addView(text("Operations", 30, Color.WHITE, true), top(4));
        hero.addView(text("Manage service bookings and partner work from one place.", 14, Color.rgb(224, 236, 228), false), top(4));
        root.addView(hero);

        ScrollView scroll = new ScrollView(this); LinearLayout outer = new LinearLayout(this); outer.setOrientation(LinearLayout.VERTICAL); outer.setPadding(dp(18), dp(20), dp(18), dp(24));
        MaterialCardView card = card(); LinearLayout form = column(); form.setPadding(dp(18), dp(18), dp(18), dp(18)); card.addView(form);
        form.addView(text("Choose your workspace", 19, INK, true));
        LinearLayout switcher = new LinearLayout(this); switcher.setOrientation(LinearLayout.HORIZONTAL); switcher.setPadding(0, dp(14), 0, dp(10));
        Button admin = button("Admin", true), partner = button("Partner", false);
        switcher.addView(admin, new LinearLayout.LayoutParams(0, -2, 1)); switcher.addView(partner, new LinearLayout.LayoutParams(0, -2, 1)); form.addView(switcher);
        loginIdentifier = field("Admin email", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        loginPassword = field("Password", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        form.addView(loginIdentifier, top(4)); form.addView(loginPassword, top(10));
        loginError = text("", 13, Color.rgb(170, 47, 42), false); form.addView(loginError, top(8));
        Button submit = button("Sign in", true); form.addView(submit, top(12));
        outer.addView(card); outer.addView(text("Your role is verified by Sucharu's backend. Role-specific permissions are enforced on every request.", 12, MUTED, false), top(14));
        scroll.addView(outer); root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1)); setContentView(root);
        admin.setOnClickListener(v -> { role="admin"; admin.setBackgroundTintList(ColorStateList.valueOf(GREEN)); partner.setBackgroundTintList(ColorStateList.valueOf(Color.rgb(236, 241, 235))); loginIdentifier.setHint("Admin email"); loginIdentifier.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS); });
        partner.setOnClickListener(v -> { role="partner"; partner.setBackgroundTintList(ColorStateList.valueOf(GREEN)); admin.setBackgroundTintList(ColorStateList.valueOf(Color.rgb(236, 241, 235))); loginIdentifier.setHint("Partner phone number"); loginIdentifier.setInputType(InputType.TYPE_CLASS_PHONE); });
        submit.setOnClickListener(v -> login(submit));
    }

    private void login(Button submit) {
        String identifier=loginIdentifier.getText().toString().trim(), password=loginPassword.getText().toString();
        if(identifier.isEmpty()||password.isEmpty()){loginError.setText("Enter your sign-in details.");return;}
        submit.setEnabled(false); submit.setText("Connecting…"); loginError.setText("Waking the service if it was idle. This can take a little while.");
        try {
            JSONObject body=new JSONObject(); body.put(role.equals("admin")?"email":"phone",identifier); body.put("password",password);
            String path=role.equals("admin")?"/auth/login":"/auth/partner/login";
            request("POST",path,body,null,false,(code,obj,array,error)->{
                submit.setEnabled(true); submit.setText("Sign in");
                if(error!=null||code<200||code>=300||obj==null){loginError.setText(error!=null?error:(obj==null?"Sign-in failed.":obj.optString("message","Sign-in failed.")));return;}
                token=obj.optString("token",""); JSONObject profile=obj.optJSONObject(role);
                if(token.isEmpty()){loginError.setText("The server did not return a session.");return;}
                displayName=profile==null?"":profile.optString("name",profile.optString("email",""));
                prefs.edit().putString("token",token).putString("role",role).putString("name",displayName).apply();
                currentTab=role.equals("admin")?"Dashboard":"Jobs"; showConsole();
            });
        } catch(Exception e){submit.setEnabled(true);submit.setText("Sign in");loginError.setText("Could not prepare the sign-in request.");}
    }

    private void showConsole() {
        root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(CREAM);
        LinearLayout header=new LinearLayout(this);header.setOrientation(LinearLayout.HORIZONTAL);header.setGravity(Gravity.CENTER_VERTICAL);header.setPadding(dp(18),dp(13),dp(14),dp(13));header.setBackgroundColor(DARK);
        LinearLayout titles=column(); titles.addView(text(role.equals("admin")?"Sucharu Admin":"Partner Workspace",19,Color.WHITE,true)); titles.addView(text(displayName.isEmpty()?"Signed in securely":displayName,12,Color.rgb(211,229,218),false),top(2));header.addView(titles,new LinearLayout.LayoutParams(0,-2,1));
        Button logout=button("Sign out",false);logout.setTextColor(Color.WHITE);logout.setBackgroundTintList(ColorStateList.valueOf(Color.rgb(42,91,76)));header.addView(logout);root.addView(header);
        nav=new LinearLayout(this);nav.setOrientation(LinearLayout.HORIZONTAL);nav.setPadding(dp(8),dp(7),dp(8),dp(7));nav.setBackgroundColor(Color.WHITE);root.addView(nav);
        ScrollView scroll=new ScrollView(this);pageContent=column();pageContent.setPadding(dp(16),dp(16),dp(16),dp(24));scroll.addView(pageContent);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        logout.setOnClickListener(v->logout());
        if(role.equals("admin")){for(String tab:adminTabs){Button b=button(tab,false);b.setTextSize(11);b.setPadding(dp(5),0,dp(5),0);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(44),1);p.setMargins(dp(2),0,dp(2),0);nav.addView(b,p);b.setOnClickListener(v->openAdminTab(tab));}}
        else nav.setVisibility(View.GONE);
        openCurrentTab();
    }

    private void openAdminTab(String tab){currentTab=tab;openCurrentTab();}
    private void openCurrentTab(){if(pageContent==null)return;pageContent.removeAllViews();if(role.equals("partner")){loadJobs();return;}if("Bookings".equals(currentTab))loadBookings();else if("Partners".equals(currentTab))loadPartners();else if("Services".equals(currentTab))loadServices();else loadDashboard();}
    private void loadDashboard(){
        pageContent.addView(text("Operations overview",23,INK,true));pageContent.addView(text("Live data from your shared Sucharu backend.",13,MUTED,false),top(4));
        addLoading("Loading dashboard…");request("GET","/dashboard/summary",null,token,false,(code,obj,arr,error)->{if(!active())return;pageContent.removeAllViews();if(error!=null||obj==null){showError(error);return;}
            pageContent.addView(text("Operations overview",23,INK,true));pageContent.addView(text("Live data from your shared Sucharu backend.",13,MUTED,false),top(4));
            LinearLayout metrics=column();metrics.setPadding(0,dp(14),0,dp(4));metric(metrics,"Bookings",String.valueOf(obj.optInt("bookings")));metric(metrics,"Active partners",String.valueOf(obj.optInt("activePartners")));metric(metrics,"Active services",String.valueOf(obj.optInt("activeServices")));pageContent.addView(metrics);
            MaterialCardView card=card();LinearLayout c=column();c.setPadding(dp(16),dp(15),dp(16),dp(15));card.addView(c);c.addView(text("Booking status",17,INK,true));JSONArray counts=obj.optJSONArray("statusCounts");if(counts!=null)for(int i=0;i<counts.length();i++){JSONObject row=counts.optJSONObject(i);if(row!=null)c.addView(text(label(row.optString("_id"))+"     "+row.optInt("count"),13,MUTED,false),top(8));}pageContent.addView(card,top(12));
            Button bookings=button("Manage bookings",true);bookings.setOnClickListener(v->openAdminTab("Bookings"));pageContent.addView(bookings,top(12));
        });
    }

    private void metric(LinearLayout parent,String title,String value){MaterialCardView c=card();LinearLayout body=column();body.setPadding(dp(15),dp(12),dp(15),dp(12));c.addView(body);body.addView(text(value,25,GREEN,true));body.addView(text(title,12,MUTED,false),top(1));parent.addView(c,top(8));}

    private void loadBookings(){
        pageContent.addView(text("Bookings",23,INK,true));Button refresh=button("Refresh",false);pageContent.addView(refresh,top(8));addLoading("Loading bookings…");
        refresh.setOnClickListener(v->loadBookings());
        request("GET","/partners",null,token,false,(pc,po,pa,pe)->{if(pe==null&&pa!=null)partnerCache=pa;request("GET","/bookings",null,token,false,(code,obj,rows,error)->{if(!active())return;pageContent.removeAllViews();pageContent.addView(text("Bookings",23,INK,true));Button again=button("Refresh",false);pageContent.addView(again,top(8));again.setOnClickListener(v->loadBookings());if(error!=null||rows==null){showError(error);return;}if(rows.length()==0){pageContent.addView(text("No service requests yet.",14,MUTED,false),top(16));return;}for(int i=0;i<rows.length();i++){JSONObject row=rows.optJSONObject(i);if(row!=null)bookingCard(row);}});});
    }

    private void bookingCard(JSONObject row){
        JSONObject service=row.optJSONObject("service"),schedule=row.optJSONObject("schedule"),customer=row.optJSONObject("customer");String id=row.optString("bookingId"),status=row.optString("status","booking_received");
        MaterialCardView c=card();LinearLayout body=column();body.setPadding(dp(15),dp(14),dp(15),dp(14));c.addView(body);
        body.addView(text(service==null?"Service request":service.optString("name","Service"),18,INK,true));body.addView(text(id+"   ·   "+label(status),12,GREEN,true),top(5));
        body.addView(text((customer==null?"Customer":customer.optString("name","Customer"))+"  ·  "+(customer==null?"":customer.optString("phone","")),13,MUTED,false),top(7));
        body.addView(text((schedule==null?"":schedule.optString("date",""))+"  ·  "+(schedule==null?"":schedule.optString("time",""))+"\n"+row.optString("area","")+"\n"+row.optString("address",""),13,INK,false),top(5));
        Spinner statusPicker=spinner(BOOKING_STATUSES);setSelection(statusPicker,BOOKING_STATUSES,status);body.addView(statusPicker,top(8));
        Spinner partnerPicker=partnerSpinner();body.addView(partnerPicker,top(6));JSONObject assigned=row.optJSONObject("partner");if(assigned!=null){String pid=assigned.optString("partnerId","");for(int i=0;i<partnerCache.length();i++){JSONObject p=partnerCache.optJSONObject(i);if(p!=null&&pid.equals(p.optString("_id")))partnerPicker.setSelection(i+1);}}
        Button saveStatus=button("Save booking status",true);body.addView(saveStatus,top(7));saveStatus.setOnClickListener(v->{saveStatus.setEnabled(false);patch("/bookings/"+id+"/status",json("status",statusPicker.getSelectedItem().toString()),(ok,msg)->{saveStatus.setEnabled(true);toast(msg);if(ok)loadBookings();});});
        Button assign=button("Save partner assignment",false);body.addView(assign,top(5));assign.setOnClickListener(v->{int pos=partnerPicker.getSelectedItemPosition();String pid=pos==0?"":partnerCache.optJSONObject(pos-1).optString("_id","");assignPartner(id,pid,assign);});
        pageContent.addView(c,top(12));
    }

    private void assignPartner(String bookingId,String partnerId,Button button){try{JSONObject b=new JSONObject();b.put("partnerId",partnerId);button.setEnabled(false);patch("/bookings/"+bookingId+"/partner",b,(ok,msg)->{button.setEnabled(true);toast(msg);if(ok)loadBookings();});}catch(Exception ignored){}}
    private Spinner partnerSpinner(){List<String> names=new ArrayList<>();names.add("Unassigned");for(int i=0;i<partnerCache.length();i++){JSONObject p=partnerCache.optJSONObject(i);if(p!=null)names.add(p.optString("name","Partner")+" · "+p.optString("status","inactive"));}Spinner s=spinner(names.toArray(new String[0]));return s;}

    private void loadPartners(){pageContent.addView(text("Partners",23,INK,true));Button create=button("+ Add partner",true);pageContent.addView(create,top(8));create.setOnClickListener(v->showCreatePartner());addLoading("Loading partners…");request("GET","/partners",null,token,false,(code,obj,rows,error)->{if(!active())return;pageContent.removeAllViews();pageContent.addView(text("Partners",23,INK,true));Button add=button("+ Add partner",true);pageContent.addView(add,top(8));add.setOnClickListener(v->showCreatePartner());if(error!=null||rows==null){showError(error);return;}partnerCache=rows;for(int i=0;i<rows.length();i++){JSONObject p=rows.optJSONObject(i);if(p!=null)partnerCard(p);}});}
    private void partnerCard(JSONObject p){MaterialCardView c=card();LinearLayout b=column();b.setPadding(dp(14),dp(13),dp(14),dp(13));c.addView(b);b.addView(text(p.optString("name","Partner"),17,INK,true));b.addView(text(p.optString("phone","")+" · "+p.optString("partnerId",""),12,MUTED,false),top(3));b.addView(text("Services: "+join(p.optJSONArray("category"))+"\nAreas: "+join(p.optJSONArray("area")),12,MUTED,false),top(5));Spinner verify=spinner(VERIFY_STATUSES);setSelection(verify,VERIFY_STATUSES,p.optString("verificationStatus","pending"));Spinner state=spinner(PARTNER_STATUSES);setSelection(state,PARTNER_STATUSES,p.optString("status","inactive"));b.addView(text("Verification",11,MUTED,true),top(7));b.addView(verify);b.addView(text("Portal status",11,MUTED,true),top(5));b.addView(state);Button save=button("Save partner status",true);b.addView(save,top(6));save.setOnClickListener(v->{try{JSONObject update=new JSONObject();update.put("verificationStatus",verify.getSelectedItem().toString());update.put("status",state.getSelectedItem().toString());patch("/partners/"+p.optString("_id"),update,(ok,msg)->{toast(msg);if(ok)loadPartners();});}catch(Exception ignored){}});if("verified".equals(p.optString("verificationStatus"))&&"active".equals(p.optString("status"))){Button access=button(p.optBoolean("portalEnabled")?"Reset partner portal password":"Enable partner portal",false);b.addView(access,top(5));access.setOnClickListener(v->showPortalPassword(p));}pageContent.addView(c,top(10));}

    private void showCreatePartner(){LinearLayout form=column();EditText name=field("Full name",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_WORDS),phone=field("Bangladesh phone number",InputType.TYPE_CLASS_PHONE),category=field("Service categories (comma separated)",InputType.TYPE_CLASS_TEXT),area=field("Service areas (comma separated)",InputType.TYPE_CLASS_TEXT);form.addView(name);form.addView(phone,top(6));form.addView(category,top(6));form.addView(area,top(6));new MaterialAlertDialogBuilder(this).setTitle("Add partner").setView(form).setNegativeButton("Cancel",null).setPositiveButton("Create",(d,w)->{try{JSONObject body=new JSONObject();body.put("name",name.getText().toString().trim());body.put("phone",phone.getText().toString().trim());body.put("category",split(category.getText().toString()));body.put("area",split(area.getText().toString()));request("POST","/partners",body,token,false,(code,obj,rows,error)->{if(!active())return;toast(error==null&&code<300?"Partner added. Verify and activate them before enabling portal access.":error==null?obj.optString("message","Could not add partner."):error);if(error==null&&code<300)loadPartners();});}catch(Exception e){toast("Check partner details.");}}).show();}
    private void showPortalPassword(JSONObject p){EditText input=field("At least 12 characters",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);new MaterialAlertDialogBuilder(this).setTitle("Set portal password").setView(input).setNegativeButton("Cancel",null).setPositiveButton("Save",(d,w)->{try{JSONObject body=new JSONObject();body.put("password",input.getText().toString());request("POST","/partners/"+p.optString("_id")+"/portal-access",body,token,false,(code,obj,rows,error)->{toast(error==null&&code<300?"Partner portal access enabled.":error==null?obj.optString("message","Could not enable access."):error);if(error==null&&code<300)loadPartners();});}catch(Exception ignored){}}).show();}

    private void loadServices(){pageContent.addView(text("Services",23,INK,true));addLoading("Loading services…");request("GET","/services?all=true",null,token,false,(code,obj,rows,error)->{if(!active())return;pageContent.removeAllViews();pageContent.addView(text("Services",23,INK,true));if(error!=null||rows==null){showError(error);return;}for(int i=0;i<rows.length();i++){JSONObject s=rows.optJSONObject(i);if(s!=null)serviceCard(s);}});}
    private void serviceCard(JSONObject s){MaterialCardView c=card();LinearLayout b=column();b.setPadding(dp(14),dp(13),dp(14),dp(13));c.addView(b);b.addView(text(s.optString("icon","🛠️")+"  "+s.optString("name","Service"),17,INK,true));b.addView(text(s.optString("category","")+"  ·  "+(s.isNull("startingPrice")?"Quote required":"Starting ৳"+s.optInt("startingPrice")),12,MUTED,false),top(4));android.widget.Switch active=new android.widget.Switch(this);active.setText("Available to customers");active.setChecked(s.optBoolean("active",true));b.addView(active,top(6));Button save=button("Save availability",false);b.addView(save,top(3));save.setOnClickListener(v->{try{JSONObject body=new JSONObject();body.put("active",active.isChecked());patch("/services/"+s.optString("_id"),body,(ok,msg)->{toast(msg);if(ok)loadServices();});}catch(Exception ignored){}});pageContent.addView(c,top(9));}

    private void loadJobs(){pageContent.addView(text("My assigned jobs",23,INK,true));Button refresh=button("Refresh jobs",false);pageContent.addView(refresh,top(8));addLoading("Loading assigned jobs…");refresh.setOnClickListener(v->loadJobs());request("GET","/partner/jobs",null,token,false,(code,obj,rows,error)->{if(!active())return;pageContent.removeAllViews();pageContent.addView(text("My assigned jobs",23,INK,true));Button again=button("Refresh jobs",false);pageContent.addView(again,top(8));again.setOnClickListener(v->loadJobs());if(error!=null||rows==null){showError(error);return;}if(rows.length()==0){pageContent.addView(text("No assigned jobs right now.",14,MUTED,false),top(16));return;}for(int i=0;i<rows.length();i++){JSONObject job=rows.optJSONObject(i);if(job!=null)jobCard(job);}});}
    private void jobCard(JSONObject job){MaterialCardView c=card();LinearLayout b=column();b.setPadding(dp(15),dp(14),dp(15),dp(14));c.addView(b);JSONObject s=job.optJSONObject("service"),date=job.optJSONObject("schedule"),customer=job.optJSONObject("customer");b.addView(text(s==null?"Service request":s.optString("name","Service"),18,INK,true));b.addView(text(job.optString("bookingId","")+" · "+label(job.optString("status","assigned")),12,GREEN,true),top(4));b.addView(text((customer==null?"Customer":customer.optString("name","Customer"))+" · "+(customer==null?"":customer.optString("phone","")),13,MUTED,false),top(7));b.addView(text((date==null?"":date.optString("date",""))+" · "+(date==null?"":date.optString("time",""))+"\n"+job.optString("address","")+"\n"+job.optString("area",""),13,INK,false),top(5));String current=job.optString("status");String next=nextPartnerStatus(current);if(next!=null){Button update=button(partnerAction(next),true);b.addView(update,top(9));update.setOnClickListener(v->{update.setEnabled(false);try{JSONObject body=new JSONObject();body.put("status",next);patch("/partner/jobs/"+job.optString("bookingId")+"/status",body,(ok,msg)->{toast(msg);if(ok)loadJobs();else update.setEnabled(true);});}catch(Exception e){update.setEnabled(true);}});}pageContent.addView(c,top(10));}
    private String nextPartnerStatus(String status){if("assigned".equals(status))return"on_the_way";if("on_the_way".equals(status))return"service_started";if("service_started".equals(status))return"completed";return null;}
    private String partnerAction(String status){if("on_the_way".equals(status))return"Mark partner on the way";if("service_started".equals(status))return"Start service";return"Complete service";}

    private interface SaveCallback{void done(boolean ok,String message);}
    private void patch(String path,JSONObject body,SaveCallback done){request("PATCH",path,body,token,true,(code,obj,arr,error)->{boolean ok=error==null&&code>=200&&code<300;done.done(ok,ok?"Saved.":error!=null?error:obj==null?"Request failed.":obj.optString("message","Request failed."));});}
    private interface Callback{void done(int code,JSONObject object,JSONArray array,String error);}
    private void request(String method,String path,JSONObject body,String auth,boolean override,Callback callback){new Thread(()->{int code=0;JSONObject object=null;JSONArray array=null;String error=null;HttpURLConnection connection=null;try{connection=(HttpURLConnection)new URL(BuildConfig.API_BASE_URL+path).openConnection();connection.setRequestMethod(override?"POST":method);connection.setConnectTimeout(20000);connection.setReadTimeout(75000);connection.setRequestProperty("Accept","application/json");if(override)connection.setRequestProperty("X-HTTP-Method-Override","PATCH");if(auth!=null&&!auth.isEmpty())connection.setRequestProperty("Authorization","Bearer "+auth);if(body!=null){connection.setRequestProperty("Content-Type","application/json; charset=utf-8");connection.setDoOutput(true);try(OutputStream out=connection.getOutputStream()){out.write(body.toString().getBytes(StandardCharsets.UTF_8));}}code=connection.getResponseCode();InputStream in=code>=200&&code<400?connection.getInputStream():connection.getErrorStream();if(in!=null){ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] buffer=new byte[4096];int n;while((n=in.read(buffer))!=-1)bytes.write(buffer,0,n);String raw=new String(bytes.toByteArray(),StandardCharsets.UTF_8);if(!raw.isEmpty()){if(raw.trim().startsWith("["))array=new JSONArray(raw);else object=new JSONObject(raw);}}}catch(Exception e){error=e instanceof java.net.SocketTimeoutException?"The server is taking longer than usual. Try again in a moment.":"Could not connect to Sucharu. Check internet and try again.";}finally{if(connection!=null)connection.disconnect();}int c=code;JSONObject o=object;JSONArray a=array;String err=error;MAIN.post(()->{if((c==401||c==403)&&!"admin".equals(role)){logout();toast("Session expired. Sign in again.");}else if((c==401||c==403)&&"admin".equals(role)){logout();toast("Admin session expired. Sign in again.");}callback.done(c,o,a,err);});}).start();}

    private boolean active(){return !isFinishing()&&token!=null&&!token.isEmpty();}
    private void logout(){token="";displayName="";prefs.edit().clear().apply();showLogin();}
    private void showError(String error){pageContent.addView(text(error==null?"Could not load this page.":error,14,Color.rgb(163,48,42),false),top(10));Button retry=button("Try again",true);retry.setOnClickListener(v->openCurrentTab());pageContent.addView(retry,top(8));}
    private void addLoading(String value){pageContent.addView(text(value,13,MUTED,false),top(12));}
    private void toast(String value){Toast.makeText(this,value,Toast.LENGTH_LONG).show();}
    private JSONObject json(String key,String value){JSONObject o=new JSONObject();try{o.put(key,value);}catch(Exception ignored){}return o;}
    private String label(String value){if(value==null)return"";String s=value.replace('_',' ');return s.isEmpty()?s:s.substring(0,1).toUpperCase(Locale.ROOT)+s.substring(1);}
    private String join(JSONArray values){if(values==null)return"Not set";StringBuilder b=new StringBuilder();for(int i=0;i<values.length();i++){if(i>0)b.append(", ");b.append(values.optString(i));}return b.toString();}
    private List<String> split(String value){List<String> values=new ArrayList<>();for(String part:value.split(",")){String v=part.trim();if(!v.isEmpty())values.add(v);}return values;}
    private Spinner spinner(String[] values){Spinner s=new Spinner(this);s.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,values));return s;}
    private void setSelection(Spinner s,String[] values,String value){for(int i=0;i<values.length;i++)if(values[i].equals(value)){s.setSelection(i);return;}}
    private EditText field(String hint,int inputType){EditText e=new EditText(this);e.setHint(hint);e.setInputType(inputType);e.setSingleLine(inputType!= (InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE));e.setTextColor(INK);e.setHintTextColor(MUTED);e.setTextSize(14);e.setPadding(dp(13),dp(9),dp(13),dp(9));GradientDrawable bg=new GradientDrawable();bg.setColor(Color.WHITE);bg.setCornerRadius(dp(11));bg.setStroke(dp(1),LINE);e.setBackground(bg);return e;}
    private Button button(String title,boolean primary){Button b=new Button(this);b.setText(title);b.setAllCaps(false);b.setTextSize(13);b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);b.setTextColor(primary?Color.WHITE:GREEN);b.setBackgroundTintList(ColorStateList.valueOf(primary?GREEN:Color.rgb(236,242,234)));return b;}
    private TextView text(String value,int size,int color,boolean bold){TextView v=new TextView(this);v.setText(value);v.setTextSize(size);v.setTextColor(color);if(bold)v.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return v;}
    private LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private MaterialCardView card(){MaterialCardView c=new MaterialCardView(this);c.setCardBackgroundColor(Color.WHITE);c.setRadius(dp(16));c.setCardElevation(0);c.setStrokeColor(LINE);c.setStrokeWidth(dp(1));return c;}
    private LinearLayout.LayoutParams top(int value){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(value);return p;}
    private int dp(float value){return (int)(value*getResources().getDisplayMetrics().density+0.5f);}
}
