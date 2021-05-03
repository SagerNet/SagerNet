package com.takisoft.preferencex;

import android.content.Context;
import android.content.res.TypedArray;
import android.text.InputType;
import android.text.TextUtils;
import android.util.AttributeSet;

import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

@Deprecated
public class AutoSummaryEditTextPreference extends EditTextPreference {
    private CharSequence summaryHasText;
    private CharSequence summary;

    private String passwordSubstitute;
    private int passwordSubstituteLength;

    private int inputType = InputType.TYPE_CLASS_TEXT;

    public AutoSummaryEditTextPreference(Context context) {
        this(context, null);
    }

    public AutoSummaryEditTextPreference(Context context, AttributeSet attrs) {
        this(context, attrs, R.attr.editTextPreferenceStyle);
    }

    public AutoSummaryEditTextPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public AutoSummaryEditTextPreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);

        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.AutoSummaryEditTextPreference, defStyleAttr, 0);
        summaryHasText = a.getText(R.styleable.AutoSummaryEditTextPreference_pref_summaryHasText);

        passwordSubstitute = a.getString(R.styleable.AutoSummaryEditTextPreference_pref_summaryPasswordSubstitute);
        passwordSubstituteLength = a.getInt(R.styleable.AutoSummaryEditTextPreference_pref_summaryPasswordSubstituteLength, 5);

        if (passwordSubstitute == null) {
            passwordSubstitute = "\u2022";
        }

        a.recycle();

        summary = super.getSummary();

        // temporary fix for the inputType attribute until this class is removed
        for (int i = 0; i < attrs.getAttributeCount(); i++) {
            int nameRes = attrs.getAttributeNameResource(i);
            if (android.R.attr.inputType == nameRes) {
                inputType = attrs.getAttributeIntValue(i, InputType.TYPE_CLASS_TEXT);
                break;
            }
        }
    }

    /**
     * Returns the summary of this Preference. If no {@code pref_summaryHasText} is set, this will
     * be displayed if no value is set; otherwise the value will be used.
     *
     * @return The summary.
     */
    @Override
    public CharSequence getSummary() {
        CharSequence text = getText();
        final boolean hasText = !TextUtils.isEmpty(text);

        if (!hasText) {
            return summary;
        } else {
            if ((inputType & InputType.TYPE_NUMBER_VARIATION_PASSWORD) == InputType.TYPE_NUMBER_VARIATION_PASSWORD ||
                    (inputType & InputType.TYPE_TEXT_VARIATION_PASSWORD) == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                    (inputType & InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD) == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD) {
                text = new String(new char[passwordSubstituteLength > 0 ? passwordSubstituteLength : text.length()]).replaceAll("\0", passwordSubstitute);
            }

            if (summaryHasText != null) {
                return String.format(summaryHasText.toString(), text);
            } else {
                return text;
            }
        }
    }

    /**
     * Sets the summary for this Preference with a CharSequence. If no {@code pref_summaryHasText}
     * is set, this will be displayed if no value is set; otherwise the value will be used.
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
     * Returns the summary for this Preference. This will be displayed if the preference
     * has a persisted value or the default value is set. If the summary
     * has a {@linkplain java.lang.String#format String formatting}
     * marker in it (i.e. "%s" or "%1$s"), then the current value will be substituted in its place.
     *
     * @return The picked summary.
     */
    @Nullable
    public CharSequence getSummaryHasText() {
        return summaryHasText;
    }

    /**
     * Sets the summary for this Preference with a resource ID. This will be displayed if the
     * preference has a persisted value or the default value is set. If the summary
     * has a {@linkplain java.lang.String#format String formatting}
     * marker in it (i.e. "%s" or "%1$s"), then the current value will be substituted in its place.
     *
     * @param resId The summary as a resource.
     * @see #setSummaryHasText(CharSequence)
     */
    public void setSummaryHasText(@StringRes int resId) {
        setSummaryHasText(getContext().getString(resId));
    }

    /**
     * Sets the summary for this Preference with a CharSequence. This will be displayed if
     * the preference has a persisted value or the default value is set. If the summary
     * has a {@linkplain java.lang.String#format String formatting}
     * marker in it (i.e. "%s" or "%1$s"), then the current value will be substituted in its place.
     *
     * @param summaryHasText The summary for the preference.
     */
    public void setSummaryHasText(@Nullable CharSequence summaryHasText) {
        if (summaryHasText == null && this.summaryHasText != null) {
            this.summaryHasText = null;
        } else if (summaryHasText != null && !summaryHasText.equals(this.summaryHasText)) {
            this.summaryHasText = summaryHasText.toString();
        }

        notifyChanged();
    }

    /**
     * Returns the substitute characters to be used for displaying passwords in the summary.
     *
     * @return The substitute characters to be used for displaying passwords in the summary.
     */
    public CharSequence getPasswordSubstitute() {
        return passwordSubstitute;
    }

    /**
     * Sets the substitute characters to be used for displaying passwords in the summary.
     *
     * @param resId The substitute characters as a resource.
     * @see #setPasswordSubstitute(String)
     */
    public void setPasswordSubstitute(@StringRes int resId) {
        setPasswordSubstitute(getContext().getString(resId));
    }

    /**
     * Sets the substitute characters to be used for displaying passwords in the summary.
     *
     * @param passwordSubstitute The substitute characters to be used for displaying passwords in
     *                           the summary.
     */
    public void setPasswordSubstitute(String passwordSubstitute) {
        this.passwordSubstitute = passwordSubstitute;
    }

    /**
     * Returns the length of the substitute password value in the summary. If this value equals to
     * or is less than 0, it will use the entered text's length; otherwise the length will equal to
     * the returned value. If the actual password's length is to be displayed, use 0 or less.
     * <p>
     * The password is subsituted with this many characters / strings supplied by
     * {@link #getPasswordSubstitute()}.
     *
     * @see #setPasswordSubstitute(int)
     * @see #setPasswordSubstitute(String)
     */
    public int getPasswordSubstituteLength() {
        return passwordSubstituteLength;
    }

    /**
     * Sets the length of the substitute password value in the summary. If this value equals to or
     * is less than 0, it will use the entered text's length; otherwise the length will equal to the
     * given value. If the actual password's length is to be displayed, use 0 or less.
     * <p>
     * The password will be subsituted with this many characters / strings supplied by
     * {@link #setPasswordSubstitute(String)}.
     *
     * @param passwordSubstituteLength The length of the substitute password in the summary.
     *                                 If the number equals to or is less than zero, the actual
     *                                 text's length will be used.
     * @see #setPasswordSubstitute(int)
     * @see #setPasswordSubstitute(String)
     */
    public void setPasswordSubstituteLength(int passwordSubstituteLength) {
        this.passwordSubstituteLength = passwordSubstituteLength;
    }
}
