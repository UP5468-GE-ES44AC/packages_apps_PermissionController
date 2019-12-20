/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.permissioncontroller.permission.ui.handheld;

import static com.android.permissioncontroller.Constants.EXTRA_SESSION_ID;
import static com.android.permissioncontroller.Constants.INVALID_SESSION_ID;
import static com.android.permissioncontroller.permission.ui.GrantPermissionsViewHandler.DENIED;
import static com.android.permissioncontroller.permission.ui.GrantPermissionsViewHandler.DENIED_DO_NOT_ASK_AGAIN;
import static com.android.permissioncontroller.permission.ui.GrantPermissionsViewHandler.GRANTED_ALWAYS;
import static com.android.permissioncontroller.permission.ui.GrantPermissionsViewHandler.GRANTED_FOREGROUND_ONLY;

import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.UserHandle;
import android.text.BidiFormatter;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.widget.NestedScrollView;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.ViewModelProvider;

import com.android.permissioncontroller.R;
import com.android.permissioncontroller.permission.ui.AppPermissionActivity;
import com.android.permissioncontroller.permission.ui.GrantPermissionsViewHandler;
import com.android.permissioncontroller.permission.ui.handheld.AppPermissionViewModel.ButtonState;
import com.android.permissioncontroller.permission.ui.handheld.AppPermissionViewModel.ButtonType;
import com.android.permissioncontroller.permission.ui.handheld.AppPermissionViewModel.ChangeTarget;
import com.android.permissioncontroller.permission.utils.KotlinUtils;
import com.android.settingslib.RestrictedLockUtils;
import com.android.settingslib.RestrictedLockUtils.EnforcedAdmin;
import com.android.settingslib.widget.ActionBarShadowController;

import java.util.Map;

import kotlin.Pair;

/**
 * Show and manage a single permission group for an app.
 *
 * <p>Allows the user to control whether the app is granted the permission.
 */
public class AppPermissionFragment extends SettingsWithLargeHeader {
    private static final String LOG_TAG = "AppPermissionFragment";

    static final String GRANT_CATEGORY = "grant_category";

    private @NonNull AppPermissionViewModel mViewModel;
    private @NonNull RadioButton mAllowButton;
    private @NonNull RadioButton mAllowAlwaysButton;
    private @NonNull RadioButton mAllowForegroundButton;
    private @NonNull RadioButton mAskOneTimeButton;
    private @NonNull RadioButton mAskButton;
    private @NonNull RadioButton mDenyButton;
    private @NonNull RadioButton mDenyForegroundButton;
    private @NonNull View mDivider;
    private @NonNull ViewGroup mWidgetFrame;
    private @NonNull TextView mPermissionDetails;
    private @NonNull NestedScrollView mNestedScrollView;
    private @NonNull String mPackageName;
    private @NonNull String mPermGroupName;
    private @NonNull UserHandle mUser;
    private boolean mIsInitialLoad;

    private @NonNull String mPackageLabel;
    private @NonNull String mPermGroupLabel;
    private Drawable mPackageIcon;

    /**
     * @return A new fragment
     */
    public static @NonNull AppPermissionFragment newInstance(@NonNull String packageName,
            @Nullable String permName, @Nullable String groupName,
            @NonNull UserHandle userHandle, @Nullable String caller, long sessionId) {
        AppPermissionFragment fragment = new AppPermissionFragment();
        fragment.setArguments(createArgs(packageName, permName, groupName, userHandle, caller,
                sessionId, null));
        return fragment;
    }

    /**
     * Create a bundle with the arguments needed by this fragment
     *
     * @param packageName The name of the package
     * @param permName The name of the permission whose group this fragment is for (optional)
     * @param groupName The name of the permission group (required if permName not specified)
     * @param userHandle The user of the app permission group
     * @param caller The name of the fragment we called from
     * @param sessionId The current session ID
     * @param grantCategory The grant status of this app permission group. Used to initially set
     * the button state
     * @return A bundle with all of the args placed
     */
    public static Bundle createArgs(@NonNull String packageName,
            @Nullable String permName, @Nullable String groupName,
            @NonNull UserHandle userHandle, @Nullable String caller, long sessionId, @Nullable
            String grantCategory) {
        Bundle arguments = new Bundle();
        arguments.putString(Intent.EXTRA_PACKAGE_NAME, packageName);
        if (groupName == null) {
            arguments.putString(Intent.EXTRA_PERMISSION_NAME, permName);
        } else {
            arguments.putString(Intent.EXTRA_PERMISSION_GROUP_NAME, groupName);
        }
        arguments.putParcelable(Intent.EXTRA_USER, userHandle);
        arguments.putString(AppPermissionActivity.EXTRA_CALLER_NAME, caller);
        arguments.putLong(EXTRA_SESSION_ID, sessionId);
        arguments.putString(GRANT_CATEGORY, grantCategory);
        return arguments;
    }


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        ActionBar ab = getActivity().getActionBar();
        if (ab != null) {
            ab.setDisplayHomeAsUpEnabled(true);
        }

