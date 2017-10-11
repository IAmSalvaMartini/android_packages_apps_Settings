/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.settings.enterprise;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.UserInfo;
import android.os.UserHandle;
import android.os.UserManager;
import android.support.v7.preference.Preference;

import com.android.settings.R;
import com.android.settings.testutils.SettingsRobolectricTestRunner;
import com.android.settings.TestConfig;
import com.android.settings.applications.EnterpriseDefaultApps;
import com.android.settings.applications.UserAppInfo;
import com.android.settings.core.PreferenceAvailabilityObserver;
import com.android.settings.testutils.FakeFeatureFactory;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Answers;
import org.mockito.ArgumentMatcher;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.List;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.argThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.anyObject;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link EnterpriseSetDefaultAppsPreferenceController}.
 */
@RunWith(SettingsRobolectricTestRunner.class)
@Config(manifest = TestConfig.MANIFEST_PATH, sdk = TestConfig.SDK_VERSION)
public final class EnterpriseSetDefaultAppsPreferenceControllerTest {

    private static final String KEY_DEFAULT_APPS = "number_enterprise_set_default_apps";

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private Context mContext;
    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private UserManager mUm;
    private FakeFeatureFactory mFeatureFactory;
    @Mock private PreferenceAvailabilityObserver mObserver;

    private EnterpriseSetDefaultAppsPreferenceController mController;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        FakeFeatureFactory.setupForTest(mContext);
        mFeatureFactory = (FakeFeatureFactory) FakeFeatureFactory.getFactory(mContext);
        mController = new EnterpriseSetDefaultAppsPreferenceController(mContext,
                null /* lifecycle */);
        mController.setAvailabilityObserver(mObserver);
    }

    @Test
    public void testGetAvailabilityObserver() {
        assertThat(mController.getAvailabilityObserver()).isEqualTo(mObserver);
    }

    private void setEnterpriseSetDefaultApps(Intent[] intents, int number) {
        final ApplicationInfo appInfo = new ApplicationInfo();
        appInfo.packageName = "app";
        for (int i = 0; i < number; i++) {
            final List<UserAppInfo> apps = new ArrayList<>(number);
            apps.add(new UserAppInfo(new UserInfo(i, "user." + i, UserInfo.FLAG_ADMIN), appInfo));
            when(mFeatureFactory.applicationFeatureProvider.findPersistentPreferredActivities(eq(i),
                    argThat(new MatchesIntents(intents)))).thenReturn(apps);
        }
    }

    private void configureUsers(int number) {
        final List<UserHandle> users = new ArrayList<>(number);
        for (int i = 0; i < 64; i++) {
            users.add(new UserHandle(i));
        }
        when(mFeatureFactory.userFeatureProvider.getUserProfiles()).thenReturn(users);
    }

    @Test
    public void testUpdateState() {
        setEnterpriseSetDefaultApps(EnterpriseDefaultApps.BROWSER.getIntents(), 1);
        setEnterpriseSetDefaultApps(EnterpriseDefaultApps.CAMERA.getIntents(), 2);
        setEnterpriseSetDefaultApps(EnterpriseDefaultApps.MAP.getIntents(), 4);
        setEnterpriseSetDefaultApps(EnterpriseDefaultApps.EMAIL.getIntents(), 8);
        setEnterpriseSetDefaultApps(EnterpriseDefaultApps.CALENDAR.getIntents(), 16);
        setEnterpriseSetDefaultApps(EnterpriseDefaultApps.CONTACTS.getIntents(), 32);
        setEnterpriseSetDefaultApps(EnterpriseDefaultApps.PHONE.getIntents(), 64);
        when(mContext.getResources().getQuantityString(R.plurals.enterprise_privacy_number_packages,
                127, 127)).thenReturn("127 apps");

        // As setEnterpriseSetDefaultApps uses fake Users, we need to list them via UserManager.
        configureUsers(64);

        final Preference preference = new Preference(mContext, null, 0, 0);
        mController.updateState(preference);
        assertThat(preference.getSummary()).isEqualTo("127 apps");
    }

    @Test
    public void testIsAvailable() {
        when(mFeatureFactory.applicationFeatureProvider.findPersistentPreferredActivities(anyInt(),
                anyObject())).thenReturn(new ArrayList<UserAppInfo>());
        assertThat(mController.isAvailable()).isFalse();
        verify(mObserver).onPreferenceAvailabilityUpdated(KEY_DEFAULT_APPS, false);

        setEnterpriseSetDefaultApps(EnterpriseDefaultApps.BROWSER.getIntents(), 1);
        configureUsers(1);
        assertThat(mController.isAvailable()).isTrue();
        verify(mObserver).onPreferenceAvailabilityUpdated(KEY_DEFAULT_APPS, true);
    }

    @Test
    public void testHandlePreferenceTreeClick() {
        assertThat(mController.handlePreferenceTreeClick(new Preference(mContext, null, 0, 0)))
                .isFalse();
    }

    @Test
    public void testGetPreferenceKey() {
        assertThat(mController.getPreferenceKey()).isEqualTo(KEY_DEFAULT_APPS);
    }

    private static class MatchesIntents extends ArgumentMatcher<Intent[]> {
        private final Intent[] mExpectedIntents;

        MatchesIntents(Intent[] intents) {
            mExpectedIntents = intents;
        }

        @Override
        public boolean matches(Object object) {
            final Intent[] actualIntents = (Intent[]) object;
            if (actualIntents == null) {
                return false;
            }
            if (actualIntents.length != mExpectedIntents.length) {
                return false;
            }
            for (int i = 0; i < mExpectedIntents.length; i++) {
                if (!mExpectedIntents[i].filterEquals(actualIntents[i])) {
                    return false;
                }
            }
            return true;
        }
    }
}
