package com.takisoft.colorpicker;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.fragment.app.DialogFragment;

public class ColorPickerDialogFragment extends DialogFragment implements OnColorSelectedListener {
    public static final String EXTRA_PARAMS = "com.takisoft.colorpicker.PARAMS";
    public static final String EXTRA_TITLE = "com.takisoft.colorpicker.DIALOG_TITLE";
    public static final String EXTRA_TITLE_RES_ID = "com.takisoft.colorpicker.DIALOG_TITLE_RES_ID";

    protected OnColorSelectedListener onColorSelectedListener;

    public static ColorPickerDialogFragment newInstance(@NonNull ColorPickerDialog.Params params) {
        return newInstance(null, params);
    }

    public static ColorPickerDialogFragment newInstance(@StringRes int title, @NonNull ColorPickerDialog.Params params) {
        Bundle args = new Bundle();
        args.putInt(EXTRA_TITLE_RES_ID, title);
        args.putParcelable(EXTRA_PARAMS, params);

        ColorPickerDialogFragment fragment = new ColorPickerDialogFragment();
        fragment.setArguments(args);
        return fragment;
    }

    public static ColorPickerDialogFragment newInstance(@Nullable CharSequence title, @NonNull ColorPickerDialog.Params params) {
        Bundle args = new Bundle();
        args.putCharSequence(EXTRA_TITLE, title);
        args.putParcelable(EXTRA_PARAMS, params);

        ColorPickerDialogFragment fragment = new ColorPickerDialogFragment();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);

        if (context instanceof OnColorSelectedListener) {
            this.onColorSelectedListener = (OnColorSelectedListener) context;
        } else if (getParentFragment() instanceof OnColorSelectedListener) {
            this.onColorSelectedListener = (OnColorSelectedListener) getParentFragment();
        } else if (getTargetFragment() instanceof OnColorSelectedListener) {
            this.onColorSelectedListener = (OnColorSelectedListener) getTargetFragment();
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();

        this.onColorSelectedListener = null;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Bundle args = getArguments();

        if (args == null || !args.containsKey(EXTRA_PARAMS)) {
            throw new IllegalArgumentException("Missing '" + EXTRA_PARAMS + "' argument.");
        }

        ColorPickerDialog.Params params = args.getParcelable(EXTRA_PARAMS);

        ColorPickerDialog dialog = new ColorPickerDialog(getActivity(), this, params);

        int titleResId = args.getInt(EXTRA_TITLE_RES_ID, 0);
        CharSequence title = titleResId != 0 ? getString(titleResId) : args.getCharSequence(EXTRA_TITLE);
        dialog.setTitle(title);

        return dialog;
    }

    @Override
    public void onColorSelected(int color) {
        if (onColorSelectedListener != null) {
            onColorSelectedListener.onColorSelected(color);
        }
    }
}
