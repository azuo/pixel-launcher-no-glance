package azuo.pixellaunchernoglance;

import android.app.AndroidAppHelper;
import android.content.pm.ApplicationInfo;
import android.content.res.XResources;
import android.util.TypedValue;
import android.view.View;
import android.widget.TextView;

import de.robv.android.xposed.IXposedHookInitPackageResources;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_InitPackageResources;
import de.robv.android.xposed.callbacks.XC_LayoutInflated;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class Xposed implements IXposedHookLoadPackage, IXposedHookInitPackageResources {
    private static boolean doubleLines = false;

    private static StackTraceElement getCaller(String methodName) {
        StackTraceElement[] stack = Thread.currentThread().getStackTrace();
        return stack[getCallerIndex(methodName, stack)];
    }

    private static int getCallerIndex(String methodName, StackTraceElement[] stack) {
        //XposedBridge.log(methodName);
        //for (StackTraceElement e : stack)
        //    XposedBridge.log("    " + e.getClassName() + "." + e.getMethodName());
        for (int i = 0; i < stack.length - 1; ++ i) {
            if (methodName.equals(stack[i].getMethodName()))
                return i + 1;
        }
        throw new NoSuchMethodError("Method not found: " + methodName);
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!"com.google.android.apps.nexuslauncher".equals(lpparam.packageName) ||
            lpparam.appInfo.targetSdkVersion <= 25)
            return;

        XposedHelpers.findAndHookMethod(
            "com.android.launcher3.Workspace",
            lpparam.classLoader,
            "bindAndInitFirstWorkspaceScreen", View.class,
            new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                if (param.args[0] == null) {
                    XposedHelpers.callMethod(
                        param.thisObject,
                        "insertNewWorkspaceScreen",
                        0, 0);
                    param.setResult(null);
                }
            }
        });
        if (BuildConfig.DEBUG)
            XposedBridge.log("Hooked method Workspace.bindAndInitFirstWorkspaceScreen(View).");

        XposedHelpers.findAndHookMethod(
            "com.android.launcher3.util.GridOccupancy",
            lpparam.classLoader,
            "markCells", Integer.TYPE, Integer.TYPE, Integer.TYPE, Integer.TYPE, Boolean.TYPE,
            new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                if (param.args[0].equals(0) &&
                    param.args[1].equals(0) &&
                    //param.args[2].equals(com.android.launcher3.InvariantDeviceProfile.numColumns + 1) &&
                    //param.args[3].equals(com.android.launcher3.config.FeatureFlags.EXPANDED_SMARTSPACE ? 2 : 1) &&
                    param.args[4].equals(true)) {
                    StackTraceElement caller = getCaller(param.method.getName());
                    String m = caller.getMethodName();
                    if (("checkItemPlacement".equals(m) || "checkAndAddItem".equals(m)) &&
                        "com.android.launcher3.model.LoaderCursor".equals(caller.getClassName())) {
                        param.setResult(null);
                    }
                }
            }
        });
        if (BuildConfig.DEBUG)
            XposedBridge.log("Hooked method GridOccupancy.markCells(int, int, int, int, boolean).");

        XposedHelpers.findAndHookMethod(
            "android.graphics.Paint",
            lpparam.classLoader,
            "getFontMetrics",
            new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                if (doubleLines) {
                    StackTraceElement[] stack = Thread.currentThread().getStackTrace();
                    int index = getCallerIndex(param.method.getName(), stack);
                    String m = stack[index].getMethodName();
                    if (("onMeasure".equals(m) &&
                         "com.android.launcher3.BubbleTextView".equals(stack[index].getClassName())) ||
                        ("calculateTextHeight".equals(m) &&
                         //"com.android.launcher3.Utilities".equals(stack[index].getClassName()) &&
                         "com.android.launcher3.DeviceProfile".equals(stack[index + 1].getClassName())))   {
                        Object result = param.getResult();
                        float bottom = XposedHelpers.getFloatField(result, "bottom");
                        float top = XposedHelpers.getFloatField(result, "top");
                        XposedHelpers.setFloatField(result, "bottom", (bottom - top) * 2.02f + top);
                    }
                }
            }
        });
        if (BuildConfig.DEBUG)
            XposedBridge.log("Hooked method Paint.getFontMetrics().");
    }

    @Override
    public void handleInitPackageResources(XC_InitPackageResources.InitPackageResourcesParam resparam) throws Throwable {
        if (!"com.google.android.apps.nexuslauncher".equals(resparam.packageName))
            return;
        ApplicationInfo appInfo = AndroidAppHelper.currentApplicationInfo();
        if (appInfo == null || appInfo.targetSdkVersion <= 25)
            return;

        doubleLines = true;

        resparam.res.setReplacement(
            resparam.res.getPackageName(),
            "dimen",
            "dynamic_grid_cell_padding_x",
            new XResources.DimensionReplacement(4.5f, TypedValue.COMPLEX_UNIT_DIP)
        );
        if (BuildConfig.DEBUG)
            XposedBridge.log("Replaced dimension dynamic_grid_cell_padding_x = 4.5dip");

        /*
        resparam.res.setReplacement(
            resparam.res.getPackageName(),
            "dimen",
            "dynamic_grid_icon_drawable_padding",
            new XResources.DimensionReplacement(2.0f, TypedValue.COMPLEX_UNIT_DIP)
        );
        if (BuildConfig.DEBUG)
            XposedBridge.log("Replaced dynamic_grid_icon_drawable_padding = 2.0dip");
        */

        resparam.res.hookLayout(
            resparam.res.getPackageName(),
            "layout",
            "all_apps_icon",
            new XC_LayoutInflated() {
            @Override
            public void handleLayoutInflated(LayoutInflatedParam liparam) throws Throwable {
                if (liparam.view instanceof TextView) {
                    TextView view = ((TextView)liparam.view);
                    view.setSingleLine(false);
                    view.setLines(2);
                    int px = (int)(liparam.res.getDisplayMetrics().density * 4.5);
                    view.setPadding(px, view.getPaddingTop(), px, view.getPaddingBottom());
                }
            }
        });
        if (BuildConfig.DEBUG)
            XposedBridge.log("Hooked layout all_apps_icon.xml.");

        resparam.res.hookLayout(
            resparam.res.getPackageName(),
            "layout",
            "app_icon",
            new XC_LayoutInflated() {
            @Override
            public void handleLayoutInflated(LayoutInflatedParam liparam) throws Throwable {
                TextView view = ((TextView)liparam.view);
                view.setSingleLine(false);
                view.setLines(2);
            }
        });
        if (BuildConfig.DEBUG)
            XposedBridge.log("Hooked layout app_icon.xml.");

        resparam.res.hookLayout(
            resparam.res.getPackageName(),
            "layout",
            "folder_icon",
            new XC_LayoutInflated() {
            @Override
            public void handleLayoutInflated(LayoutInflatedParam liparam) throws Throwable {
                TextView view = ((TextView)liparam.view.findViewById(liparam.res.getIdentifier(
                    "folder_icon_name", "id", liparam.res.getPackageName())));
                view.setSingleLine(false);
                view.setLines(2);
            }
        });
        if (BuildConfig.DEBUG)
            XposedBridge.log("Hooked layout folder_icon.xml.");

        resparam.res.hookLayout(
            resparam.res.getPackageName(),
            "layout",
            "folder_application",
            new XC_LayoutInflated() {
            @Override
            public void handleLayoutInflated(LayoutInflatedParam liparam) throws Throwable {
                TextView view = ((TextView)liparam.view);
                view.setSingleLine(false);
                view.setLines(2);
            }
        });
        if (BuildConfig.DEBUG)
            XposedBridge.log("Hooked layout folder_application.xml.");
    }

    //public static class MainActivity extends android.app.Activity {
    //}
}
