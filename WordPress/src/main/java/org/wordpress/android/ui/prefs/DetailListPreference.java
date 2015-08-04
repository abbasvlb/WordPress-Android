package org.wordpress.android.ui.prefs;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.TypedArray;
import android.graphics.Typeface;
import android.net.Uri;
import android.preference.ListPreference;
import android.support.annotation.NonNull;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.RadioButton;
import android.widget.TextView;

import org.wordpress.android.R;
import org.wordpress.android.widgets.TypefaceCache;
import org.wordpress.passcodelock.AppLockManager;

/**
 * Custom {@link ListPreference} used to display detail text per item.
 */

public class DetailListPreference extends ListPreference
        implements SiteSettingsFragment.HasHint {
    private DetailListAdapter mListAdapter;
    private String[] mDetails;
    private int mSelectedIndex;
    private String mTitle;
    private String mHint;
    private String mHelpUrl;

    public DetailListPreference(Context context, AttributeSet attrs) {
        super(context, attrs);

        TypedArray array = context.obtainStyledAttributes(attrs, R.styleable.DetailListPreference);

        for (int i = 0; i < array.getIndexCount(); ++i) {
            int index = array.getIndex(i);
            if (index == R.styleable.DetailListPreference_entryDetails) {
                int id = array.getResourceId(index, -1);
                if (id != -1) {
                    mDetails = array.getResources().getStringArray(id);
                } else {
                    mDetails = null;
                }
            } else if (index == R.styleable.DetailListPreference_longClickHint) {
                mHint = array.getString(index);
            } else if (index == R.styleable.DetailListPreference_dialogTitle) {
                mTitle = array.getString(index);
            } else if (index == R.styleable.DetailListPreference_dialogHelpUrl) {
                mHelpUrl = array.getString(index);
            }
        }

        array.recycle();
    }

    public DetailListPreference(Context context) {
        super(context);

        mSelectedIndex = 0;
        mDetails = null;
        setLayoutResource(R.layout.detail_list_preference);
    }

    public void setDetails(String[] details) {
        mDetails = details;
    }

    @Override
    protected void onPrepareDialogBuilder(@NonNull AlertDialog.Builder builder) {
        mListAdapter = new DetailListAdapter(getContext(), R.layout.detail_list_preference, mDetails);
        mSelectedIndex = findIndexOfValue(getValue());

        builder.setSingleChoiceItems(mListAdapter, mSelectedIndex,
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        if (mSelectedIndex != which) {
                            mSelectedIndex = which;
                            notifyChanged();
                        }
                        DetailListPreference.this.onClick(dialog, DialogInterface.BUTTON_POSITIVE);
                        dialog.dismiss();
                    }
                });
        builder.setNegativeButton(R.string.cancel, null);
        builder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                // TODO: save new setting
            }
        });

        View titleView = View.inflate(getContext(), R.layout.detail_list_preference_title, null);

        // Don't show the custom title view if there is no title or help URL
        if (titleView != null && !TextUtils.isEmpty(mTitle) && !TextUtils.isEmpty(mHelpUrl)) {
            TextView titleText = (TextView) titleView.findViewById(R.id.title);
            View infoView = titleView.findViewById(R.id.info_button);

            if (infoView != null) {
                if (TextUtils.isEmpty(mHelpUrl)) {
                    infoView.setVisibility(View.GONE);
                } else {
                    infoView.setVisibility(View.VISIBLE);
                    infoView.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            Uri uri = Uri.parse(mHelpUrl);
                            AppLockManager.getInstance().setExtendedTimeout();
                            getContext().startActivity(new Intent(Intent.ACTION_VIEW, uri));
                        }
                    });
                }
            }

            if (titleText != null) {
                titleText.setText(mTitle);
            }

            builder.setCustomTitle(titleView);
        } else {
            builder.setTitle(getTitle());
        }
    }

    @Override
    protected void onDialogClosed(boolean positiveResult) {
        CharSequence[] entryValues = getEntryValues();
        if (positiveResult && entryValues != null && mSelectedIndex < entryValues.length) {
            String value = entryValues[mSelectedIndex].toString();
            if (callChangeListener(value)) {
                setValue(value);
            }
        }
    }

    private class DetailListAdapter extends ArrayAdapter<String> {
        public DetailListAdapter(Context context, int resource, String[] objects) {
            super(context, resource, objects);
        }

        @Override
        public View getView(final int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = View.inflate(getContext(), R.layout.detail_list_preference, null);
            }

            final RadioButton radioButton = (RadioButton) convertView.findViewById(R.id.radio);
            TextView mainText = (TextView) convertView.findViewById(R.id.main_text);
            TextView detailText = (TextView) convertView.findViewById(R.id.detail_text);

            if (mainText != null && position < getEntries().length) {
                mainText.setText(getEntries()[position]);
                mainText.setTypeface(TypefaceCache.getTypeface(getContext(),
                        TypefaceCache.FAMILY_OPEN_SANS,
                        Typeface.NORMAL,
                        TypefaceCache.VARIATION_NORMAL));
            }

            if (detailText != null && position < mDetails.length) {
                detailText.setText(mDetails[position]);
                detailText.setTypeface(TypefaceCache.getTypeface(getContext(),
                        TypefaceCache.FAMILY_OPEN_SANS,
                        Typeface.NORMAL,
                        TypefaceCache.VARIATION_LIGHT));
            }

            if (radioButton != null && mSelectedIndex == position) {
                radioButton.setChecked(true);
            }

            convertView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (radioButton != null) {
                        radioButton.setChecked(true);
                    }
                    DetailListPreference.this.callChangeListener(getEntryValues()[position]);
                }
            });

            return convertView;
        }
    }

    @Override
    public boolean hasHint() {
        return !TextUtils.isEmpty(mHint);
    }

    @Override
    public String getHintText() {
        return mHint;
    }
}
