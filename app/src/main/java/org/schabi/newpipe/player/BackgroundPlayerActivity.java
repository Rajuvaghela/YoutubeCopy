package org.schabi.newpipe.player;

import android.content.Intent;
import android.view.MenuItem;

import org.schabi.newpipe.R;
import org.schabi.newpipe.util.PermissionHelper;

import static org.schabi.newpipe.player.BackgroundPlayer.ACTION_CLOSE;

public final class BackgroundPlayerActivity extends ServicePlayerActivity {

    private static final String TAG = "BackgroundPlayerActivity";

    @Override
    public String getTag() {
        return TAG;
    }

    @Override
    public String getSupportActionTitle() {
        return getResources().getString(R.string.title_activity_background_player);
    }

    @Override
    public Intent getBindIntent() {
        return new Intent(this, BackgroundPlayer.class);
    }

    @Override
    public void startPlayerListener() {
        if (player != null && player instanceof BackgroundPlayer.BasePlayerImpl) {
            ((BackgroundPlayer.BasePlayerImpl) player).setActivityListener(this);
        }
    }

    @Override
    public void stopPlayerListener() {
        if (player != null && player instanceof BackgroundPlayer.BasePlayerImpl) {
            ((BackgroundPlayer.BasePlayerImpl) player).removeActivityListener(this);
        }
    }

    @Override
    public int getPlayerOptionMenuResource() {
        return R.menu.menu_play_queue_bg;
    }

    @Override
    public boolean onPlayerOptionSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_switch_popup) {

            if (!PermissionHelper.isPopupEnabled(this)) {
                PermissionHelper.showPopupEnablementToast(this);
                return true;
            }

            this.player.setRecovery();
            getApplicationContext().sendBroadcast(getPlayerShutdownIntent());
            getApplicationContext().startService(getSwitchIntent(PopupVideoPlayer.class));
            return true;
        }
        return false;
    }

    @Override
    public Intent getPlayerShutdownIntent() {
        return new Intent(ACTION_CLOSE);
    }
}
