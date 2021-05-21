package com.takisoft.preferencex;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.TypedArray;
import android.database.Cursor;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Parcel;
import android.os.Parcelable;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.AttributeSet;

import com.takisoft.preferencex.ringtone.R;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.content.res.TypedArrayUtils;
import androidx.preference.DialogPreference;
import androidx.preference.Preference;

/**
 * A {@link Preference} that displays a ringtone picker as a dialog.
 * <p>
 * This preference will save the picked ringtone's URI as a string into the SharedPreferences. The
 * saved URI can be fed directly into {@link RingtoneManager#getRingtone(Context, Uri)} to get the
 * {@link Ringtone} instance that can be played.
 *
 * @see RingtoneManager
 * @see Ringtone
 */
@SuppressWarnings("WeakerAccess,unused")
public class RingtonePreference extends DialogPreference {
    private static final int CUSTOM_RINGTONE_REQUEST_CODE = 0x9000;
    private static final int WRITE_FILES_PERMISSION_REQUEST_CODE = 0x9001;

    private int ringtoneType;
    private boolean showDefault;
    private boolean showSilent;
    private boolean showAdd;

    private Uri ringtoneUri;

    private CharSequence summaryHasRingtone;
    private CharSequence summary;

    private int miscCustomRingtoneRequestCode = CUSTOM_RINGTONE_REQUEST_CODE;
    private int miscPermissionRequestCode = WRITE_FILES_PERMISSION_REQUEST_CODE;

    @IntDef({
            RingtoneManager.TYPE_ALL,
            RingtoneManager.TYPE_ALARM,
            RingtoneManager.TYPE_NOTIFICATION,
            RingtoneManager.TYPE_RINGTONE
    })
    @Retention(RetentionPolicy.SOURCE)
    protected @interface RingtoneType {
    }

    static {
        PreferenceFragmentCompat.registerPreferenceFragment(RingtonePreference.class, RingtonePreferenceDialogFragmentCompat.class);
    }

