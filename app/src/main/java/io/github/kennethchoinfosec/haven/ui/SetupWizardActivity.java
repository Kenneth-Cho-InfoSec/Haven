package io.github.kennethchoinfosec.haven.ui;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContract;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;

import android.app.admin.DevicePolicyManager;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import io.github.kennethchoinfosec.haven.R;
import io.github.kennethchoinfosec.haven.receivers.HavenDeviceAdminReceiver;
import io.github.kennethchoinfosec.haven.util.AuthenticationUtility;
import io.github.kennethchoinfosec.haven.util.LocalStorageManager;
import io.github.kennethchoinfosec.haven.util.Utility;

public class SetupWizardActivity extends AppCompatActivity {
    // RESUME_SETUP should be used when MainActivity detects the provisioning has been
    // finished by the system, but the Haven inside the profile has never been brought up
    // due to the user having not clicked on the notification yet (on Android 7 or lower).
    // TODO: When we remove support for Android 7, get rid of all of these nonsense :)
    public static final String ACTION_RESUME_SETUP = "io.github.kennethchoinfosec.haven.RESUME_SETUP";
    public static final String ACTION_PROFILE_PROVISIONED = "io.github.kennethchoinfosec.haven.PROFILE_PROVISIONED";

    private DevicePolicyManager mPolicyManager = null;
    private LocalStorageManager mStorage = null;

    private final ActivityResultLauncher<Void> mProvisionProfile =
            registerForActivityResult(new ProfileProvisionContract(), this::setupProfileCb);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        // The user could click on the "finish provisioning" notification while having removed
        // this activity from the recents stack, in which case the notification will start a new
        // instance of activity
        if (ACTION_PROFILE_PROVISIONED.equals(getIntent().getAction()) && Utility.isWorkProfileAvailable(this)) {
            // ...in which case we should finish immediately and go back to MainActivity
            startActivity(new Intent(this, MainActivity.class));
            finish();
            return;
        }

