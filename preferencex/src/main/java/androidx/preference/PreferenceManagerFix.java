package androidx.preference;

import android.content.Context;
import android.content.SharedPreferences;

import java.lang.reflect.Field;
import java.util.Set;

import androidx.annotation.NonNull;
import androidx.collection.ArraySet;

public class PreferenceManagerFix extends PreferenceManager {

    private static Field editorField;
    private boolean noCommit;

    private boolean inflateInProgress;

    private static Set<String> packages = new ArraySet<>();

    static {
        Field[] fields = androidx.preference.PreferenceManager.class.getDeclaredFields();
        for (Field field : fields) {
            //Log.d("FIELD", field.toString());
            if (field.getType() == SharedPreferences.Editor.class) {
                editorField = field;
                editorField.setAccessible(true);
                break;
            }
        }

        registerPreferencePackage("com.takisoft.preferencex");
    }

    public PreferenceManagerFix(Context context) {
        super(context);
    }

    @Override
    public PreferenceScreen inflateFromResource(Context context, int resId, PreferenceScreen rootPreferences) {
        try {
            inflateInProgress = true;
            setNoCommitFix(true);
            PreferenceInflater inflater = new PreferenceInflater(context, this);

            String[] defPacks = inflater.getDefaultPackages();

            /*String[] newDefPacks = new String[defPacks.length + 1];
            newDefPacks[0] = "com.takisoft.preferencex.";
            System.arraycopy(defPacks, 0, newDefPacks, 1, defPacks.length);*/

            String[] newDefPacks = new String[defPacks.length + packages.size()];
            packages.toArray(newDefPacks);
            System.arraycopy(defPacks, 0, newDefPacks, packages.size(), defPacks.length);

            inflater.setDefaultPackages(newDefPacks);

            rootPreferences = (PreferenceScreen) inflater.inflate(resId, rootPreferences);
            rootPreferences.onAttachedToHierarchy(this);
            setNoCommitFix(false);
            inflateInProgress = false;
            return rootPreferences;
        } catch (Throwable e) {
            e.printStackTrace();
        } finally {
            inflateInProgress = false;
        }

        return super.inflateFromResource(context, resId, rootPreferences);
    }

    @Override
    SharedPreferences.Editor getEditor() {
        if (!inflateInProgress || editorField == null) {
            return super.getEditor();
        }

        if (noCommit) {
            SharedPreferences.Editor editor = null;
            try {
                editor = (SharedPreferences.Editor) editorField.get(this);

                if (editor == null) {
                    editor = this.getSharedPreferences().edit();
                    editorField.set(this, editor);
                }
            } catch (IllegalAccessException e) {
                // TODO is this really what we want?
            }
            return editor;
        } else {
            return this.getSharedPreferences().edit();
        }
    }

    @Override
    boolean shouldCommit() {
        if (!inflateInProgress) {
            return super.shouldCommit();
        } else {
            return noCommit;
        }
    }

    private void setNoCommitFix(boolean noCommit) throws IllegalAccessException {
        SharedPreferences.Editor editor = (SharedPreferences.Editor) editorField.get(this);

        if (!noCommit && editor != null) {
            editor.apply();
        }

        this.noCommit = noCommit;
    }

    /**
     * Registers a {@link Preference}'s package so the users don't need to supply fully-qualified
     * names in the XML files. Only the package part of the class will be used.
     *
     * @param preferenceClass the {@link Preference} to be registered
     */
    public static void registerPreferencePackage(@NonNull Class<Preference> preferenceClass) {
        registerPreferencePackage(preferenceClass.getPackage().getName());
    }

    /**
     * Registers a {@link Preference}'s package so the users don't need to supply fully-qualified
     * names in the XML files.
     *
     * @param preferencePackage the {@link Preference}'s package name to be registered
     */
    public static void registerPreferencePackage(@NonNull String preferencePackage) {
        packages.add(preferencePackage + (preferencePackage.endsWith(".") ? "" : "."));
    }