    public RingtonePreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);

        android.preference.RingtonePreference proxyPreference;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            proxyPreference = new android.preference.RingtonePreference(context, attrs, defStyleAttr, defStyleRes);
        } else {
            proxyPreference = new android.preference.RingtonePreference(context, attrs, defStyleAttr);
        }

        ringtoneType = proxyPreference.getRingtoneType();
        showDefault = proxyPreference.getShowDefault();
        showSilent = proxyPreference.getShowSilent();

        summary = super.getSummary();

        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.RingtonePreference, defStyleAttr, 0);
        showAdd = a.getBoolean(R.styleable.RingtonePreference_pref_showAdd, true);
        summaryHasRingtone = a.getText(R.styleable.RingtonePreference_pref_summaryHasRingtone);

        if (a.getBoolean(R.styleable.RingtonePreference_useSimpleSummaryProvider, false)) {
            setSummaryProvider(SimpleSummaryProvider.getInstance());
        }
        a.recycle();
    }

    public RingtonePreference(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    @SuppressLint("RestrictedApi")
    public RingtonePreference(Context context, AttributeSet attrs) {
        this(context, attrs, TypedArrayUtils.getAttr(context, R.attr.dialogPreferenceStyle,
                android.R.attr.dialogPreferenceStyle));
    }

    public RingtonePreference(Context context) {
        this(context, null);
    }

    /**
     * Returns the sound type(s) that are shown in the picker.
     *
     * @return The sound type(s) that are shown in the picker.
     * @see #setRingtoneType(int)
     */
    @RingtoneType
    public int getRingtoneType() {
        return ringtoneType;
    }

    /**
     * Sets the sound type(s) that are shown in the picker. See {@link RingtoneManager} for the
     * possible values.
     *
     * @param ringtoneType The sound type(s) that are shown in the picker.
     */
    public void setRingtoneType(@RingtoneType int ringtoneType) {
        this.ringtoneType = ringtoneType;
    }

    /**
     * Returns whether to a show an item for the default sound/ringtone.
     *
     * @return Whether to show an item for the default sound/ringtone.
     */
    public boolean getShowDefault() {
        return showDefault;
    }

    /**
     * Sets whether to show an item for the default sound/ringtone. The default
     * to use will be deduced from the sound type(s) being shown.
     *
     * @param showDefault Whether to show the default or not.
     */
    public void setShowDefault(boolean showDefault) {
        this.showDefault = showDefault;
    }

    /**
     * Returns whether to a show an item for 'None'.
     *
     * @return Whether to show an item for 'None'.
     */
    public boolean getShowSilent() {
        return showSilent;
    }

    /**
     * Sets whether to show an item for 'None'.
     *
     * @param showSilent Whether to show 'None'.
     */
    public void setShowSilent(boolean showSilent) {
        this.showSilent = showSilent;
    }

    /**
     * Returns whether to a show an item for 'Add new ringtone'.
     * <p>
     * Note that this requires {@link android.Manifest.permission#WRITE_EXTERNAL_STORAGE}. If it's
     * not supplied in the manifest, the item won't be displayed.
     *
     * @return Whether to show an item for 'Add new ringtone'.
     */
    public boolean getShowAdd() {
        return showAdd;
    }

    boolean shouldShowAdd() {
        if (showAdd) {
            try {
                PackageInfo pInfo = getContext().getPackageManager().getPackageInfo(getContext().getPackageName(), PackageManager.GET_PERMISSIONS);
                String[] permissions = pInfo.requestedPermissions;
                for (String permission : permissions) {
                    if (Manifest.permission.WRITE_EXTERNAL_STORAGE.equals(permission)) {
                        return true;
                    }
                }
            } catch (PackageManager.NameNotFoundException e) {
                e.printStackTrace();
            }
        }

        return false;
    }

    /**
     * Sets whether to show an item for 'Add new ringtone'.
     * <p>
     * Note that this requires {@link android.Manifest.permission#WRITE_EXTERNAL_STORAGE}. If it's
     * not supplied in the manifest, the item won't be displayed.
     *
     * @param showAdd Whether to show 'Add new ringtone'.
     */
    public void setShowAdd(boolean showAdd) {
        this.showAdd = showAdd;
    }

    /**
     * This request code will be used to start the file picker activity that the user can use
     * to add new ringtones. The new ringtone will be delivered to
     * {@link RingtonePreferenceDialogFragmentCompat#onActivityResult(int, int, Intent)}.
     * <p>
     * The default value equals to {@link #CUSTOM_RINGTONE_REQUEST_CODE}
     * ({@value #CUSTOM_RINGTONE_REQUEST_CODE}).
     */
    public int getCustomRingtoneRequestCode() {
        return miscCustomRingtoneRequestCode;
    }

    /**
     * Sets the request code that will be used to start the file picker activity that the user can
     * use to add new ringtones. The new ringtone will be delivered to
     * {@link RingtonePreferenceDialogFragmentCompat#onActivityResult(int, int, Intent)}.
     * <p>
     * The default value equals to {@link #CUSTOM_RINGTONE_REQUEST_CODE}
     * ({@value #CUSTOM_RINGTONE_REQUEST_CODE}).
     *
     * @param customRingtoneRequestCode the request code for the file picker
     */
    public void setCustomRingtoneRequestCode(int customRingtoneRequestCode) {
        this.miscCustomRingtoneRequestCode = customRingtoneRequestCode;
    }

    /**
     * This request code will be used to ask for user permission to save (write) new ringtone
     * to one of the public external storage directories (only applies to API 23+). The result will
     * be delivered to
     * {@link RingtonePreferenceDialogFragmentCompat#onRequestPermissionsResult(int, String[], int[])}.
     * <p>
     * The default value equals to {@link #WRITE_FILES_PERMISSION_REQUEST_CODE}
     * ({@value #WRITE_FILES_PERMISSION_REQUEST_CODE}).
     */
    public int getPermissionRequestCode() {
        return miscPermissionRequestCode;
    }

    /**
     * Sets the request code that will be used to ask for user permission to save (write) new
     * ringtone to one of the public external storage directories (only applies to API 23+). The
     * result will be delivered to
     * {@link RingtonePreferenceDialogFragmentCompat#onRequestPermissionsResult(int, String[], int[])}.
     * <p>
     * The default value equals to {@link #WRITE_FILES_PERMISSION_REQUEST_CODE}
     * ({@value #WRITE_FILES_PERMISSION_REQUEST_CODE}).
     *
     * @param permissionRequestCode the request code for the file picker
     */
    public void setPermissionRequestCode(int permissionRequestCode) {
        this.miscPermissionRequestCode = permissionRequestCode;
    }

    public Uri getRingtone() {
        return onRestoreRingtone();
    }

    public void setRingtone(Uri uri) {
        setInternalRingtone(uri, false);
    }

    private void setInternalRingtone(Uri uri, boolean force) {
        Uri oldUri = onRestoreRingtone();

        final boolean changed = (oldUri != null && !oldUri.equals(uri)) || (uri != null && !uri.equals(oldUri));

        if (changed || force) {
            final boolean wasBlocking = shouldDisableDependents();

            ringtoneUri = uri;
            onSaveRingtone(uri);

            final boolean isBlocking = shouldDisableDependents();

            notifyChanged();

            if (isBlocking != wasBlocking) {
                notifyDependencyChange(isBlocking);
            }
        }
    }

    /**
     * Called when a ringtone is chosen.
     * <p>
     * By default, this saves the ringtone URI to the persistent storage as a
     * string.
     *
     * @param ringtoneUri The chosen ringtone's {@link Uri}. Can be null.
     */
    protected void onSaveRingtone(Uri ringtoneUri) {
        persistString(ringtoneUri != null ? ringtoneUri.toString() : "");
    }

    /**
     * Called when the chooser is about to be shown and the current ringtone
     * should be marked. Can return null to not mark any ringtone.
     * <p>
     * By default, this restores the previous ringtone URI from the persistent
     * storage.
     *
     * @return The ringtone to be marked as the current ringtone.
     */
    protected Uri onRestoreRingtone() {
        final String uriString = getPersistedString(ringtoneUri == null ? null : ringtoneUri.toString());
        return !TextUtils.isEmpty(uriString) ? Uri.parse(uriString) : null;
    }

    @Override
    protected Object onGetDefaultValue(TypedArray a, int index) {
        return a.getString(index);
    }

    @Override
    protected void onSetInitialValue(Object defaultValueObj) {
        String value = getPersistedString((String) defaultValueObj);
        setInternalRingtone(!TextUtils.isEmpty(value) ? Uri.parse(value) : null, true);
    }

    @Override
    public boolean shouldDisableDependents() {
        return super.shouldDisableDependents() || onRestoreRingtone() == null;
    }

    /**
     * Returns the summary of this Preference. If no {@code pref_summaryHasRingtone} is set, this will be
     * displayed if no ringtone is selected; otherwise the ringtone title will be used.
     *
     * @return The summary.
     */
    @Override
    @SuppressWarnings("unchecked")
    public CharSequence getSummary() {
        if (getSummaryProvider() != null) {
            return getSummaryProvider().provideSummary(this);
        }

        if (ringtoneUri == null) {
            return summary;
        } else {
            String ringtoneTitle = getRingtoneTitle();
            if (summaryHasRingtone != null && ringtoneTitle != null) {
                return String.format(summaryHasRingtone.toString(), ringtoneTitle);
            } else if (ringtoneTitle != null) {
                return ringtoneTitle;
            } else {
                return summary;
            }
        }
    }

    /**
     * Sets the summary for this Preference with a CharSequence. If no {@code pref_summaryHasRingtone} is
     * set, this will be displayed if no ringtone is selected; otherwise the ringtone title will be
     * used.
     *
     * @param summary The summary for the preference.
     */
    @Override
    public void setSummary(CharSequence summary) {
        super.setSummary(summary);
        if (summary == null && this.summary != null) {
            this.summary = null;
        } else if (summary != null && !summary.equals(this.summary)) {
            this.summary = summary.toString();
        }
    }

    /**
     * Returns the picked summary for this Preference. This will be displayed if the preference
     * has a persisted value or the default value is set. If the summary
     * has a {@linkplain java.lang.String#format String formatting}
     * marker in it (i.e. "%s" or "%1$s"), then the current ringtone's title
     * will be substituted in its place.
     *
     * @return The picked summary.
     */
    @Nullable
    public CharSequence getSummaryHasRingtone() {
        return summaryHasRingtone;
    }

    /**
     * Sets the picked summary for this Preference with a resource ID. This will be displayed if the
     * preference has a persisted value or the default value is set. If the summary
     * has a {@linkplain java.lang.String#format String formatting}
     * marker in it (i.e. "%s" or "%1$s"), then the current ringtone's title
     * will be substituted in its place.
     *
     * @param resId The summary as a resource.
     * @see #setSummaryHasRingtone(CharSequence)
     */
    public void setSummaryHasRingtone(@StringRes int resId) {
        setSummaryHasRingtone(getContext().getString(resId));
    }

    /**
     * Sets the picked summary for this Preference with a CharSequence. This will be displayed if
     * the preference has a persisted value or the default value is set. If the summary
     * has a {@linkplain java.lang.String#format String formatting}
     * marker in it (i.e. "%s" or "%1$s"), then the current ringtone's title
     * will be substituted in its place.
     *
     * @param summaryHasRingtone The summary for the preference.
     */
    public void setSummaryHasRingtone(@Nullable CharSequence summaryHasRingtone) {
        if (summaryHasRingtone == null && this.summaryHasRingtone != null) {
            this.summaryHasRingtone = null;
        } else if (summaryHasRingtone != null && !summaryHasRingtone.equals(this.summaryHasRingtone)) {
            this.summaryHasRingtone = summaryHasRingtone.toString();
        }

        notifyChanged();
    }

    /**
     * Returns the selected ringtone's title, or {@code null} if no ringtone is picked.
     *
     * @return The selected ringtone's title, or {@code null} if no ringtone is picked.
     */
    public String getRingtoneTitle() {
        Context context = getContext();
        ContentResolver cr = context.getContentResolver();
        String[] projection = {MediaStore.MediaColumns.TITLE};

        String ringtoneTitle = null;

        if (ringtoneUri != null) {
            int type = RingtoneManager.getDefaultType(ringtoneUri);

            switch (type) {
                case RingtoneManager.TYPE_ALL:
                case RingtoneManager.TYPE_RINGTONE:
                    ringtoneTitle = context.getString(R.string.ringtone_default);
                    break;
                case RingtoneManager.TYPE_ALARM:
                    ringtoneTitle = context.getString(R.string.alarm_sound_default);
                    break;
                case RingtoneManager.TYPE_NOTIFICATION:
                    ringtoneTitle = context.getString(R.string.notification_sound_default);
                    break;
                default:
                    try {
                        Cursor cursor = cr.query(ringtoneUri, projection, null, null, null);
                        if (cursor != null) {
                            if (cursor.moveToFirst()) {
                                ringtoneTitle = cursor.getString(0);
                            }

                            cursor.close();
                        }
                    } catch (Exception ignore) {
                    }
            }
        }

        return ringtoneTitle;
    }

    @Override
    protected Parcelable onSaveInstanceState() {
        final Parcelable superState = super.onSaveInstanceState();
        if (isPersistent()) {
            // No need to save instance state since it's persistent
            return superState;
        }

        final SavedState myState = new SavedState(superState);
        myState.ringtoneUri = getRingtone();
        return myState;
    }

    @Override
    protected void onRestoreInstanceState(Parcelable state) {
        if (state == null || !state.getClass().equals(SavedState.class)) {
            // Didn't save state for us in onSaveInstanceState
            super.onRestoreInstanceState(state);
            return;
        }

        SavedState myState = (SavedState) state;
        super.onRestoreInstanceState(myState.getSuperState());
        setRingtone(myState.ringtoneUri);
    }

    private static class SavedState extends BaseSavedState {
        private Uri ringtoneUri;

        public SavedState(Parcel source) {
            super(source);
            ringtoneUri = source.readParcelable(Uri.class.getClassLoader());
        }

        public SavedState(Parcelable superState) {
            super(superState);
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            super.writeToParcel(dest, flags);
            dest.writeParcelable(ringtoneUri, flags);
        }

        public static final Parcelable.Creator<SavedState> CREATOR =
                new Parcelable.Creator<SavedState>() {
                    @Override
                    public SavedState createFromParcel(Parcel in) {
                        return new SavedState(in);
                    }

                    @Override
                    public SavedState[] newArray(int size) {
                        return new SavedState[size];
                    }
                };
    }

    public static final class SimpleSummaryProvider implements SummaryProvider<RingtonePreference> {
        private static SimpleSummaryProvider instance;

        private SimpleSummaryProvider() {
        }

        /**
         * Retrieve a singleton instance of this simple
         * {@link androidx.preference.Preference.SummaryProvider} implementation.
         *
         * @return a singleton instance of this simple
         * {@link androidx.preference.Preference.SummaryProvider} implementation
         */
        public static SimpleSummaryProvider getInstance() {
            if (instance == null) {
                instance = new SimpleSummaryProvider();
            }
            return instance;
        }

        @Override
        public CharSequence provideSummary(RingtonePreference preference) {
            if (preference.ringtoneUri == null) {
                return preference.getContext().getString(androidx.preference.R.string.not_set);
            } else {
                return preference.getRingtoneTitle();
            }
            /*if (preference.ringtoneUri == null) {
                return preference.summary;
            } else {
                String ringtoneTitle = preference.getRingtoneTitle();
                if (preference.summaryHasRingtone != null && ringtoneTitle != null) {
                    return String.format(preference.summaryHasRingtone.toString(), ringtoneTitle);
                } else if (ringtoneTitle != null) {
                    return ringtoneTitle;
                } else {
                    return preference.summary;
                }
            }*/
        }
    }
}
