package com.jobbootcamp.course.media;

import com.tencentcloudapi.common.Credential;
import com.tencentcloudapi.common.exception.TencentCloudSDKException;
import com.tencentcloudapi.vod.v20180717.VodClient;
import com.tencentcloudapi.vod.v20180717.models.DescribeMediaInfosRequest;
import com.tencentcloudapi.vod.v20180717.models.DescribeMediaInfosResponse;
import com.tencentcloudapi.vod.v20180717.models.MediaInfo;
import com.tencentcloudapi.vod.v20180717.models.MediaMetaData;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * 腾讯云 VOD 适配器；只在 VOD_ENABLED=true 时注册。无凭据时不伪装可用，验证返回明确错误。
 */
@Component
@ConditionalOnProperty(name = "app.media.vod.enabled", havingValue = "true")
public class TencentVodAdapter implements MediaPort {

    private static final String[] INFO_FILTERS = {"basicInfo", "metaData"};

    private final VodClient vodClient;
    private final String appId;
    private final VodPlaySigner playSigner;

    public TencentVodAdapter(@Value("${app.media.vod.app-id}") String appId,
                             @Value("${app.media.vod.secret-id}") String secretId,
                             @Value("${app.media.vod.secret-key}") String secretKey,
                             @Value("${app.media.vod.region}") String region,
                             VodPlaySigner playSigner) {
        this.appId = appId;
        this.playSigner = playSigner;
        this.vodClient = new VodClient(new Credential(secretId, secretKey), region);
    }

    @Override
    public MediaVerification verify(MediaRef media) {
        if (media.fileId() == null || media.fileId().isBlank()) {
            return MediaVerification.fail("VOD_FILE_ID_MISSING", "VOD fileId 为空");
        }
        DescribeMediaInfosRequest request = new DescribeMediaInfosRequest();
        request.setFileIds(new String[]{media.fileId()});
        request.setFilters(INFO_FILTERS);
        try {
            DescribeMediaInfosResponse response = vodClient.DescribeMediaInfos(request);
            MediaInfo[] infos = response.getMediaInfoSet();
            if (infos == null || infos.length == 0) {
                return MediaVerification.fail("VOD_NOT_FOUND", "VOD 中未找到该媒资");
            }
            MediaInfo info = infos[0];
            MediaMetaData metadata = info.getMetaData();
            if (metadata == null) {
                return MediaVerification.fail("VOD_METADATA_MISSING", "VOD 媒资缺少元数据");
            }
            Number duration = metadata.getDuration();
            if (duration == null || duration.doubleValue() <= 0) {
                return MediaVerification.fail("VOD_DURATION_INVALID", "VOD 媒资时长无效");
            }
            return MediaVerification.ok(Math.round(duration.doubleValue() * 1000), null);
        } catch (TencentCloudSDKException e) {
            return MediaVerification.fail("VOD_ERROR", "VOD 查询失败：" + e.getErrorCode());
        }
    }

    @Override
    public MediaAuthorization authorize(MediaRef media, Long userId, Long authSessionId,
                                        String playSessionId, Instant expiresAt) {
        String psign = playSigner.sign(appId, media.fileId(), userId, authSessionId, expiresAt);
        return new MediaAuthorization("VOD", playSessionId, expiresAt, appId, media.fileId(), psign,
                null, media.durationMs());
    }
}