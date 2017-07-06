package org.wordpress.android.ui.uploads;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.support.annotation.NonNull;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.wordpress.android.WordPress;
import org.wordpress.android.fluxc.Dispatcher;
import org.wordpress.android.fluxc.generated.PostActionBuilder;
import org.wordpress.android.fluxc.model.MediaModel;
import org.wordpress.android.fluxc.model.PostModel;
import org.wordpress.android.fluxc.store.MediaStore;
import org.wordpress.android.fluxc.store.MediaStore.OnMediaUploaded;
import org.wordpress.android.fluxc.store.PostStore;
import org.wordpress.android.fluxc.store.SiteStore;
import org.wordpress.android.ui.media.services.MediaUploadReadyListener;
import org.wordpress.android.util.AppLog;
import org.wordpress.android.util.AppLog.T;
import org.wordpress.android.util.DateTimeUtils;
import org.wordpress.android.util.FluxCUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import javax.inject.Inject;

import de.greenrobot.event.EventBus;

public class UploadService extends Service {
    private static final String MEDIA_LIST_KEY = "mediaList";
    private static final String LOCAL_POST_ID_KEY = "localPostId";

    private static MediaUploadManager sMediaUploadManager;
    private static PostUploadManager sPostUploadManager;
    private PostUploadNotifier mPostUploadNotifier;

    private static final List<PostModel> sPostsWithPendingMedia = new ArrayList<>();

    // To avoid conflicts by editing the post each time a single upload completes, this map tracks completed media by
    // the post they're attached to, allowing us to update the post with the media URLs in a single batch at the end
    private static HashMap<Integer, List<MediaModel>> mCompletedUploadsByPost = new HashMap<>();

    @Inject Dispatcher mDispatcher;
    @Inject MediaStore mMediaStore;
    @Inject PostStore mPostStore;
    @Inject SiteStore mSiteStore;

    @Override
    public void onCreate() {
        super.onCreate();
        ((WordPress) getApplication()).component().inject(this);
        AppLog.i(T.MAIN, "Upload Service > created");
        mDispatcher.register(this);
        // TODO: Recover any posts/media uploads that were interrupted by the service being stopped
    }

    @Override
    public void onDestroy() {
        // TODO: Cancel in-progress uploads

        // update posts with any completed uploads in our post->media map
        for (Integer postId : mCompletedUploadsByPost.keySet()) {
            PostModel updatedPost = updatePostWithCurrentlyCompletedUploads(mPostStore.getPostByLocalPostId(postId));
            mDispatcher.dispatch(PostActionBuilder.newUpdatePostAction(updatedPost));
        }

        // TODO revert state of any uploading posts back to draft

        // TODO unregister managers as well
        mDispatcher.unregister(this);
        AppLog.i(T.MAIN, "Upload Service > destroyed");
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (sMediaUploadManager == null) {
            sMediaUploadManager = new MediaUploadManager();
        }

        if (mPostUploadNotifier == null) {
            mPostUploadNotifier = new PostUploadNotifier(getApplicationContext(), this);
        }

        if (sPostUploadManager == null) {
            sPostUploadManager = new PostUploadManager(this, mPostUploadNotifier);
        }

        if (intent.hasExtra(MEDIA_LIST_KEY)) {
            unpackMediaIntent(intent);
        }

        if (intent.hasExtra(LOCAL_POST_ID_KEY)) {
            unpackPostIntent(intent);
        }

        return START_REDELIVER_INTENT;
    }

    private void unpackMediaIntent(@NonNull Intent intent) {
        // TODO right now, in the case we had pending uploads and the app/service was restarted,
        // we don't really have a way to tell which media was supposed to be added to which post,
        // unless we open each draft post from the PostStore and try to see if there was any locally added media to try
        // and match their IDs.
        // So let's hold on a bit on this functionality, the service won't be recovering any
        // pending / missing / cancelled / interrupted uploads for now

//        // add local queued media from store
//        List<MediaModel> localMedia = mMediaStore.getLocalSiteMedia(site);
//        if (localMedia != null && !localMedia.isEmpty()) {
//            // uploading is updated to queued, queued media added to the queue, failed media added to completed list
//            for (MediaModel mediaItem : localMedia) {
//
//                if (MediaUploadState.UPLOADING.name().equals(mediaItem.getUploadState())) {
//                    mediaItem.setUploadState(MediaUploadState.QUEUED.name());
//                    mDispatcher.dispatch(MediaActionBuilder.newUpdateMediaAction(mediaItem));
//                }
//
//                if (MediaUploadState.QUEUED.name().equals(mediaItem.getUploadState())) {
//                    addUniqueMediaToQueue(mediaItem);
//                } else if (MediaUploadState.FAILED.name().equals(mediaItem.getUploadState())) {
//                    getCompletedItems().add(mediaItem);
//                }
//            }
//        }

        // add new media
        @SuppressWarnings("unchecked")
        List<MediaModel> mediaList = (List<MediaModel>) intent.getSerializableExtra(MEDIA_LIST_KEY);
        if (mediaList != null) {
            sMediaUploadManager.uploadMedia(mediaList);
        }
    }

