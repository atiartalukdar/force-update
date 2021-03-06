package net.skoumal.forceupdate;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.TextUtils;

import net.skoumal.forceupdate.provider.ApkVersionProvider;
import net.skoumal.forceupdate.provider.CarretoGooglePlayVersionProvider;
import net.skoumal.forceupdate.provider.MasterVersionProvider;
import net.skoumal.forceupdate.provider.SlaveVersionProvider;
import net.skoumal.forceupdate.util.Versions;
import net.skoumal.forceupdate.view.activity.ActivityUpdateView;
import net.skoumal.forceupdate.view.activity.ForceUpdateActivity;
import net.skoumal.forceupdate.view.activity.RecommendedUpdateActivity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ForceUpdate {

    private static final String SHARED_PREFERENCES_NAME = "ForceUpdate1234";

    private static final String SHARED_PREFERENCES_REQUEST_INTERRUPTED = "request_interrupted";

    private static final String SHARED_PREFERENCES_LAST_MIN_ALLOWED_VERSION_REQUEST = "last_min_allowed_version_request";

    private static final String SHARED_PREFERENCES_LAST_RECOMMENDED_VERSION_REQUEST = "last_recommended_version_request";

    private static final String SHARED_PREFERENCES_LAST_EXCLUDED_VERSIONS_REQUEST = "last_excluded_versions_request";

    private static final String SHARED_PREFERENCES_LAST_MIN_ALLOWED_VERSION = "last_min_allowed_version";

    private static final String SHARED_PREFERENCES_LAST_RECOMMENDED_VERSION = "last_recommended_version";

    private static final String SHARED_PREFERENCES_LAST_EXCLUDED_VERSIONS = "last_excluded_versions";

    private static final String SHARED_PREFERENCES_LAST_MIN_ALLOWED_VERSION_PAYLOAD = "last_min_allowed_version_payload";

    private static final String SHARED_PREFERENCES_LAST_RECOMMENDED_VERSION_PAYLOAD = "last_recommended_version_payload";

    private static final String SHARED_PREFERENCES_LAST_EXCLUDED_VERSIONS_PAYLOAD = "last_excluded_versions_payload";

    private static boolean alreadyInstantiated;

    private Application application;

    private VersionProvider minAllowedVersionProvider;

    private int minAllowedVersionInterval;

    private VersionProvider recommendedVersionProvider;

    private int recommendedVersionInterval;

    private VersionProvider excludedVersionProvider;

    private int excludedVersionInterval;

    private VersionProvider currentVersionProvider;

    private UpdateView forcedUpdateView;

    private UpdateView recommendedUpdateView;

    private List<Class<?>> forceUpdateActivities;

    private SharedPreferences sharedPreferences;

    private Activity resumedActivity;

    public ForceUpdate(Application gApplication,
                       boolean gDebug,
                       VersionProvider gForcedVersionProvider, int gForcedVersionInterval,
                       VersionProvider gRecommendedVersionProvider, int gRecommendedVersionInterval,
                       VersionProvider gExcludedVersionListProvider, int gExcludedVersionListInterval,
                       VersionProvider gCurrentVersionProvider,
                       UpdateView gForcedVersionView,
                       UpdateView gRecommendedVersionView,
                       List<Class<?>> gForceUpdateActivities) {

        if(alreadyInstantiated) {
            throw new RuntimeException("ForceUpdate library is already initialized.");
        }

        String permission = "android.permission.INTERNET";
        int res = gApplication.checkCallingOrSelfPermission(permission);
        if(res != PackageManager.PERMISSION_GRANTED) {
            throw new RuntimeException("Internet permission is necessary for version checks.");
        }

        if(!gDebug && (gForcedVersionInterval < 60 || gRecommendedVersionInterval < 60 || gExcludedVersionListInterval < 60)) {
            throw new RuntimeException("Minimal fetch interval is 60s");
        }

        application = gApplication;

        minAllowedVersionProvider = gForcedVersionProvider;

        minAllowedVersionInterval = gForcedVersionInterval;

        recommendedVersionProvider = gRecommendedVersionProvider;

        recommendedVersionInterval = gRecommendedVersionInterval;

        excludedVersionProvider  = gExcludedVersionListProvider;

        excludedVersionInterval = gExcludedVersionListInterval;

        currentVersionProvider = gCurrentVersionProvider;

        forcedUpdateView = gForcedVersionView;

        recommendedUpdateView = gRecommendedVersionView;

        forceUpdateActivities = gForceUpdateActivities;

        sharedPreferences = gApplication.getSharedPreferences(SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE);

        alreadyInstantiated = true;
    }

    public void init() {

        application.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            @Override
            public void onActivityCreated(Activity activity, Bundle savedInstanceState) {

            }

            @Override
            public void onActivityStarted(Activity activity) {

            }

            @Override
            public void onActivityResumed(Activity activity) {
                ForceUpdate.this.onActivityResumed(activity);
            }

            @Override
            public void onActivityPaused(Activity activity) {
                ForceUpdate.this.onActivityPaused(activity);
            }

            @Override
            public void onActivityStopped(Activity activity) {

            }

            @Override
            public void onActivitySaveInstanceState(Activity activity, Bundle outState) {

            }

            @Override
            public void onActivityDestroyed(Activity activity) {

            }
        });
    }

    private void onActivityResumed(Activity gActivity) {

        boolean isForceUpdateActivity = false;

        //TODO [1] detect infinite loop because of showView -> onActivityResumed loop

        for(Class<?> c : forceUpdateActivities) {
            if(c.isInstance(gActivity)) {
                isForceUpdateActivity = true;
                break;
            }
        }

        if(!isForceUpdateActivity) {
            resumedActivity = gActivity;

            // show force update view if it is required by min-allowed or excluded version
            checkSavedMinAllowedAndExcludedVersions();

            checkForUpdate();

        }
    }

    private void checkSavedMinAllowedAndExcludedVersions() {
        String lastMinAllowedVersionStr = sharedPreferences.getString(SHARED_PREFERENCES_LAST_MIN_ALLOWED_VERSION, null);
        boolean viewShown = false;
        if(lastMinAllowedVersionStr != null) {
            Version lastMinAllowedVersion = new Version(lastMinAllowedVersionStr);

            String lastMinAllowedVersionPayload = sharedPreferences.getString(SHARED_PREFERENCES_LAST_MIN_ALLOWED_VERSION_PAYLOAD, null);

            viewShown = showViewIfNeeded(forcedUpdateView, currentVersionProvider.getVersionResult().getVersion(), lastMinAllowedVersion, lastMinAllowedVersionPayload);
        }

        if(!viewShown) {
            Set<String> lastExcludedVersionStrSet = sharedPreferences.getStringSet(SHARED_PREFERENCES_LAST_EXCLUDED_VERSIONS, null);
            if(lastExcludedVersionStrSet != null) {
                List<Version> lastExcludedVersionList = Versions.toVersionList(lastExcludedVersionStrSet);

                Set<String> lastExcludedPayloadSet = sharedPreferences.getStringSet(SHARED_PREFERENCES_LAST_EXCLUDED_VERSIONS_PAYLOAD, new HashSet<String>(0));
                List<String> lastExcludedPayloadList = new ArrayList<>(lastExcludedPayloadSet);

                showViewIfNeeded(forcedUpdateView, currentVersionProvider.getVersionResult().getVersion(), lastExcludedVersionList, lastExcludedPayloadList);
            }
        }
    }

    private void onActivityPaused(Activity activity) {
        resumedActivity = null;
    }

    private void checkForUpdate() {

        //TODO [1] finalize request interruption detection and show notification when last request was interrupted and there is update, ideal way is to show notification only in case of crash
        boolean previousRequestInterrupted = sharedPreferences.getBoolean(SHARED_PREFERENCES_REQUEST_INTERRUPTED, false);

        new Thread(new Runnable() {

            @Override
            public void run() {

                sharedPreferences.edit().putBoolean(SHARED_PREFERENCES_REQUEST_INTERRUPTED, true).apply();

                Version currentVersion = currentVersionProvider.getVersionResult().getVersion();

                boolean updateAvailable = checkVersion(minAllowedVersionProvider,
                        minAllowedVersionInterval,
                        forcedUpdateView,
                        currentVersion,
                        SHARED_PREFERENCES_LAST_MIN_ALLOWED_VERSION_REQUEST,
                        SHARED_PREFERENCES_LAST_MIN_ALLOWED_VERSION,
                        SHARED_PREFERENCES_LAST_MIN_ALLOWED_VERSION_PAYLOAD);

                if(!updateAvailable) {
                    updateAvailable = checkVersion(excludedVersionProvider,
                            excludedVersionInterval,
                            forcedUpdateView,
                            currentVersion,
                            SHARED_PREFERENCES_LAST_EXCLUDED_VERSIONS_REQUEST,
                            SHARED_PREFERENCES_LAST_EXCLUDED_VERSIONS,
                            SHARED_PREFERENCES_LAST_EXCLUDED_VERSIONS_PAYLOAD);
                }

                if(!updateAvailable) {
                    checkVersion(recommendedVersionProvider,
                            recommendedVersionInterval,
                            recommendedUpdateView,
                            currentVersion,
                            SHARED_PREFERENCES_LAST_RECOMMENDED_VERSION_REQUEST,
                            SHARED_PREFERENCES_LAST_RECOMMENDED_VERSION,
                            SHARED_PREFERENCES_LAST_RECOMMENDED_VERSION_PAYLOAD);
                }

                sharedPreferences.edit().putBoolean(SHARED_PREFERENCES_REQUEST_INTERRUPTED, false).apply();

            }
        }).start();

        if(previousRequestInterrupted) {
            // TODO [2] block main thread till the request finishes to avoid crash during request
        }
    }

    /**
     * Checks version in given version provider and compares it with given current version. Version
     * is checked only in case the defined interval is exceeded.
     * @param gVersionProvider provider of expected version
     * @param gVersionInterval how often we should check for expected version
     * @param gVersionView view to show when update is required / recommended
     * @param gCurrentVersion current version, typically version of APK
     * @param gSharedPreferencesLastRequestKey key where last request timestamp will be stored
     * @return
     */
    private boolean checkVersion(VersionProvider gVersionProvider,
                              int gVersionInterval,
                              UpdateView gVersionView,
                              Version gCurrentVersion,
                              String gSharedPreferencesLastRequestKey,
                              String gSharedPreferencesLastVersionKey,
                              String gSharedPreferencesLastPayloadKey) {

        long lastRequest = sharedPreferences.getLong(gSharedPreferencesLastRequestKey, 0);

        if(gVersionProvider != null &&
                lastRequest + (gVersionInterval * 1000) < System.currentTimeMillis()) {

            VersionResult result = gVersionProvider.getVersionResult();

            if(!result.isError()) {

                sharedPreferences.edit().putLong(gSharedPreferencesLastRequestKey, System.currentTimeMillis()).apply();

                if(!TextUtils.equals(gSharedPreferencesLastVersionKey, SHARED_PREFERENCES_LAST_EXCLUDED_VERSIONS)) {
                    Version version = result.getVersion();
                    String payload = result.getPayload();

                    sharedPreferences.edit()
                            .putString(gSharedPreferencesLastVersionKey, version.toString())
                            .putString(gSharedPreferencesLastPayloadKey, payload)
                            .apply();

                    return showViewIfNeeded(gVersionView, gCurrentVersion, version, payload);
                } else {
                    List<Version> versionList = result.getVersionList();
                    List<String> payloadList = result.getPayloadList();

                    sharedPreferences.edit()
                            .putStringSet(gSharedPreferencesLastVersionKey, Versions.toStringSet(versionList))
                            .putStringSet(gSharedPreferencesLastPayloadKey, new HashSet<>(payloadList))
                            .apply();

                    return showViewIfNeeded(gVersionView, gCurrentVersion, versionList, payloadList);
                }

            } else {
                return true;
            }
        }

        return false;
    }

    private boolean showViewIfNeeded(UpdateView gVersionView, Version gCurrentVersion, List<Version> gExcludedVersionList, List<String> gExcludedVersionPayloadList) {
        for(int i = 0; i < gExcludedVersionList.size(); i++) {
            Version version = gExcludedVersionList.get(i);
            if (gCurrentVersion.compareTo(version) == 0 && resumedActivity != null) {
                gVersionView.showView(resumedActivity, gCurrentVersion, version, gExcludedVersionPayloadList.get(i));
                return true;
            }
        }

        return false;
    }

    private boolean showViewIfNeeded(UpdateView gVersionView, Version gCurrentVersion, Version gExpectedVersion, String gExpectedVersionPayload) {
        if (gCurrentVersion.compareTo(gExpectedVersion) < 0 && resumedActivity != null) {
            gVersionView.showView(resumedActivity, gCurrentVersion,
                    gExpectedVersion, gExpectedVersionPayload);
            return true;
        } else {
            return false;
        }
    }

    /**
     * Mainly for testing and debug purposes. Avoid using it in production apps.
     */
    public void clearCache() {
        sharedPreferences.edit().clear().apply();
    }

    public static class Builder {

        private Application application;

        private VersionProvider minAllowedVersionProvider = null;
        private MasterVersionProvider minAllowedMasterVersionProvider = null;

        private int minAllowedVersionInterval = 24 * 3600;

        private VersionProvider recommendedVersionProvider;
        private MasterVersionProvider recommendedMasterVersionProvider = null;

        private int recommendedVersionInterval = 24 * 3600;

        private VersionProvider excludedVersionListProvider;
        private MasterVersionProvider excludedMasterVersionProvider = null;

        private int excludedVersionListInterval = 24 * 3600;

        private VersionProvider currentVersionProvider;

        private UpdateView forcedVersionView = new ActivityUpdateView(ForceUpdateActivity.class);

        private UpdateView recommendedVersionView = new ActivityUpdateView(RecommendedUpdateActivity.class);;

        private List<Class<?>> forceUpdateActivities = new ArrayList<>();

        private boolean debug;

        public Builder() {

        }

        public Builder application(Application gApplication) {
            application = gApplication;

            forceUpdateActivities.add(ForceUpdateActivity.class);

            return this;
        }

        public Builder debug(boolean gDebug) {
            debug = gDebug;

            if(!BuildConfig.DEBUG) {
                ForceUpdateLogger.w("Debug mode enabled for production build!");
            }

            return this;
        }

        public Builder masterVersionProvider(MasterVersionProvider gMaster) {
            minAllowedMasterVersionProvider = gMaster;
            recommendedMasterVersionProvider = gMaster;
            excludedMasterVersionProvider = gMaster;

            return this;
        }

        public Builder minAllowedVersionProvider(VersionProvider gProvider) {
            minAllowedVersionProvider = gProvider;

            return this;
        }

        public Builder minAllowedVersionProvider(MasterVersionProvider gProvider) {
            minAllowedMasterVersionProvider = gProvider;

            return this;
        }

        public Builder minAllowedVersionCheckMinInterval(int gSeconds) {
            if(gSeconds < 60 && !debug) {
                throw new RuntimeException("Minimum check interval is 60s, ForceUpdate.Builder.debug(BuildConfig.DEBUG) method prior this one to allow lower intervals.");
            }

            minAllowedVersionInterval = gSeconds;

            return this;
        }

        public Builder excludedVersionListProvider(VersionProvider gProvider) {
            excludedVersionListProvider = gProvider;

            return this;
        }

        public Builder excludedVersionListProvider(MasterVersionProvider gProvider) {
            excludedMasterVersionProvider = gProvider;

            return this;
        }

        public Builder excludedVersionListCheckMinInterval(int gSeconds) {
            if(gSeconds < 60 && !debug) {
                throw new RuntimeException("Minimum check interval is 60s, ForceUpdate.Builder.debug(BuildConfig.DEBUG) method prior this one to allow lower intervals.");
            }

            excludedVersionListInterval = gSeconds;

            return this;
        }

        public Builder recommendedVersionProvider(VersionProvider gProvider) {
            recommendedVersionProvider = gProvider;

            return this;
        }

        public Builder recommendedVersionProvider(MasterVersionProvider gProvider) {
            recommendedMasterVersionProvider = gProvider;

            return this;
        }

        public Builder recommendedVersionCheckMinInterval(int gSeconds) {
            if(gSeconds < 60 && !debug) {
                throw new RuntimeException("Minimum check interval is 60s, ForceUpdate.Builder.debug(BuildConfig.DEBUG) method prior this one to allow lower intervals.");
            }

            recommendedVersionInterval = gSeconds;

            return this;
        }

        public Builder currentVersionProvider(VersionProvider gProvider) {
            currentVersionProvider = gProvider;

            return this;
        }

        public Builder forcedUpdateView(UpdateView gUpdateView) {
            forcedVersionView = gUpdateView;

            return this;
        }

        public Builder recommendedUpdateView(UpdateView gUpdateView) {
            recommendedVersionView = gUpdateView;

            return this;
        }

        public Builder addForceUpdateActivity(Class<?> gActivity) {
            forceUpdateActivities.add(gActivity);

            return this;
        }

        public ForceUpdate build() {
            if(application == null) {
                throw new RuntimeException("Please call ForceUpdate.Builder#application(Application) method before build.");
            }

            if(minAllowedVersionProvider == null && minAllowedMasterVersionProvider != null) {
                SlaveVersionProvider slaveProvider = new SlaveVersionProvider(minAllowedMasterVersionProvider);
                minAllowedVersionProvider = slaveProvider;
                minAllowedMasterVersionProvider.setMinAllowedProvider(slaveProvider);
            }

            if(recommendedVersionProvider == null && recommendedMasterVersionProvider != null) {
                SlaveVersionProvider slaveProvider = new SlaveVersionProvider(recommendedMasterVersionProvider);
                recommendedVersionProvider = slaveProvider;
                recommendedMasterVersionProvider.setRecommendedProvider(slaveProvider);
            }

            if(excludedVersionListProvider == null && excludedMasterVersionProvider != null) {
                SlaveVersionProvider slaveProvider = new SlaveVersionProvider((excludedMasterVersionProvider));
                excludedVersionListProvider = slaveProvider;
                excludedMasterVersionProvider.setExcludedProvider(slaveProvider);
            }

            if(currentVersionProvider == null) {
                currentVersionProvider = new ApkVersionProvider(application);
            }

            if(recommendedVersionProvider == null) {
                recommendedVersionProvider = new CarretoGooglePlayVersionProvider(application);
            }

            return new ForceUpdate(application,
                    debug,
                    minAllowedVersionProvider, minAllowedVersionInterval,
                    recommendedVersionProvider, recommendedVersionInterval,
                    excludedVersionListProvider, excludedVersionListInterval,
                    currentVersionProvider,
                    forcedVersionView, recommendedVersionView,
                    forceUpdateActivities);
        }

        public ForceUpdate buildAndInit() {
            ForceUpdate fu = build();
            fu.init();

            return fu;
        }

    }
}