    /**
     * Returns the name of the {@link SharedPreferences} file that preferences will use.
     *
     * @param context the context to be used
     * @return the name of the {@link SharedPreferences} file
     */
    public static String getDefaultSharedPreferencesName(Context context) {
        return context.getPackageName() + "_preferences";
    }

    /**
     * Returns the mode of the {@link SharedPreferences} file that preferences will use.
     *
     * @return the mode of the {@link SharedPreferences} file
     */
    public static int getDefaultSharedPreferencesMode() {
        return Context.MODE_PRIVATE;
    }

    /**
     * Sets the default values from an XML preference file by reading the values defined
     * by each {@link Preference} item's {@code android:defaultValue} attribute. This should
     * be called by the application's main activity.
     * <p>
     *
     * @param context   The context of the shared preferences.
     * @param resId     The resource ID of the preference XML file.
     * @param readAgain Whether to re-read the default values.
     *                  If false, this method sets the default values only if this
     *                  method has never been called in the past (or if the
     *                  {@link #KEY_HAS_SET_DEFAULT_VALUES} in the default value shared
     *                  preferences file is false). To attempt to set the default values again
     *                  bypassing this check, set {@code readAgain} to true.
     *                  <p class="note">
     *                  Note: this will NOT reset preferences back to their default
     *                  values. For that functionality, use
     *                  {@link PreferenceManager#getDefaultSharedPreferences(Context)}
     *                  and clear it followed by a call to this method with this
     *                  parameter set to true.
     */
    public static void setDefaultValues(Context context, int resId, boolean readAgain) {
        // Use the default shared preferences name and mode
        setDefaultValues(context, getDefaultSharedPreferencesName(context),
                getDefaultSharedPreferencesMode(), resId, readAgain);
    }

    /**
     * Similar to {@link #setDefaultValues(Context, int, boolean)} but allows
     * the client to provide the filename and mode of the shared preferences
     * file.
     *
     * @param context               The context of the shared preferences.
     * @param sharedPreferencesName A custom name for the shared preferences file.
     * @param sharedPreferencesMode The file creation mode for the shared preferences file, such
     *                              as {@link android.content.Context#MODE_PRIVATE} or {@link
     *                              android.content.Context#MODE_PRIVATE}
     * @param resId                 The resource ID of the preference XML file.
     * @param readAgain             Whether to re-read the default values.
     *                              If false, this method will set the default values only if this
     *                              method has never been called in the past (or if the
     *                              {@link #KEY_HAS_SET_DEFAULT_VALUES} in the default value shared
     *                              preferences file is false). To attempt to set the default values again
     *                              bypassing this check, set {@code readAgain} to true.
     *                              <p class="note">
     *                              Note: this will NOT reset preferences back to their default
     *                              values. For that functionality, use
     *                              {@link PreferenceManager#getDefaultSharedPreferences(Context)}
     *                              and clear it followed by a call to this method with this
     *                              parameter set to true.
     * @see #setDefaultValues(Context, int, boolean)
     * @see #setSharedPreferencesName(String)
     * @see #setSharedPreferencesMode(int)
     */
    public static void setDefaultValues(Context context, String sharedPreferencesName,
                                        int sharedPreferencesMode, int resId, boolean readAgain) {
        final SharedPreferences defaultValueSp = context.getSharedPreferences(
                KEY_HAS_SET_DEFAULT_VALUES, Context.MODE_PRIVATE);

        if (readAgain || !defaultValueSp.getBoolean(KEY_HAS_SET_DEFAULT_VALUES, false)) {
            final PreferenceManagerFix pm = new PreferenceManagerFix(context);
            pm.setSharedPreferencesName(sharedPreferencesName);
            pm.setSharedPreferencesMode(sharedPreferencesMode);
            pm.inflateFromResource(context, resId, null);

            defaultValueSp.edit()
                    .putBoolean(KEY_HAS_SET_DEFAULT_VALUES, true)
                    .apply();
        }
    }
}
