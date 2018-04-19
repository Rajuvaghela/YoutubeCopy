package org.schabi.newpipe.fragments.subscription;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;
import android.support.v4.text.util.LinkifyCompat;
import android.support.v7.app.ActionBar;
import android.text.TextUtils;
import android.text.util.Linkify;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.nononsenseapps.filepicker.Utils;

import org.schabi.newpipe.BaseFragment;
import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.subscription.SubscriptionExtractor;
import org.schabi.newpipe.report.ErrorActivity;
import org.schabi.newpipe.report.UserAction;
import org.schabi.newpipe.subscription.services.SubscriptionsImportService;
import org.schabi.newpipe.util.Constants;
import org.schabi.newpipe.util.FilePickerActivityHelper;
import org.schabi.newpipe.util.ServiceHelper;

import java.util.Collections;
import java.util.List;

import icepick.State;

import static org.schabi.newpipe.extractor.subscription.SubscriptionExtractor.ContentSource.CHANNEL_URL;
import static org.schabi.newpipe.subscription.services.SubscriptionsImportService.CHANNEL_URL_MODE;
import static org.schabi.newpipe.subscription.services.SubscriptionsImportService.INPUT_STREAM_MODE;
import static org.schabi.newpipe.subscription.services.SubscriptionsImportService.KEY_MODE;
import static org.schabi.newpipe.subscription.services.SubscriptionsImportService.KEY_VALUE;

public class SubscriptionsImportFragment extends BaseFragment {
    private static final int REQUEST_IMPORT_FILE_CODE = 666;

    @State
    protected int currentServiceId = Constants.NO_SERVICE_ID;

    private List<SubscriptionExtractor.ContentSource> supportedSources;
    private String relatedUrl;
    @StringRes
    private int instructionsString;

    public static SubscriptionsImportFragment getInstance(int serviceId) {
        SubscriptionsImportFragment instance = new SubscriptionsImportFragment();
        instance.setInitialData(serviceId);
        return instance;
    }

    public void setInitialData(int serviceId) {
        this.currentServiceId = serviceId;
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Views
    //////////////////////////////////////////////////////////////////////////*/

    private TextView infoTextView;

    private EditText inputText;
    private Button inputButton;

    ///////////////////////////////////////////////////////////////////////////
    // Fragment LifeCycle
    ///////////////////////////////////////////////////////////////////////////


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setupServiceVariables();
        if (supportedSources.isEmpty() && currentServiceId != Constants.NO_SERVICE_ID) {
            ErrorActivity.reportError(activity, Collections.emptyList(), null, null, ErrorActivity.ErrorInfo.make(UserAction.SOMETHING_ELSE,
                    NewPipe.getNameOfService(currentServiceId), "Service don't support importing", R.string.general_error));
            activity.finish();
        }
    }

    @Override
    public void setUserVisibleHint(boolean isVisibleToUser) {
        super.setUserVisibleHint(isVisibleToUser);
        if (isVisibleToUser) {
            setTitle(getString(R.string.import_title));
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_import, container, false);
    }

    /*/////////////////////////////////////////////////////////////////////////
    // Fragment Views
    /////////////////////////////////////////////////////////////////////////*/

    @Override
    protected void initViews(View rootView, Bundle savedInstanceState) {
        super.initViews(rootView, savedInstanceState);

        inputButton = rootView.findViewById(R.id.input_button);
        inputText = rootView.findViewById(R.id.input_text);

        infoTextView = rootView.findViewById(R.id.info_text_view);

        // TODO: Support services that can import from more than one source (show the option to the user)
        if (supportedSources.contains(CHANNEL_URL)) {
            inputButton.setText(R.string.import_title);
            inputText.setVisibility(View.VISIBLE);
            inputText.setHint(ServiceHelper.getImportInstructionsHint(currentServiceId));
        } else {
            inputButton.setText(R.string.import_file_title);
        }

        if (instructionsString != 0) {
            if (TextUtils.isEmpty(relatedUrl)) {
                setInfoText(getString(instructionsString));
            } else {
                setInfoText(getString(instructionsString, relatedUrl));
            }
        } else {
            setInfoText("");
        }

        ActionBar supportActionBar = activity.getSupportActionBar();
        if (supportActionBar != null) {
            supportActionBar.setDisplayShowTitleEnabled(true);
            setTitle(getString(R.string.import_title));
        }
    }

    @Override
    protected void initListeners() {
        super.initListeners();
        inputButton.setOnClickListener(v -> onImportClicked());
    }

    private void onImportClicked() {
        if (inputText.getVisibility() == View.VISIBLE) {
            final String value = inputText.getText().toString();
            if (!value.isEmpty()) onImportUrl(value);
        } else {
            onImportFile();
        }
    }

    public void onImportUrl(String value) {
        ImportConfirmationDialog.show(this, new Intent(activity, SubscriptionsImportService.class)
                .putExtra(KEY_MODE, CHANNEL_URL_MODE)
                .putExtra(KEY_VALUE, value)
                .putExtra(Constants.KEY_SERVICE_ID, currentServiceId));
    }

    public void onImportFile() {
        startActivityForResult(FilePickerActivityHelper.chooseSingleFile(activity), REQUEST_IMPORT_FILE_CODE);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (data == null) return;

        if (resultCode == Activity.RESULT_OK && requestCode == REQUEST_IMPORT_FILE_CODE && data.getData() != null) {
            final String path = Utils.getFileForUri(data.getData()).getAbsolutePath();
            ImportConfirmationDialog.show(this, new Intent(activity, SubscriptionsImportService.class)
                    .putExtra(KEY_MODE, INPUT_STREAM_MODE)
                    .putExtra(KEY_VALUE, path)
                    .putExtra(Constants.KEY_SERVICE_ID, currentServiceId));
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    // Subscriptions
    ///////////////////////////////////////////////////////////////////////////

    private void setupServiceVariables() {
        if (currentServiceId != Constants.NO_SERVICE_ID) {
            try {
                final SubscriptionExtractor extractor = NewPipe.getService(currentServiceId).getSubscriptionExtractor();
                supportedSources = extractor.getSupportedSources();
                relatedUrl = extractor.getRelatedUrl();
                instructionsString = ServiceHelper.getImportInstructions(currentServiceId);
                return;
            } catch (ExtractionException ignored) {
            }
        }

        supportedSources = Collections.emptyList();
        relatedUrl = null;
        instructionsString = 0;
    }

    private void setInfoText(String infoString) {
        infoTextView.setText(infoString);
        LinkifyCompat.addLinks(infoTextView, Linkify.WEB_URLS);
    }
}
