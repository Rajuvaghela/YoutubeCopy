package org.schabi.newpipe;

import android.app.IntentService;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.DrawableRes;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Toast;

import org.schabi.newpipe.extractor.Info;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.StreamingService.LinkType;
import org.schabi.newpipe.extractor.channel.ChannelInfo;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.playlist.PlaylistInfo;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.player.helper.PlayerHelper;
import org.schabi.newpipe.playlist.ChannelPlayQueue;
import org.schabi.newpipe.playlist.PlayQueue;
import org.schabi.newpipe.playlist.PlaylistPlayQueue;
import org.schabi.newpipe.playlist.SinglePlayQueue;
import org.schabi.newpipe.report.UserAction;
import org.schabi.newpipe.util.ExtractorHelper;
import org.schabi.newpipe.util.NavigationHelper;
import org.schabi.newpipe.util.PermissionHelper;
import org.schabi.newpipe.util.ThemeHelper;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;

import icepick.Icepick;
import icepick.State;
import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Consumer;
import io.reactivex.schedulers.Schedulers;

import static org.schabi.newpipe.extractor.StreamingService.ServiceInfo.MediaCapability.AUDIO;
import static org.schabi.newpipe.extractor.StreamingService.ServiceInfo.MediaCapability.VIDEO;
import static org.schabi.newpipe.util.ThemeHelper.resolveResourceIdFromAttr;

/**
 * Get the url from the intent and open it in the chosen preferred player
 */
public class RouterActivity extends AppCompatActivity {

    @State protected int currentServiceId = -1;
    private StreamingService currentService;
    @State protected LinkType currentLinkType;
    @State protected int selectedRadioPosition = -1;
    protected int selectedPreviously = -1;

    protected String currentUrl;
    protected CompositeDisposable disposables = new CompositeDisposable();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Icepick.restoreInstanceState(this, savedInstanceState);

        if (TextUtils.isEmpty(currentUrl)) {
            currentUrl = getUrl(getIntent());

            if (TextUtils.isEmpty(currentUrl)) {
                Toast.makeText(this, R.string.invalid_url_toast, Toast.LENGTH_LONG).show();
                finish();
            }
        }

