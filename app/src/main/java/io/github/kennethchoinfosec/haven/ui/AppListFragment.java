package io.github.kennethchoinfosec.haven.ui;

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

import io.github.kennethchoinfosec.haven.R;
import io.github.kennethchoinfosec.haven.services.IAppInstallCallback;
import io.github.kennethchoinfosec.haven.services.IGetAppsCallback;
import io.github.kennethchoinfosec.haven.services.ILoadIconCallback;
import io.github.kennethchoinfosec.haven.services.IHavenService;
import io.github.kennethchoinfosec.haven.services.HavenService;
import io.github.kennethchoinfosec.haven.util.ApplicationInfoWrapper;
import io.github.kennethchoinfosec.haven.util.LocalStorageManager;
import io.github.kennethchoinfosec.haven.util.Utility;

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
        new Thread(() -> {
            try {
                Bundle response = mService.getManagedApplicationRestrictions(packageName);
                Bundle values = response.getBundle("values");
                ArrayList<Bundle> schema = response.getParcelableArrayList("schema");
                Activity activity = getActivity();
                if (activity == null) return;
                activity.runOnUiThread(() -> {
                    if (!isAdded()) return;
                    if (schema == null || schema.isEmpty()) {
                        new MaterialAlertDialogBuilder(requireContext())
                                .setTitle(getString(R.string.managed_restrictions_title, appLabel))
                                .setMessage(R.string.managed_restrictions_none)
                                .setPositiveButton(android.R.string.ok, null)
                                .show();
                        return;
                    }
                    Bundle current = values == null ? new Bundle() : values;
                    String[] labels = schema.stream()
                            .map(entry -> {
                                String title = entry.getString("title");
                                String key = entry.getString("key");
                                return title == null || title.trim().isEmpty() ? key : title;
                            })
                            .toArray(String[]::new);
                    new MaterialAlertDialogBuilder(requireContext())
                            .setTitle(getString(R.string.managed_restrictions_title, appLabel))
                            .setItems(labels, (dialog, which) -> editManagedRestriction(
                                    packageName, schema.get(which), current))
                            .setNegativeButton(android.R.string.cancel, null)
                            .show();
                });
            } catch (Exception e) {
                Activity activity = getActivity();
                if (activity != null) {
                    activity.runOnUiThread(() -> Toast.makeText(activity,
                            R.string.managed_restrictions_load_failed, Toast.LENGTH_SHORT).show());
                }
            }
        }).start();
    }

    private void editManagedRestriction(String packageName, Bundle entry, Bundle current) {
        String key = entry.getString("key");
        String title = entry.getString("title");
        if (title == null || title.trim().isEmpty()) title = key;
        String description = entry.getString("description");
        int type = entry.getInt("type", RestrictionEntry.TYPE_NULL);

        if (type == RestrictionEntry.TYPE_BOOLEAN) {
            boolean enabled = current.containsKey(key)
                    ? current.getBoolean(key) : entry.getBoolean("defaultBoolean");
            String[] options = {getString(R.string.managed_restrictions_disabled),
                    getString(R.string.managed_restrictions_enabled)};
            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle(title)
                    .setMessage(description)
                    .setSingleChoiceItems(options, enabled ? 1 : 0, (dialog, which) -> {
                        Bundle updated = new Bundle(current);
                        updated.putBoolean(key, which == 1);
                        saveManagedApplicationRestrictions(packageName, updated);
                        dialog.dismiss();
                    })
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
            return;
        }

        if (type == RestrictionEntry.TYPE_CHOICE || type == RestrictionEntry.TYPE_MULTI_SELECT) {
            String[] labels = entry.getStringArray("choiceEntries");
            String[] choices = entry.getStringArray("choiceValues");
            if (labels == null || choices == null || labels.length == 0 || labels.length != choices.length) {
                showUnsupportedRestriction(title, description);
                return;
            }
            Set<String> selected = selectedRestrictionValues(current, entry, key);
            if (type == RestrictionEntry.TYPE_CHOICE) {
                String currentValue = selected.isEmpty() ? null : selected.iterator().next();
                int checked = Arrays.asList(choices).indexOf(currentValue);
                new MaterialAlertDialogBuilder(requireContext())
                        .setTitle(title)
                        .setMessage(description)
                        .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                            Bundle updated = new Bundle(current);
                            updated.putStringArray(key, new String[]{choices[which]});
                            saveManagedApplicationRestrictions(packageName, updated);
                            dialog.dismiss();
                        })
                        .setNegativeButton(android.R.string.cancel, null)
                        .show();
            } else {
                boolean[] checked = new boolean[choices.length];
                for (int i = 0; i < choices.length; i++) checked[i] = selected.contains(choices[i]);
                Set<String> selectedValues = new HashSet<>(selected);
                new MaterialAlertDialogBuilder(requireContext())
                        .setTitle(title)
                        .setMessage(description)
                        .setMultiChoiceItems(labels, checked, (dialog, which, isChecked) -> {
                            if (isChecked) selectedValues.add(choices[which]);
                            else selectedValues.remove(choices[which]);
                        })
                        .setNegativeButton(android.R.string.cancel, null)
                        .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                            Bundle updated = new Bundle(current);
                            updated.putStringArray(key, selectedValues.toArray(new String[0]));
                            saveManagedApplicationRestrictions(packageName, updated);
                        })
                        .show();
            }
            return;
        }

        if (type == RestrictionEntry.TYPE_STRING || type == RestrictionEntry.TYPE_INTEGER) {
            EditText input = new EditText(requireContext());
            input.setSingleLine(true);
            String currentText;
            if (type == RestrictionEntry.TYPE_INTEGER) {
                input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED);
                currentText = String.valueOf(current.containsKey(key)
                        ? current.getInt(key) : entry.getInt("defaultInteger"));
            } else {
                input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
                currentText = current.getString(key, entry.getString("defaultString", ""));
            }
            input.setText(currentText);
            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle(title)
                    .setMessage(description)
                    .setView(input)
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                        Bundle updated = new Bundle(current);
                        if (type == RestrictionEntry.TYPE_INTEGER) {
                            try {
                                updated.putInt(key, Integer.parseInt(input.getText().toString()));
                            } catch (NumberFormatException ignored) {
                                Toast.makeText(requireContext(), R.string.managed_restrictions_save_failed,
                                        Toast.LENGTH_SHORT).show();
                                return;
                            }
                        } else {
                            updated.putString(key, input.getText().toString());
                        }
                        saveManagedApplicationRestrictions(packageName, updated);
                    })
                    .show();
            return;
        }

        showUnsupportedRestriction(title, description);
    }

    private Set<String> selectedRestrictionValues(Bundle current, Bundle entry, String key) {
        String[] values = current.getStringArray(key);
        if (values == null) {
            String singleValue = current.getString(key);
            if (singleValue != null) values = new String[]{singleValue};
        }
        if (values == null) values = entry.getStringArray("defaultStrings");
        if (values == null) {
            String defaultValue = entry.getString("defaultString");
            if (defaultValue != null) values = new String[]{defaultValue};
        }
        return values == null ? new HashSet<>() : new HashSet<>(Arrays.asList(values));
    }

    private void showUnsupportedRestriction(String title, String description) {
        String message = description == null || description.isEmpty()
                ? getString(R.string.managed_restrictions_unsupported)
                : description + "\n\n" + getString(R.string.managed_restrictions_unsupported);
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    private void saveManagedApplicationRestrictions(String packageName, Bundle restrictions) {
        new Thread(() -> {
            boolean saved = false;
            try {
                mService.setManagedApplicationRestrictions(packageName, restrictions);
                saved = true;
            } catch (Exception ignored) {
                // Report the failure in the UI below.
            }
            final boolean succeeded = saved;
            Activity activity = getActivity();
            if (activity != null) {
                activity.runOnUiThread(() -> {
                    if (!isAdded()) return;
                    Toast.makeText(requireContext(), succeeded
                                    ? R.string.managed_restrictions_saved
                                    : R.string.managed_restrictions_save_failed,
                            Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
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