    private void unpackPostIntent(@NonNull Intent intent) {
        PostModel post = mPostStore.getPostByLocalPostId(intent.getIntExtra(LOCAL_POST_ID_KEY, 0));
        if (post != null) {
            // TODO track analytics
            // TODO Show a notification in either case
            if (!hasPendingMediaUploadsForPost(post)) {
                sPostUploadManager.uploadPost(post);
            } else {
                sPostsWithPendingMedia.add(post);
            }
        }
    }

    /**
     * Adds a post to the queue.
     */
    public static void addPostToUpload(Context context, PostModel post) {
        Intent intent = new Intent(context, UploadService.class);
        intent.putExtra(UploadService.LOCAL_POST_ID_KEY, post.getId());
        context.startService(intent);
    }

    /**
     * Adds a post to the queue and tracks post analytics.
     * To be used only the first time a post is uploaded, i.e. when its status changes from local draft or remote draft
     * to published.
     */
    public static void addPostToUploadAndTrackAnalytics(Context context, PostModel post) {
        Intent intent = new Intent(context, UploadService.class);
        intent.putExtra(UploadService.LOCAL_POST_ID_KEY, post.getId());
        context.startService(intent);
    }

    public static void setLegacyMode(boolean enabled) {
        PostUploadManager.setLegacyMode(enabled);
    }

    public static void uploadMedia(Context context, ArrayList<MediaModel> mediaList) {
        if (context == null) {
            return;
        }

        if (sMediaUploadManager != null) {
            sMediaUploadManager.uploadMedia(mediaList);
            return;
        }

        Intent intent = new Intent(context, UploadService.class);
        intent.putExtra(UploadService.MEDIA_LIST_KEY, mediaList);
        context.startService(intent);
    }

    /**
     * Returns true if the passed post is either currently uploading or waiting to be uploaded.
     * Except for legacy mode, a post counts as 'uploading' if the post content itself is being uploaded - a post
     * waiting for media to finish uploading counts as 'waiting to be uploaded' until the media uploads complete.
     */
    public static boolean isPostUploadingOrQueued(PostModel post) {
        return PostUploadManager.isPostUploadingOrQueued(post);
    }

    /**
     * Returns true if the passed post is currently uploading.
     * Except for legacy mode, a post counts as 'uploading' if the post content itself is being uploaded - a post
     * waiting for media to finish uploading counts as 'waiting to be uploaded' until the media uploads complete.
     */
    public static boolean isPostUploading(PostModel post) {
        return PostUploadManager.isPostUploading(post);
    }

    public static void cancelQueuedPostUpload(PostModel post) {
        PostUploadManager.cancelQueuedPostUpload(post);
    }

    public static synchronized PostModel updatePostWithCurrentlyCompletedUploads(PostModel post) {
        // now get the list of completed media for this post, so we can make post content
        // updates in one go and save only once
        if (post != null) {
            MediaUploadReadyListener processor = new MediaUploadReadyProcessor();
            List<MediaModel> mediaList = mCompletedUploadsByPost.get(post.getId());
            if (mediaList != null && !mediaList.isEmpty()) {
                for (MediaModel media : mediaList) {
                    post = updatePostWithMediaUrl(post, media, processor);
                }
                // finally remove all completed uploads for this post, as they've been taken care of
                mCompletedUploadsByPost.remove(post.getId());
            }
        }
        return post;
    }

    public static boolean hasPendingMediaUploadsForPost(PostModel postModel) {
        return MediaUploadManager.hasPendingMediaUploadsForPost(postModel);
    }