        setContentView(R.layout.activity_setup_wizard);
        mPolicyManager = getSystemService(DevicePolicyManager.class);
        mStorage = LocalStorageManager.getInstance();
        // Don't use switchToFragment for the first time
        // because we don't want animation for the first fragment
        // (it would have nothing to animate upon, resulting in a black background)
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.setup_wizard_container,
                        ACTION_RESUME_SETUP.equals(getIntent().getAction()) ?
                                new ActionRequiredFragment() : new WelcomeFragment())
                .commit();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // DummyActivity will start this activity with an empty intent
        // once the provision is finalized
        if (ACTION_PROFILE_PROVISIONED.equals(intent.getAction()) && Utility.isWorkProfileAvailable(this))
            finishWithResult(true);
    }

    private<T extends BaseWizardFragment> void switchToFragment(T fragment, boolean reverseAnimation) {
        getSupportFragmentManager()
                .beginTransaction()
                .setCustomAnimations(
                        reverseAnimation ? R.anim.slide_in_from_left : R.anim.slide_in_from_right,
                        reverseAnimation ? R.anim.slide_out_to_right : R.anim.slide_out_to_left
                )
                .replace(R.id.setup_wizard_container, fragment)
                .commit();
    }

    private void finishWithResult(boolean succeeded) {
        setResult(succeeded ? RESULT_OK : RESULT_CANCELED);
        finish();
    }

    private void setupProfile() {
        if (!mPolicyManager.isProvisioningAllowed(DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE)) {
            switchToFragment(new FailedFragment(), false);
            return;
        }

        // The user may have aborted provisioning before without clearing data
        // This can cause issues if the authentication utility thinks we
        // could do authentication due to the presence of keys
        AuthenticationUtility.reset();

        try {
            mProvisionProfile.launch(null);
        } catch (ActivityNotFoundException e) {
            // How could this fail???
            switchToFragment(new FailedFragment(), false);
        }
    }

    private void setupProfileCb(Boolean result) {
        if (result) {
            if (Utility.isWorkProfileAvailable(this)) {
                // On Oreo and later versions, since we make use of the activity intent
                // ACTION_PROVISIONING_SUCCESSFUL, the provisioning UI will not finish
                // until that activity returns. In this case, there is really no need for us
                // to do anything else here (and this callback may not even be called because
                // the activity will likely be already finished by this point).
                // There is no need for more action
                finishWithResult(true);
                return;
            }

            // Provisioning finished, but we still need to tell the user
            // to click on the notification to bring up Haven inside the
            // profile. Otherwise, the setup will not be complete
            mStorage.setBoolean(LocalStorageManager.PREF_IS_SETTING_UP, true);
            switchToFragment(new ActionRequiredFragment(), false);
        } else {
            switchToFragment(new FailedFragment(), false);
        }
    }

    public static class SetupWizardContract extends ActivityResultContract<Void, Boolean> {
        @NonNull
        @Override
        public Intent createIntent(@NonNull Context context, Void input) {
            return new Intent(context, SetupWizardActivity.class);
        }

        @Override
        public Boolean parseResult(int resultCode, @Nullable Intent intent) {
            return resultCode == RESULT_OK;
        }
    }

    public static class ResumeSetupContract extends ActivityResultContract<Void, Boolean> {
        @NonNull
        @Override
        public Intent createIntent(@NonNull Context context, Void input) {
            Intent intent = new Intent(context, SetupWizardActivity.class);
            intent.setAction(ACTION_RESUME_SETUP);
            return intent;
        }

        @Override
        public Boolean parseResult(int resultCode, @Nullable Intent intent) {
            return resultCode == RESULT_OK;
        }
    }

    private static class ProfileProvisionContract extends ActivityResultContract<Void, Boolean> {
        @NonNull
        @Override
        public Intent createIntent(@NonNull Context context, Void input) {
            ComponentName admin = new ComponentName(context.getApplicationContext(), HavenDeviceAdminReceiver.class);
            Intent intent = new Intent(DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE);
            intent.putExtra(DevicePolicyManager.EXTRA_PROVISIONING_SKIP_ENCRYPTION, true);
            intent.putExtra(DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME, admin);
            return intent;
        }

        @Override
        public Boolean parseResult(int resultCode, @Nullable Intent intent) {
            return resultCode == RESULT_OK;
        }
    }

    // ==== SetupWizard steps ====
    private static abstract class BaseWizardFragment extends Fragment {
        protected SetupWizardActivity mActivity = null;
        protected View mWizardRoot = null;
        protected TextView mTitle = null;
        protected MaterialButton mBackButton = null;
        protected MaterialButton mNextButton = null;
        protected LinearProgressIndicator mProgress = null;

        protected abstract int getLayoutResource();
        protected abstract int getTitleRes();

        public void onNavigateBack() {
            // For sub-classes to implement
        }

        public void onNavigateNext() {
            // For sub-classes to implement
        }

        @Override
        public void onAttach(@NonNull Context context) {
            super.onAttach(context);
            mActivity = (SetupWizardActivity) context;
        }

        @Override
        public void onDetach() {
            super.onDetach();
            mActivity = null;
        }

        @Nullable
        @Override
        public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
            View view = inflater.inflate(getLayoutResource(), container, false);
            return view;
        }

        @Override
        public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
            super.onViewCreated(view, savedInstanceState);
            mWizardRoot = view.findViewById(R.id.wizard_root);
            mTitle = view.findViewById(R.id.setup_wizard_title);
            mBackButton = view.findViewById(R.id.setup_wizard_back);
            mNextButton = view.findViewById(R.id.setup_wizard_next);
            mProgress = view.findViewById(R.id.setup_wizard_progress);

            mTitle.setText(getTitleRes());
            mBackButton.setText(R.string.setup_wizard_back);
            mNextButton.setText(R.string.setup_wizard_next);
            mBackButton.setOnClickListener((v) -> onNavigateBack());
            mNextButton.setOnClickListener((v) -> onNavigateNext());
            setProgressVisible(false);

            int initialPaddingLeft = mWizardRoot.getPaddingLeft();
            int initialPaddingTop = mWizardRoot.getPaddingTop();
            int initialPaddingRight = mWizardRoot.getPaddingRight();
            int initialPaddingBottom = mWizardRoot.getPaddingBottom();
            ViewCompat.setOnApplyWindowInsetsListener(mWizardRoot, (v, windowInsets) -> {
                Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
                v.setPadding(
                        initialPaddingLeft,
                        initialPaddingTop + insets.top,
                        initialPaddingRight,
                        initialPaddingBottom + insets.bottom
                );
                return WindowInsetsCompat.CONSUMED;
            });
        }

        protected void setProgressVisible(boolean visible) {
            mProgress.setVisibility(visible ? View.VISIBLE : View.GONE);
        }

        protected void setNavigationVisible(boolean backVisible, boolean nextVisible) {
            mBackButton.setVisibility(backVisible ? View.VISIBLE : View.GONE);
            mNextButton.setVisibility(nextVisible ? View.VISIBLE : View.GONE);
        }
    }

    protected static abstract class TextWizardFragment extends BaseWizardFragment {
        protected abstract int getTextRes();

        @Override
        public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
            super.onViewCreated(view, savedInstanceState);
            TextView tv = view.findViewById(R.id.setup_wizard_generic_text);
            tv.setText(getTextRes());
        }
    }

    public static class WelcomeFragment extends TextWizardFragment {
        @Override
        protected int getLayoutResource() {
            return R.layout.fragment_setup_wizard_generic_text;
        }

        @Override
        protected int getTitleRes() {
            return R.string.setup_wizard_welcome;
        }

        @Override
        protected int getTextRes() {
            return R.string.setup_wizard_welcome_text;
        }

        @Override
        public void onNavigateNext() {
            super.onNavigateNext();
            mActivity.switchToFragment(new PermissionsFragment(), false);
        }

        @Override
        public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
            super.onViewCreated(view, savedInstanceState);
            setNavigationVisible(false, true);
        }
    }

    public static class PermissionsFragment extends TextWizardFragment {
        @Override
        protected int getLayoutResource() {
            return R.layout.fragment_setup_wizard_generic_text;
        }

        @Override
        protected int getTitleRes() {
            return R.string.setup_wizard_permissions;
        }

        @Override
        protected int getTextRes() {
            return R.string.setup_wizard_permissions_text;
        }

        @Override
        public void onNavigateBack() {
            super.onNavigateBack();
            mActivity.switchToFragment(new WelcomeFragment(), true);
        }

        @Override
        public void onNavigateNext() {
            super.onNavigateNext();
            mActivity.switchToFragment(new CompatibilityFragment(), false);
        }

    }

    public static class CompatibilityFragment extends TextWizardFragment {
        @Override
        protected int getLayoutResource() {
            return R.layout.fragment_setup_wizard_generic_text;
        }

        @Override
        protected int getTitleRes() {
            return R.string.setup_wizard_compatibility;
        }

        @Override
        protected int getTextRes() {
            return R.string.setup_wizard_compatibility_text;
        }

        @Override
        public void onNavigateBack() {
            super.onNavigateBack();
            mActivity.switchToFragment(new PermissionsFragment(), true);
        }

        @Override
        public void onNavigateNext() {
            super.onNavigateNext();
            mActivity.switchToFragment(new ReadyFragment(), false);
        }

    }

    public static class ReadyFragment extends TextWizardFragment {
        @Override
        protected int getLayoutResource() {
            return R.layout.fragment_setup_wizard_generic_text;
        }

        @Override
        protected int getTitleRes() {
            return R.string.setup_wizard_ready;
        }

        @Override
        protected int getTextRes() {
            return R.string.setup_wizard_ready_text;
        }

        @Override
        public void onNavigateBack() {
            super.onNavigateBack();
            mActivity.switchToFragment(new CompatibilityFragment(), true);
        }

        @Override
        public void onNavigateNext() {
            super.onNavigateNext();
            mActivity.switchToFragment(new PleaseWaitFragment(), false);
            mActivity.setupProfile();
        }

    }

    public static class PleaseWaitFragment extends TextWizardFragment {
        @Override
        protected int getLayoutResource() {
            return R.layout.fragment_setup_wizard_generic_text;
        }

        @Override
        protected int getTitleRes() {
            return R.string.setup_wizard_please_wait;
        }

        @Override
        protected int getTextRes() {
            return R.string.setup_wizard_please_wait_text;
        }

        @Override
        public void onAttach(@NonNull Context context) {
            super.onAttach(context);
        }

        @Override
        public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
            super.onViewCreated(view, savedInstanceState);
            setProgressVisible(true);
            setNavigationVisible(false, false);
        }
    }

    public static class ActionRequiredFragment extends TextWizardFragment {
        @Override
        protected int getLayoutResource() {
            return R.layout.fragment_setup_wizard_generic_text;
        }

        @Override
        protected int getTitleRes() {
            return R.string.setup_wizard_action_required;
        }

        @Override
        protected int getTextRes() {
            return R.string.setup_wizard_action_required_text;
        }

        @Override
        public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
            super.onViewCreated(view, savedInstanceState);
            setProgressVisible(true);
            setNavigationVisible(false, false);
        }
    }

    public static class FailedFragment extends TextWizardFragment {
        @Override
        protected int getLayoutResource() {
            return R.layout.fragment_setup_wizard_generic_text;
        }

        @Override
        protected int getTitleRes() {
            return R.string.setup_wizard_failed;
        }

        @Override
        protected int getTextRes() {
            return R.string.setup_wizard_failed_text;
        }

        @Override
        public void onNavigateNext() {
            super.onNavigateNext();
            mActivity.finishWithResult(false);
        }

        @Override
        public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
            super.onViewCreated(view, savedInstanceState);
            setNavigationVisible(false, true);
            mNextButton.setText(android.R.string.ok);
        }
    }
}