        mPackageName = getArguments().getString(Intent.EXTRA_PACKAGE_NAME);
        mPermGroupName = getArguments().getString(Intent.EXTRA_PERMISSION_GROUP_NAME);
        if (mPermGroupName == null) {
            mPermGroupName = getArguments().getString(Intent.EXTRA_PERMISSION_NAME);
        }
        mUser = getArguments().getParcelable(Intent.EXTRA_USER);
        mPackageLabel = BidiFormatter.getInstance().unicodeWrap(
                KotlinUtils.INSTANCE.getPackageLabel(getActivity().getApplication(), mPackageName,
                        mUser));
        mPermGroupLabel = KotlinUtils.INSTANCE.getPermGroupLabel(getContext(),
                mPermGroupName).toString();
        mPackageIcon = KotlinUtils.INSTANCE.getBadgedPackageIcon(getActivity().getApplication(),
                mPackageName, mUser);
        long sessionId = getArguments().getLong(EXTRA_SESSION_ID, INVALID_SESSION_ID);

        AppPermissionViewModelFactory factory = new AppPermissionViewModelFactory(
                getActivity().getApplication(), mPackageName, mPermGroupName, mUser, sessionId);
        mViewModel = new ViewModelProvider(this, factory).get(AppPermissionViewModel.class);

        mViewModel.getButtonStateLiveData().observe(this, this::setRadioButtonsState);
        mViewModel.getDetailResIdLiveData().observe(this, this::setDetail);
        mViewModel.getShowAdminSupportLiveData().observe(this, this::setAdminSupportDetail);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        Context context = getContext();
        ViewGroup root = (ViewGroup) inflater.inflate(R.layout.app_permission, container, false);

        mIsInitialLoad = true;

        setHeader(mPackageIcon, mPackageLabel, null, null, false);
        updateHeader(root.requireViewById(R.id.large_header));

        ((TextView) root.requireViewById(R.id.permission_message)).setText(
                context.getString(R.string.app_permission_header, mPermGroupLabel));

        TextView usageView = root.requireViewById(R.id.usage_summary);
        if (mViewModel.shouldShowPermissionUsage()) {
            usageView.setText(mViewModel.getUsageSummary(getContext(), mPermGroupLabel,
                    mPackageLabel));
        } else if (mViewModel.shouldShowUsageView()) {
            usageView.setText(R.string.app_permission_footer_not_available);
        } else {
            usageView.setVisibility(View.GONE);
        }

        String caller = getArguments().getString(AppPermissionActivity.EXTRA_CALLER_NAME);

        TextView footer1Link = root.requireViewById(R.id.footer_link_1);
        footer1Link.setText(context.getString(R.string.app_permission_footer_app_permissions_link,
                mPackageLabel));
        setBottomLinkState(footer1Link, caller, Intent.ACTION_MANAGE_APP_PERMISSIONS);

        TextView footer2Link = root.requireViewById(R.id.footer_link_2);
        footer2Link.setText(context.getString(R.string.app_permission_footer_permission_apps_link));
        setBottomLinkState(footer2Link, caller, Intent.ACTION_MANAGE_PERMISSION_APPS);

        mAllowButton = root.requireViewById(R.id.allow_radio_button);
        mAllowAlwaysButton = root.requireViewById(R.id.allow_always_radio_button);
        mAllowForegroundButton = root.requireViewById(R.id.allow_foreground_only_radio_button);
        mAskOneTimeButton = root.requireViewById(R.id.ask_one_time_radio_button);
        mAskButton = root.requireViewById(R.id.ask_radio_button);
        mDenyButton = root.requireViewById(R.id.deny_radio_button);
        mDenyForegroundButton = root.requireViewById(R.id.deny_foreground_radio_button);
        mDivider = root.requireViewById(R.id.two_target_divider);
        mWidgetFrame = root.requireViewById(R.id.widget_frame);
        mPermissionDetails = root.requireViewById(R.id.permission_details);

        mNestedScrollView = root.requireViewById(R.id.nested_scroll_view);

        if (mViewModel.getButtonStateLiveData().getValue() != null) {
            setRadioButtonsState(mViewModel.getButtonStateLiveData().getValue());
        } else {
            mAllowButton.setVisibility(View.GONE);
            mAllowAlwaysButton.setVisibility(View.GONE);
            mAllowForegroundButton.setVisibility(View.GONE);
            mAskOneTimeButton.setVisibility(View.GONE);
            mAskButton.setVisibility(View.GONE);
            mDenyButton.setVisibility(View.GONE);
            mDenyForegroundButton.setVisibility(View.GONE);
        }