    // this keeps a map for all completed media for each post, so we can process the post easily
    // in one go later
    private void addMediaToPostCompletedMediaListMap(MediaModel media) {
        List<MediaModel> mediaListForPost = mCompletedUploadsByPost.get(media.getLocalPostId());
        if (mediaListForPost == null) {
            mediaListForPost = new ArrayList<>();
        }
        mediaListForPost.add(media);
        mCompletedUploadsByPost.put(media.getLocalPostId(), mediaListForPost);
    }

    private static synchronized PostModel updatePostWithMediaUrl(PostModel post, MediaModel media,
                                                                 MediaUploadReadyListener processor){
        if (media != null && post != null && processor != null) {
            // actually replace the media ID with the media uri
            PostModel modifiedPost = processor.replaceMediaFileWithUrlInPost(post, String.valueOf(media.getId()),
                    FluxCUtils.mediaFileFromMediaModel(media));
            if (modifiedPost != null) {
                post = modifiedPost;
            }

            // we changed the post, so let’s mark this down
            if (!post.isLocalDraft()) {
                post.setIsLocallyChanged(true);
            }
            post.setDateLocallyChanged(DateTimeUtils.iso8601FromTimestamp(System.currentTimeMillis() / 1000));

        }
        return post;
    }

    private synchronized void updatePostAndStopServiceIfUploadsComplete(){
        // we only trigger the actual post modification / processing after we've got
        // no other pending/inprogress uploads, and we have some completed uploads still to be processed
        // TODO
        if (!mCompletedUploadsByPost.isEmpty()) {
            // here we need to edit the corresponding post with all completed uploads
            // also bear in mind the service could be handling media uploads for different posts,
            // so we also need to take into account processing completed uploads in batches through
            // each post
            for (Integer postId : mCompletedUploadsByPost.keySet()) {
                PostModel updatedPost = updatePostWithCurrentlyCompletedUploads(mPostStore.getPostByLocalPostId(postId));
                mDispatcher.dispatch(PostActionBuilder.newUpdatePostAction(updatedPost));
            }
        }
        stopServiceIfUploadsComplete();
    }

    private void stopServiceIfUploadsComplete() {
        // TODO: Stop service if no more media or posts are left to upload
        if (sPostsWithPendingMedia.isEmpty()) {
            // TODO
        }
        AppLog.i(T.MAIN, "Upload Service > completed");
        mDispatcher.unregister(sMediaUploadManager);
        EventBus.getDefault().unregister(sMediaUploadManager);
        // TODO unregister PostUploadManager
        stopSelf();
    }

    @SuppressWarnings("unused")
    @Subscribe(threadMode = ThreadMode.MAIN, priority = 8)
    public void onMediaUploaded(OnMediaUploaded event) {
        if (event.media == null) {
            return;
        }

        // TODO Handle errors and cancellations
        if (event.completed) {
            if (event.media.getLocalPostId() != 0) {
                AppLog.d(T.MEDIA, "Processing completed media with id " + event.media.getId() + " and local post id "
                        + event.media.getLocalPostId());
                // add the MediaModel object within the OnMediaUploaded event to our completedMediaList
                addMediaToPostCompletedMediaListMap(event.media);

                // If this was the last media upload a pending post was waiting for, send it to the PostUploadManager
                Iterator<PostModel> iterator = sPostsWithPendingMedia.iterator();
                while (iterator.hasNext()) {
                    PostModel postModel = iterator.next();
                    if (!UploadService.hasPendingMediaUploadsForPost(postModel)) {
                        // Fetch latest version of the post, in case it has been modified elsewhere
                        PostModel latestPost = mPostStore.getPostByLocalPostId(postModel.getId());

                        // Replace local with remote media in the post content
                        PostModel updatedPost = updatePostWithCurrentlyCompletedUploads(latestPost);
                        mDispatcher.dispatch(PostActionBuilder.newUpdatePostAction(updatedPost));

                        // TODO Should do some extra validation here
                        // e.g. what if the post has local media URLs but no pending media uploads?
                        iterator.remove();
                        // TODO Need to track analytics here if it was originally added with addPostToUploadAndTrackAnalytics()
                        addPostToUpload(this, updatedPost);
                    }
                }
            }
        }

    }
}