        setTheme(ThemeHelper.isLightThemeSelected(this)
                ? R.style.RouterActivityThemeLight : R.style.RouterActivityThemeDark);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        Icepick.saveInstanceState(this, outState);
    }

    @Override
    protected void onStart() {
        super.onStart();

        handleUrl(currentUrl);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        disposables.clear();
    }

    private void handleUrl(String url) {
        disposables.add(Observable
                .fromCallable(() -> {
                    if (currentServiceId == -1) {
                        currentService = NewPipe.getServiceByUrl(url);
                        currentServiceId = currentService.getServiceId();
                        currentLinkType = currentService.getLinkTypeByUrl(url);
                        currentUrl = url;
                    } else {
                        currentService = NewPipe.getService(currentServiceId);
                    }

                    return currentLinkType != LinkType.NONE;
                })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(result -> {
                    if (result) {
                        onSuccess();
                    } else {
                        onError();
                    }
                }, this::handleError));
    }

    private void handleError(Throwable error) {
        error.printStackTrace();

        if (error instanceof ExtractionException) {
            Toast.makeText(this, R.string.url_not_supported_toast, Toast.LENGTH_LONG).show();
        } else {
            ExtractorHelper.handleGeneralException(this, -1, null, error, UserAction.SOMETHING_ELSE, null);
        }

        finish();
    }

    private void onError() {
        Toast.makeText(this, R.string.url_not_supported_toast, Toast.LENGTH_LONG).show();
        finish();
    }

    protected void onSuccess() {
        final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        final String selectedChoiceKey = preferences.getString(getString(R.string.preferred_open_action_key), getString(R.string.preferred_open_action_default));

        final String showInfoKey = getString(R.string.show_info_key);
        final String videoPlayerKey = getString(R.string.video_player_key);
        final String backgroundPlayerKey = getString(R.string.background_player_key);
        final String popupPlayerKey = getString(R.string.popup_player_key);
        final String alwaysAskKey = getString(R.string.always_ask_open_action_key);

        if (selectedChoiceKey.equals(alwaysAskKey)) {
            final List<AdapterChoiceItem> choices = getChoicesForService(currentService, currentLinkType);

            if (choices.size() == 1) {
                handleChoice(choices.get(0).key);
            } else if (choices.size() == 0) {
                handleChoice(showInfoKey);
            } else {
                showDialog(choices);
            }
        } else if (selectedChoiceKey.equals(showInfoKey)) {
            handleChoice(showInfoKey);
        } else {
            final boolean isExtVideoEnabled = preferences.getBoolean(getString(R.string.use_external_video_player_key), false);
            final boolean isExtAudioEnabled = preferences.getBoolean(getString(R.string.use_external_audio_player_key), false);
            final boolean isVideoPlayerSelected = selectedChoiceKey.equals(videoPlayerKey) || selectedChoiceKey.equals(popupPlayerKey);
            final boolean isAudioPlayerSelected = selectedChoiceKey.equals(backgroundPlayerKey);

            if (currentLinkType != LinkType.STREAM) {
                if (isExtAudioEnabled && isAudioPlayerSelected || isExtVideoEnabled && isVideoPlayerSelected) {
                    Toast.makeText(this, R.string.external_player_unsupported_link_type, Toast.LENGTH_LONG).show();
                    handleChoice(showInfoKey);
                    return;
                }
            }

            final List<StreamingService.ServiceInfo.MediaCapability> capabilities = currentService.getServiceInfo().getMediaCapabilities();

            boolean serviceSupportsChoice = false;
            if (isVideoPlayerSelected) {
                serviceSupportsChoice = capabilities.contains(VIDEO);
            } else if (selectedChoiceKey.equals(backgroundPlayerKey)) {
                serviceSupportsChoice = capabilities.contains(AUDIO);
            }

            if (serviceSupportsChoice) {
                handleChoice(selectedChoiceKey);
            } else {
                handleChoice(showInfoKey);
            }
        }
    }

    private void showDialog(final List<AdapterChoiceItem> choices) {
        final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        final Context themeWrapperContext = getThemeWrapperContext();

        final LayoutInflater inflater = LayoutInflater.from(themeWrapperContext);
        final LinearLayout rootLayout = (LinearLayout) inflater.inflate(R.layout.preferred_player_dialog_view, null, false);
        final RadioGroup radioGroup = rootLayout.findViewById(android.R.id.list);

        final DialogInterface.OnClickListener dialogButtonsClickListener = (dialog, which) -> {
            final int indexOfChild = radioGroup.indexOfChild(
                    radioGroup.findViewById(radioGroup.getCheckedRadioButtonId()));
            final AdapterChoiceItem choice = choices.get(indexOfChild);

            handleChoice(choice.key);

            if (which == DialogInterface.BUTTON_POSITIVE) {
                preferences.edit().putString(getString(R.string.preferred_open_action_key), choice.key).apply();
            }
        };

        final AlertDialog alertDialog = new AlertDialog.Builder(themeWrapperContext)
                .setTitle(R.string.preferred_open_action_share_menu_title)
                .setView(radioGroup)
                .setCancelable(true)
                .setNegativeButton(R.string.just_once, dialogButtonsClickListener)
                .setPositiveButton(R.string.always, dialogButtonsClickListener)
                .setOnDismissListener((dialog) -> finish())
                .create();

        //noinspection CodeBlock2Expr
        alertDialog.setOnShowListener(dialog -> {
            setDialogButtonsState(alertDialog, radioGroup.getCheckedRadioButtonId() != -1);
        });

        radioGroup.setOnCheckedChangeListener((group, checkedId) -> setDialogButtonsState(alertDialog, true));
        final View.OnClickListener radioButtonsClickListener = v -> {
            final int indexOfChild = radioGroup.indexOfChild(v);
            if (indexOfChild == -1) return;

            selectedPreviously = selectedRadioPosition;
            selectedRadioPosition = indexOfChild;

            if (selectedPreviously == selectedRadioPosition) {
                handleChoice(choices.get(selectedRadioPosition).key);
            }
        };

        int id = 12345;
        for (AdapterChoiceItem item : choices) {
            final RadioButton radioButton = (RadioButton) inflater.inflate(R.layout.list_radio_icon_item, null);
            radioButton.setText(item.description);
            radioButton.setCompoundDrawablesWithIntrinsicBounds(item.icon, 0, 0, 0);
            radioButton.setChecked(false);
            radioButton.setId(id++);
            radioButton.setLayoutParams(new RadioGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            radioButton.setOnClickListener(radioButtonsClickListener);
            radioGroup.addView(radioButton);
        }

        if (selectedRadioPosition == -1) {
            final String lastSelectedPlayer = preferences.getString(getString(R.string.preferred_open_action_last_selected_key), null);
            if (!TextUtils.isEmpty(lastSelectedPlayer)) {
                for (int i = 0; i < choices.size(); i++) {
                    AdapterChoiceItem c = choices.get(i);
                    if (lastSelectedPlayer.equals(c.key)) {
                        selectedRadioPosition = i;
                        break;
                    }
                }
            }
        }

        selectedRadioPosition = Math.min(Math.max(-1, selectedRadioPosition), choices.size() - 1);
        if (selectedRadioPosition != -1) {
            ((RadioButton) radioGroup.getChildAt(selectedRadioPosition)).setChecked(true);
        }
        selectedPreviously = selectedRadioPosition;

        alertDialog.show();
    }

    private List<AdapterChoiceItem> getChoicesForService(StreamingService service, LinkType linkType) {
        final Context context = getThemeWrapperContext();

        final List<AdapterChoiceItem> returnList = new ArrayList<>();
        final List<StreamingService.ServiceInfo.MediaCapability> capabilities = service.getServiceInfo().getMediaCapabilities();

        final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        boolean isExtVideoEnabled = preferences.getBoolean(getString(R.string.use_external_video_player_key), false);
        boolean isExtAudioEnabled = preferences.getBoolean(getString(R.string.use_external_audio_player_key), false);

        returnList.add(new AdapterChoiceItem(getString(R.string.show_info_key), getString(R.string.show_info),
                resolveResourceIdFromAttr(context, R.attr.info)));

        if (capabilities.contains(VIDEO) && !(isExtVideoEnabled && linkType != LinkType.STREAM)) {
            returnList.add(new AdapterChoiceItem(getString(R.string.video_player_key), getString(R.string.video_player),
                    resolveResourceIdFromAttr(context, R.attr.play)));
            returnList.add(new AdapterChoiceItem(getString(R.string.popup_player_key), getString(R.string.popup_player),
                    resolveResourceIdFromAttr(context, R.attr.popup)));
        }

        if (capabilities.contains(AUDIO) && !(isExtAudioEnabled && linkType != LinkType.STREAM)) {
            returnList.add(new AdapterChoiceItem(getString(R.string.background_player_key), getString(R.string.background_player),
                    resolveResourceIdFromAttr(context, R.attr.audio)));
        }

        return returnList;
    }

    private Context getThemeWrapperContext() {
        return new ContextThemeWrapper(this,
                ThemeHelper.isLightThemeSelected(this) ? R.style.LightTheme : R.style.DarkTheme);
    }

    private void setDialogButtonsState(AlertDialog dialog, boolean state) {
        final Button negativeButton = dialog.getButton(DialogInterface.BUTTON_NEGATIVE);
        final Button positiveButton = dialog.getButton(DialogInterface.BUTTON_POSITIVE);
        if (negativeButton == null || positiveButton == null) return;

        negativeButton.setEnabled(state);
        positiveButton.setEnabled(state);
    }

    private void handleChoice(final String selectedChoiceKey) {
        final List<String> validChoicesList = Arrays.asList(getResources().getStringArray(R.array.preferred_open_action_values_list));
        if (validChoicesList.contains(selectedChoiceKey)) {
            PreferenceManager.getDefaultSharedPreferences(this).edit()
                    .putString(getString(R.string.preferred_open_action_last_selected_key), selectedChoiceKey)
                    .apply();
        }

        if (selectedChoiceKey.equals(getString(R.string.popup_player_key)) && !PermissionHelper.isPopupEnabled(this)) {
            PermissionHelper.showPopupEnablementToast(this);
            finish();
            return;
        }

        // stop and bypass FetcherService if InfoScreen was selected since
        // StreamDetailFragment can fetch data itself
        if (selectedChoiceKey.equals(getString(R.string.show_info_key))) {
            disposables.add(Observable
                    .fromCallable(() -> NavigationHelper.getIntentByLink(this, currentUrl))
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(intent -> {
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);

                        finish();
                    }, this::handleError)
            );
            return;
        }

        final Intent intent = new Intent(this, FetcherService.class);
        final Choice choice = new Choice(currentService.getServiceId(), currentLinkType, currentUrl, selectedChoiceKey);
        intent.putExtra(FetcherService.KEY_CHOICE, choice);
        startService(intent);

        finish();
    }

    private static class AdapterChoiceItem {
        final String description, key;
        @DrawableRes final int icon;

        AdapterChoiceItem(String key, String description, int icon) {
            this.description = description;
            this.key = key;
            this.icon = icon;
        }
    }

    private static class Choice implements Serializable {
        final int serviceId;
        final String url, playerChoice;
        final LinkType linkType;

        Choice(int serviceId, LinkType linkType, String url, String playerChoice) {
            this.serviceId = serviceId;
            this.linkType = linkType;
            this.url = url;
            this.playerChoice = playerChoice;
        }

        @Override
        public String toString() {
            return serviceId + ":" + url + " > " + linkType + " ::: " + playerChoice;
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Service Fetcher
    //////////////////////////////////////////////////////////////////////////*/

    public static class FetcherService extends IntentService {

        private static final int ID = 456;
        public static final String KEY_CHOICE = "key_choice";
        private Disposable fetcher;

        public FetcherService() {
            super(FetcherService.class.getSimpleName());
        }

        @Override
        public void onCreate() {
            super.onCreate();
            startForeground(ID, createNotification().build());
        }

        @Override
        protected void onHandleIntent(@Nullable Intent intent) {
            if (intent == null) return;

            final Serializable serializable = intent.getSerializableExtra(KEY_CHOICE);
            if (!(serializable instanceof Choice)) return;
            Choice playerChoice = (Choice) serializable;
            handleChoice(playerChoice);
        }

        public void handleChoice(Choice choice) {
            Single<? extends Info> single = null;
            UserAction userAction = UserAction.SOMETHING_ELSE;

            switch (choice.linkType) {
                case STREAM:
                    single = ExtractorHelper.getStreamInfo(choice.serviceId, choice.url, false);
                    userAction = UserAction.REQUESTED_STREAM;
                    break;
                case CHANNEL:
                    single = ExtractorHelper.getChannelInfo(choice.serviceId, choice.url, false);
                    userAction = UserAction.REQUESTED_CHANNEL;
                    break;
                case PLAYLIST:
                    single = ExtractorHelper.getPlaylistInfo(choice.serviceId, choice.url, false);
                    userAction = UserAction.REQUESTED_PLAYLIST;
                    break;
            }


            if (single != null) {
                final UserAction finalUserAction = userAction;
                final Consumer<Info> resultHandler = getResultHandler(choice);
                fetcher = single
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(info -> {
                            resultHandler.accept(info);
                            if (fetcher != null) fetcher.dispose();
                        }, throwable -> ExtractorHelper.handleGeneralException(this,
                                choice.serviceId, choice.url, throwable, finalUserAction, ", opened with " + choice.playerChoice));
            }
        }

        public Consumer<Info> getResultHandler(Choice choice) {
            return info -> {
                final String videoPlayerKey = getString(R.string.video_player_key);
                final String backgroundPlayerKey = getString(R.string.background_player_key);
                final String popupPlayerKey = getString(R.string.popup_player_key);

                final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
                boolean isExtVideoEnabled = preferences.getBoolean(getString(R.string.use_external_video_player_key), false);
                boolean isExtAudioEnabled = preferences.getBoolean(getString(R.string.use_external_audio_player_key), false);
                boolean useOldVideoPlayer = PlayerHelper.isUsingOldPlayer(this);

                PlayQueue playQueue;
                String playerChoice = choice.playerChoice;

                if (info instanceof StreamInfo) {
                    if (playerChoice.equals(backgroundPlayerKey) && isExtAudioEnabled) {
                        NavigationHelper.playOnExternalAudioPlayer(this, (StreamInfo) info);

                    } else if (playerChoice.equals(videoPlayerKey) && isExtVideoEnabled) {
                        NavigationHelper.playOnExternalVideoPlayer(this, (StreamInfo) info);

                    } else if (playerChoice.equals(videoPlayerKey) && useOldVideoPlayer) {
                        NavigationHelper.playOnOldVideoPlayer(this, (StreamInfo) info);

                    } else {
                        playQueue = new SinglePlayQueue((StreamInfo) info);

                        if (playerChoice.equals(videoPlayerKey)) {
                            NavigationHelper.playOnMainPlayer(this, playQueue);
                        } else if (playerChoice.equals(backgroundPlayerKey)) {
                            NavigationHelper.enqueueOnBackgroundPlayer(this, playQueue, true);
                        } else if (playerChoice.equals(popupPlayerKey)) {
                            NavigationHelper.enqueueOnPopupPlayer(this, playQueue, true);
                        }
                    }
                }

                if (info instanceof ChannelInfo || info instanceof PlaylistInfo) {
                    playQueue = info instanceof ChannelInfo ? new ChannelPlayQueue((ChannelInfo) info) : new PlaylistPlayQueue((PlaylistInfo) info);

                    if (playerChoice.equals(videoPlayerKey)) {
                        NavigationHelper.playOnMainPlayer(this, playQueue);
                    } else if (playerChoice.equals(backgroundPlayerKey)) {
                        NavigationHelper.playOnBackgroundPlayer(this, playQueue);
                    } else if (playerChoice.equals(popupPlayerKey)) {
                        NavigationHelper.playOnPopupPlayer(this, playQueue);
                    }
                }
            };
        }

        @Override
        public void onDestroy() {
            super.onDestroy();
            stopForeground(true);
            if (fetcher != null) fetcher.dispose();
        }

        private NotificationCompat.Builder createNotification() {
            return new NotificationCompat.Builder(this, getString(R.string.notification_channel_id))
                    .setOngoing(true)
                    .setSmallIcon(R.drawable.ic_newpipe_triangle_white)
                    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                    .setContentTitle(getString(R.string.preferred_player_fetcher_notification_title))
                    .setContentText(getString(R.string.preferred_player_fetcher_notification_message));
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Utils
    //////////////////////////////////////////////////////////////////////////*/

    /**
     * Removes invisible separators (\p{Z}) and punctuation characters including
     * brackets (\p{P}). See http://www.regular-expressions.info/unicode.html for
     * more details.
     */
    private final static String REGEX_REMOVE_FROM_URL = "[\\p{Z}\\p{P}]";

    private String getUrl(Intent intent) {
        // first gather data and find service
        String videoUrl = null;
        if (intent.getData() != null) {
            // this means the video was called though another app
            videoUrl = intent.getData().toString();
        } else if (intent.getStringExtra(Intent.EXTRA_TEXT) != null) {
            //this means that vidoe was called through share menu
            String extraText = intent.getStringExtra(Intent.EXTRA_TEXT);
            final String[] uris = getUris(extraText);
            videoUrl = uris.length > 0 ? uris[0] : null;
        }

        return videoUrl;
    }

    private String removeHeadingGibberish(final String input) {
        int start = 0;
        for (int i = input.indexOf("://") - 1; i >= 0; i--) {
            if (!input.substring(i, i + 1).matches("\\p{L}")) {
                start = i + 1;
                break;
            }
        }
        return input.substring(start, input.length());
    }

    private String trim(final String input) {
        if (input == null || input.length() < 1) {
            return input;
        } else {
            String output = input;
            while (output.length() > 0 && output.substring(0, 1).matches(REGEX_REMOVE_FROM_URL)) {
                output = output.substring(1);
            }
            while (output.length() > 0
                    && output.substring(output.length() - 1, output.length()).matches(REGEX_REMOVE_FROM_URL)) {
                output = output.substring(0, output.length() - 1);
            }
            return output;
        }
    }

    /**
     * Retrieves all Strings which look remotely like URLs from a text.
     * Used if NewPipe was called through share menu.
     *
     * @param sharedText text to scan for URLs.
     * @return potential URLs
     */
    protected String[] getUris(final String sharedText) {
        final Collection<String> result = new HashSet<>();
        if (sharedText != null) {
            final String[] array = sharedText.split("\\p{Space}");
            for (String s : array) {
                s = trim(s);
                if (s.length() != 0) {
                    if (s.matches(".+://.+")) {
                        result.add(removeHeadingGibberish(s));
                    } else if (s.matches(".+\\..+")) {
                        result.add("http://" + s);
                    }
                }
            }
        }
        return result.toArray(new String[result.size()]);
    }
}
