package com.arcturus.haven.ui;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.RestrictionEntry;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.text.InputType;
import android.widget.EditText;
import android.widget.PopupMenu;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.view.ActionMode;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import com.arcturus.haven.R;
import com.arcturus.haven.services.IAppInstallCallback;
import com.arcturus.haven.services.IGetAppsCallback;
import com.arcturus.haven.services.ILoadIconCallback;
import com.arcturus.haven.services.IHavenService;
import com.arcturus.haven.services.HavenService;
import com.arcturus.haven.util.ApplicationInfoWrapper;
import com.arcturus.haven.util.LocalStorageManager;
import com.arcturus.haven.util.Utility;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class AppListFragment extends BaseFragment {
    // Menu Items
    private static final int MENU_ITEM_CLONE = 10001;
    private static final int MENU_ITEM_UNINSTALL = 10002;
    private static final int MENU_ITEM_FREEZE = 10003;
    private static final int MENU_ITEM_UNFREEZE = 10004;
    private static final int MENU_ITEM_LAUNCH = 10005;
    private static final int MENU_ITEM_CREATE_UNFREEZE_SHORTCUT = 10006;
    private static final int MENU_ITEM_AUTO_FREEZE = 10007;
    private static final int MENU_ITEM_ALLOW_CROSS_PROFILE_WIDGET = 10008;
    private static final int MENU_ITEM_ALLOW_CROSS_PROFILE_INTERACTION = 10009;
    private static final int MENU_ITEM_MANAGE_APP_RESTRICTIONS = 10010;

    private IHavenService mService = null;
    private boolean mIsRemote = false;
    private boolean mRefreshing = false;
    private Drawable mDefaultIcon = null;
    private ApplicationInfoWrapper mSelectedApp = null;

    // Cache of allowed Cross-profile widget providers
    // Only useful if this fragment manages the work profile
    private Set<String> mCrossProfileWidgetProviders = new HashSet<>();

    // Packages allowed to interact across profiles
    private Set<String> mCrossProfilePackages = new HashSet<>();

    // Views
    private RecyclerView mList = null;
    private AppListAdapter mAdapter = null;
    private SwipeRefreshLayout mSwipeRefresh = null;
    private MainUiViewModel mUiViewModel = null;

    // Variable to store the current action mode object if any
    private ActionMode mActionMode = null;

    static AppListFragment newInstance(IHavenService service, boolean isRemote) {
        AppListFragment fragment = new AppListFragment();
        Bundle args = new Bundle();
        args.putBinder("service", service.asBinder());
        args.putBoolean("is_remote", isRemote);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mDefaultIcon = getActivity().getPackageManager().getDefaultActivityIcon();
        IBinder service = getArguments().getBinder("service");
        mService = IHavenService.Stub.asInterface(service);
        mIsRemote = getArguments().getBoolean("is_remote");
    }

    @Override
    public void onResume() {
        super.onResume();
        refresh();
    }

    @Override
    public void onPause() {
        super.onPause();
        mSelectedApp = null;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_list, container, false);

        // Save the views
        mList = view.findViewById(R.id.fragment_list_recycler_view);
        mSwipeRefresh = view.findViewById(R.id.fragment_swipe_refresh);
        mAdapter = new AppListAdapter(mService, mDefaultIcon);
        mAdapter.setContextMenuHandler((info, v) -> {
            mSelectedApp = info;
            PopupMenu popup = new PopupMenu(requireContext(), v);
            populateAppMenu(popup.getMenu());
            if (popup.getMenu().size() == 0) {
                mSelectedApp = null;
                return;
            }
            popup.setOnMenuItemClickListener(this::onContextItemSelected);
            popup.setOnDismissListener(ignored -> mSelectedApp = null);
            popup.show();
        });
        if (mIsRemote) {
            // Allow multi-select actions if this is in the work profile
            // to allow things like multi-app unfreeze shortcuts
            mAdapter.allowMultiSelect();
            mAdapter.setActionModeHandler(this::createMultiSelectActionMode);
            mAdapter.setActionModeCancelHandler(() -> {
                if (mActionMode != null) {
                    mActionMode.finish();
                }
            });
        }
        mList.setAdapter(mAdapter);
        mList.setLayoutManager(new LinearLayoutManager(getActivity()));
        mList.setHasFixedSize(true);

        mSwipeRefresh.setColorSchemeColors(
                ContextCompat.getColor(requireContext(), R.color.md_theme_primary),
                ContextCompat.getColor(requireContext(), R.color.md_theme_secondary),
                ContextCompat.getColor(requireContext(), R.color.md_theme_tertiary));
        mSwipeRefresh.setProgressBackgroundColorSchemeColor(
                ContextCompat.getColor(requireContext(), R.color.md_theme_surfaceContainerHigh));
        mSwipeRefresh.setOnRefreshListener(this::refresh);
        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mUiViewModel = new ViewModelProvider(requireActivity()).get(MainUiViewModel.class);
        mUiViewModel.searchQuery().observe(getViewLifecycleOwner(), query -> {
            String filter = query == null || query.isEmpty() ? null : query;
            mAdapter.setSearchQuery(filter);
        });
        mUiViewModel.events().observe(getViewLifecycleOwner(), event -> {
            if (event == null || event.isRemoteProfile() != mIsRemote || !event.markHandled()) {
                return;
            }
            if (event.type() == MainUiViewModel.EventType.REFRESH_APPS) {
                refresh();
            } else if (event.type() == MainUiViewModel.EventType.CONTEXT_MENU_CLOSED) {
                mSelectedApp = null;
            }
        });
    }

    void refresh() {
        if (mAdapter == null) return;
        if (mRefreshing) return;
        if (mAdapter.isMultiSelectMode()) {
            mSwipeRefresh.setRefreshing(false);
            return; // Disallow refreshing when we are multi-selecting
        }
        mRefreshing = true;
        mSwipeRefresh.setRefreshing(true);

        try {
            mService.getApps(new IGetAppsCallback.Stub() {
                @Override
                public void callback(List<ApplicationInfoWrapper> apps) {
                    if (mIsRemote) {
                        mCrossProfileWidgetProviders.clear();
                        mCrossProfilePackages.clear();

                        // Update the cross-profile packages / widget providers list
                        try {
                            mCrossProfileWidgetProviders.addAll(mService.getCrossProfileWidgetProviders());

                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                                mCrossProfilePackages.addAll(mService.getCrossProfilePackages());
                        } catch (RemoteException ignored) {

                        }
                    }

                    if (mIsRemote) {
                        Utility.deleteMissingApps(
                                LocalStorageManager.PREF_AUTO_FREEZE_LIST_WORK_PROFILE,
                                apps);
                    }
                    runOnUiThread(() -> {
                        mSwipeRefresh.setRefreshing(false);
                        mAdapter.setData(apps);
                        mRefreshing = false;
                    });
                }
            }, ((MainActivity) getActivity()).mShowAll);
        } catch (RemoteException e) {
            // Just... do nothing for now
        }
    }

    // Enter multi-select mode for work profile
    boolean createMultiSelectActionMode() {
        mActionMode = ((AppCompatActivity) getActivity()).startSupportActionMode(new ActionMode.Callback() {
            @Override
            public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                menu.add(Menu.NONE, MENU_ITEM_CREATE_UNFREEZE_SHORTCUT, Menu.NONE, R.string.create_unfreeze_shortcut)
                        .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
                return true;
            }

            @Override
            public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
                mode.setTitle(R.string.batch_operation);
                return true;
            }

            @Override
            public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
                List<ApplicationInfoWrapper> list = mAdapter.getSelectedItems();
                if (list == null) {
                    // We can't perform any action on nothing
                    return false;
                }

                switch (item.getItemId()) {
                    case MENU_ITEM_CREATE_UNFREEZE_SHORTCUT:
                        // When multiple apps are selected for creating unfreeze & launch shortcut
                        // the shortcut will launch the first one, before which all the others
                        // will be unfrozen. This helps apps that has dependency relationships.
                        loadIconAndAddUnfreezeShortcut(list.get(0), list);
                        mode.finish();
                        return true;
                }

                return false;
            }

            @Override
            public void onDestroyActionMode(ActionMode mode) {
                mActionMode = null;
                mAdapter.cancelMultiSelectMode();
            }
        });
        return true;
    }

    private void populateAppMenu(Menu menu) {
        if (mSelectedApp == null) return;

        if (mIsRemote) {
            if (!mSelectedApp.isSystem())
                menu.add(Menu.NONE, MENU_ITEM_CLONE, Menu.NONE, R.string.clone_to_main_profile);
            // Freezing / Unfreezing is only available in profiles that we can control
            if (mSelectedApp.isHidden()) {
                menu.add(Menu.NONE, MENU_ITEM_UNFREEZE, Menu.NONE, R.string.unfreeze_app);
                menu.add(Menu.NONE, MENU_ITEM_LAUNCH, Menu.NONE, R.string.unfreeze_and_launch);
            } else {
                menu.add(Menu.NONE, MENU_ITEM_FREEZE, Menu.NONE, R.string.freeze_app);
                menu.add(Menu.NONE, MENU_ITEM_LAUNCH, Menu.NONE, R.string.launch);
            }
            // Cross-profile widget / packages settings is also limited to work profile
            MenuItem crossProfileWdiegt =
                    menu.add(Menu.NONE, MENU_ITEM_ALLOW_CROSS_PROFILE_WIDGET, Menu.NONE,
                            R.string.allow_cross_profile_widgets);
            crossProfileWdiegt.setCheckable(true);
            crossProfileWdiegt.setChecked(
                    mCrossProfileWidgetProviders.contains(mSelectedApp.getPackageName()));

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                MenuItem crossProfileInteraction =
                        menu.add(Menu.NONE, MENU_ITEM_ALLOW_CROSS_PROFILE_INTERACTION, Menu.NONE,
                                R.string.allow_cross_profile_interaction);
                crossProfileInteraction.setCheckable(true);
                crossProfileInteraction.setChecked(
                        mCrossProfilePackages.contains(mSelectedApp.getPackageName()));
            }

            if (!mSelectedApp.isHidden()) {
                menu.add(Menu.NONE, MENU_ITEM_MANAGE_APP_RESTRICTIONS, Menu.NONE,
                        R.string.manage_app_restrictions);
            }

            // TODO: If we implement God Mode (i.e. Haven as device owner), we should
            // TODO: use two different lists to store auto freeze apps because we'll be
            // TODO: able to freeze apps in main profile.
            MenuItem autoFreeze = menu.add(Menu.NONE, MENU_ITEM_AUTO_FREEZE, Menu.NONE, R.string.auto_freeze);
            autoFreeze.setCheckable(true);
            autoFreeze.setChecked(
                    LocalStorageManager.getInstance().stringListContains(
                            LocalStorageManager.PREF_AUTO_FREEZE_LIST_WORK_PROFILE, mSelectedApp.getPackageName()));
            menu.add(Menu.NONE, MENU_ITEM_CREATE_UNFREEZE_SHORTCUT, Menu.NONE, R.string.create_unfreeze_shortcut);
        } else {
            menu.add(Menu.NONE, MENU_ITEM_CLONE, Menu.NONE, R.string.clone_to_work_profile);
        }

        if (!mSelectedApp.isSystem()) {
            // We can't uninstall system apps in both cases
            // but we'll be able to "freeze" them
            menu.add(Menu.NONE, MENU_ITEM_UNINSTALL, Menu.NONE, R.string.uninstall_app);
        }

    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        if (mSelectedApp == null) return false;

        switch (item.getItemId()) {
            case MENU_ITEM_CLONE:
                if (Utility.isMIUI() && !mSelectedApp.isSystem()) {
                    // Cannot clone non-system apps on MIUI
                    // Keep this variable intact when showing the dialog
                    final ApplicationInfoWrapper selectedApp = mSelectedApp;
                    new MaterialAlertDialogBuilder(requireContext())
                            .setMessage(R.string.miui_cannot_clone)
                            .setPositiveButton(android.R.string.ok, null)
                            .setNegativeButton(R.string.continue_anyway, (diag, button) ->
                                    installOrUninstall(selectedApp, true))
                            .show();
                } else {
                    installOrUninstall(mSelectedApp, true);
                }
                return true;
            case MENU_ITEM_UNINSTALL:
                installOrUninstall(mSelectedApp, false);
                return true;
            case MENU_ITEM_FREEZE:
                try {
                    mService.freezeApp(mSelectedApp);
                } catch (RemoteException e) {

                }
                Toast.makeText(getContext(),
                        getString(R.string.freeze_success, mSelectedApp.getLabel()), Toast.LENGTH_SHORT).show();
                refresh();
                return true;
            case MENU_ITEM_UNFREEZE:
                try {
                    mService.unfreezeApp(mSelectedApp);
                } catch (RemoteException e) {

                }
                Toast.makeText(getContext(),
                        getString(R.string.unfreeze_success, mSelectedApp.getLabel()), Toast.LENGTH_SHORT).show();
                refresh();
                return true;
            case MENU_ITEM_LAUNCH:
                // LAUNCH and UNFREEZE_AND_LAUNCH share the same ID
                // because the implementation of UNFREEZE_AND_LAUNCH in DummyActivity
                // will work for both
                Intent intent = new Intent(DummyActivity.UNFREEZE_AND_LAUNCH);
                intent.setComponent(new ComponentName(getContext(), DummyActivity.class));
                intent.putExtra("packageName", mSelectedApp.getPackageName());
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                DummyActivity.registerSameProcessRequest(intent);
                startActivity(intent);
                return true;
            case MENU_ITEM_CREATE_UNFREEZE_SHORTCUT:
                loadIconAndAddUnfreezeShortcut(mSelectedApp, null);
                return true;
            case MENU_ITEM_AUTO_FREEZE:
                boolean orig = LocalStorageManager.getInstance().stringListContains(
                        LocalStorageManager.PREF_AUTO_FREEZE_LIST_WORK_PROFILE, mSelectedApp.getPackageName());

                if (!orig) {
                    LocalStorageManager.getInstance().appendStringList(
                            LocalStorageManager.PREF_AUTO_FREEZE_LIST_WORK_PROFILE, mSelectedApp.getPackageName());
                } else {
                    LocalStorageManager.getInstance().removeFromStringList(
                            LocalStorageManager.PREF_AUTO_FREEZE_LIST_WORK_PROFILE, mSelectedApp.getPackageName());
                }
                return true;
            case MENU_ITEM_ALLOW_CROSS_PROFILE_WIDGET: {
                boolean newState = !item.isChecked();
                try {
                    if (mService.setCrossProfileWidgetProviderEnabled(mSelectedApp.getPackageName(), newState)) {
                        item.setChecked(newState);

                        // Update the cached list of all cross-profile widget providers
                        if (newState) {
                            mCrossProfileWidgetProviders.add(mSelectedApp.getPackageName());
                        } else {
                            mCrossProfileWidgetProviders.remove(mSelectedApp.getPackageName());
                        }
                    }
                } catch (RemoteException e) {

                }
                return true;
            }
            case MENU_ITEM_MANAGE_APP_RESTRICTIONS:
                showManagedApplicationRestrictions(mSelectedApp.getPackageName(), mSelectedApp.getLabel());
                return true;
            case MENU_ITEM_ALLOW_CROSS_PROFILE_INTERACTION: {
                boolean newState = !item.isChecked();
                if (newState) {
                    mCrossProfilePackages.add(mSelectedApp.getPackageName());
                } else {
                    mCrossProfilePackages.remove(mSelectedApp.getPackageName());
                }
                try {
                    mService.setCrossProfilePackages(new ArrayList<>(mCrossProfilePackages));
                    item.setChecked(newState);
                } catch (RemoteException ignored) {

                }
                return true;
            }
        }

        return super.onContextItemSelected(item);
    }

    private void showManagedApplicationRestrictions(String packageName, String appLabel) {
        mSelectedApp = null;
        if (getActivity() != null) {
            ManagedRestrictionsDialog.show(requireActivity(), mService, packageName, appLabel, true);
        }
    }

    void installOrUninstall(final ApplicationInfoWrapper app, final boolean isInstall) {
        mSelectedApp = null;
        IAppInstallCallback.Stub callback = new IAppInstallCallback.Stub() {
            @Override
            public void callback(int result) {
                runOnUiThread(() ->
                        installAppCallback(result, app, isInstall));
            }
        };

        try {
            if (isInstall) {
                ((MainActivity) getActivity()).getOtherService(mIsRemote)
                        .installApp(app, callback);
            } else {
                mService.uninstallApp(app, callback);
            }
        } catch (RemoteException e) {
            // TODO: Maybe tell the user?
        }
    }

    void installAppCallback(int result, ApplicationInfoWrapper app, boolean isInstall) {
        if (result == Activity.RESULT_OK) {
            String message = getString(isInstall ? R.string.clone_success : R.string.uninstall_success);
            message = String.format(message, app.getLabel());
            Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
            refresh();
        } else if (result == HavenService.RESULT_CANNOT_INSTALL_SYSTEM_APP) {
            Toast.makeText(getContext(),
                    getString(isInstall ? R.string.clone_fail_system_app :
                            R.string.uninstall_fail_system_app), Toast.LENGTH_SHORT).show();
        } else if (result == Activity.RESULT_CANCELED) {
            Toast.makeText(getContext(), R.string.app_operation_cancelled, Toast.LENGTH_SHORT).show();
        }
    }

    void loadIconAndAddUnfreezeShortcut(final ApplicationInfoWrapper app, final List<ApplicationInfoWrapper> linkedApps) {
        try {
            // Call the service to load the latest icon
            mService.loadIcon(app, new ILoadIconCallback.Stub() {
                @Override
                public void callback(Bitmap icon) {
                    runOnUiThread(() -> addUnfreezeShortcut(app, linkedApps, icon));
                }
            });
        } catch (RemoteException e) {
            // Ignore
        }
    }

    void addUnfreezeShortcut(ApplicationInfoWrapper app, List<ApplicationInfoWrapper> linkedApps, Bitmap icon) {
        String id = "Haven-" + app.getPackageName();
        // First, create an Intent to be sent when clicking on the shortcut
        Intent launchIntent = new Intent(DummyActivity.PUBLIC_UNFREEZE_AND_LAUNCH);
        launchIntent.setComponent(new ComponentName(getContext(), DummyActivity.class));
        launchIntent.putExtra("packageName", app.getPackageName());
        if (linkedApps != null) {
            String appListStr = linkedApps.stream()
                    .map(ApplicationInfoWrapper::getPackageName).collect(Collectors.joining(","));
            id += appListStr.hashCode();
            // Multiple apps can be added so that
            // these "linked" apps are all unfrozen
            // before launching the main app
            // Note: PersistableBundle doesn't support String array lists inside them
            launchIntent.putExtra("linkedPackages", appListStr);
        }
        launchIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        // Then tell the launcher to add the shortcut
        Utility.createLauncherShortcut(getContext(), launchIntent,
                Icon.createWithBitmap(icon), id,
                app.getLabel());
    }
}
