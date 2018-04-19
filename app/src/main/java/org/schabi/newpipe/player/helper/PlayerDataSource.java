package org.schabi.newpipe.player.helper;

import android.content.Context;
import android.support.annotation.NonNull;

import com.google.android.exoplayer2.source.ExtractorMediaSource;
import com.google.android.exoplayer2.source.SingleSampleMediaSource;
import com.google.android.exoplayer2.source.dash.DashMediaSource;
import com.google.android.exoplayer2.source.dash.DefaultDashChunkSource;
import com.google.android.exoplayer2.source.hls.HlsMediaSource;
import com.google.android.exoplayer2.source.smoothstreaming.DefaultSsChunkSource;
import com.google.android.exoplayer2.source.smoothstreaming.SsMediaSource;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.upstream.TransferListener;

public class PlayerDataSource {
    private static final int MANIFEST_MINIMUM_RETRY = 5;
    private static final int EXTRACTOR_MINIMUM_RETRY = Integer.MAX_VALUE;
    private static final int LIVE_STREAM_EDGE_GAP_MILLIS = 10000;

    private final DataSource.Factory cacheDataSourceFactory;
    private final DataSource.Factory cachelessDataSourceFactory;

    public PlayerDataSource(@NonNull final Context context,
                            @NonNull final String userAgent,
                            @NonNull final TransferListener<? super DataSource> transferListener) {
        cacheDataSourceFactory = new CacheFactory(context, userAgent, transferListener);
        cachelessDataSourceFactory = new DefaultDataSourceFactory(context, userAgent, transferListener);
    }

    public SsMediaSource.Factory getLiveSsMediaSourceFactory() {
        return new SsMediaSource.Factory(new DefaultSsChunkSource.Factory(
                cachelessDataSourceFactory), cachelessDataSourceFactory)
                .setMinLoadableRetryCount(MANIFEST_MINIMUM_RETRY)
                .setLivePresentationDelayMs(LIVE_STREAM_EDGE_GAP_MILLIS);
    }

    public HlsMediaSource.Factory getLiveHlsMediaSourceFactory() {
        return new HlsMediaSource.Factory(cachelessDataSourceFactory)
                .setAllowChunklessPreparation(true)
                .setMinLoadableRetryCount(MANIFEST_MINIMUM_RETRY);
    }

    public DashMediaSource.Factory getLiveDashMediaSourceFactory() {
        return new DashMediaSource.Factory(new DefaultDashChunkSource.Factory(
                cachelessDataSourceFactory), cachelessDataSourceFactory)
                .setMinLoadableRetryCount(MANIFEST_MINIMUM_RETRY)
                .setLivePresentationDelayMs(LIVE_STREAM_EDGE_GAP_MILLIS);
    }

    public SsMediaSource.Factory getSsMediaSourceFactory() {
        return new SsMediaSource.Factory(new DefaultSsChunkSource.Factory(
                cacheDataSourceFactory), cacheDataSourceFactory);
    }

    public HlsMediaSource.Factory getHlsMediaSourceFactory() {
        return new HlsMediaSource.Factory(cacheDataSourceFactory);
    }

    public DashMediaSource.Factory getDashMediaSourceFactory() {
        return new DashMediaSource.Factory(new DefaultDashChunkSource.Factory(
                cacheDataSourceFactory), cacheDataSourceFactory);
    }

    public ExtractorMediaSource.Factory getExtractorMediaSourceFactory() {
        return new ExtractorMediaSource.Factory(cacheDataSourceFactory)
                .setMinLoadableRetryCount(EXTRACTOR_MINIMUM_RETRY);
    }

    public ExtractorMediaSource.Factory getExtractorMediaSourceFactory(@NonNull final String key) {
        return getExtractorMediaSourceFactory().setCustomCacheKey(key);
    }

    public SingleSampleMediaSource.Factory getSampleMediaSourceFactory() {
        return new SingleSampleMediaSource.Factory(cacheDataSourceFactory);
    }
}