        getActivity().setTitle(
                getPreferenceManager().getContext().getString(R.string.app_permission_title,
                        mPermGroupLabel));

        return root;
    }

    private void setBottomLinkState(TextView view, String caller, String action) {
        if ((caller.equals(AppPermissionGroupsFragment.class.getName())
                && action.equals(Intent.ACTION_MANAGE_APP_PERMISSIONS))
                || (caller.equals(PermissionAppsFragment.class.getName()))
                && action.equals(Intent.ACTION_MANAGE_PERMISSION_APPS)) {
            view.setVisibility(View.GONE);
        } else {
            view.setOnClickListener((v) -> {
                mViewModel.showBottomLinkPage(this, action);
            });
        }
    }

    @Override
    public void onStart() {
        super.onStart();

        ActionBar ab = getActivity().getActionBar();
        if (ab != null) {
            ab.setElevation(0);
        }

        ActionBarShadowController.attachToView(getActivity(), getLifecycle(), mNestedScrollView);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            getActivity().onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void setRadioButtonsState(Map<ButtonType, ButtonState> states) {
        if (states == null && mViewModel.getButtonStateLiveData().isInitialized()) {
            mViewModel.finishActivity(getActivity());
            return;
        } else if (states == null) {
            return;
        }

        mAllowButton.setOnClickListener((v) -> {
            mViewModel.requestChange(true, false, this, ChangeTarget.CHANGE_FOREGROUND);
            setResult(GRANTED_ALWAYS);
        });
        mAllowAlwaysButton.setOnClickListener((v) -> {
            mViewModel.requestChange(true, false, this, ChangeTarget.CHANGE_BOTH);
            setResult(GRANTED_ALWAYS);
        });
        mAllowForegroundButton.setOnClickListener((v) -> {
            mViewModel.requestChange(true, false, this, ChangeTarget.CHANGE_FOREGROUND);
            mViewModel.requestChange(false, false, this, ChangeTarget.CHANGE_BACKGROUND);
            setResult(GRANTED_FOREGROUND_ONLY);
        });
        // mAskOneTimeButton only shows if checked hence should do nothing
        mAskButton.setOnClickListener((v) -> {
            mViewModel.requestChange(false, false, this, ChangeTarget.CHANGE_BOTH);
            setResult(DENIED);
        });
        mDenyButton.setOnClickListener((v) -> {
            mViewModel.requestChange(false, true, this, ChangeTarget.CHANGE_BOTH);
            setResult(DENIED_DO_NOT_ASK_AGAIN);
        });
        mDenyForegroundButton.setOnClickListener((v) -> {
            mViewModel.requestChange(false, true, this, ChangeTarget.CHANGE_FOREGROUND);
            setResult(DENIED_DO_NOT_ASK_AGAIN);
        });

        // TODO(ntmyren): pass button states in a non-order specific way
        setButtonState(mAllowButton, states.get(ButtonType.ALLOW));
        setButtonState(mAllowAlwaysButton, states.get(ButtonType.ALLOW_ALWAYS));
        setButtonState(mAllowForegroundButton, states.get(ButtonType.ALLOW_FOREGROUND));
        setButtonState(mAskOneTimeButton, states.get(ButtonType.ASK_ONCE));
        setButtonState(mAskButton, states.get(ButtonType.ASK));
        setButtonState(mDenyButton, states.get(ButtonType.DENY));
        setButtonState(mDenyForegroundButton, states.get(ButtonType.DENY_FOREGROUND));

        mIsInitialLoad = false;
    }

    private void setButtonState(RadioButton button, AppPermissionViewModel.ButtonState state) {
        int visible = state.isShown() ? View.VISIBLE : View.GONE;
        button.setVisibility(visible);
        if (state.isShown()) {
            button.setChecked(state.isChecked());
            button.setEnabled(state.isEnabled());
        }
        if (mIsInitialLoad) {
            button.jumpDrawablesToCurrentState();
        }
    }



    private void setResult(@GrantPermissionsViewHandler.Result int result) {
        Intent intent = new Intent()
                .putExtra(AppPermissionActivity.EXTRA_RESULT_PERMISSION_INTERACTED, mPermGroupName)
                .putExtra(AppPermissionActivity.EXTRA_RESULT_PERMISSION_RESULT, result);
        getActivity().setResult(Activity.RESULT_OK, intent);
    }

    private void setDetail(Pair<Integer, Integer> detailResIds) {
        if (detailResIds == null) {
            mWidgetFrame.setVisibility(View.GONE);
            mDivider.setVisibility(View.GONE);
            return;
        }
        mWidgetFrame.setVisibility(View.VISIBLE);
        if (detailResIds.getSecond() != null) {
            // If the permissions are individually controlled, also show a link to the page that
            // lets you control them.
            mDivider.setVisibility(View.VISIBLE);
            showRightIcon(R.drawable.ic_settings);
            mWidgetFrame.setOnClickListener(v -> mViewModel.showAllPermissions(this));
            mPermissionDetails.setText(getPreferenceManager().getContext().getString(
                    detailResIds.getFirst(), detailResIds.getSecond()));
        } else {
            mPermissionDetails.setText(getPreferenceManager().getContext().getString(
                    detailResIds.getFirst()));
        }
        mPermissionDetails.setVisibility(View.VISIBLE);

    }

    private void setAdminSupportDetail(EnforcedAdmin admin) {
        if (admin != null) {
            showRightIcon(R.drawable.ic_info);
            mWidgetFrame.setOnClickListener(v ->
                    RestrictedLockUtils.sendShowAdminSupportDetailsIntent(getContext(), admin)
            );
        } else {
            mWidgetFrame.removeAllViews();
        }
    }

    /**
     * Show the given icon on the right of the first radio button.
     *
     * @param iconId the resourceId of the drawable to use.
     */
    private void showRightIcon(int iconId) {
        mWidgetFrame.removeAllViews();
        ImageView imageView = new ImageView(getPreferenceManager().getContext());
        imageView.setImageResource(iconId);
        mWidgetFrame.addView(imageView);
        mWidgetFrame.setVisibility(View.VISIBLE);
    }

    /**
     * Show a dialog that warns the user that she/he is about to revoke permissions that were
     * granted by default.
     *
     *
     * The order of operation to revoke a permission granted by default is:
     *
     *  1. `showDefaultDenyDialog`
     *  1. [DefaultDenyDialog.onCreateDialog]
     *  1. [AppPermissionViewModel.onDenyAnyWay]
     * TODO: Remove once data can be passed between dialogs and fragments with nav component
     *
     * @param changeTarget Whether background or foreground should be changed
     * @param messageId The Id of the string message to show
     * @param userFixed Whether the permission state should be user fixed
     */
    void showDefaultDenyDialog(ChangeTarget changeTarget, @StringRes int messageId,
            boolean userFixed) {
        Bundle args = getArguments().deepCopy();
        args.putInt(DefaultDenyDialog.MSG, messageId);
        args.putSerializable(DefaultDenyDialog.CHANGE_TARGET, changeTarget);
        args.putBoolean(DefaultDenyDialog.USER_FIXED, userFixed);
        DefaultDenyDialog defaultDenyDialog = new DefaultDenyDialog();
        defaultDenyDialog.setCancelable(true);
        defaultDenyDialog.setArguments(args);
        defaultDenyDialog.show(getChildFragmentManager().beginTransaction(),
                DefaultDenyDialog.class.getName());
    }

    /**
     * A dialog warning the user that they are about to deny a permission that was granted by
     * default, or that they are denying a permission on a Pre-M app
     *
     * @see #showDefaultDenyDialog(ChangeTarget, int, boolean)
     */
    public static class DefaultDenyDialog extends DialogFragment {
        static final String MSG = DefaultDenyDialog.class.getName() + ".arg.msg";
        static final String CHANGE_TARGET = DefaultDenyDialog.class.getName()
                + ".arg.changeTarget";
        private static final String KEY = DefaultDenyDialog.class.getName() + ".arg.key";
        static final String USER_FIXED = DefaultDenyDialog.class.getName()
                + ".arg.userFixed";

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            AppPermissionFragment fragment = (AppPermissionFragment) getParentFragment();
            AlertDialog.Builder b = new AlertDialog.Builder(getContext())
                    .setMessage(getArguments().getInt(MSG))
                    .setNegativeButton(R.string.cancel,
                            (DialogInterface dialog, int which) -> dialog.cancel())
                    .setPositiveButton(R.string.grant_dialog_button_deny_anyway,
                            (DialogInterface dialog, int which) ->
                                    fragment.mViewModel.onDenyAnyWay((ChangeTarget)
                                            getArguments().getSerializable(CHANGE_TARGET),
                                            getArguments().getBoolean(USER_FIXED, false)));
            Dialog d = b.create();
            d.setCanceledOnTouchOutside(true);
            return d;
        }

        @Override
        public void onCancel(DialogInterface dialog) {
            AppPermissionFragment fragment = (AppPermissionFragment) getParentFragment();
            fragment.setRadioButtonsState(fragment.mViewModel.getButtonStateLiveData().getValue());
        }
    }
}