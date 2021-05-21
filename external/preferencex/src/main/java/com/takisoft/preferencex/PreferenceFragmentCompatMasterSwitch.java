package com.takisoft.preferencex;


import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.TypedArray;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.view.ContextThemeWrapper;
import androidx.appcompat.widget.SwitchCompat;
import androidx.preference.Preference;
import androidx.preference.PreferenceDataStore;
import androidx.preference.PreferenceScreen;

public abstract class PreferenceFragmentCompatMasterSwitch extends PreferenceFragmentCompat {
    private MasterSwitch masterSwitch = createMasterSwitch();

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //masterSwitch = new MasterSwitch();
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = super.onCreateView(inflater, container, savedInstanceState);

        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;

            if (masterSwitch != null) {
                masterSwitch.onCreateView(inflater, group);
                masterSwitch.refreshMasterSwitch();
            }
        } else {
            throw new IllegalArgumentException("The root element must be an instance of ViewGroup");
        }

        return view;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        if (masterSwitch != null) {
            masterSwitch.onDestroyView();
        }
    }

    @Override
    public void setPreferenceScreen(PreferenceScreen preferenceScreen) {
        super.setPreferenceScreen(preferenceScreen);

        if (masterSwitch != null) {
            masterSwitch.refreshMasterSwitch();
        }
    }

    protected MasterSwitch createMasterSwitch() {
        return new MasterSwitch();
    }

    public MasterSwitch getMasterSwitch() {
        return masterSwitch;
    }

    @Override
    public void onViewStateRestored(@Nullable Bundle savedInstanceState) {
        super.onViewStateRestored(savedInstanceState);

        if (masterSwitch != null) {
            masterSwitch.updateViews();
        }
    }

    public class MasterSwitch {
        private final int[] ATTRS = {R.attr.pref_masterSwitchBackgroundOn, R.attr.pref_masterSwitchBackgroundOff};

        private View masterView;
        private TextView masterTitle;
        private SwitchCompat switchCompat;

        @Nullable
        private PreferenceDataStore dataStore;

        private boolean isCheckedSet;
        private boolean isChecked;

        @Nullable
        private OnMasterSwitchChangeListener onChangeListener;

        private MasterSwitch() {
        }

        private void onCreateView(LayoutInflater inflater, ViewGroup group) {
            if (group.findViewById(R.id.pref_master_switch_view) != null) {
                return;
            }

            TypedValue typedValue = new TypedValue();
            requireContext().getTheme().resolveAttribute(R.attr.pref_masterSwitchStyle, typedValue, true);

            ContextThemeWrapper ctx = new ContextThemeWrapper(requireContext(), typedValue.resourceId != 0 ? typedValue.resourceId : R.style.PreferenceMasterSwitch);
            //ctx.getTheme().applyStyle(typedValue.resourceId != 0 ? typedValue.resourceId : R.style.PreferenceMasterSwitch, true);
            LayoutInflater inf = inflater.cloneInContext(ctx);

            masterView = inf.inflate(R.layout.preference_list_master_switch, group, false);
            masterTitle = masterView.findViewById(android.R.id.title);
            switchCompat = masterView.findViewById(R.id.switchWidget);

            setMasterBackground(ctx);

            masterView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    performClick(v);
                }
            });

            group.addView(masterView, 0, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }

        private void onDestroyView() {
            masterView = null;
            masterTitle = null;
            switchCompat = null;
            isCheckedSet = false;
        }

        private void setMasterBackground(@NonNull Context ctx) {
            TypedArray a = ctx.obtainStyledAttributes(ATTRS);
            int colorOn = a.getColor(a.getIndex(0), 0);
            int colorOff = a.getColor(a.getIndex(1), 0);
            a.recycle();

            StateListDrawable drawable = new StateListDrawable();
            drawable.addState(new int[]{android.R.attr.state_selected}, new ColorDrawable(colorOn));
            drawable.addState(new int[]{}, new ColorDrawable(colorOff));
            masterView.setBackgroundDrawable(drawable);
        }

        private void refreshMasterSwitch() {
            PreferenceScreen preferenceScreen = getPreferenceScreen();

            if (preferenceScreen == null) {
                return;
            }

            boolean checked = getPersistedBoolean(false);

            if (checked != isChecked || !isCheckedSet) {
                isCheckedSet = true;
                isChecked = checked;

                if (masterTitle != null) {
                    masterTitle.setText(preferenceScreen.getTitle());
                }

                getPreferenceScreen().notifyDependencyChange(shouldDisableDependents());
            }

            updateViews();
        }

        private void updateViews() {
            if (masterView != null && getPreferenceScreen() != null) {
                masterView.findViewById(androidx.preference.R.id.icon_frame).setVisibility(getPreferenceScreen().isIconSpaceReserved() ? View.VISIBLE : View.GONE);
            }

            if (masterTitle != null) {
                masterTitle.setText(getTitle());
                masterTitle.setSingleLine(isSingleLineTitle());
            }

            if (masterView != null) {
                ImageView iconView = masterView.findViewById(android.R.id.icon);
                iconView.setImageDrawable(getIcon());
            }

            if (masterView != null && switchCompat != null) {
                masterView.setSelected(isChecked);
                switchCompat.setChecked(isChecked);
            }
        }

        private void notifyChanged() {
            updateViews();

            // TODO notify internal listener
        }

        private void performClick(View v) {
            performClick();
            updateViews();
        }

        private void performClick() {
            onClick();

            /*if (mOnClickListener != null && mOnClickListener.onPreferenceClick(this)) {
                return;
            }*/
        }

        protected void onClick() {
            final boolean newValue = !isChecked();
            if (callChangeListener(newValue)) {
                setChecked(newValue);
            }
        }

        public boolean isChecked() {
            return isChecked;
        }

        /**
         * Sets the checked state and saves it.
         *
         * @param checked The checked state
         */
        public void setChecked(boolean checked) {
            final boolean changed = isChecked != checked;
            if (changed) {
                isChecked = checked;
                persistBoolean(checked);
                //if (changed) {
                getPreferenceScreen().notifyDependencyChange(shouldDisableDependents());
                notifyChanged();
                //}
            }
        }

        private boolean shouldDisableDependents() {
            boolean mDisableDependentsState = false; // TODO

            boolean shouldDisable = mDisableDependentsState ? isChecked : !isChecked;
            return shouldDisable || getPreferenceScreen().shouldDisableDependents();
        }

        /**
         * Attempts to persist a {@link Boolean} if this preference is persistent.
         *
         * <p>The returned value doesn't reflect whether the given value was persisted, since we may not
         * necessarily commit if there will be a batch commit later.
         *
         * @param value The value to persist
         * @return {@code true} if the preference is persistent, {@code false} otherwise
         * @see #getPersistedBoolean(boolean)
         */
        private boolean persistBoolean(boolean value) {
            if (!shouldPersist()) {
                return false;
            }

            if (value == getPersistedBoolean(!value)) {
                return true;
            }

            PreferenceDataStore dataStore = getPreferenceDataStore();
            if (dataStore != null) {
                dataStore.putBoolean(getKey(), value);
            } else {
                SharedPreferences.Editor editor = getPreferenceManager().getSharedPreferences().edit();
                editor.putBoolean(getKey(), value);
                editor.apply();
            }
            return true;
        }

        /**
         * Attempts to get a persisted {@link Boolean} if this preference is persistent.
         *
         * @param defaultReturnValue The default value to return if either this preference is not
         *                           persistent or this preference is not in the SharedPreferences.
         * @return The value from the storage or the default return value
         * @see #persistBoolean(boolean)
         */
        private boolean getPersistedBoolean(boolean defaultReturnValue) {
            if (!shouldPersist()) {
                return defaultReturnValue;
            }

            PreferenceDataStore dataStore = getPreferenceDataStore();
            if (dataStore != null) {
                return dataStore.getBoolean(getKey(), defaultReturnValue);
            }

            return getPreferenceManager().getSharedPreferences().getBoolean(getKey(), defaultReturnValue);
        }

        @SuppressWarnings("BooleanMethodIsAlwaysInverted")
        private boolean shouldPersist() {
            return getPreferenceManager() != null && getPreferenceScreen().isPersistent() && getPreferenceScreen().hasKey();
        }

        private String getKey() {
            return getPreferenceScreen().getKey();
        }

        /**
         * Call this method after the user changes the preference, but before the internal state is
         * set. This allows the client to ignore the user value.
         *
         * @param newValue The new value of this preference
         * @return {@code true} if the user value should be set as the preference value (and persisted)
         */
        private boolean callChangeListener(boolean newValue) {
            return onChangeListener == null || onChangeListener.onMasterSwitchChange(newValue);
        }

        /**
         * Sets the callback to be invoked when this preference is changed by the user (but before
         * the internal state has been updated).
         *
         * @param onMasterSwitchChangeListener The callback to be invoked
         */
        public void setOnPreferenceChangeListener(
                OnMasterSwitchChangeListener onMasterSwitchChangeListener) {
            onChangeListener = onMasterSwitchChangeListener;
        }

        /**
         * Returns the callback to be invoked when this preference is changed by the user (but before
         * the internal state has been updated).
         *
         * @return The callback to be invoked
         */
        public OnMasterSwitchChangeListener getOnPreferenceChangeListener() {
            return onChangeListener;
        }

        @Nullable
        public PreferenceDataStore getPreferenceDataStore() {
            return dataStore;
        }

        public void setPreferenceDataStore(@Nullable PreferenceDataStore dataStore) {
            this.dataStore = dataStore;
        }

        @Nullable
        public CharSequence getTitle() {
            return getPreferenceScreen() != null ? getPreferenceScreen().getTitle() : null;
        }

        @Nullable
        public Drawable getIcon() {

            return getPreferenceScreen() != null ? getPreferenceScreen().getIcon() : null;
        }

        public boolean isSingleLineTitle() {
            return getPreferenceScreen() != null && getPreferenceScreen().isSingleLineTitle();
        }
    }

    /**
     * Interface definition for a callback to be invoked when the value of this
     * {@link Preference} has been changed by the user and is about to be set and/or persisted.
     * This gives the client a chance to prevent setting and/or persisting the value.
     */
    public interface OnMasterSwitchChangeListener {
        /**
         * Called when a preference has been changed by the user. This is called before the state
         * of the preference is about to be updated and before the state is persisted.
         *
         * @param newValue The new value of the preference
         * @return {@code true} to update the state of the preference with the new value
         */
        boolean onMasterSwitchChange(boolean newValue);
    }
}
