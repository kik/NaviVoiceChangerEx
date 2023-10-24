package io.github.kik.navivoicechangerex.module.com.google.android.apps.maps;

import android.util.Base64;
import android.util.Pair;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class Mod implements IXposedHookLoadPackage {
    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        XposedBridge.log("Loaded app: " + lpparam.packageName);
        if (!lpparam.packageName.equals("com.google.android.apps.maps")) {
            return;
        }
        Pair<String, Integer> version = getPackageVersion(lpparam);
        if (version.second == 1067208629) { //11.100.0501
            patch(lpparam, "azrn", "azrm", "b", "azrr");
        } else if (version.second == 1067217575) { // 11.101.0102
            patch(lpparam, "azvx", "azvw", "b", "azwb");
        } else if (version.second >= 1067208629) {
            XposedBridge.log("unknown version installed");
        } else {
            XposedBridge.log("too old version installed");
        }
    }

    private static Pair<String, Integer> getPackageVersion(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        try {
            Class<?> parserCls = XposedHelpers.findClass("android.content.pm.PackageParser", lpparam.classLoader);
            Object parser = parserCls.newInstance();
            File apkPath = new File(lpparam.appInfo.sourceDir);
            Object pkg = XposedHelpers.callMethod(parser, "parsePackage", apkPath, 0);
            String versionName = (String) XposedHelpers.getObjectField(pkg, "mVersionName");
            int versionCode = XposedHelpers.getIntField(pkg, "mVersionCode");
            return new Pair<>(versionName, versionCode);
        } catch (Throwable e) {
            XposedBridge.log("failed to get package version");
            throw e;
        }
    }

    private static boolean synthesize(String text, String path) {
        XposedBridge.log("synthesize() called: " + text);

        HttpUrl baseUrl = HttpUrl.parse("http://192.168.39.5:50021");
        OkHttpClient client = new OkHttpClient();
        String speakerId = "26";

        Request r = new Request.Builder()
                .url(baseUrl.newBuilder()
                        .addPathSegment("audio_query")
                        .addQueryParameter("speaker", speakerId)
                        .addQueryParameter("text", text)
                        .build())
                .post(RequestBody.create("", null))
                .build();
        try (Response response = client.newCall(r).execute()) {
            String audio = response.body().string();

            Request r2 = new Request.Builder()
                    .url(baseUrl.newBuilder()
                            .addPathSegment("synthesis")
                            .addQueryParameter("speaker", speakerId)
                            .build())
                    .post(RequestBody.create(audio, MediaType.parse("application/json")))
                    .build();
            try (Response response2 = client.newCall(r2).execute()) {
                byte[] bs = response2.body().bytes();
                try (FileOutputStream os = new FileOutputStream(path)) {
                    os.write(bs);
                }
            }
        } catch (IOException ioe) {
            XposedBridge.log(ioe);
            return false;
        }
        return true;
    }

    private static class SynthesizeMethod extends XC_MethodReplacement {
        private final String className;
        private Field field;

        public SynthesizeMethod(String className) {
            this.className = className;
            this.field = null;
        }

        @Override
        protected synchronized Object replaceHookedMethod(MethodHookParam methodHookParam) throws Throwable {
            Object param = methodHookParam.args[0];
            if (field == null) {
                field = XposedHelpers.findField(param.getClass(), "a");
            }
            String text = (String)field.get(param);
            String path = (String)methodHookParam.args[1];
            return synthesize(text, path);
        }
    }

    private void patch(XC_LoadPackage.LoadPackageParam lpparam, String httpSynthesizeClassName, String rpcSynthesizeClassName, String methodName, String paramClassName) {
        XposedHelpers.findAndHookMethod(httpSynthesizeClassName, lpparam.classLoader, methodName, paramClassName, String.class, new SynthesizeMethod(httpSynthesizeClassName));
        XposedHelpers.findAndHookMethod(rpcSynthesizeClassName, lpparam.classLoader, methodName, paramClassName, String.class, new SynthesizeMethod(rpcSynthesizeClassName));
    }

    public static final String dummyWav =
            "UklGRiS2AABXQVZFZm10IBAAAAABAAEAwF0AAIC7AAACABAAZGF0YQC2AAD+//3//f////////8B"+
            "AAIAAQD+/wAAAgD+//7/AAD+//3//f/+////AQABAP7/AQD+//z//f/8/wAAAgAFAAIA/f8BAP7/"+
            "+/8BAAEAAAADAAEAAQAAAAEA/v////7///8AAP//AgABAAEAAQD//////P///wAAAQADAAEAAAD/"+
            "/wAA/////wIA/v////7//f8DAP//AAABAP7/AgAAAAAA/////wAA////////AQACAAIAAgAAAAAA"+
            "AQD//wIAAQD+/wIAAAD//////v/+//7/AAAAAAIAAAD//wAAAAAAAP//AQD//wEAAgAAAAAAAAD+"+
            "//3//v/9//3//f8AAP3//f/9//z//v/+//////8BAP7////9//3/AAAAAAAA///7////AAD//wMA"+
            "AgAEAAEA/v8AAPr//v8CAP7/BQABAPz/AAD6//3/AgABAAIAAQD8////AgD+///////+/wAAAgAC"+
            "AAIABAAAAP/////+/////P/9//7/AQABAAAAAQD///7/AQABAAAAAAD7//v//v/+//7/AAABAP//"+
            "/P/6//v/+v/8////AQAEAAEAAAAAAAIA//8AAAAAAgABAP7/AQD9//v/AQAAAP//AAAAAP//AQAC"+
            "AAEAAAAAAAEAAgAAAP7/AAABAP/////+//3//v///wAA/v///wEAAAABAAAA/////wAA///9//7/"+
            "AgACAP//AAD+//3//v8AAAAAAQAAAP7//v8AAP//AAAAAP7/AQABAP///////wEA/v///wAA/v/+"+
            "/////////wAAAAACAAAAAAAAAP7///8BAP//AAAAAAAA//8BAAMAAAABAAAA/P/9//7///8AAP//"+
            "AQAAAPr//P8BAAEAAQADAP///v/+//z//v/8//3/AQD+//7////+/////f/8////AAABAAEAAQAB"+
            "AAEAAgACAAIAAAAAAAEA//8BAAEAAgABAP///////wIAAgABAAEAAAD+////AQABAAAAAAAAAP7/"+
            "AAACAAIAAwACAAAAAAAAAP//AAD+//7/AAD+//7/AAAAAP///v/5//v/AQAAAAEAAQD8//3//P//"+
            "/wIAAAABAAAAAAD9//7/AAACAAIA/f///wAA/v8DAAAAAgACAPn/AAD8//j/DQAOAAUABAD1//L/"+
            "DQAOAAIAAQDz//j/AgACAAcABAACAP3/+v/7//7/AAD///z/+//+/wIAAQD//wAA///+//7/AQAC"+
            "AAMAAwAAAAEA/v8AAAEAAQABAP//BAAEAAQABAACAP7/AQACAAAAAQAAAAIAAgACAAIABAABAP//"+
            "AgD///////8CAAMA///9//3//v/+/wEAAQD///7//P/+/wAA/v8AAAAA/v/9//v//f///////v//"+
            "//7/AAD/////AAD+/wAA//8AAAAAAAAAAAAAAAD//wEA//8BAAMABAACAAIAAwACAAMAAQADAAMA"+
            "BQAFAAIAAAAAAAAA/f///wEA//////7//f/9//v//f/9//7///8AAP7///8BAP3/AAABAP7//f//"+
            "/wEA//8BAAAA/v////z//v////z/AAABAP//AAD/////AAD+/wEAAwAFAAUA//8AAAAA//8AAAEA"+
            "AAD//wEAAgABAAIAAAD+//7///8CAAEAAQABAP///////wEA//8BAAIAAAD+//z///8BAAAAAAD+"+
            "//z//v/+//3/AAADAAEAAQAAAP//AQD+/wAAAQAAAAEAAQABAAIAAAD+////AQAAAAAAAAD/////"+
            "/v8BAAAA//8CAP///v////7/AAABAP7////+//7/AAAAAAAAAAABAAAA/v/+////AAABAAAAAAD/"+
            "/wAAAAABAAIAAwACAAIAAgACAAIABAAGAAUABQAGAAMAAwAEAAIAAgACAAAA//8AAAAAAAAAAP7/"+
            "AQAAAAIAAgADAAMAAgAEAAIAAAACAAMAAgAFAAMAAgABAP////////7//f/+/wAAAQD////////+"+
            "/wAAAgACAAMABAAFAAMAAwADAP//AgABAP//AAAAAP//AAABAP/////+//3//f/7//r//f8BAP3/"+
            "/P/+//3/AAD+//7///8AAP///v////3////9/////v/9//3/+//+///////+/////f///wEA/v8B"+
            "AAAAAAD///7////9//7///8AAAAA//8AAP//AAABAAAA/f/8//7//v/+/wAA/P/8//7//v/+//3/"+
            "/v/9//3//P///////v8CAAAA/v////z//f/+//3///////7//f/7//7//v/+/////v/8////////"+
            "////AAAAAAAAAgABAP////////7//v////7//P/8//z//f/9//v//f////z/+f/8//3//f////7/"+
            "/P/9//7//f/8//z//v/9//v//P/9//v/+f/5//v//P/7//r/+//8//r//P////7//v/+/////v/8"+
            "////AAAAAAEAAAAAAP///f///////v/+//z//v/9//3//f/7//3//P/9//7//v/8/////v/9/wAA"+
            "//8AAP//AAABAAAA/v/+//7//f/+/////f/+/wAA/f/9///////9//7//v/6//v/+f/9//z//P/9"+
            "//n//P/8//z//f/+//7//P/7//3//v/+/wIAAAD//////v///wAA///+/wEAAAABAAIAAQAAAP//"+
            "AAAAAAAA//////7//v/9//3//P/7//z//P/8//3//P/9//3/+//9//3//v/+//3/+////wEA/v//"+
            "//////////3//f/+/wAAAAAAAAEA/v/+//7///8AAAAAAAD+/wEAAAD+////AAAAAAAAAgD+////"+
            "///+//7///8AAP//AAABAAEA//////////8BAAMAAgAAAAEAAQABAAAAAAAAAAAAAwACAAMAAgAC"+
            "AAEA//8BAAEABAD///7/AQD//wIAAgACAAIAAwADAAIAAQACAAQABAAEAAEA//8CAAIAAgAFAAMA"+
            "AQABAAIAAgADAAIAAgABAAEAAgAAAP//AAAAAAIAAwAAAAEAAQAAAAIAAAABAAEA//8BAAEAAQAC"+
            "AAEAAgABAAEAAgACAAQABAAFAAMAAwAEAAQABQAEAAQABQADAAMABQADAAIAAwADAAQABAAFAAMA"+
            "AgADAAIAAgACAAQABAADAAIAAgABAAMABAADAAMAAwADAAMAAgABAAIAAwAEAAMAAwAEAAIAAgAC"+
            "AAEAAwAEAAEAAQADAAEA//8CAAIAAwACAAEAAAABAAMAAQACAAIAAQACAAMAAwAFAAMABAAGAAMA"+
            "BgAEAAUABAACAAUABQAEAAUAAwADAAMAAAACAAIAAwACAAEAAAAAAAEA//////3//v8AAP//AAD+"+
            "//3//v8AAP//////////AAD//wAA//////7///8BAAAAAQABAAEAAAAAAAAAAQACAAAAAAAAAP//"+
            "AAABAAAAAQABAP////8AAP////8AAP//AQD///////8AAAEA/v/9//7/AAD//wAAAAD+////AAAA"+
            "AAAAAAD///7///8AAP7//f8AAAEA//8AAAAA/v///wAA/////////////wAAAAD///////////7/"+
            "////////AAD+///////+/wAA/f/+//7//v////3//v////7//v/+////AAD///3//v/9//7////+"+
            "/////v//////AAD//wAAAAD/////AAABAP//AAD///////8AAP////8DAAAAAQABAAEAAAD//wEA"+
            "AAABAAAA/////wAA////////////////AAD+///////+//7///8AAP///v8AAP///f/////////+"+
            "///////9//3//v/+/////////////f/+/////v///////f/+//////8AAP7//v///////////wAA"+
            "AAAAAP//////////AAAAAAAAAAD/////AAAAAAIAAQAAAAAAAAAAAAEAAwAAAAEAAQAAAAEA//8A"+
            "AAAA//8BAAAAAQABAAAA/////wAAAAAAAAAAAAD//////f///wEAAQD+//3///8AAAAAAAAAAP7/"+
            "/v8AAAEAAAD//wAAAAAAAAAAAAABAAEAAgACAAIAAgACAAMAAgACAAIAAwAEAAQAAgABAAMAAwAC"+
            "AAMAAgADAAMAAgACAAMAAgACAAEAAQACAAEAAQAAAAIAAgABAAAAAQADAAEAAgABAAAAAQAAAAIA"+
            "AwACAAIAAQAAAAEAAgAAAAEAAwABAAMAAwACAAIAAgADAAIAAgACAAIAAgACAAMAAwACAAIAAgAC"+
            "AAEAAwACAAEAAwABAAMAAwAAAAIAAgABAAIAAQABAAIAAgABAAAA//8BAAEAAgACAAAAAQAAAP//"+
            "AAABAAAAAAABAAAAAQABAAIAAAAAAAEAAQABAAIAAgAAAAEAAAAAAAAAAQACAAAAAgABAAIAAgAB"+
            "AAIAAgABAAIAAQABAAIAAQABAP//AAAAAAAAAQABAAEA//8AAAAA//////7//v8AAAAAAAAAAP//"+
            "///+///////+/wAA///+//7////////////+/////v/+//7//f/9//7/AQD///7//v////7/////"+
            "//////////////8AAP///v///wAA/////wAA///+///////+//7//v/9//7////9//7//f/+////"+
            "/f////7//f/9//3//v/+///////+//3//f/+/////v////7//v/+////AAD+//7//v/+////////"+
            "//7//////wAA///+//7//v/////////+//7//v/+//////8BAP//////////AAD//wAA///+/wAA"+
            "/////////v8AAAAA///+/wAA//////////////7//v///wAA//////////8AAP/////+//7////+"+
            "//////8AAP////8AAP////////////8AAAEAAAAAAAAAAAAAAAAAAAAAAAAAAQAAAAAAAAAAAAAA"+
            "//8AAP//AAD///7/AQAAAAAAAAD/////AAAAAP///v///wAA//////7//v8AAAAA//8AAP///v/+"+
            "/////////////v/+//7////+//7////+//7/////////AAD//////v///////v///wAAAAAAAAAA"+
            "AAD//wAAAAD//wAAAAAAAAAA//8AAAAAAAD/////AAD/////AAD///7//v/+//////////7//v/+"+
            "//7//v/+//7////+//7//v/+//3//v/+//7//v/+/////v/9//3//v8AAP///v////////////7/"+
            "//8AAP////8AAP////8AAP//AAAAAP////8AAAAAAAAAAAAA/v////////8AAP7/AAAAAP//AAD/"+
            "///////+//////////////////7//v/+//3//v/+//3//f/+//7//f/9//3//f/9//7////+//3/"+
            "/v////////////7//////wAAAAD/////AAAAAAAAAAABAAEAAAAAAAAAAQACAAAAAAABAAAAAAAA"+
            "AP//AAAAAAAA//8AAP////////3//v///////v///////v/9//3////9/////v/8//3//f/+////"+
            "/////////v///////f////////8BAAEAAAAAAAAAAQABAAAAAAAAAAEAAAABAAEAAAAAAAAAAAD+"+
            "/////v/9/wAA/v////7//f/9//v//f/9//v//f/8//z//P/6//r//P/8//z//P/6//v/+//8//z/"+
            "+//7//z//f/7//3//P/+//3//f////3///////7//v8AAP3//v/+//7//v/9/wAA/v////7//P//"+
            "/wAA///+//7//v////7//v/9//3//f/+//7//v////3///////3//P/8//z//f/+//7//P/7//z/"+
            "+//9//v/+//9//v/+v/6//r/+//8//r/+f/8//v/+//6//n/+f/7//v/+v/7//r/+f/6//r/+v/7"+
            "//r/+v/7//7//P/8//v/+v/9//z//f/9//z//f/9////AAD//////v8BAAEA/v8BAAAAAgACAAMA"+
            "AwACAAMAAQADAAMAAwAFAAUAAwACAAMAAgACAAIAAQD//wAAAAD//////v/8//n/+v/6//r/+P/2"+
            "//f/9//3//X/9v/3//X/9v/2//f/9f/3//b/9f/4//n/+//5//z/+v/7//3//f////z//v8AAAAA"+
            "AwAAAAEAAgABAAIAAQABAAAAAAAAAP7/AAD8//7//v/9//z/+//7//r/+//6//z/9//6//r/+//9"+
            "//r//f/7////AQADAAMAAwAEAAMACAAIAAoACQAKAA4ACQAOAA8ADgAPAA4ADgANAA4ADQAJAA0A"+
            "CQAIAAsABgAGAAIA//8CAAAAAQD8//n/+//4//f/9//y//T/9v/x//D/7//v//H/8P/z//D/8P/x"+
            "//L/9P/0//b/8//2//f/+P/6//v/+//8//3//v8AAP//AwADAAQAAgACAAYABgAIAAUABAAHAAcA"+
            "BwAIAAYABgAGAAUABwAEAAQABQAFAAQAAAABAAAABAADAP7//v/+//3////+//r/+v/5//r/+v/6"+
            "//j/+f/3//j/+P/1//X/9P/4//L/8//0//T/9v/y//b/+P/2//j/+P/3//n/+P/6//3//v/8//3/"+
            "///9/wIAAQADAAUAAAACAAQABQAJAAoABQAGAAgACAALAA0ACgAKAAwADQATABAADQARAA8AEwAT"+
            "ABUAFAAWABkAFwAaABoAHAAdABgAGgAdABoAGQAXABUAFAAUABMAEAAPAAcABwAHAAAA/v/6//j/"+
            "8//u/+z/5v/l/+P/3v/a/9j/2P/V/9L/0P/O/83/z//P/83/zf/P/87/0P/U/9n/2v/c/97/4//q"+
            "/+3/8f/z//r//f8DAAoADQASABIAGQAeACIAJwApAC8AMQA0ADUAOAA2ADsAPwA9AEAAOwA8ADoA"+
            "OAA2ADMAMgAwADAAKAAnACUAHgAeABsAFAAPAA0ACgAHAAQA/P/7//j/9P/y/+3/5//j/+P/3v/Y"+
            "/9X/1P/R/9H/yv/E/8T/wv/D/7//vv+//73/vf/A/7z/wP/E/8D/xP/F/8z/zf/R/9j/2//l/+v/"+
            "8v/0//3/BAAKABcAGwAhACUAKgAvADkAPQBAAEIAPgBFAEoATwBNAEsASwBKAE8ATABFAEcAQQA/"+
            "AD4AOwA5ADEAKwApACUAIQAaABUADwAHAAUA/P/3//H/5//g/9v/0//S/8v/yP++/7f/sP+m/7D/"+
            "pP+y/6v/sf+S/4n/sf/L//H/s/+u/6r/uv/a/+D/7f/r/+v/3f/w//D/BwAUABsAFwAVAAgADQA2"+
            "ABwAIQAiACgAMQAfAB8ADwAlADwAOAAvACcAJQAWACAANQA0ADQAJwAfACYAJwAvADMAJQAsAC4A"+
            "LQAqACcALwA0ADMAMAAtACYAIwAiACcAIgAgABcAEgAPAAcADQAIAAIA+//y/+7/6P/j/93/3v/W"+
            "/8r/y//A/7z/vv+4/7f/tP+w/6j/qP+o/67/rf+i/6r/rP+t/7P/sv+z/7j/vv++/8X/x//S/9r/"+
            "2v/f/+j/7//3/wIAAgAIABIAFwAfACoAKgAxADkAPgBHAEoASQBLAE4AVABXAFQAVQBPAE0ASQBI"+
            "AEUAQAA9ADQALgAqACUAHgAXABEADgAHAAQAAAD8//X/9P/0/+v/6P/l/+P/5P/j/+P/4P/X/9b/"+
            "1//e/9v/1P/Q/8n/0f/Q/87/zf/L/8//xv/C/8n/yv/F/9H/xv/D/83/yf/T/9X/2f/Z/+T/5//l"+
            "/+r/7//8////BwAJAAoACwAUACEAJQAqACoAKQAvADUAPABEAEcARABLAE0ASQBUAFUAVwBbAF0A"+
            "WQBeAF8AWwBiAF8AXgBdAFUASgBLAEEAQwBCADIAKgAcABIADwAKAPv/7//i/9X/z//D/7H/rv+h"+
            "/5P/i/+A/3z/d/9x/2j/Zf9a/2f/c/9T/2v/SP9U/47/d/+g/4X/wP+3/8T/vv9d/9f/2f/6//7/"+
            "uv/Q/+7/AQD7//b/1f/0//z/FAAKAOz/6v/f/wIABAAXABEAHQA4ACYAHAAeABcAKQBqAMUAzQB+"+
            "AEgAUACoANkA7ADoAOgA3wDLANkAzQD1AA0BBwHuAOEA3QC4AK8AnQC0AKQAkwCDAFMASQAdACIA"+
            "NAA3ACEA9v/K/6f/vP+3/8j/uf+T/4n/bv9T/0//cf9y/3L/WP8p/yP/Iv8x/y7/I/8Z/x3/GP8L"+
            "/x7/Gv8N/xj/H/8v/0f/Pv85/zv/S/9g/3L/g/+B/5D/lv+d/7L/u//K/9b/4f/u//T/+/8AAAUA"+
            "DgAeAC4ANQA8AEcARABGAFsAZgB3AIAAhgCQAJsAnACdAKsAtgDHANcA1wDaANgA1wDkAO0A9QD4"+
            "APIA7ADkANsA2gDbANAAxgC6AKcAngCPAH0AeQBoAFIATQAxABsAGQD9//f/7f/X/8b/uP+o/5T/"+
            "iv96/3f/bv9e/1H/P/8w/y7/Mv8v/yn/F/8L/wb/Bf8O/xT/FP8M/wf/Av8I/xf/I/8v/zf/O/89"+
            "/0T/TP9a/3X/hf+a/6P/nP+j/7D/wv/l//r/AAAKAAcACgAgAC4AQABWAFUAUwBUAFkAYQB4AI0A"+
            "iwCRAI4AiQCUAJkArQC8AMEAxADJANAA4QDsAPIA/gAAARMBFwEZAScBLwE4ASoBUQFGAUcBOAEf"+
            "AQsBCgFvAggC3gB0AN//dgBqADwA/P+2/7L/l/97/wL/Gf/y/vb+8f61/tn+cP4x/hT+T/6T/o3+"+
            "gv5N/mL+XP6D/nb+af6J/qP+/P74/g3/DP/2/hT/RP+D/4n/q//Q/93/5v/j//v/EgBDAGEAgACS"+
            "AG8AXgBoAJgAtgDAAJ8AjACmAMAAygCnAJkAmgCyAMYAyQDNALMAswDCANAA1wDMAMsAyQDeAPcA"+
            "CgEFAQ4BCAEhASsBSwGGAXgBHwKDAmkClgFcAbwBkQFqAUcAYgBoAAYAMQCC/y3/2f7U/v3+vP51"+
            "/vz9eP1X/XH9kv3H/bn9l/1g/UL9Wv2L/ef92v0T/mr+Y/6f/pX+tf4K/yH/Yv+y/w4APgAeAA4A"+
            "CgA8AJEA4QA0ATcB/gDAAJQApwDZAP0AEgEoATYBDwHKAG4AIwA4AHEAuwDhALYAeABAACIACgAW"+
            "ACwARgBpAGkAaABmAFwAcACKAJgAvQDAAMYA0gDAAP8AMgE3ASsBFQEWAdMAFgHCALUArgCE/xf/"+
            "hwFcBuQF/AH+/sD87P1q/1kAigEQAZIADgCv/Sv9hP3f/Gn+Lv7j/QD/+f4m/v785vyl+6X8B/63"+
            "/kMBkQHZADL//vwF/GH9OgBZAekC+gJ7AecA+f9s/3//owDcANYBpgK0AtICMAHs/8L+jP4c/0cA"+
            "gwFBAgACugBU/+X93f2F/m//CAAIAY8BxwDy/8P+s/48/+X/CAHYAboC+AJOAhUBIQAfAKQAzAGx"+
            "AgQD9ALqAZEAZ//b/pv+wv3N/TcA5wKbAtj/mvzC+sH6Jvy8/WL+JP+h/sr9oPze+338QfwB/az9"+
            "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"+
            "UwJQAqsCzQLrAloDZgOpA8wDtQOJA1YDcAMnAwQDvgIfAvUBeAESAd0AQwDA//7+Yv7M/Tz9uvwL"+
            "/IH7q/oW+nb53vhk+HP4D/le+bb5CPo4+3z8Pv7s/+YAPQL5AsgD6wTmBZEFGAWGBIgDogPOAtoB"+
            "sAHfAHcAYQDP/5X/OP/q/vv+Av+H/vX95/3N/fb9N/4O/v79Y/58/gj/tv/X/yUAUwCfAOAAOgFu"+
            "Aa4BNQIzApQC8wLwAmADgAPDAy8E4QOpA44DjgPEA4UDKAPTAl4CLgLZAXUBUQGlAPX/Lv9m/tb9"+
            "Ev1Y/H77oPp0+aT4w/fm9pj1tPX496z4ePkV+mL7T/6UAMYCiATYBGMFYQVCBZoG7wWZBF8DFQI5"+
            "AssBngCzAMAAPwBmAE4A8P/V/7X/C/9l///+Yf35/Df8gfzI/Xb9B/3w/OD8Ev76/8kA8QBBAVIB"+
            "+wEAA70DIQT4A1AD9QJvA2MDQwMfAxcDcwM9Aw4DUQO5A5kDsAOKA/AChwKaAdwAcwA2ACD/pf3V"+
            "/Or7f/sK+0T6lPmO+Mz3uPc++Af5aPlI+dv5OPvD/IP+q/+rAIcBSwJzAxwElQQRBcMEOwQPBKkD"+
            "XQNLA3oC/QHeASEBlAAaAOf/KwDk/xr/uP4v/s39I/7M/Yn95f2Z/Zj9MP5v/un+gv/m/3IA1wD5"+
            "AFgB1gE2AsgC4AKoAgADLANPA94D/wOeA58DjAOfA/EDsANWA9cCggI9Au0BjQH7AGEAa/+h/rf9"+
            "nvzt+wT7GvpN+W34afeR9i73Wfj++En5WPkG+l380f5rAI8B+AHoAqADqQTUBeEFvwXWBPgDwAOP"+
            "A/wCRgLBAU8B8ABaANr/ov+9//r/j//k/nH+4f3H/VD+Xf75/an97PwY/cX9a/44/0T/kP8HAHgA"+
            "PQEkArUCKQNnAzUDbgPcAwIEJAQBBLIDqgNZAzQDfQNxA1QDDwNgAjUCFAK9AUYBmgDc/8n+Av4L"+
            "/Vr8yfvs+sj5YPhB96P2LPcx+KL4Nvhm+B/5Xfs9/or/9gCpAUMCDgQrBQEG9gYMBvEEbATVA9MD"+
            "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"+
            "4PgL+Tv5PflM+e35sPru++/8eP1o/m//YQCSASICdQJFA2kDtAMUBNwD4gO+A0oDZANhAwoD1AJ+"+
            "AnICaAJJAvcBxgHUAbUBugGgAVIBFgHvAKEAkQBzAEYAMwANABcAIABMAH0AvgD6ACsBWwGCAb8B"+
            "/QEWAgYC9gGuAbEBkwFQATYBtwBUAMH/MP+4/jT+n/3e/Fb8qvsn+3r64vmh+Y75uPl2+Yf5qvlR"+
            "+qL7rPxN/c39lP6O/+wAogEWAnUCiwLoAgsDMANEAxMD4wKvAo8CoAJoAjUCLwIxAjcCOgIPAgAC"+
            "NwIxAhICrAFLAQ0BEwHcAG8ALwDA/8f/yv+y/+j/GgBUAKwA6wAeAYgBqAHCAQEC6gH3AfoBqgGe"+
            "AWoBDgHrAI8AIwDS/2j//P7D/jn+qP0y/YP88/uP+4T7hvt5+xv71vr/+lj7EvyI/Oz8X/2w/V7+"+
            "Gv+f/wsAWACGAMcAEgESAUMBVQFMAZABkAGQAawBwwEHAnQCeAJlAoQCewKlAqICcgIcAtsBogFW"+
            "AVcBEAHTAJYAYwBpAGsAZQBqAJAAtQDrAPEA9wAHAQcBAgH0AM4AqQCfAHYAggByAFoAeABWADkA"+
            "CgDH/5v/Vv8B/5b+Lf6t/Rv93Py9/LP8evwj/FL83PyG/fX9Ef5O/qv+2v4g/1b/W/+u/7P/eP+I"+
            "/3T/iP+x/6H/rv/W/wUANQBqAKIADAF2AaMBvAHSAfMBFwIwAhYC5wHKAZkBaAFqAUsBNgEeAeoA"+
            "9AD+ABMBEAEHARABDAEHAewA0QC5AJwAXQAWAOv/xf+8/77/u//I/+j/AAATABgABQD9/+7/zP+z"+
            "/37/Ef+0/oX+lP61/p/+V/5D/on+vP4O/yL/If96/2X/QP8i/wP///4B/8r+gf6A/kj+Xv57/nD+"+
            "yP4K/yP/b/+6//z/bACaAKYA3wD5ABwBKQESAQIB+gAIAfoACAEHAQMBDgEBARMBHwEoAR4BJAEk"+
            "ASgBKgHxANkAwwCyAJwAbABOAEcAVABTAFUAZgCGAJwAqADIAN8A6gDtANQAxwCvAF8AEwDt//X/"+
            "EAADAL7/mP+S/6D/wP+p/37/cP9Y/x3/2f58/kP+HP7k/an9av1J/S/9PP1Q/Xr9x/38/UH+b/63"+
            "/hb/Vv+K/6b/yv///y0AOgBFAF0AgQC4AOQA+gAgAUgBYwGKAZ8BqQGzAbABmgF+AVgBJgEUAQoB"+
            "/ADzANgAvAC+ANAA7gAVATcBWQF3AY8BmgGlAacBjwF6AVgBHwHZAG4ADwAGACkATgAzAOH/vf/P"+
            "/9X/wv+Q/1T/Vv8x/87+Xf7j/a/9mP1k/SD98fzZ/NX88/wc/WD9sf3z/SD+Xf6c/tz+Gv8q/zr/"+
            "cv+c/6//yP/R////VACQAMMA+AAuAV4BewGJAZgBswGzAaUBewE5AQ8B5wDVANcA1gDTAN4A5gDz"+
            "ABABQgGBAb8B8wEYAicCJAItAhIC7wHHAY0BSwH6AKIAcgCrAMIAsQBzAAwAEQAbAAoA7f+X/3T/"+
            "UP/t/l/+2v18/W79Zf37/M78lPya/NL86/w0/Xz9xv3x/R/+Nf5t/qD+x/74/hz/TP9a/3n/mf/g"+
            "/yIAYgCwAOQAKwFPAVkBZwF7AY0BmgGQAVcBLAHyAL8AtAChAL4A3ADmAOoA9AARAT0BigHGAQ0C"+
            "QQI+AjUCFQLnAckBnwF0AT0B6QCdAG8ApwDlANIAmABOADgATwBPABcA0v+x/3D/A/9v/sf9gv11"+
            "/V79I/3m/MD8yfz4/B39Zf2k/ej9Hf4s/lf+bf57/sL+0v4A/y7/J/9f/4P/0/8sAHoAwwD5AD0B"+
            "SgFpAWkBbAGYAY4BegFBAfYAxwCoAJ8AqAC7ANcA8AD8AP0AEAFEAYAB4AErAjsCIwL+AeABrQGJ"+
            "AVIBKQEEAb8AjQCPALcArgC0AJcAdQCCAFcANQDz/8D/lP8p/7H+Df6o/YH9Yf1Z/Tb9HP0o/Tz9"+
            "Xf2Q/cr9C/5H/mP+a/5v/nr+if6x/uv+E/9N/3D/k//I/wEAVACOANUACAEtATgBKQEuARsBLwE9"+
            "AR0B7gC+AJsAlAChAK4AtwDEAOYABwEhATsBYwGjAe0BFwIVAgwC/AHRAZUBXQFLASMB2wC5ALYA"+
            "6QAFAd8AowCVAKEAlABhAAsA5f+s/2T/0/4l/s/9nv2d/Xb9UP07/S/9Vv13/Yn9rf3u/Tr+Yf5T"+
            "/kP+O/5k/pT+tv7p/hH/S/+A/7P/8f8xAIIAvADyAA4BFAEiARsBHQEhASYBCwHzAN4AuwC6ALYA"+
            "xgDZAPEAEwEdATYBPQFWAaUBAQIuAg8C8wHhAc0BqgFqATkBGQH+ALgAewCVANEA8gDOAKAAgAB3"+
            "AHgAKgDW/6X/bf8T/4P+9f2h/YX9ev1g/UD9QP1P/W79lP2y/en9HP5S/l7+O/41/kX+a/6h/tX+"+
            "//46/3P/sP/f/x4AcgCvAAYBLAErASsBEAEBAfcA/QAAAesA5QDHALAAtAC4AOMA/gAgATUBLAFU"+
            "AWgBpQHfAe4BAQLyAfgB8wHUAaQBfQE9AQ8B2ACFAHsAmwDPANoA0gCgAHYAbwA+AP//pv9i/xf/"+
            "tv5T/t79jP2H/Yb9fv2E/XD9g/2h/c795v34/Sr+JP45/if+Lf5b/nn+x/7p/iH/X/+S/9P/AABG"+
            "AHUAqQDrAPgAAAH5AN0A4gDeAOIA2wDNANQAwADGAMkA3gD5ABkBNAE5AVsBWgF+AaMBwAHtAfYB"+
            "AwLsAdgBsAGJAXcBPwEHAcoAswC/AM8AzwCxAJYAfQBnADMAAgDH/3P/Q//e/nH+D/6z/aD9iP2Q"+
            "/Yb9cf2L/Y79sf3Y/e79Ef4n/jz+Mv4i/jn+V/6P/tT+Cv9G/33/t//2/yUAWwCbAMIA8AACAe0A"+
            "6gDZAOIA5wDqAOoA1gDkAOgA7wD9AAwBKQE6AUMBVwFeAZ0B0QHkARQC/QH6AeQBygG2AZABgQFX"+
            "AS4BAwH/AO0A2gDVALEAiQBxAFQACwDL/3f/LP/e/ov+Qf7T/an9lv2C/Yr9fv19/Yr9q/3H/d79"+
            "Bf4P/hj+If4n/iz+Pv51/p7+5v4g/1T/mf/Q/xgASwB+AKoAxwDjAOoA2QC/ALwAxwDSANwA3ADh"+
            "AOsA+AAKAQsBJAFFAUsBVgFeAX8BoAG0AcgB0gHxAfYB7wHhAcUBvAGnAYEBZQE2AQIB7gDfANMA"+
            "tgCKAEQABQDl/57/UP/0/qr+dP4f/ub9lv10/XT9bv2R/ZX9p/2i/br95P3t/RT+Hv4s/kb+WP5t"+
            "/pj+z/4Q/1b/if/M/wIAPABpAJQAuAC2AN8A2ADYANUAqwC0ALEAwwDWANgA6ADvAP4AHQEsASwB"+
            "QQFSAW4BhgGZAbYBygHkAfoB/QH8AfUB8AHUAaQBkAFQASoB8ADhAAMByACuAFYAJwD8/63/bP/u"+
            "/r3+d/48/u39if15/WH9bf2A/Yj9jf2d/af9uv3d/fH9C/4Z/jj+Qv5l/ob+sf7u/ij/gf+r//j/"+
            "LgBaAJIAmwDIAMoAwwDQAMEAxgC3AKsAvgDDAN8A2QDWAPYA9AAnAS0BNAFKATsBaQFlAZABvwHS"+
            "AQcCAgIeAgwCCQICAtYBxQF/AXABSgFGATQBCQHsAI0AbAAvAPz/vf9c/yb/1f6N/jz+0v2f/X/9"+
            "f/2Q/YH9l/2T/Z39mv2k/dH93P0Q/hD+Mf5I/kj+ef6C/tP+Af9M/6D/w/8aADEAZgB+AIEAmQCD"+
            "AJoAlACRAJYAlwCsAKgAsQCyAM4A5QD9ABsBIgEtASABLQE8AV4BlwG4AesBCwI6AlkCRgI5AjEC"+
            "JwITAugBsQGHAUwBMQEaAe4AxgB0AD0ADgC+/4D/GP/M/pT+OP75/ZH9b/1r/Wr9j/17/Yn9mv2f"+
            "/bb9v/3V/f/9FP4p/jn+Rf5n/oX+u/7r/i7/gP/J/wcAIwBZAGUAdwB5AG4AggB1AIQAhACbAK4A"+
            "swDIANQA5wD7ABoBIgE7ATkBRAFTAVwBkgGOAckB7wEfAlsCOgJRAjACJAIHAtoBzwGbAYQBWwFB"+
            "ARUB5ACrAFsALwDl/6b/Wf8M/9f+d/4x/tL9m/2C/Vz9gv10/ZD9qP2h/bv9tP3j/e/9Cv4p/iL+"+
            "Tf5R/nD+mv69/gT/PP+N/83/+v8hADYAVgBXAFcAWABVAFwAVwBeAHAAjgCoALUA0ADvAAQBJQE0"+
            "AUYBVgFPAWABZQF2AaQBvwH0AQ8COgJTAjkCQAIKAv0B3QG1AbEBbQFgATsBEQHgAJsAcQA3ABEA"+
            "zf+K/0X/Av/W/nr+K/7X/a/9sP2q/cT9uf3M/dP9wv3P/db9+P0G/hj+J/4w/kn+Vv53/p3+0/4O"+
            "/0L/e/+w/+X/BwAdADcAPQA0ADAALAA1ADsARABdAGsAfgCPAJsAxADRAPcAIwEsATcBKAFAAVAB"+
            "bQGqAeYBPwJOAmsCeQJcAlACGwIWAvQB5QHMAZcBkgFzAXcBRAEJAdkAkgBhABUAw/9x/zD/6P6m"+
            "/lj+G/4F/vD99P3w/fD94f3P/cf9tv2z/an9pv23/cT91P3j/QL+KP5Z/pH+u/78/jX/Yv+G/6P/"+
            "vv/I/9r/2P/i//b///8FAAUACwAaADIARQBbAHcAqQDUAO0ABgEcAUoBZAF4AZgBtwH5ATQCTgJ2"+
            "AqYCugKrApUCgwJ/AncCXgI+Ah0CBALQAaQBXwEgAekApQB6ADMA9f+o/2j/HP+y/mD+Gf72/eT9"+
            "z/22/Z79j/14/XT9dv1y/X39ef2J/ZP9nP2z/cn9/v04/nX+pv7X/g7/NP9V/2z/gP+Q/5r/rP+z"+
            "/7n/w//Q/+7/EQApAC4AQABfAIAAqwDMAPMAFQE6AWEBgAGhAcQBAQI1AmUCngLFAusC9QL1Au0C"+
            "0QLMAq8CjgJ9AmYCTwI0AgMCyAF+ATIB8ACdAFoACwDE/23/B/+k/lH+Ff7i/cr9pf2Q/W39UP05"+
            "/Sn9Iv0J/Qj9+/z7/A/9IP1B/Wv9pv3g/SL+Y/6U/s3+Av8w/1L/X/9Z/1b/Zv90/43/q//I//r/"+
            "KgBRAGoAewCaAMMA7QAQAScBOgFaAX4BpwHOAfwBOQJ4Ar8C+wIsA1EDYQNhA0wDNgMkAxADBAPp"+
            "AsUCqAJ8AkkCBwK+AXUBPgH6AJ8ATQDg/3v/F/+5/lb+9P2k/Vj9NP0N/fb86fzL/Lz8tfyr/JP8"+
            "ifyN/Iz8ovy6/NX8Cv1f/a397v0u/mP+nv7Y/gn/KP8y/z3/Tv9a/2b/fv+a/8v/AgAsAEgAXQCA"+
            "AJ8AwADjAP4AGgE8AWEBiAG+AfwBNAJ7ArUC9wIzA10DggPOAwwECwTDA3kDKAPuAt4CqwLGAugC"+
            "vwMyBDcEjgMmAi8BEwBE/xL+U/0F/Rv9b/1P/UP9K/0i/fr82PyH/Cv8Avzu+xP8dvzK/Bv9t/2J"+
            "/jb/qf/j/woAVwBzAGIACQDP/6T/ov/M/9T/EQA7AKUA4QC6AEkAjv8V/7f+c/4h/vL9G/5a/uj+"+
            "Wv+j//X/RgC/ACIBdgGOAZ4B8AFnAhcDmAM3BOAEUwWYBWUFLgUNBRwF8AR1BOoD8wLvATABYABr"+
            "/yj+xvx1+0/6N/k0+B/4jfhl+Rn64vmR+Y755/mm+n77NPwR/TH+SP8uACoBVgJ/A64EYQVlBegE"+
            "FgTkAyAEdARJBJsDFANhAqYBtwD//3D/wP75/dH8wvs1+9X6Ovt9/JX9T/5f/nP+zP6V/2QAFAEz"+
            "AiYDMQQ4BU0GoAf7CE4J2ghSCDMHRQZIBVkE0AP0AhwCGAH7/9b+7fx4+3P5R/c59ePyHfKi8v30"+
            "c/dE+fX5wPnX+Qj6+/pU+8f8n/5aAAkD8ARKB8kIYwlICaYIrgetBagEYwQPBTkFhgTaA+MCoQFE"+
            "/xf9YPvl+Tn5zfgd+Yr5p/kK+hv7lPwQ/Wn9d/1l/lsAAwLtAygGeQgOCv8KHAv8CpAKJgnbBzcH"+
            "tAZfBs0FpQRXAwwClf8f/WL7cPmr9zL1xPJM8Q/xPPF886r28/j5+bn4NPij+PP5Ovum/e8AtgNM"+
            "BvgHqAlFCrUJnwhwCK8HBwYZBeUELQYcBswEIwNOAU3/vPzn+nH52PgL+PH30vhf+YT5V/li+n37"+
            "7fy6/S3+KgDcAacDxAXAB70Jigu1Cy0LGgvBCcoIfggICM4HNge6BWMEQgPsAE7+4ft/+Zf3R/ZW"+
            "9KPy+fEF8WnxxvOq9mf4Ovmr+Cj4Kvmk+iD9rf9/ApEEaAbPB4kI/giJCEoIKggoCFIHhwYwBhAG"+
            "UAXRA9MB8v+h/sr8Uvs/+pL5JPk8+YP5nPl/+S753PlU+4/8QP0x/sb/3wH3A20FLQcYCQ0Khgq1"+
            "CoMKDApgCcwInwg+CBQHjgUUBGQCSwDO/Z37yflJ+LX2pfQb8+Xxj/H38Qb0tPbi9y746/d7+Iz5"+
            "rfvS/SUAIgOOBC0GawcPCDIIFwhRCJkI8wilB8EGlQbqBcYEXAPaAVkAxf7d/J/7xPoq+pL52/nu"+
            "+Y35V/km+Sv6V/tM/Ob8bv6CAFACgQT3BU0HkwhLCacJ3gmgCRUJGwlKCQEJJQjYBuEEZQNRAcj+"+
            "9vyt+uj4Nvfc9erzHPKN8bzxt/Mq9jT4Bvgr+Az4dPgj+/f8wP9QAp4EmQXKBsYHvwfzCAgJRwkk"+
            "CdgHcQYrBgkGKwUOBNIB0v9p/rn8hPsP+2D62vm0+ZP5T/kR+S/51/k7+w/86/yV/pUABAMYBbAG"+
            "yge0CEAJagnNCe8JvwnGCaAJ9wjxByMGGQQ0AmMAEP7T+8z5A/hN9lj06PJx8YrxfPJ29D735Pe3"+
            "94/3VPjb+dj83//1AaAEZgVHBqEH1wghCXQJ8wkoCfwIQQcmBm4GnwXKA8oBr/+a/TX89/qL+jr6"+
            "kvnT+KT4XPjv9zz4LvmZ+sH7Bf2s/ogBKgTyBWcHBAimCFEJXwqmCuMK/AotCtwJ9wgnB1wFmAOJ"+
            "Afj/+P3g+5P57Pc49u/zs/Li8HLwpPFz9P/2rPh8+Lb3KPlW+qf9mQBYA0gFlAZWCPcIUAqCCWcJ"+
            "jQkUCSsIlgYdBpYFJQUHA+4AoP5c/Mn6cfku+bD4Z/j297330PeT99P3TPno+hn88P0kAA8DpQUX"+
            "BzAIxQh2CU8KJwumC3kL7AoOCjYJJghtBoIEhwJ+ALn+Df1b+3r5t/cQ9cPy3/D373zwZvJj9tX4"+
            "tfml+Dn4cPkY/JX/wAJvBaQGgQhfCdwKAwuGCTQJZwgmCP0G4AWJBSIF0APwAPj+vfzd+mf5yveb"+
            "9yz3zPbe9o/3lPeA9434x/kW/N/9lP9WAkgFJgdLCHgJJgoHC0AL1wv8C2ILyQqWCawI5wbyBHgC"+
            "TgDz/uL80vtI+kj4n/bo873xAfCs75bv+/HD99b6ZPsT+2v6Tvvd/j8BcATdBqgHWwnVCtsMtQth"+
            "Cu4IrgdBBwsFkQSbBM0D7AF8/hD80vqG+ev3+vZy9uL1VPbG9vH3nvg9+AL5PPuA/WT/DgLXBMsH"+
            "DgmrCVcK9ArDC1ULmwslC5IK0wnYCCQImwXGAhAA0P2C/F/7Ivpx+c336/V787vwaO927mvvVfI6"+
            "+PH8Gf1R/X/9gP1fAMQDOQahCO0JfgrmC90NFwyoCaEIYgYxBVkDNgJVA3MC9P9Y/VT62fgG+JH2"+
            "Cfb29dr1BfaF9+34cvkE+mf6m/wx/zMBdARWB9gJRQtGC30LgAveC38L9ApgClQJyQh8B/4FwQNM"+
            "AAb9OvuG+vf5Evnj9yX29PN08dzuXO7V7UXvo/UQ/A8A0AB5/2n/xQANA4UFyAgECxILNwxFDboN"+
            "EAybCAcH2gSVAgYBZQC5AbcAav30+ff3Mfdm9XD0d/Qz9eT1tfb0+H36WPuo+8z8pv9lAnkERQdt"+
            "CwoN9wxyDAwMpgz7C9MK3wk9CQUIfgZLBfECBACd/Nj5Rfmp+Gz3yvbF9fLz5PDc7WntF+wA73L2"+
            "MP2iAokBmwDtAU4CzgSfB6IIGAtADCAMFQ5tDs8LKgiBBroDfQGB/zj+rAAbADX89fgt9y32kPRK"+
            "8+nzOvXN9kf43vlx+w78w/yF/nEBowPmBdUIYAw0D1MOdw3hDJsLLwsFCrQI5wfZBnEFWQO9AA7+"+
            "8vog+e73ePaB9Yf0NvMX8aLuae0w7BvuX/YT/uIBZwFhAKIBKwMBBvUIUgnxCmIM5QsTDsEOpQum"+
            "BysGTgRgAZ//M/5A/wn/5vrQ9/T2nfWO9MLz9fOQ9Q73XviU+sn8+fy8/bb/IwK/BF8HmQq0DXAP"+
            "Yw71DDEMRAuKCnkJWQiQB+AFoAOBAZ/+bvwG+rv3WfYm9Tn0zvP28o3wCO5Z7WfsVvBj/BkBCgFE"+
            "AhkBLgMRBgAHkQlXC8EKCAwWDvoNQw1PCvUFfwSrAt/+0P1d/wn/ofyl+Nv1OfZD9evziPSJ9Z/2"+
            "0vd8+VP8mv7F/tz+ZAHrBM4Gtwl8DUAPsA+dDZgLeguVCiQJkQgsCKsFVwIdAHr+xPyF+iv3KPV5"+
            "9PXzCfOh8Wfwc+1/6prthvcf/noAngC8/+wBiARbBkUIAQnRCRgMZgxlDoMP5gqYB9UGKATHAFP/"+
            "L//9/9P+0/ro99T2g/Vr9Ej0IfWb9r/33/j2+rb94/2U/WIAkQP+BfUIZgxWDkMPJw53Cw8LVgoN"+
            "CaUJ6AiLBrMDUAD7/sj9KfuO+Nz14PPK8/TyTfFm8BntQ+pD7w34Ev55/9r9Y/5f/0QDzAVzBgUK"+
            "qArzC90NFA4vDToLYQiCBp8FxAHx/p4AqgGL/1T8qfdz9Rv1mfQV9kr3OPjp90D4F/u5/GP9RP6+"+
            "//kCzQblCJQL+w5vDjINRgx0CdIIegkkCWsITAYGAyoA1/5z/W76kfcP9X7z8fK88TLx/e5h7Hfv"+
            "RfZw/Iv8Avv9+4z7twDCBS8GhgmXCuUJMAusDNcMEgvvCeMIkgZUBNgBVgGfAs4AQP3w+Rn3w/US"+
            "9mD39vfm+HH4Jfci+bj6jPuc/dz+vwGyBZ0H8AmJDH0Nuw1yDE4K+AjACIwJIAlHB0gEJwLb/8T8"+
            "QfvI+Bf2mfSz8qnx/u9j7U3wRvVh+dT67/ZZ9kf4w/v3AIcC6gWFCQkJQgoEDH8LlQo4CvwJCAma"+
            "Bw4F1wMDBooFMgGz/DH5tfdX9z34i/n4+SH5iPdc+H35hvle+in7df0GAhQFGwbQCTwNEA11DA8K"+
            "0AdGCN8Itwj7B9kG8gR/Ai3/oPvf+az3d/WZ9MjzCfGA70v0//dE+IL4S/QX89X4s/pF/Y0C5gUU"+
            "CEQIWAgJCaoJrAi6CGgKSgmtBiMGpgaAB1kGVAH9/P76JfoR+Uv6F/zQ+j/5D/j297z4jvhC+f77"+
            "1v5gATkEUwfgCZQL6wviCoIJVgggCBEI/QcBCBYGCQMvAJf9s/sj+dr2RPZP9O7xVfLY9EX4wPif"+
            "9gf0m/JE9mT5WvszARYFgwUnBk8H/we5B6cHkwjzCY0J6gfGB20I8giDB4gCF/89/Zv6kvrj+7j8"+
            "9PvA+Vv5I/ns9633ofhB+7X+CwFMAtIEOwgTCqUKTwo5CR0I0QcwCOcHbgZdBKIB8/5v/Tv88/kN"+
            "+Mn2Y/RR86r1WflL+Q/2z/QD9NPztfbp+XL92wFdBGUEEAW7B74HgwaYB2YIaQhsBwAHgAkACxsJ"+
            "jgWkATH+v/ud+4P8o/35/a/7pPqo+vH4CPjq+Cn71f1MANUBTgMgBhQILwmjCVcI7wZhBtsGDAeU"+
            "BW0DxAAx/zn9sPoo+sX3BvUc9Qb2ufjO+aH3l/aR9WP1vvZJ94v6uP9QAokDSAWMBnoGlQY4B04H"+
            "/AZRBkUGYQhuCkYKAQhMBFgBXv62+1H8YP12/fj8l/s++5/6qPlB+mL7Tf1e/x4BlAMEBkMI5wiv"+
            "B+YGzwX6BLAFGgXiA38CbwAn/pz7Ovpf+MD1efU49x/51vkP+Af3lvZD9mb3jPcg+Sf91P+uAfcE"+
            "3QboBi0HXQe9Bj4GxgU9BV0H2gkfCu4IlwY8A7//Cv02/AP9VP3q/KP8n/wL/Nb7M/yM/Mj9L/8y"+
            "AHQCgAVgB1UI3geFBo4FWQR7A88CNwFx/4z92Ps/+u34GPhM9pD3Vvot+jf5Qfcv9tz2mfeQ+YX7"+
            "ovz5/gQBZAIIBfoGUgfLB/cHvQZ2BWcFlQYtCLAIFQi8Bv4D+gCh/nL8+vso/Kv7tPsL/EX8mPwo"+
            "/XP+MwDjAGEBrgKXBJYGugfNBwoHFQb6BGIDaAHL/wj+o/t4+o35Kviz9/D30fix+Rf5uff99vL2"+
            "KPhb+UT6cvzN/rr//wC0A0UFRgZtB4QHgAfhBrIFBQZKByQIjAcWBhAF8gItAGP+kv0a/Sf8Y/uS"+
            "+1r8s/xc/dn+yv9gAIoAxgDaAlkFSQbJBv8GGwYTBdUD2AHm/2P9vPq++Y75N/kG+Un6rfuD+736"+
            "Tfiq9mr3ePeK+G77Kf3O/v4AbQI4BG4FMQU7BYgFPQXLBCIFHAZBB9wHRAdmBncEfAFp//D9Hv3l"+
            "/In8aPys/B392v2n/pf/KgAbADwABQGoApkELgYtB4UH+gaLBWIDuwDq/VH7gvn4+Nj4M/mP+rz7"+
            "mvxr/IH6iviT9wP3g/dU+Wr7Tv5dAV0DjwQ3BQ4FigQmBGwDLANTAxEEKQbbBygIhgeSBdYCZgA4"+
            "/or81fvm++37evyo/Z/+j/8yAE4AMAD+/xgAbQEoA8UEcgZSB5QHIQc5BVACcv+g/Dn60PiJ+P/4"+
            "lfrP/P39yf0o/Pz5fPjK9y73kfdw+TH8qv/JAsgE8wUzBvkEowOZAloB4QACAkwEzgZRCGIISwfc"+
            "BOgBC//S/J37C/sL+8z7FP1w/uv/NgHfAXQBZgDn/w8A4QBaAhcEwAVBB8sHuwagBIUBMP5c+0T5"+
            "Kvgn+Fz5v/vb/dz+tv4g/XH7Evr3+Ij4FPmI+hb9OAC1AvgEJAbLBe8ELQNBARUAhP/QAFMDVwXj"+
            "BisH9gUqBJ8B6v4c/an71Pr0+pr7Bf3k/pIAHQIKA/kCUwKoAXABsgFvAqED6QTQBRAG9wTIAkIA"+
            "av3c+jP5lPgO+ar6oPwR/rX+ov4l/jr9GvzV+h/6cfqH+1H9lP+1AUQDdwSyBIUEuAMFAu8AnwDr"+
            "AJMBugJjA5UDZANlAoEBUwAK/wf+Qf1+/Cj8kfyH/Uj/+QBpApQD+wPkA9cD9gMEBB4E6gNIAzkC"+
            "cwCJ/gX9HfyE+3X7Hfzl/PP9aP5G/kD+OP4v/tn9b/3P/LT8B/18/XT+Vv9eAGkBUQLuAvMCHwJB"+
            "Af8A0ADsAO8AvwChAIoAfgCHAJsAhABjABIAcv+n/t39uP1n/mX/pwCuAVAC/QKxA18E9ARUBQkF"+
            "3wMZAhEAJP7f/DH84Pv8+1r8Xv3O/t3/egDLALcASgCe/4r+nP1G/dP81fxf/dr9pv7Y/+8AxQGG"+
            "AkQCnQHzAAAAov9l/wT/zP6r/pP+0v5O/9j/ngAFAekAgQDk/2H/Wv/a/2oA8AAsAVwB8AGFAkkD"+
            "NwTDBNAEDwR9AuMAkf9g/pX9M/0J/WD9KP4K/9//bgDUAF8BggHvAAsABf8l/qb9UP3i/M78E/2u"+
            "/dz+6v96ALsAmQBbAG0AbgAaAI7/yP4Z/t79yf39/bf+T/8eANMA7gAJAQ0BLQGWAe4BwwFuAVIB"+
            "FwFMAcoBfgJiA7wDegOEAowBgwDU/1T/wf5n/vb9Rv7X/n3/w/8HAKgACwGLARsBRABr/5X+CP6K"+
            "/Ub9zPzY/HP9Tf5R/7T/wf/C/wMAcACQAEAAe//K/lL+Ff4v/mj+tv5Y//L/TQCSAKsACgHFAW4C"+
            "vQJ8Ah0C0gGWAZYBqQHjASYCXwI7Ao0ByQAzAMX/m/9R/9z+l/7R/oL/8f8hAD8AnwD2AC8B2QAM"+
            "AED/ff4l/tL9e/38/OH8Y/0R/rn+Bf8p/3P///9YAHkAPgCV//7+ov6D/lj+df67/gv/of8NAHoA"+
            "1wBYAQMCqALsAqYCaQIHAsgBlgFHAWUBjwHEAb8BQwHuAH4ATQA/ABYAy/+H/8X/CwAGAL//gf+i"+
            "/z0AkgBsAN//Ov/g/n/+QP65/SL96Pwi/dj9dv7l/h//Pv+h/xwARQAKAI3/Jf/i/rX+l/6g/tv+"+
            "Lf+a/x0ApQDXADgB+QF5As4CmgJLAhACwgF5AREBLwGgAS8CSALkATUBWgDN/5z/pf+B/1r/df/X"+
            "/xAA//+4/7v/KgCkAKEAIACy/xv/nf46/rz9TP0j/VX9tP1C/tz+If8+/2v/uP82AFQA5f9Y/wv/"+
            "v/5+/o3+zP4v/4v/9v9mANUABAFeAQoCbQKtAmgCOAIXAsEBeAEqAWIBtwENAh4CyQEVAT4Avf9Y"+
            "/0f/L//7/i7/xP9MAC4ABAAgAFwAywCiAAIAWf/t/nr+Av6r/VD9Yv2D/dn9XP7q/k3/Sv9Y/7H/"+
            "JQA5ANb/Qv/w/sP+kf6r/vb+WP+w/+f/ZwDKAPoAYAECAqIC+gLjAosCWQIDAowBIwEpAY4B6AHR"+
            "AXEB0wAmAJP/MP9J/zH/JP8+/8T/NAAvABAA/P+EAOcAywAzAJX/EP+B/gX+eP0q/TD9Y/2a/SX+"+
            "wP4z/0n/Q/+b/xsARwDK/0b/4f7F/on+kv7q/i//of+9/14A4AAiAY4BNQIMA0gDSwMAA6sCMAKX"+
            "AT0BUwGaAasBbwEJAY0Avf8Z//P+HP9w/5H/v/9WAGwAHgDL/+L/dQDFAI4A2/9C/9f+Yf7X/XT9"+
            "Vf16/ab93v1F/sP+HP85/1X/q/8TAAsAof8R/8z+mf6A/rX+Dv90/7j/JgC6AEIBcwHEAW8C6wI2"+
            "AwUDzQJ+AuEBWQEtAXkBsQGyAVEB8ABUALr/N/8n/6T/mv9f/5z/TAB5ADMAw//e/1kAiQAoAHD/"+
            "/P56/v/9jP1X/UP9c/2r/eP9df4A/0b/Rf98/93/GwDp/3n/Av+//sP+qv7v/mX/sP8DAGQA5gBj"+
            "AaABFALfAloDcQMiA84CKwJwAd8AzQBcAZwBvAEqAdwAXAC//5r/XP+y/3T/Zv/C/00AUQCz/5r/"+
            "+/+HACIAnf8S/6n+Qv53/S/9F/07/Vz9lP1B/t/+Qv89/17/y/8RAA0Af/86/xr/2f7H/tH+Ov+i"+
            "/9z/QwARAbUBJgJgAp0CUQODA14D9AJOArcBOwEEAS4BeQFAAdoASQAjAAYA0/+g/1z/g//S/20A"+
            "XgD4/4z/lP8LACYAxP/V/mH+D/63/U39Ef0q/UT9f/3n/Xr+CP9m/4v/1P8XAFkA9/+Q/1H/8f71"+
            "/sT+E/92/7j/FACNAFIB0wEdAlYCBgOWA5cDUQOmAvwBagEWARIBLQFVAe0ARADx/9L/3f+p/5j/"+
            "iP/W/5IAzgBpAL7/h/+m/xQAtv/B/i/+5P2//Sv99fwH/V39v/3g/XD++P6H/6P/rv8mAGsAcADk"+
            "/3f/Lv///uL+0P5K/5z/CgCfAC4B0gEfAmwC4gJwA5UDSgOlAucBfgHdAM0ABwEdAeQABQDi/+b/"+
            "/P/j/5j/mP/p/8MA0wBnAMH/df+g/7X/ef+2/lb+Gf7E/Uf94/zQ/Cz9kP37/aD+Lf+v/5b/sv8X"+
            "AIEAdgDd/4P/Ev///tn+5v5W/6D/PQDOAF4B+wFoAqQCDQONA48DKgNwArYBUgEFARMBRgH/AJEA"+
            "AQCb/7z/mP9a/2b/wf+eACEBFAFIAKn/s/+0/6H/6v5e/tf9hf1N/cD8wPzQ/EL9rv1S/k3/rf/U"+
            "/73/HABwAHsAKwB2/3L/Df+//rf+3/6m/yEA0QCAAT4CygLeAkADlQPDA3EDpALSAT4B9ACmAKoA"+
            "uQBYAOH/Zv95/9j/1f+9/93/tQA+AcgAGQC5/93/8f9k/23+wv2l/Xf95/ya/J785fxG/a39cf4+"+
            "/+3/6v/P/0cAvQC+ACQAdv/3/sX+wP7F/iT/4v+CABsB0AFvAvgCRwO0AzgELQS2A8oCwgEWAXMA"+
            "eABoABcA2f9a/4P/mf+T/zz/Ff8SAN8APgGMANj/tP+R/5f/2v5p/hD+0v16/aT8evx+/O/8Uf3Z"+
            "/eP+sP8lAPz/TgD6ADMB2ADE/yz/yv6G/mb+Zv5V/zYACAHqAdECogPYA/4DGARABPIDGgM6AkkB"+
            "qAAVALT/eP96/2j/Tv9u/yz/GP84/0UARAE4Ab0A5//s/+D/Vf+X/uv97f19/cj8H/z++4f88/yX"+
            "/Wf+af8jAE4AgwDDAEoBGAFIAFv/m/55/hb+Jv6i/nb/qQC0AcACWQPdA0kEbwSTBDcEqgP6AiIC"+
            "LwEdAKX/Sv8v/xr/9P5T/0L/QP9G/xwAdQGGAeoAHQAFAPv/bf9//sH9rP02/bj8Hvzx+z/8gfxy"+
            "/XH+i/8JAPb/RgCKAEIBOAGQAND/4P5t/rf9u/1W/iv/agB7AZACSQO/AwUEPgS0BL8EKAR+A8YC"+
            "wgGmANz/oP+e/3D/Lv8J/yn/K//j/lL/hwBwAU4BqwBSAFIAUQBx/z7+f/0Z/ZL8nftA+577B/yd"+
            "/I/9vP6v/y4APgBRAPwAgAEsAUoAhf/t/k/+y/23/WL+iv/+AFwCYQMKBHIEzgQvBWcFHwVvBLMD"+
            "zAJzAQAAxP4n/hH+Bv4D/gr+RP6Q/vr+3f/kAEUB9gCsAPEANQHwAK//Mf7//Lr77vpY+jz6tfqa"+
            "+w39tP7t/4sA3gBsAfcBQAIFAv0A0/+U/sL9Mf06/db9kP4RAK0BVgO3BMkFyQZFBzYHoAaUBW0E"+
            "XAOHAbH/f/62/Wn9A/3X/Ob8sfzo/Fb9jf5LAPQAPQEtARYBQgEoATwAFP+Y/fn7P/t8+qP6Mvv0"+
            "+2n9uf7G/3wABAGPAYgCJwPmApcB1f8C/qf88/sm/Oj85P33/4gBNwMJBVwGuQfZCOUI+wcNB4kF"+
            "8wPSAV3/5Py7+q/5w/md+gL73fo1+hX6UvtC/cT+BQDyAK0ByQJUA58CYQHs/0T+If7v/aD9/P1/"+
            "/lcAhgJQBF0FXgYjB5cHWAfdBIsB/f0P+5L52vim+Gr5APvK/XIBjARXB2kJowoDC/wJyQdWBX0C"+
            "jv/P/N/5KPcv9ebz+fKh8mjytfN89rP5Hf23/+0BiwQ7BzsIIAhcBrcDKwJwAAD/7v35/H/9aP9Z"+
            "AaYDjwWTBt0HeAi7B+cFmgKQ/hL71PeP9b/0ZfXl95T70v/+AwEITwsMDpIPWg+cDfoJtAVmAQv9"+
            "Qvj789Hw/+7L7qDv0fFS9YT5NP1JABEDWAZwCQ8LqgpoCK4FCgPcAJn+jvwc+6761PvA/QEAggIg"+
            "BVoH6QhuCQoI7gQPAfv8aPnY9kn1bfWX9+n6Gf/oA5UI4Aw9EKkRaRFdDwwLkAW5/9L5gvQP8Ivr"+
            "/+f85rHn1+p28Q35sv+cBCIHygrmD8wSUBOcEMYKLQXI//z6kvkk+Xn4U/my+sr8PwB8A3cGiQmK"+
            "CugHJwPK/az4tPV89D70qPUM+Kz7mAG3CHsPYBTCFX8UkRJMDzAKXQQq/S32gPHg7R/r0ulh6R/q"+
            "d+0q8yX6hADlAzME3QSuB/kL+g8xEOoMDAhBAtf+0v4//8z/+v7Y/Cr8yPxO/sIAEQNJBAgEzAEY"+
            "/g/61vdY+K76OP0h/wUBQgMFBzIMlRC5EXgPrwvtBzgFpAKn/gz70/fd81PxBvCk7vDtoe077azv"+
            "afV/+w4BEgTVBLoIEg5BEkkVUBKMC+YFNQHW/74Ag/5G+zj5GfhA+Z76nPzv/jAAYgFGAQv/L/zm"+
            "+ST6Uf3E/+UAtALTBMsHngw/EPwQMw+vCgUHhgSyAAz+dvvb97316POQ8sXxnPAa78PtXe5o8ef3"+
            "/v6fAn8F3QgHDRITPBboFLQQ6AjLAhYBSP+I/Kj46vT49Nv2lPhL+/f9NgD8AX4CeQE9/l37M/wg"+
            "/zEBMQLPAlwE8AeqC84OLhBhDRIJ6AV0AnX/Uf1E+136cvh+9cT0nfRH887xc+//68XrifEm+o4B"+
            "UAYHCmAPkhWeGCkX8REpCscC9v5z/VH6CfZk83v0OPi2+lf8tv4pALUAXwHVAIz+ffzk+zT+ZQHJ"+
            "AWkCBwbTCVYMDw5HDdQKnwcXBNoBXP9p/Bb7mfum+1X6s/g696j1//Kt72/rIekz7Y71K/9iBrYK"+
            "PxBPFlUaJRsBFkIMUQNm/SP6ffas8SvwQPMv97X6Af6FADkCrQJqAp4B1v6U+wP8zv6AAGcB5gNJ"+
            "CDcM/Q1BDXoLrwjiBEYCEQA3/GT5MfrF/ED+j/0//Fn7PPmU9L3uG+iz5Jjrmvas/lgFmQrCEL0Z"+
            "1h4tHZQWQAqY/yX7avde8nTuPe758b72TPv9AEMFKQZmBQ8ECgK4/sn7KvwM/iL+HP+vA8UJ9wyb"+
            "DOILGgruBu0DGwH9/Vj6rfgL+wT+3/5y//X//v7++0b2DO5v56/mlezK9Y78NwLvCjYUhRtOH8sc"+
            "3ROPCMj/ZPoW9Y/u+OpZ7XHytfaN/AIEFwjBB7EGvgX4Ann+CvuV+xX9lfx+/mwF3QpvDFUN5wxS"+
            "ClkGFAII/+f7nPcS91n7Y/8AAUICiwLP/8X68/IL6i/lS+fA71v5fP5bBF0NYhaeHtAgvhqXD3sD"+
            "Ivwx+cfyS+tj6l3uM/PD+FoABgjnCgAJpwZkBXUBk/zw++37P/qI+XH+RAfGDMoMeAuYC0wJQgV7"+
            "Au7+mPmH9sP4h/1zAIcBYwNZBDoBQPrw8Wbo4OKY6EbyYPhi/V8DmgyrGhgj2iAyGjINeQD8/LD4"+
            "Fu+B6Cno8Oz486H61ALxCmwNoAraCM8GCwGx+8z5Ffnf9cL2e/+ZCYoOug2WDFoMiwrCBuwC+PyT"+
            "9h712/ji/VEB9wMiBqUFhQCN91DsmuMC5NfsQ/a++OH5KQTEEV0dDyWsIb8UHQitAOv8svem7ZTm"+
            "jukq8Cj1rvw7Bh8MKgtcCGwGdgOA/n/6ovrO+O311vhBA94MOw8VDQYMuQsICJUFWwIe/MD2u/Wf"+
            "+vz/gQIaBZMHQwXE/hv1FOh34TjnUO5Q9ET5+/qGBt4XySF6JUUfsg+HA4sANf1+9FnqaOW46RDw"+
            "RPYOAawKoQz9CnwIlgfNA3D9nvsI+dn04vIp+uwGxg1LDZUL4QwMDKsKqwduAXj6+vQx9zv9o/+P"+
            "AXsFRQbgAwb9efL65uDfmuZI8Dv1V/i1/Y4L5xv0JZglHxvLDBoDl/8B/KHx8uWY5BLr7vBg+dwE"+
            "+QvQDCUItQduB5wB8/yX+U/3H/OE9ZUBtg1FEOQMnQzKC6YL2Ai4AxP9TvUO9CH5dv6yAWcFOwat"+
            "BKT/JvXj6PXhTOWJ7tj35/a8+GQJshiHIk8n9R4JDkAE0gC8/P/0NeiN4qHpJPD99bEAzAkRDQ0J"+
            "5geqB40Dlf76+SD55vQl9Sb/XQyHEcQNNAwfDN8MwgkzBUT+/PV68wr2ZfzaAO0DzQZ7BmoA0vdE"+
            "7PLe+uH57rjzaPeV/DwEFBfMJcQloSGAFPkCYv2U/dr0lucv4vLmdO999rf+hAm2D/AKYgiuBwQE"+
            "Qv/y+Vr5p/UM9X79KQu6EscP1wx/C5sMlgkmBML9ofU88oD0CfufAcoFQwfhB+sDfvl16o3efODA"+
            "6iX18PVT+FEJXhiVIjYq8STMEgMEJf55+qjywuXV32fmbu6r9LX/xgw/EioMAAlFCFUEUf+D+S34"+
            "yvSE9c7/Nw60FCwQywyxC34MXgjzAfL6jvJn8O7zSvtvArkG4Ag9Ca8CPfgT6X/aY+F17iDyFve2"+
            "/uQICBzjKbsnkiEPE+0A2fqi+VnvjuIJ4PPl1OwH9YsCyg9eFCYNQwrOCdkDvf5y+rn4rvPo9WIC"+
            "tA+yFIUQJw+oDPEJRAWP/p74QPJ975/y5/p9A+UI6AgnBvT9fe8v4uTfCulX8sb3tvpFBoEWQiEB"+
            "JykksRjBBrv6Xfch8GXnLuRb5vnq0+/1+x4OvxUcEn4KAAdDBTkAwfxB+aH38va0/hkLUhJEEwQP"+
            "fg5hCrYDSf5r+Xf2M/L18Jr3GAL0B2cIHQXm/l3x4+Io3hXjJPEI+WD9wAv1Fzse8yaTJ5cXxQcy"+
            "+4Lwfe0C6Ariw+Yl7hnyDvu9CrAVphM1DhQJDQTp/5X7QPv2+Rn6YP+7CnETPRJVD7MKOAgMA/r6"+
            "m/cc9ITyqvXA+nkCpAiPBx4D7flz7ULdxNk26q/0Gv32BEMLbBlLJQUpliTlFmMEFPaL7jroUOPk"+
            "4kbnye2Y8xb/oQ85FwYVoQ00B84Dm/9S/TP7VvqM+2UCZQzeEfIR8A5cDDQF/f0S+b/16/T38unz"+
            "2/oFBHoJhggpAXz2HOic3YPguOo29a78CgXFECMeaSknKkAioBOdACLzoOtU5Gbfg+Ht5nHtbPd8"+
            "BIcRjRiVFYAQNwwcBeb/Gv15+7r5R/tcAiML3BDfEHEOWQmhA5j9Fvk79mzydPEN9pL+qwWtB3MD"+
            "6vsP7+Lgkt3P5cPxG/ulAoMKnxYEJKoq8yiKHCMKlvni7g3oEuER3/rimOh58MX8KAt1FkQZXBV3"+
            "D0IJggNT/1n+qvwb/Hf/LQcyD+0QHA/ICgoFBf6A9y/0H/Jr8R70QvuxA9wH9gUV/5vyH+Sg28Lg"+
            "iOtx9Zb/5AbIE1cjmyv9LC4lrhPPALbyMef53r3cGd/I5CDt6PiWCMAWthu3GOUSaAxDBk8ANf3/"+
            "+/r7BP+XBRYNbBD2D0UMuQWl/Kn0sPGB8P3x4PSd+doBUgZ8BdT/BvQS59zhCubH7GX3fAHiBhsT"+
            "aR9TJDImFh5qDUj/O/Jx5vPhhuJU5UzrhPXrAK4L5BQPF5oUbhAjCioECQBX/6P/PAIOByIKsQx5"+
            "DHcIZALA+1X1g/GX8HjvJfMn+af+ogS3BGT/IvcT7krmpuX/7QX1w/8ODq4VjR6rJK0f7BhfDzP+"+
            "1O8H51riTeOq5/3ugfeSApYNfxPTFGsSvg0yCJYDeQBaAFAEugcsCcALIQ2ECxMG2v6I+cvy6u6o"+
            "70nwU/P/9yr8j/5Z/uL5IPFa64zpYe1B9wYBFgtaE1waoSGzIF4aKhJRAdfzP+3A5EPkFeqk71v4"+
            "tQJJCzMQGRJqEOYMNQhyAi3/HgAABY4ImAnCCwoMDgl7BL3+G/fP8DLvfu6q73fynPVG9232TvcU"+
            "9Anxq/Uk+l8Acgg3D5kTFxgkG1kVchDQCM77MfJj6zXpBOt+8E32D/wDBIQMJhBOD9oNrQnVBYID"+
            "dgMsBAEIOAwtCesHTAeQBH0ABvg/8rHtVOsc7V7vYfMu91f7ZPno9pz3uvQE+FL+7gQqDDIUXxq9"+
            "FyIXZxMdCsECWPZ67G7ouehl70/0bvt8BNUH3gy3D2kL6wgvBpgDkgOcBdIJEAvSCnIJOgZoAoH8"+
            "UvZR8Jjs++tr7P/uK/Jt9af4mvns+LT5L/7xA9QJMA8uEnEVvRXHE0sRqwja/7T3re376jXss+1X"+
            "80r6nQESCNgMaw42DUoLkAnjBqoF1wepCLQJjQgwBdYAB/ui9lvwMuyC6+Xqkez+7rnzOPcv+Sb+"+
            "WgDKAnQInQ1GDiMRzhRPEgoUSA/nBHX/JvYD8I7uFu0U77LzbPnuAIMGTwqJDvEM7gvYC40I/Abg"+
            "B9IJvgdkBSsBAftK9gXwAe0y64/qY+wb76rzx/Ya+g798QA7BZcIUQ2HEdkU4RX1Fr0SgAsOBnn8"+
            "zvVn8RTu+e5A8OLyu/eK/kkESQcECfgKswtIC7ML/ApjC0oK+AekBav/oPqL9fzvgeur6P7qau5w"+
            "8oX1NPjl/Bb+XwAtBWYHbArKD7USKhM3EnsPQQ0nCHoBAfu081nxWfCQ8PD00/hg/WACBgajCHUK"+
            "DAqGCFwITAivCHQHNAVnA5UAdP3C9+Pyse5m7MbtWu5S8V3z9fd1/Jf8vQCVA24HJQ3EEIgTTRO7"+
            "EOQOAwzPBUQBsfqH9T/0TvIo9MX1Q/h5/Hn/JgJtA98EIAWwBtwIPwrJDC0MCwo+B7kBlvtY9krx"+
            "k+5U7Qrt5e8z82D3iPoi/UD/5QDZA+gEZwgbDJENTxDjD5ENAAtXBg8CtP7O+mP4mva39b33svnw"+
            "+7b9rP9hAqwD9QWmBk0HkAlQChcLnAmCBnQCwfw+9yTxye1s7ejtGvHd8+T2UPql/Bn/Qf/XAbcD"+
            "7QQjCjEL5A5rEewNdA+dDKYFvAEG/D75PffU9Kv1h/VK+VP9nf6SAM0BSQTCBfYFewZBB8MILAnx"+
            "B7MFfQF1/Lj3DvQs8IrudvDa8sP1k/jr+gD8sv9gBGwF1ge9CkwLfAz7DHEKQgkOCMUCMwE4AAv7"+
            "Avrs+cf4ovkz+lX7p/zC/tABMgONBBcHrAkoCy4K5AcGBaEByv2N+fr2g/Ue8yTyR/TS9Cj3G/s3"+
            "/D//DgAk/zMAQQADA8QGUwjjCUoKDQtaCo4GAgaJA4r+RP7J+x767/pg+pr8kvz0/OH/ewCsAkwE"+
            "kQS9BZQGGQb8BBcEHQH9/1f/pv2a/Ar6Zfhb9bbz4/Xy9qX3s/mj/M/+rAFGBKQDKQP7BBwGSAa+"+
            "BUAFmQdTBw4FrQQGAoP/tP0k+xj5Cfks+gr8zP/MADQCxQXlBagFrQVOBPsDcgJZAXYBk/9f/+D/"+
            "M/3l+zn82fkR+RT5yPj++KT7G/0Y/RMBYgH1AdABEgE6AUwA3AIpAgABhQPiAwQDPwJ5AFIAP/9f"+
            "/ob/If9g/1YBGAJ5AfUBRQN1AggBVwMbA2wCYwObAK3/BAAG/wr+mP4c/3T+Gf+e/zv/Kv5+/lP+"+
            "CP0E/mD9vfxu/Xv9dv0c/d38NP2j/pz+z//+/jH/kwD0/gYCVgPQAsUCyANwBFgEhAQKAsEBfwDj"+
            "/y4Bqv+oAD4DlgH8Ad8BqP+BAIkAvP9y/9T/6v+vAPEALgCQ/8H/Ef/3+5T6ePvM+sH6QPzI+iL9"+
            "gv6c/Kf9hv1X/fb9L/5n/3oBjAMsB2wGzAbYBycGlARIAucAi/5LANn/uP+FAAcBigGW/7L/nf65"+
            "/tX92P7LAKcA0wHDAjQC9wHNAnsBKQAI/oP7Ivtl+hD6TPvZ+5P9U/2K/P38T/xD/ef80v3P/mQB"+
            "UgFkASkEtAP4Bb4E/gWZA4oCfAWEAMgAJQEIATwARwBFAPP+SABr//7/tv7u/70BqgD8AB8AYABP"+
            "/3YA4wC0/pX+w/4T/zz8Fvze+mD6efvs+tz97f2L/w8AIAB3AJ4AUwJjAUsC9AJ0A74CjwOeBWID"+
            "2gFIApgAqv7g/nH9KP60/xgBQAJRAJ8BbgIvAND+Wv72/HP98v0T/g0AFgGaAnECzwHm/zL/Mv4/"+
            "/U7+T/4/APD/RAHCAkkBFQFcAO7+Iv3G/PL9if6q/7QBHAPbAr0CoACs/Vr+EPwg/Bf9rP34/zYA"+
            "OQJ4AesAXgDS/hb+/v2O/rv+KAC6AYkC6gJJA5cClALtAYMBSQDyACAB3f/WAeIAFgErAEoAIgD3"+
            "/u3+wPz5/t/+0P7Q/3j+kv/VAKn/Av74/hD9Lvym/Qf9TP/b/wwAhAAr/xcBXADR/S//p/8U/wsB"+
            "NgI8AfsB8QGkAQsC/v+0AMcBYgFwAHQAhgL4AGEBOwIgAqwAEwBiAcb+2P4dAEL/Q/5q/0b/2/42"+
            "/5H/Nv9e/in/7P92AAv/CQG0/6r/gwBv/jMB4P+F/vL/QQCw/mD/zQAt/wkAs/9UANQATP23/wQC"+
            "q/6yAA0CiQCdAbX/xP8PADj/HwAYAbYAAgHxAcT/lQGgABwAmgCXAGwBbv7uADgAVv6YAZ0Alv9h"+
            "Afv/0P+KAVQBWAAnACX/U/8qAOz+nAAFAOL/Bf+p/k7/YP5j/6P/Gf/W/ZP+WgC6AaT+Fv8AAPX9"+
            "7P/K/5gAFgD0/xwACADdAHr/WgCqAZYBIACBAJ8CPwFyATwDNgD6/4cBIP/3/5AB2ADfAMIBAADB"+
            "AJwBo//pATwAD/4a/53/TP/K/2D/Bv9QAC//pADx/jMAPP+C/osBg/wMAAEAsP5hAPD9DwDsAMn/"+
            "Yv9D/y//pAEj/2r/KAHE/0wB6//sAXoBaf+7Af//SwBLAVcAPAGrAK8AFQAmAf3/pgC2Ab3+SAAE"+
            "/9//SQDD/0UCeAC2AGkBIQAeAMD+4QApAFL/YwFG/3sB0v7B/sQA//xMAEf/6P3JAJz+pv/C/57+"+
            "BQBlACABk/8IAHAAQAAGALT/VgEPADQADgIGANMA2AF9/u0ArwA6/xoBBgCIAD0ACgGiAPv/PgFX"+
            "ACsBGQAcAC8BY//yALYBWv81Ad8ATv70Agf/A/9lAov+eAD5/jf/w/9s/9H/4QAs/xsAq//z/IUB"+
            "Wv7x/x0BWf5zAKMACP9//+sB+f0y//EArP5bAIX/Tf+OANkBSv82AWoBlf/QAS3/EAJcAEIA8wGF"+
            "/1oB9gFfAHf/SgI7/5f/IQGh/9MAKv8GAYD/0QAyAL/+aAJU/vX/0/6bALz/f/6rATX9iAGj/gwA"+
            "HwCP/VYByv0mANP/Tf/f//D/RACO/pMBm//NAHsBJwAFAtH/6gFfAHj/hQEBAGL/vwLC/y7/hQE/"+
            "/pwBaf8G/18BTgBF/yYBHgBr/yoCe/0LAY0Abv5iARgAVAA1/70AVv+GAEcA8P1aAVD+cP/PAH3/"+
            "+v9iAJkAl/5cAV7/cv/XAPv+ZgEMAD3/WwLy/7H+mQJB/fsAvgHS/QgCxf/UAdf+vgGgAPP++wFY"+
            "/h8BBP5pANr+DP9vAIT//f9vANwAtv4eAl7+JwHn/k0AQwAu/uUBsv5sACn/OQD3/xf/BAHM/6j9"+
            "lAHY/7/+yACCAAUBEgBSACv/AAGd/w8BaAAXARsABACqAEn+zQHc/SwBdwDC/moBKv8WAV//CgAM"+
            "AHYADAAZ/94A6P0VAbz/R/7GAdr+Sf/X/1D/pf9YANb+nv/Q/93+qQG//9X/ugDz/wIAs/+4/wYA"+
            "uwA8/1ABRgAU/48B+/57AJYAKf8QAP8ACP/4/1QB2f1gAff+FgAkAbv+CwFs/+X/+P5CAKv+6v+j"+
            "/8X/bwBz/bMB7/15/yEA1v6U/6v/CABV/q8A7/2IATT/sP8KAab/kALd/aoBKAA/AGoAZv/aAdn+"+
            "4gDw/7b/ZQAdAOT/7//5AOj+MQBr/0T/ywBq/i0A6P8l/5j/sf+2/1T/kAAB/m0ADf8M/rEBKP7E"+
            "/5sAOv8HAP7/k/+L/2z/2f+RABf/YgAwAWIA6v/B/wwAAwBOACn/DgAyAHL/OgE3/3r/uwAG/9v/"+
            "CwAu/8z/rgCu/gkAwwAg/7QA0/9A//v/gv8+/34AkP48/3YALf4xABAAJf/i//L/L//s/wQABf8C"+
            "AAgAZ/8MAAMAF//l/w8AHgBN/73/AAAt/4X/GP9mAOb/TQAyAY7+mQAPAID+rgA//9P/pv+c/0f/"+
            "OQBlAJv/5QA8/wEB6f+SAAYAEv8nAL3+HwCo/5H/qv40AOH+af+5/7L+RwGd/sj/7/9E/+n/B/9B"+
            "ANX/0f6A/5z/WP8G//T/OwDW/4v/Jf/z/07/rv9t/3UAkv9fAJQA/v7KAbz+pwA7AJ//kQA8/60A"+
            "Tv4IAdD+UADL/27+tgDZ/qwA+/7LAED/TQDE/6X/QQDl/i4Bdv62AKT+xP/4/57+1wAM/2kAYf8d"+
            "AHD/p/+f/2P/Pf8M/zEBJf6SAEIAmv6qANL+bv9sAFr/2f9fAN//UP8tAKX/Kv+sAL//dACJ/yYA"+
            "1f8e/wEAMf+4/+f/3P+y/zUAz/5UADz/3f+kABn/WAGQ/jsA4v/c/hoBlP+U/+b/LABt/zIAo/8q"+
            "/6X/mf+2/0//KwAF/8L/X/8x/6z/EP9nAKH/Zv8xABL/k//hAEz/ZQBQAKn+oP+h//b/mP8y/3YA"+
            "vf8m/5b/LgDG/6r/zf+L/9sAq/9jAM8AEwBa/4n/ZgAw/1gAG/+2/8b/qf+RAP3+PwAm/6r/+//y"+
            "/9j/6f9pAMD+XAC3/0H/5P/h//f/j/+o/+D/KwB6/zsA/P+I/8z/QP+Q/6T+QQCe/4P/tQB7/rEA"+
            "1P89/+X/bv/S/1MAzf+g//D/BP9wAT//+v9nANn/RwCF/+cA8P75ADP/VgCYAJT+mwAU/wkAR//O"+
            "/6b/aQAmAMr/WgCN/0EAJf+WABv/kP8bAHL+wf9+/+T/3v+U/x8AXP8PAJT/Vf98ADr/OABx/83/"+
            "gADp/28ADAANAJb/JAAi/7X/mwB8/00A7f9eAEYA4f9bAJb/OgCc/4z/MADl/9P/DACa/wkA3P8B"+
            "AJAAf//h/4L/0/98/8P/nP/s/77/BQD1/53/4QAB/zwAW/8oAA8A0f82AH3/EwEe/7T/e/9eAMH/"+
            "9v8fAIf/QQDH/yEAUv8oAH//UgAAAOX/3P/s/+//t/8UAKn/KgCQ/0IA7/+wAM3/6f+P/zsAqwCU"+
            "/8QAgP/q/7L/y/+n/6AAeP+g/7YAv/6mAIr/vf+OAND/OgDb/zQA9/7n/yoAu/99ALP/6f+L/8D/"+
            "EQD4//j/JwA4AOr/QwCm/yMA7f9r/2MAaP/O/xUAlf/z//f/NwDw/5YArP9ZABUAUP/9AKr/4v8N"+
            "AJIARAAdABAAFABOAPH+QgC9/+z/5P8MAFoAOwA3AMr/hgCR/+//2v/x/xgAEADz/7r/eABT/3YA"+
            "xv+M/+8AW/9vAG7/ZADF/8L/eQBC/3UABf/eAMj/DADq/1YAUwBf/0QBo/6sAO3/2v/GANP/gAB9"+
            "/2sAj/8MAEMArf91APf/QgB9AOX/CQAdACb/ggCG/5z/xACY/1cApP+rAMT/GwDJAGD//v+d/1IA"+
            "hP8vAC8A+f8eAPn/RwB1/xsBUP8qAEsAZP8sAK7//v/B/4AAdv+TAAoADQAsAOb/ggAvAAoASgAw"+
            "AHT/LQAJAIQAIP88AFn/DwAMAbz+JgF3AM7/XwAnAEMAu/8sAFYA9v/wAMb/vf81AMn/CQB2/58A"+
            "zv/J/y4A/v9RAAsAHQBA/20Acf+3/z0AewCFAEL/yABH/xkBdf+MAC8Amf8LAXr+PwHG/iMBAQCO"+
            "AHIAXP/sANP+5QB3/7EAoP+RACMA4v8dAJ3/hgDy/p0Awv+FAEQApf9fABoAuv9FAKMASf+iAPH/"+
            "+v9GAAIAagA1AAAA6v8vAeD+WgDX/9D/5QCE/y0BKv93AAUAZv98AIT/OwDO/3z/cADf/4wAJgCo"+
            "/w4BwP8fACEAUQAYANT/lwCx/zIAkQBM/2QAUQCr/+H/7QDV/6L/fgGn/pcAGgDE/6oAp/+MAJMA"+
            "yP/U/94AaP/mAEr/QgAMAe7+BwHVAAsAFAAWAMn/NQDk/zUA6/9XANT/XADL/2//CQGx/gYBgv+5"+
            "/zUBGP/iAOH/2v+XAN7/qv8pALL/YgCVAP7/2//b/9wARv8AAVAA6P/3/2UAugB8/kcBc/9eAHoA"+
            "SABbALcAw//1/ykBQv8TAZH+4QG//rgAwQDv/vUBoP7tAXz+SgDb/+//hACi/rAB3P6eAMv/rACv"+
            "ABj/dQFl/hcBXwBo/5AARf9sAKIAVQBYAAMBef5EAUn/oQAHAUT/HwKW/mgAvv8gAKX/TQDr/5j/"+
            "ugDw/jABaf9kAfL/agBXAcv9bQLy/joANgHY/qUBVf4wAdr/e//KADj/+ABc/8wAHwD+/2cAj/+I"+
            "ANb/hgCA/0cA//9iALAAof/vAGf+WwEs/wcAlQGK/pQBzP7jAOf/0f/WAHX/9gAjAIAAnv/AAMb/"+
            "UAC3/yMAhwDF/0MB5v9IAOT/WQBb/6kAgACe/w8BIwCa/8QAUv93/z8Bx/4xAcj/vv8iANT/dQC4"+
            "//sAZv9sADMAQwCX/z0ALwCg/+UArf7pAO3/MABsAPH+0AFX/20A3QCH/1YAUABPAIr/nQAeABkA"+
            "PABRAM//RgDDANb/kwDY/8f/HwD6/1AArf9PAGsAtv+yAK3+2gDG/2r/6ABN/8IBiP7iAK//+P9d"+
            "Ad3+1wB0//kANv+dAA4A7/+mADv/iABw/98Ac/+/APn/7f92AC4A4gB6/moBkP/2/08BIf/IALr/"+
            "/P8TAOj/UAAKAHYACQBsAEEACQB2AIb/6/8aAMT/jgBp/4oAFwCV/6sAAgCQAIf/hABlABsATwBm"+
            "/x0AFwAHAOn/vwANACUAJACk/20A0/+wAKj/5f+AAHz/9f/c/7kAx/88AEoA/P8BAE8AZwB5/6AA"+
            "NP85AAAAvP81AL7/igDt/xkAkQBDAG3/rwAcAG7/PgCZAMf/NQBOAGr/PwBt/w8Ax//f/4YAaf95"+
            "AB4AVgDZAFIA0//m/zAAMf96AW7/q/8+AZT+ZQAXAEcAzgCV//3/1P+q/9T/IAAeAFcAv//e/8EA"+
            "sv/aABUAlABXAPv/3wBe//3/hv8NAHr/TwCGANT/DQCi/2cAvP/Q/+v/WgBMAOb/ZACQ/zcAEgDj"+
            "/04AWf9oACEATAADAaX/RgAsACgAyQCf//sAWAA9AAAAWgD//93/sADd/kgBff9PAOv/2P+SAG7/"+
            "7gBb/1cAAgCTACr/AwAiAID/uABu/oMAdf8jADsA4f4JAWb/RQCEAIL/RgCN/+z/tP+2/+b/x/+S"+
            "ABEAwv/p/3AAOQBDADoA2f/V/wgAUgCs//sAzP8jAPoAs/7+APT+5/8UAI7/xwGS/rEB5P52AOgA"+
            "9f6aACT/rwCk/tgB1f4SAn8ASv8YAXv9TgLP/TcBRv/A/0MAhf+0AYb9GwJQ/hYATwC3//wAoP6b"+
            "AJj+UgFE/g0AmwE6/oABRP+RACgAWwAiAR7/cQFe/+P/KQDr/rkAg/9aAKUAFP/0APX/s/+NAQb/"+
            "DgAqACoAjf/JABQAGACyABT/7gE4/p4BUP85/6kAQv6KAAz/DQGN/98AiP9EALP/3f8aAbz/+ACw"+
            "/6oAlv5SAQUAKf+mAQ3/6/9TACYAx/+2/9MAvP69/8sAS/7tAML+2f8/AEH+jwGj/pIAFgDx/iUA"+
            "O/5/AKH/0QEo/rsAdAA0/nYCNP2cAYn/Af9jACT/4gE2AEYAcAF9/z4AkQFw/b0AvQD0/X4BGf8q"+
            "/44BMP5yAJkA5/5bAbr/kABUAKz+cQAYACn/SQDAABf+sgA6/5n/OAAi/o8AMwANALT+gAH4/iMA"+
            "awHs/XD/Sv+B/+3/vf/a//H+iwJe/hEA9gK2/d4C5f4SAnH+RgLOAKn+BQMT/wIEHv2MAj3/qwGU"+
            "/1wAPgA7/n4C6Ps0AQ/9zwJU/IEAmACI+sUDAvzl/nn+p/2D/3P+2/+O/msBUv7cAMwAf/1TAwv/"+
            "jgEAAWsAjgGiAeIBTwLSAgICdgN1/iUDqwDC/20Bxv6ZAb3/WgBj/9z9QgA6/oD/G/5I/u7/X/yf"+
            "AJX9mf5U/tz97v31/Gz/E/15AGP+Rf/k/5f/XwDF/z8Bj/9AAvD+dQHPBOT+DQbkAMwBKAZ6/gAG"+
            "MQOfAAED+ADw/3j/qP+n/vv9tv2t/vn9JP4C/0j/1/4//+D9Af4b/tv9nP7A/d/9vf+O/eT/xgDy"+
            "/bcA4v0//8X+Wv8nAHD/0gEhAZEBPAMqAyMDCQR8AzMEKAHHA/gDEwHgAwsBagCaAYv9mP3a/Bn8"+
            "of2V/dj+Qf5zAP/+Ov7C/uT8Sfyg/JH8OfxV/nP+wf8iAO8AcwFh//X+/v58/hj/b//0/6UBFATT"+
            "BAIF+QSMA1YEbwJxApwCmQJ9A9UCawMqAzYBmv8n/gf78/oc+hT6DvwF/Zf9N/7e/pf9yf0o/q/6"+
            "Nfv2+zH7Uf4H/3MB1QK1AtQCjwFdAV3/IP/D/6D/SwLJA90EYAb+BsEFLgQgA8YBgAIxAgICBgLb"+
            "AicDlwG0/0798vpT+fn4+fju+aT7bP3l/U7+9/0y/KT6m/rk+SP6ivyI/ncB1gNUBbQFdwRCAxAB"+
            "0P/b/1z/ZwIcBE0FvQduB1oH2QXGAzQCOQFQAiMCmAKTA1kCRwJy//f7svl49jP2IPdJ+GX6Avu1"+
            "/Dv+Ov26/Cb73Pns+Az6K/zQ/U8CMQYMBxMI4wbKBLsD9AD4/7AApwISBFMGmwgXCUUILwY6AxUB"+
            "NgGjAFUBlAFGApoDEgIg/zv7bPd69Uz0zfT09nX51fqn/PP+3fwo/Fr75fhV+YP64/vk/ikEcQfF"+
            "CO0JBwj4BRMFAwKXAJQBtwIOBUMHFAklCbMHXgV/As0ASABQAT8BoQDMAegAuf9e/FT33fVs85Lz"+
            "xPbv+BT7ZPzq/Hv8fvsl+ov4Yfk0+qr6Wf84BBYIBAstCygKigfJBOUCLQEVARkCJAVqCLoJQgqY"+
            "CegGhALv/rD9HP+b/8f/7wAMAZ4Bxf4u+Xn2b/Le8F30J/YU+RT8tPxr/Pj8o/y4+W/5Pvps+tH8"+
            "FwF4B8cM9wxQDBgK7QboBCIC/AB0AXAD/AbtCE4JWAoRCNMDmP7t+9T9K/6M/9X/9/8gAr//5vsp"+
            "+FLzB/Ic8gX1APjo+v7+hf1s/WP9LPpq+eL5V/o7+1j/PQXWCdcNmw1WCzMJUwW2ArEATwAnAkYE"+
            "kQfUCVkKaQnmBTIA3Puy+mP7A/+JAHsBBQRuAjYBRfz+9Z/zm+8p8bj0WPhG/8P/av7T/yP+r/ps"+
            "+b/4B/gY/NH+oQI6DBQQMA5BDEUI0ANAAUD+r/0pAIEDpAc0CwkMKgtPB0UAtvqC9074N/yh/0AC"+
            "1QWkB6YF0wFq+uHzvfGZ7YPts/V3/BEANgOGApL/Rv/s+8X14PVF+H35O/4RBswMDhFcEGAL7gcs"+
            "A4/+9vxG/HD/awSfCKQMRg1gC+UGrf8W+ZD1mvZ++2gBSQT8BsQJwgaWAWz7SPOf7lXs5Ozh79b4"+
            "4gN8BD8CXwLKADP7Cvd/9nv20fkA/3EFEwweFGIUdAxxCWQDq/1//Rf8W/43BJsHVgsxDtoKLQV7"+
            "/tr1QvS59jT7NgVpBwkGhwcQA5j+gfc17p7tauv16134/AJKA08EZAYT/4/7CfxR9YP0kvvY/mgC"+
            "Xw0QFUwS1A6GCLQBVf90/Jn8+f6vAsoJeAwdDK0K+AOr/Fb3QfMi9cz/SwYpB4YGhwM/AxT8lvE7"+
            "7ijqKepV9AT+agCYA98FUwCS/Zj9bvf/9BL78v7iAP4KyBSqE5oPJgq0AxQAtP1g/HT+ZALbB5gM"+
            "OgtoCf4Ed/vS9yfy1/HS/usFOQmoBikD2ARQ/azzuO3/6i3sdu9u/AMCTQPsCFwByP1Q/zb4zvRe"+
            "+ksA8AHKCVwTBRMkEBUKowKZ/tf8qfwt/c4CxAfRCKML6ggUAgD+2PcF8vzyIfqPBb0MSgliBdcD"+
            "J/+Y9p/tAeuZ7aHxhfuOAsoF6Qf/AIz8gP0L+QL0m/jh/5EBQAiyEUYSCxBNCbz/Yvyp+z784P9x"+
            "BMYIewsECyoJNAM4+5n3I/XX8w76zwb8DoENtAYSAoX/pfjI797qPO0V8b/39/+LBLAFnwAM+o/5"+
            "wPrO9FX1/P7uAogHsg6rEJsQOA1pA1j7mfyX/1UBtAX5BpoIQw2BC6wD4/x997/0OPTq9aQBHA48"+
            "D/sKewSg/z/9JvM16urpNeyq9RT8IgFDCOME0vyj+fP6h/Up8x/9igC1BDQO4w6sEKQSNwjn/PT7"+
            "Zf5tAVEEeQUaCDELCguMBXn9u/jO9p7zT/VS/owI2w+qDv8G8gCu/s/3Gu1y6Uvr1vH3+JL+lgeU"+
            "Cvv+zPgf+xP1HfUG+4z8xgPGC6gNpBBGFLYNYQCZ+yH9if6TATgDEQVuCkQLnAbQAYr4p/Pa9UP1"+
            "f/ouA2sLzBObDWICFgGB/Pn1su2I6DfztfUo9voAxgh7B0T9aPqY+KP2o/uu9zz52gkADq0J3w/I"+
            "EmYJvABB+in58wA5BYEEDwW5By8KTwd+/ar1BvXB9935AfplAisPAxO0DOkCQQCY/Rn11Ow068zz"+
            "tPdC+LEE4AZBAD7/2/av9cT78PeJ9rQA+gojCrIL5xF8DTEGrf81+vv8vgJdAicEiQj8BsQGagNz"+
            "/Bz3MfRb+Kz8jf6eBJ4OYBLFCywDf/+v/QX4dO977b3zBfSo+E4BOQMBBRL9xfHD9R79XPan9rAD"+
            "pgeUCzEOvgpwDbwL1ABk+pn90APoBtkFkwN4BKoGfgW5/B71fvXO+eb8Q/1BBCYNuA+RC/MCEP9v"+
            "AC/75e+Y7HLw1PbG+HL5SwO2CZn+NfV6+pX3xvbc/v/8hAI4DuUM4AuGEBEL5P/d/PT9YADzAmUE"+
            "jgQ6BA8FbQO3/mf3DfUL+Wj7zP0qAgwKWRGjD30FqQDeAWT72vGn7cjwo/WF80H4pgoBCiX7mfjZ"+
            "9a738/2++Mz2DwYHEAMIlApJFOwMqQLD/Cj7ogETBUcB6AClBGoFFgMt/D34PPj99vn5lv12ARkL"+
            "VBKODSoGAwPDA+L/TvRT7uzvifZH9F32dwLuBJcFU/vl7o35zAEd9bz2qQfkCbAL9w06CigNAAub"+
            "/jH5Cv/2A8sDzgFQANMANgJuAbT6o/WY9tD6sP7a/1wGYA84ENULYgVNATsEIv5u8Tfv1/Im+OTz"+
            "vfFYArUL9/4F8xP4Ivpz+RP+TvtSAd4O7wwFCdQPuQ4WA1f/E/9f/0wDhAO6AaT/VP3H/q//KPpo"+
            "9gj5z/xEAHkBKweoD8EQIwlfAsMEFAIr+KHvce7w9hj1sPATAWcHpf8O/cHy/PP6/0f8M/cMAkAN"+
            "ewlpCkUQXQ0eCMcB//1//0ICwwBpAaUDD/8z/Uf9f/0F+6T1r/gR/48C+gWiC50OCAxoBzYFMwP8"+
            "/Sf2zO4K8kjzfvIE9yH8YAcdBWDzyvN9/yr6p/mMArYC1wm5DSMJrwwnD/QH2wB0AEgA9P90AEkA"+
            "I//P/Kz8iv3m/XL5Cfc/+x3/2gOHB4cLpA4ADaoGOQRgA4b7P/Rb8ObyMfTZ72T1/AWDB3D6/fcT"+
            "9/D32f/V/bb7TQhDDnkGlQouEuoKGQSNAcH/pwDQAGX/6/9f/4r7H/sY/PP9QPus93z8bgE2BQYJ"+
            "cg35DEQJUgefBHwAb/oP9GTxEfPd7mbxg/txACgG/f648U34nQIK+lr5OgfLB7II1Qy2Co4Mlgw7"+
            "Ba//MAFhANT9hP/F/3v8dfgG+1r/I/4u+qn5Ef1ZAUYHxwrHDJ0MWAolBskDhADn92Tz5PFA8iTw"+
            "qe3N+dAKuwTp9nv5W/l/+gMCa/47/toJIAxhB/QN/BHUCNsCSQHO/+n+Rv7u/k7+lPpO+CT9YP8O"+
            "/sP77vmY/sMBCgYVDP4OLQyPB9oGZwQy/jn3sPE58anypuzP8pf9kwDwBiL8SfGR+z4BWvvj/uII"+
            "PwhhCSgN3wzLDKoJLQQIAEwAFf5e/IAA1/1b+PT37/sLALv+9PoT/In/5QF7CIwMow3NDAMJywWK"+
            "Atf9UPWc8fXw/u9470bu8/r7CzMDtvY8+4X3k/qHBcYAcALBC8UK8gnbD4gOEgfoA20BoP6N/Lb9"+
            "Tf+L/DP4d/Y2/A3/WP1I/Yr9EAFbBB4ICA1OD5MLEgg5BkoB+Pvx9Arxp/CT79vq5PLBAAgCywQa"+
            "/KHyUfw0AUn8uQLgC7AHmwk5DzMNcAvHBxkD/wAD//H7Zf0q/8b6sPaH93D9DQAq/iT8xv1kAUEE"+
            "gQqqDWENdQtCCdoFJgF5/Fb1ivLL7+zt7u1c7Jb4jQlCBN76yPxc+PX7kAXwAaMDnwvwCn8KXhAW"+
            "DwIIHATPABT96Pnf+mr9O/yL+On2bvxSALj/rf4Y/RsAEQQYCe8OhBDiDIYISAVM/175kvTG8Pnv"+
            "OvBQ6wDxbfy8AAsIVAB09JT6i/+G/fgEtgy0CdIKTA0nDL8KvgflAwwAh/3y+VT6s/6O/Hz3wvYi"+
            "/JUAPQCF/tf+fAH6BK8Lbw9sD0sN2gmHBFj+TPmk84nxuu7Y7TDucOwO9jMGVQXT/C38efiD/BoG"+
            "DQXwBR4MmQxDCnsOJg/xB9YDYgIV/iL5o/k9++35+/a29eH69//5AED/T/7+AccFPwoKD1gQRg4O"+
            "CwgHUgE8+o30C/GK7uDunuud7mH1AfveBm4DCPjp+cb+pf4fBOkK7wmwDNEOSAwqDHoK7wX6AVH/"+
            "v/r596H6qPqH9wX2bPnB/rIAMP57/fwAQwQbCtgNyw+fEMQOTAoNA1H8G/Yx81/wPe3C7Wzsb+zw"+
            "+gUD7wAvApP4tvZyALwC0gOsCo4PVAzzDdsPdwteCNwEGADk+n/4Ffm++uj6/PbN9nH70/9PAJX+"+
            "ZP+sARAGLws/D2oR9g+LCzoHcP+8+VP2GvJ58QXt5+1p7E7tvfwRBQcEkf2p+db6S/4EBMEGJAin"+
            "C/oNWw0JEDQOygWXAtb/Lfsl+p76LPv1+ZD1xfb2/In/5QBr/5L+7wG4BagLDhCQES8P1wq+BQb/"+
            "Mvkj9Yvz0+/M7dnrz+x88lb5+wTfBlX7WPj//lr9xQBnCQoJqQx9Dy4MXgwYDd0HyQI5AH/6ovf/"+
            "+Lv68/q9+Mb54f0HAf//Ov4R/88AegUpC+gPERISEVUKpwNf/YX2qvT08Nbv8u4N7XjsdvTsAHkE"+
            "TQQs/G71lfuQAsIAIga/DpoKvQt2EB8NQQrvBdn/tP2Y+xn57/vf/WD7r/gs+YP/HAEN/2b92P2U"+
            "AakGsw3TELkRBw2kCBoDZPpD+KryRvHr74btk/BV63v0zgaKA/n/Svyb8+H9BwRlAMAGLAvXDLMM"+
            "1Q0cD7oJywSRAu7/hfvf+cj7Bv33+3H4hvoKAA4AQ/4k/JX9DAMcBuYK4BB0EGsOxwhIAJ77QPVn"+
            "8kTw6O7U7i7vAvSb+YcEKAhD+mv2I/5n+X/+3AZCBN0LCg9+CrUMUg3pB3gDvgBN/e77Bv3x/kIA"+
            "hvx/+kT9EwBy/uT6Xfwk/18DdgiXDMkOjA8vCi4Ej/+v+H725PHD78rwN+7l7Tj30QATBdgDIfpZ"+
            "9477Uv+x/pwCtQmKCFkMAw9LC4ILhAfZ/4H+jv6Z/Iv/4ALc/4v79frY/rz/G/3C+4D8GgBrBLQI"+
            "BA13DswLLQlbAt784fke9THz/+6L7/LtSu+z+V0DMAdNAI36B/kX+6r+5P9bAVAJUgwACl8O3gzM"+
            "B+QF2gBx/rX/zP2n/2sDt/9O/Kr9dP/L/2j8XPrX/HEA6wMWCIQMHQ7FDKsFwAA4/RL4ofbp8Ozv"+
            "ke6D7sX06/gzBlEHxfo++276pPdsAC0BCwFhDL0LpwnwDU8MjQiaBK0Atv4U//f+0gFvA9T+Wv3w"+
            "/tP/tv5N+lH5FvxHALkGIgqLDOEOCAnrAiH/afkn95HypfAo8XHvYfKh+C4BtQcsAiH7WPk/+Rj/"+
            "nfwr/iQICgjSCa0Ohwz8CNQGbQFH/83/OP89ApEF1wIH/+X90/4rAFb8cvkZ/PP+JgP5B1gKEg2L"+
            "CTwF1AGF+gX5I/WA8lzw3+7O72zva/zbBc8ClgFF/PT4KADR/938IgOGBqUJVQ27DC4MeQl+A5QA"+
            "hAB4/sb/oQLrAQ8A5fzi/RsCMAAK/cf7Gf2aAnQEBwcQCxYLjAiCBJP9ovko91LzLfHv7obuO++A"+
            "9PD9GQR3BRcAEvv7/o79C/0lAkUDawgMDRwLbwwYDXQG5gMLAUD9Gf+AAE4BngIq/6z8YgGiAoj/"+
            "XPwF/Pr/QgMaBZMIYwlhCLgGaf/6+sL26fKN8OvuMO+/7FDzuf1YA6UHOwLV++r/if++/SkBwAJi"+
            "B9UKdwpJDEQMDAh9BKQBsvyo+5b+agCVAmAAff+GAj4DaAIs/wf+zQB1AyIFQgjACGwHrwNu/Rb5"+
            "IvR28JruO+407hTvjfRY/o0EsgcnAjX9kwEB/wsAUwMCA+oIdgtHCbMMzQtVBqoE9gDr+z78If76"+
            "/nYAzv5z/jACtgPHAsYA+/4yAowEJAa8CGYI6wZtAwH+G/kj9YrxTPCW76juDu+58zf68ADyBG8A"+
            "8/7LAEYBdgFmASwEHAjoCXUKrwwzDLUJTwe8A3n/nPyH/F/8g/y9/L7+hQLFBDgFXgP6AncDkQKP"+
            "A9gEbQQPBLUAevxR+JLzMfIn7x3wbvAA8aH1lPhFAfcD7wAUAtEC5ALJBC4FNgYgCb4JeAoDC80K"+
            "NwgeBncC6/wS/Cz6Tvqu+1/6+f26AaMENAauBS0GAQVuBDYETgSPBFoCHv9K+wH49PR68fbwd/BE"+
            "8OHyl/bf+z0AOQK8AN4BGwQgAzMEIgX2BdYHTAjFCQEMjwqgCDwGBAJf/oL7uvp/+tL5DPom/acB"+
            "hQQNBywIDwhuByIFlAPJA6YCWAH0/n/7kvn/9eXzZPJ88fLxBfMk9/n53Pov/Wz+Vv+JA2kD2wP2"+
            "Bo8GQAhzCQwJ5AqCCyMIfQbmAhr9GP08+zr55fp2+hT8TABwAgIEUgYFB5oHFggqB3QHOwYyBC0C"+
            "fP2Y+ob4y/Um9a/yxPCj7+XvjfNa9QX7Ef9DAJ4CMAImBNAGKAjfBxsISAjRCAsKdAj3BoUFGgF4"+
            "/Sv8qfnG+uL6Gfsk/U/8of+cAs4EWwc1B+QHXgg/CJYHFwZNBLcBR/96/B/5tfdN9rT1avWe89jz"+
            "4fRG98H5A/uC+zX8Ff4SAFEFPwjACFYKSAp2CtsKyQgkBukDdf/s/Hr6B/qJ+/n6YPtd+8j7fPw9"+
            "/rcARwL2A2kFPgc7CZkJ7wm4CRAI0wXAAukA4f/k/m3+Zv1d/HX7wvlu+OX2lvUz9ZT1RPaW9k33"+
            "wvew+Eb7mf10/1kBZQIcBhQJFgtlC0kJogmXBy0GdwXCAgYBdv97/l3+5v1b/Vv9Ov6V/u/9g/6s"+
            "/6YA2AF5AsMCJgNmA8oDWwNuAnQBfgAzAMj/c/4b/dr7ivtv+4n7MfyG+5X8/fxh/O39tP2D/N38"+
            "mfwE/Uj/yQCMASsC9AFAAZIBsgGFAQkCAgHLADACcwIjA/gD5AOtA10DLwMeA1YDsQKVAVwBowAG"+
            "AIQAkgBiAIsAp/9A/1z/6/6///z/T//k/hX+hv1//b384Pr0+dD51fpH/MP8Zv1l/cX83/yC/Fz8"+
            "gf7S/5YA1gH+AnIFowcxCVYJLQdqBR4E2wKxAmoBx/+I/wwAnwAzAMf/Af8q/xT/3f0n/h7+Sv5w"+
            "/0H/Mf8kABEADwC5/5v9wfvT+538Vvyn/Gb8KPtN/eD+QgCcAU4A+f/WAMUBagHrAgsEUAMNA6cB"+
            "MQF4ARkBpgDK/zD/WP/Y/0YBHgIBAr4BfAEiAo8CcQIDAiEBDgE0AAQAaQB8/83/IP+o/h3/XP5X"+
            "/hv+ufxT+4/6yvpx+yv8x/y3/fH+tv67/+IA7P/4/xMA4//dAH0B/QISBcwEOgRYBFIEOwM6AtsA"+
            "UP+//qr93f3n/pP/MwB2AE0AJwAvAMQAogHAAU0BZQD0/3AASgCq/07/x/7K/hf/kv8vAHcAw/8E"+
            "/6X+N/2//Mr8/PyK/cX9lv7D//sAxgELAp8BwwA5ADgApwAOAUYBngHvAXACgAJQAuQBSwFlAQwB"+
            "wgCoACUB2QFoAS4A3f4O/iH9dvy3+477mPwe/Rr+NP9G/4D/l/7d/Sj+0/02/mb/ewBvAZUCQgMr"+
            "BO0EcAQJBF8E5gO5ArEBHwCu/zUAl/9p/5f/Gf+5/6kAZQHKANr/Uf/w/Xj9ZfyG+/v76ftO/Nb9"+
            "F/8tAJUA/v/7//D/xv+0/+v/wABoAeUBawK3ApYCrwENAa8ADwA2/43+5v72/t7+Gv/y/8UAxwAG"+
            "AET/tP5x/hH/Xf8MAKEA9gDuAW0CMQKsAfcA9AAbAaIAygBFAaoBBgLCATQB/wBNAHX/Sv8W/43+"+
            "1P4P/7r+5f4D/l79Yf2E/I77Fvv3+lH7pPzx/S//PwDUALUBXQJyAlUCOQI2Ai0C4wHEAZMBawGa"+
            "AdQB1wGRAcIB6wGoAS8BpQBgACMAyP9B/7n+Wv49/pj+n/54/pj+2f6O/w4As/9E/zP/MP9v/1P/"+
            "sP/3ANEBcAKlAgsCdwGkAKb/+P6c/X/8y/z3/EH9O/6O/g7/iP/u/sH+YP+P/1cAsAFPAuICwQPw"+
            "A6kDPAMCAiwB0wAYAB//tv4Q/4X/9v9DAFoAswDVAFkAVQAnAGb/z/6r/sP+o/6Y/rb+/f5Z/97+"+
            "cf6H/qv9HP23/Jr81P3D/lv/dgBtAQICugIGA8kC2QJ+AvoBUgIlAk0CFAOnAlEC0AGfAGQApf9K"+
            "/i/+G/4H/s7+N/9b//H/nf/K/ln+dP3//Gr9k/19/tv/WgD6AGUBsAA8AD4Aof+d/9v/HAAuAY0C"+
            "iAMVBNMDEQJ5AAv/df0f/Tf9cv2T/nf/TgCjAUQCNgLNAdUAj/9Z/r/9tv0U/h//LwAjAaABTQH4"+
            "AHMAtP/n/iL+9f1P/tr+kP+AAIkBJAI/AvQBOgGiAMT/yf7n/gD/E//d/1MA8ADAAdABgwEiAW4A"+
            "j/8V/wH/BP8q/2v//v+XAE4A8P+S/6v+Zf6Q/p3+3/4w//D/CgGfAQMCSgK1AakAy/9Z/1v/WP+O"+
            "/zIAtAD/AFYBWwHWAA4A7v76/VH98/xB/c/9iv6I/2YA7QBIAUgBBAG/AGoAVABkAJEABQFoAZ8B"+
            "zQHIAVIBogAlAJH/Af/y/i7/rP8VAGcA+wBFARkB5wCAAMn/IP9h/uf9sv02/eH8Bf0f/WL9pv7w"+
            "/8gAiwHLAfYBUwJOAvkBpAFXAR8B/QDvAPoA9gDbAL8AKABe/7/+QP7//eb9/P05/sv+fP/g/z8A"+
            "lAB4AEYAEADo/+v/yP+8/6r/5/+AALQA5ADvAK4ArQCbAHIAqQDFAMIABgEsARoBGAH9AMsAoAAg"+
            "AIn/gP+W/3f/l/9Q/+H+u/5P/gD+9f2u/Zr9kf6X/wIAjQChAJ0AHwHSAEcAawDcABMBZgGoAZoB"+
            "1gGlATABrADC/7z+bP5Z/v39Jv7C/oT/7P/H/8L/2/9g/wr/9P7O/gr/Zv/N/0MAnQDmACQBJgHq"+
            "AMMAoQBdAHUAnQCYAAgBTAEsARgBzgCMAFEAFQD6/yoAVgAOAMb/eP/M/nT+Xf4i/ln+jP66/jT/"+
            "bv+h/+P/DQBjALMAwAC6AP8AGQG6AK4ApgCOAJoAQwDr/+H/3v/D/+P/AwDp/wwA+/+U/2X/OP8A"+
            "//z+3/65/iH/jP+F/7//3//X/xsACgDw/0QAVQB+AOUA6gA7AYsBgQGWAVgBCQG2AG4AWwBUAIMA"+
            "mACpAL8AjgA7AKX/5P4//pn9TP1G/X39If6B/iP///9iAGsAVABAADgAMgCw/yEAAAFCASkCjwJn"+
            "AoIC+gEvAXYAr/8w/8X+mP7p/jP/X/+w/7r/h/8f/3X+SP5U/kL+Xv6z/lr/GABtAKIA9QDlAJUA"+
            "QgD8/+L/1f/k/2cA5gAHAXsBuQGAATwBpAAZAAMA1P+4/wQAGgCEAO0AswCNAFUA/v+T//z+q/6H"+
            "/nv+c/6K/vj+P/9c/67/1f/Z//H/HACUAMIAyQD8ACkBQAEBAR4BMQH2AMcAbgBUAD4AOAAoAOP/"+
            "0P+A/0H/MP/2/uL+x/6+/s7+3f7k/g7/Tv9P/3P/hv+C/8n/8/8LAEEAWQCRAMgA0AD9ABYB9QDS"+
            "AKcAgQBvAGoAdgBvAHsAmwCOAIIAaAAxAA4A5//E/8D/mP95/5T/kf97/3P/Rf8p/yD/BP8p/03/"+
            "ff8uAKoAoQC+AOQA6wD6AM0AegBhAFoASQBEAB0AWACtAHEAOwD8/7z/nP9p/0b/HP8E/zL/VP9e"+
            "/4H/j/+U/4X/ZP9Z/1P/cP+T/6D/uf8JAFsAfACbAJEAgAB5AF8ASgA5ADAASAB0AIQAewCGAJkA"+
            "gAB2AE0ALQA9ACYAMAAaAAUAFwAcACAACQACAOj/1v/C/6D/fP9L/1b/Kf/7/t7+Af+9/wMACgBf"+
            "AJwA5wD9AMMAmABpAJYAgABIAI0AhwCtAI0ANABgAPj/nf+A/yL/Cf/y/hb/Zf9r/3H/pP/Q/6j/"+
            "m/+J/2f/kf+F/2z/nv/0/z0AVABOAG8AgQBnAG0AawBiAFIATABaAGAAdgBRADUAMQASABYAEAD1"+
            "/+v/6f/e//n/HAAoADgAPABAAEEAQQBgAGAAQwBFAC8AKQD//9X/yP94/1D/M/83/7j/IwBQAIsA"+
            "xADuANYAuQBvAB0A/v+i/5j/zP/4/xMANgAsABQALwDi/77/lf9S/1P/W/9//6T/1P/k/97/3P+z"+
            "/6b/hP9M/0n/R/9j/47/xv8IADcAVgBTAF8AYQBQAEMALAAnADEAKwAnADQAMQAZAAAA5v/s//b/"+
            "7f8GAAoAFwAmACEAPQA6AC4AKQAQAAkAIgA0AD0AVwBjAGIAZgBXAFUAVABJAEMASwBcAGQAdQBh"+
            "ADgAIADn/7D/df83/x//BP/6/hb/QP+B/z8ApAB5AGEAWABmABAA4f+0/47/vf+8//b/EgAxAGkA"+
            "SgA9AA8A6v/U/5L/df9w/4D/qP/C/9H/2v/a/9P/sP+L/3n/f/+d/6v/wP/z/yMASwBqAGcAdwCJ"+
            "AHcAcwBaAEgATABMAF8AZwBjAGMAZABiAFMAQABFADUAJAAqABkAHgAkAAUA8P/e/9X/0f+//6//"+
            "rP+t/6f/oP+a/4v/gP95/0T/Nv8q/8r/IwAwAJ0AbwC/ALEAiwByACIAKAAOABQA+/8uAD4ACgAo"+
            "APn/wP+5/3T/Sf9N/1D/av+J/6j/4f/0/+H/9//r/+H/3//N/9v/6v8FABgAOgBVAGIAeQB2AGsA"+
            "aQBnAFQAXABgAFgAXgBVAFMAOAAZABYAAgDl/9T/zf/X/+H/7f/y//X//f8CAP7/8v/u/+f/5P/j"+
            "/8//xv/E/8D/qP+U/4z/iP+z/9//9v8eACIAOgBaADUANAAbAPv/AgD6/wAAAgASAC4ALwAmAB0A"+
            "EwACANr/tv+q/6j/r//K/9P/3v///wYACAADAPn//P/6//X/+P8JACgARABbAGYAZgBoAFkAQQAs"+
            "ABYABgADAAQABQAOABkAHwAcAAYA8//d/8T/uf+q/6b/s/+//9X/4f/r//f//f8CAP7/AwAIAAwA"+
            "GwAhAC0ANgA7AD0AOQApABcABQDw/+T/2v/T/9f/4//n/+n/6v/t/+//6P/h/9n/1f/d/+P/4P/o"+
            "//X//v/+//n/9f/u/+z/5//m//H/CgAjADEAPAA+AD4AOgAmABcABgAAAAsAGQAhACwAPAA/ADYA"+
            "KgASAPv/8v/d/9b/1//e/+z/8f/6//3/+//2//X/7v/m/+b/6P/y//j/+P8BAAUAAQD4/+f/1//J"+
            "/8T/vP+7/8T/zf/e/+r/9f/8//n/+P/3//X/7//x//b/+/8PAB0ALAA7AEkAVABSAFEATABEADkA"+
            "KgAcAAwA/f/1//L/6f/m/+f/4P/f/9v/0//Y/9n/5P/w//X/CgAYACAAIwAnACkAJgAiABoAFgAU"+
            "ABMAFQAdACEAJgAtACwALwAnACEAIAAWAA4ABwAAAPz/+f/z//j/+v/5//j/8f/s/+r/5v/e/9j/"+
            "zf/L/83/xv+9/7j/sf+w/7H/qf+t/7X/u//M/9z/5//+/w4AFQAhACgAKwApACcAIwAcABgAFAAT"+
            "ABMAEAAQAA0ACQAGAAEA+f/0//L/8f/4//7/AwANABYAFwAZABkAGgAcABkAGwAfACUAJwAsADQA"+
            "NAAyADAAKQAjACAAHgAfAB8AGgAYABkAGgAeABkADwANAA0ADAACAP3/AAD8//r/9v/0//T/7//r"+
            "/+L/3v/c/9n/2P/b/9X/0P/O/8b/x//H/8n/0//f/+f/7f/3////AwAFAAIA/v/+/wEAAQD8//7/"+
            "BQAJAA0ADgAHAAQAAgD8//n/+v8GAA4AFgAhACoANgA3ADMAMgArACUAJAAdABkAHgApAC0AKgAs"+
            "ACgAIgAbAAUA9//1//L/8P/n/+T/6//w/+7/6f/l/+T/4//c/9X/0v/Q/9H/z//M/8r/zv/Q/8v/"+
            "yv/G/8T/wP+9/7b/rf+u/6z/rv+1/7f/xf/T/+r/9f8LABsAGQApABYADwARAAkADgAGAAgADAAU"+
            "ABoAGAASAA8ADQAJAAoABQAGAA8AFQAdAB8AHQAiACMAGgALAAQA//8BAAcACQARAB8AJwAoACcA"+
            "HgAWABEABwABAPz/AAAIAA8AFAASABcAGQATAA8ABwD//wEA//8AAAUACgAPABAACwAEAP7/9//w"+
            "/+n/4//j/+f/6P/o/+r/6f/m/+r/6//q/+//9P/6/wAABgAGAAoADAAGAAIAAQAAAAAA//8CAA0A"+
            "FgAbAB4AHgAbABsAGgATAA4AEgATABMAGAAcACIAJwAlACAAHQAfABoAGAAYAB0AKAAtADMAMwAv"+
            "ACkAIAAQAAAA/f///wMACAAOABgAHgAjAB4AFAARAAkAAgD/////BAAMABQAFwAWABsAFQAIAP7/"+
            "7P/i/9//3f/c/9//4//o/+r/5//h/93/2f/P/8n/xf/C/8X/yP/J/9H/0//S/9X/1//W/9T/1P/W"+
            "/9n/3P/h/+T/6P/p/+3/7v/u//P/8v/y//X/9P/1//X/9f/2//b/9//1//T/9P/2//n/9//4//3/"+
            "/v/9//7/AAD///7//////wAAAwACAAMAAwAEAAYABwAGAAcACQAJAAgABgAGAAcABwAHAAYABwAH"+
            "AAYABAD///v/+f/3//T/8v/x//H/7v/u/+//7P/s/+r/6P/p/+r/6v/t/+//8v/z//T/9f/2//f/"+
            "9v/2//r/+//+/wEAAgAHAAgACAAHAAUABgACAAIAAgABAAQABgAJAAoADgAOAA4AEwASABEAEgAP"+
            "AA8AEgASABEAEQASABMAEwASABIADwAJAAcAAQD+//3/+v/9//v/+//8//n/+f/3//b/9v/0//X/"+
            "8v/z//L/7//w/+z/7P/u/+7/7//x/+//7v/v/+3/7P/s/+j/6P/r/+z/7v/w/+//8v/1//j/+//8"+
            "//7//v/+////AAABAAMABgAJAAsAEQATABUAGQAYABgAGgAYABYAFwAYABgAGgAbAB0AIQAkACUA"+
            "IgAjACMAIQAjACIAIQAgAB8AHwAfACAAIwAkAB8AHQAbABgAFQATABEADQAMAAoABwAJAAcABwAG"+
            "AAYACAAFAAMAAAD///z//P/6//3//f/8/wAAAQAEAAUABgAHAAYAAwADAAEAAAACAAMACAAKAA0A"+
            "EgARABQAFAATABUAEwASAA8ADAAOABAAEgAUABYAGAAYABgAFQASABAACgAFAAYABQAFAAkACAAJ"+
            "AAoACwALAAsACAAFAAIA/f/8//z/+//8//v/+v/+/////P/6//X/8v/x/+3/7P/q/+j/5//q/+z/"+
            "7P/u/+3/7P/q/+f/5P/k/+L/4//m/+b/6//x//H/9P/2//f/9//2//b/+P/z//H/8P/x//T/9//6"+
            "//z///8AAAEAAgACAAEA///9///////+/wEAAwAHAAUABwALAAoACgAKAAYAAgAGAAUAAwAFAAUA"+
            "BgAIAAcABQAFAAQAAgAAAP3//v/8//j/+f/5//b/9v/3//f/9f/z//D/7v/s/+j/5v/l/+T/4//g"+
            "/+L/5f/n/+r/6v/p/+3/7v/u/+7/7P/v//H/8P/x//X/+P/5//3//v8AAAIAAQADAAEAAQADAAIA"+
            "AgADAAUABwAIAAsADQAOABIAEgARABEAEQAQAA8ADgAMAAsACQAGAAUABAAEAAMA/v/9//z/+f/5"+
            "//b/9v/1//P/9P/z//L/8v/y//L/8v/w/+3/6//p/+f/6P/m/+X/6P/r/+7/7//y//H/8P/x//D/"+
            "8v/0//X/9//4//j//P/+/wAA///9////AQABAAAAAAD//wIABAAHAAgACAAJAAkADAANAA0ADQAN"+
            "AAoACgALAAoADQARABAAEQAUABUAFAAUABAADgAKAAYABwAFAAIAAQD/////AAABAAIAAgAAAAAA"+
            "///9//z//f/8//v//f/+/wAAAQAAAAIAAgACAAEAAQACAAQABAAEAAQAAwAEAAUABwAIAAcABwAJ"+
            "AAoACgALAAsACwAMAAwADgAOAA4AEQAPAA0ADgAOAA0ADAAKAAkABwAFAAQABQAHAAUABAAFAAUA"+
            "BAACAAIAAQABAAEAAQABAAEAAQACAAIAAAAAAP///f/9//3/+//7//z//P/8//z/+//7//r/+P/3"+
            "//f/9v/1//T/9P/2//n/+v/8//3///8AAAAAAQAAAAAA//////////////////8AAP7//v//////"+
            "/v/9//z//P/6//j/+P/2//n/+v/5//r/+v/5//n/+v/3//b/9P/z//P/8v/z//L/8v/z//P/9f/3"+
            "//f/+P/3//b/9//1//P/9P/0//T/9P/3//n/+f/6//v//P///////v/+////AAAAAAEAAQACAAIA"+
            "AwAFAAcACQALAAsACgAMAA0ADQAOAA4ADQAOAA8ADwAPAA4ADwAPAA0ADgAMAAwACQAGAAUABAAD"+
            "AAMABQAHAAUABQAFAAQAAwABAAEAAQD//////v/+/wAA//8AAAIAAgABAAIAAQABAAEAAAD///7/"+
            "/v///////v8AAAIAAwADAAcABwAGAAYAAwAEAAMAAgAEAAUABQAHAAkACgAMAA0ADQANAA8ADwAO"+
            "AA0ADAALAAsACgALAAsACgAKAAoACgAJAAgABQAEAAUABAACAAEA///9//3//f/8//z//P/8//7/"+
            "AAAAAAAA///9//v/+v/6//n/+f/5//n/+//9////AAABAAEAAAABAAAA///9//z/+v/6//v//f/+"+
            "/wAAAQAEAAUABgAHAAkABwAHAAcABgAGAAcACQAIAAoADAAMABAAEgAPAA4ADQALAAcABQACAAEA"+
            "AgABAAIAAgADAAQABAAFAAQAAwACAAAA/v/+//z//P/9//3//v///wAAAQAAAAAAAAD///7/+//6"+
            "//r//P/8//v/+////wEAAAAAAP7/AAABAP7//v/9//v//P/+////AAABAAEAAgADAAQAAwACAAAA"+
            "//////7//////wAAAQACAAMABAAEAAQABQAFAAUABAADAAEAAQACAAMAAwADAAMABAAFAAYABQAG"+
            "AAcABQAEAAMAAgACAAIAAgABAAEAAgADAAIAAgACAAEAAQABAAIAAQABAAEAAQACAAMABAAFAAYA"+
            "BgAGAAUABQAEAAQAAgABAAIAAgACAAIAAwAEAAUABQAFAAUABQAEAAIAAwAEAAIAAQABAP//AQAB"+
            "AAEAAgACAAMAAwADAAIA///+//3/+//8//z//v/+//7//////wAAAQAAAAAAAAD///7//v/9//z/"+
            "/f/9//3/AAD////////+/wAA///+//z//P/7//z//v/+//3//P/9//3////+//7///8AAAEAAAAB"+
            "AAAA//////7//v/9//3//f/8//z//v///wAA/////////v/7//n/+v/6//n/+f/6//v//f/+//7/"+
            "/v/9//z/+v/5//r/+v/5//n/+v/6//v//P/+//z//f/+//3/AAAAAP///v/+//7//f/+////AAAD"+
            "AAMAAwAEAAIAAwACAAEAAQAAAAAAAAAAAAAAAQABAAMAAgACAAIAAQABAAEAAQABAAEAAQABAAAA"+
            "AAABAAIA//8AAAAAAQAAAP7//f///wAA/v/+//7//f/+/////v/9//3/+//8//7//v/+//3//P/8"+
            "//z//f/+//3///8AAAAAAQAAAAEAAgACAAMABAAEAAUAAwAFAAYABgAGAAcABwAIAAgACAAHAAcA"+
            "CAAHAAYABQAEAAQABQAEAAQAAwAEAAQABQAFAAYABQAFAAYABwAHAAQABAADAAMAAwAEAAQABAAD"+
            "AAMAAgABAAIAAgAAAP//AAAAAAAAAAABAAIAAgADAAMAAwADAAMAAAACAAQAAwADAAIAAQADAAQA"+
            "AwAEAAUABQAGAAcABwAFAAQAAwADAAMAAwAEAAMABQAGAAYACAAJAAkACAAJAAkACQAKAAkACAAI"+
            "AAcABgAIAAcABwAHAAYACAAJAAgABgAEAAIAAAACAAIAAAD/////AAACAAIAAwAEAAQABAADAAIA"+
            "AQAAAAAAAQACAAIAAwAEAAMAAwACAAEAAAAAAP////8AAP///////wAAAgACAAIAAgADAAMABAAD"+
            "AAMAAwACAAEAAQACAAEAAAAAAAAAAAAAAAAA/v///////f/+/wAAAQD//wEAAgAAAAEAAAAAAAEA"+
            "AgADAAMAAgADAAIAAgADAAMAAgABAAEAAAD//wAAAQAAAAEAAAABAAEA//8AAAAA/v8AAAAA///+"+
            "/wAAAAAAAP////8AAAEAAAD9//3//v/9//3//f/9//z//f/+//7//v/9//z/+//8//3//f/+//3/"+
            "/P/8//3//f/9//3////+//7//v/9//7//f/+//7///8AAAAA//8AAAEAAgADAAMABQAEAAMAAwAD"+
            "AAMAAgACAAEAAAAAAAEAAQABAP///v/+//7///8AAAAA//8BAAEAAQACAAEAAQD//wAA////////"+
            "AAAAAP///v/+///////9//3//f/8//z//P/8//3//f/9//7//v/9//7//v///////f/9//z//P/9"+
            "//3//P/9/////////wAA///+//7//P/9//z/+//8//v//P/9////AAD//////v/+////AAAAAAAA"+
            "//////7//v///wAA/v/+/////v8AAAAA///+//3//P/8//3/+//7//z/+v/6//v/+//7//v/+//8"+
            "//v/+v/7//n/+f/6//n/+f/6//n/9//3//j/9//3//f/9//4//f/9f/2//j/9//3//n/+f/5//n/"+
            "+v/6//r/+v/5//r/+v/4//j/+f/4//r/+v/5//r/+f/5//j/+P/5//j/+P/4//n/+f/5//n/+v/7"+
            "//z//P/7//v/+//7//v/+//5//n/+f/5//f/9//4//j/+f/3//n/+f/4//r/+P/3//j/9//3//f/"+
            "9//2//j/+P/3//f/+f/7//j/+P/3//b/9//4//j/+P/3//b/9//4//n/+f/4//j/+P/4//n/+f/4"+
            "//j/+P/5//j/9//5//n/+v/5//r/+//6//v/+v/5//v//P/6//r/+f/7//r/+v/8//3//f/8//3/"+
            "/f/9//z//P/8//z/+//8//z/+//6//r/+v/6//r/+//6//r/+v/7//v//P/7//v/+//7//v/+//8"+
            "//z//P/7//r/+v/7//v/+//7//v//P/8//z//f/9//3//f/9//7////+//7////+///////+////"+
            "///9//7/////////AAD///7//v/8/wAA/v/+/wAA/f//////AQABAAEAAAAAAP//AQADAAMABAAD"+
            "AAMAAgABAAEAAgAAAP/////+/wAAAQABAAAAAAD//wAAAQD//wAAAAD///////////7//v///wAA"+
            "AAAAAAEA/////wAA/////wAA///9//7////+//////8AAAAA///+//7/AAD/////AAAAAAAA//8A"+
            "AAAAAQAAAP//AAAAAP////8AAP////8AAP////////7//v///wAA//8AAAAA/////////////wAA"+
            "AQABAAAAAAAAAAAA//////7//v/9//3//f/+//7//v////7///8AAP7//v/+//3//v/+//7//v//"+
            "/////v/9//7//v///wAA/P/9//7//f/+/////v/9//7//v//////AAAAAP7//v///////v/+//z/"+
            "/f/9//3//v/+/////////////v/+//7/////////AAD///7//v/+//7//v/+///////+//3//v/+"+
            "//3//f/8//z//P/9//z//P/8//v//f/9//3//P/8//z//f/9//3//f/9//z//P/9//z/+//8//7/"+
            "/P/7//v/+//7//v/+//8//z/+//8//z//P/9//z//P/9//z/+//8//z//P/8//v/+//7//z//P/8"+
            "//z/+//8//3//f/9//z/+//8//v//P/9//z//P/8//z//P/7//v/+v/7//v/+//7//v/+v/7//n/"+
            "+P/5//r/+v/5//n/+v/8//v/+v/6//n/+f/7//v/+f/6//v/+v/7//r/+v/7//z//P/8//z/+//7"+
            "//v//P/8//v//P/8//z/+//7//v/+//8//v/+//8//v/+v/6//v/+//6//r/+v/6//r/+v/6//r/"+
            "+//5//r/+v/7//v/+v/6//r/+//7//v/+v/6//r/+v/8//z//P/7//v/+//6//v/+//7//z//f/8"+
            "//z/+//7//v/+//7//v//P/8//3//f/9//7//v/9//3//f/9//3//v/9//7//f/9//7//f/+//7/"+
            "/f/9//3//f/+//z//P/8//3//v/9//3//v/+//7////+//7//v/9//3//v/+/////v/+//7//v//"+
            "/wEA//8AAAAA//8AAP//////////AAAAAAEAAAAAAAAAAAAAAAAAAAAAAAEAAAAAAP//////////"+
            "/////////////wAA///+/wAAAAABAAEAAQAAAP//AQABAAEAAAAAAAAA/////wAA/////wAA////"+
            "//7//////////v/+//7////+///////////////////////+///////+//7//v/9//7//v/+//7/"+
            "/v/+//7/AAD///7//v/+//7//v/+/////v///////v///////////////////wAA/////wAA///+"+
            "//////////7//v///wEAAAD//////v/+///////9//7////+/////f/9//7////////////+//7/"+
            "/v///////v///w==";
}
