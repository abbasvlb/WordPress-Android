package org.wordpress.android.ui.reader;

import org.wordpress.android.models.ReaderTag;
import org.wordpress.android.ui.reader.actions.ReaderActions;
import org.wordpress.android.ui.reader.services.ReaderPostService;
import org.wordpress.android.util.StringUtils;

/**
 * Reader-related EventBus event classes
 */
public class ReaderEvents {
    public static class FollowedTagsChanged {}
    public static class RecommendedTagsChanged{}

    public static class TagAdded {
        private final String mTagName;
        public TagAdded(String tagName) {
            mTagName = tagName;
        }
        public String getTagName() {
            return StringUtils.notNullStr(mTagName);
        }
    }

    public static class FollowedBlogsChanged {}
    public static class RecommendedBlogsChanged {}

    public static class UpdatePostsStarted {
        private final ReaderPostService.UpdateAction mAction;
        public UpdatePostsStarted(ReaderPostService.UpdateAction action) {
            mAction = action;
        }
        public ReaderPostService.UpdateAction getAction() {
            return mAction;
        }
    }

    public static class UpdatePostsEnded {
        private final ReaderTag mReaderTag;
        private final ReaderActions.UpdateResult mResult;
        private final ReaderPostService.UpdateAction mAction;
        public UpdatePostsEnded(ReaderActions.UpdateResult result,
                                ReaderPostService.UpdateAction action) {
            mResult = result;
            mAction = action;
            mReaderTag = null;
        }
        public UpdatePostsEnded(ReaderTag readerTag,
                                ReaderActions.UpdateResult result,
                                ReaderPostService.UpdateAction action) {
            mReaderTag = readerTag;
            mResult = result;
            mAction = action;
        }
        public ReaderTag getReaderTag() {
            return mReaderTag;
        }
        public ReaderActions.UpdateResult getResult() {
            return mResult;
        }
        public ReaderPostService.UpdateAction getAction() {
            return mAction;
        }
    }

    public static class UpdateCommentsStarted {}
    public static class UpdateCommentsEnded {
        private final ReaderActions.UpdateResult mResult;
        public UpdateCommentsEnded(ReaderActions.UpdateResult result) {
            mResult = result;
        }
        public ReaderActions.UpdateResult getResult() {
            return mResult;
        }
    }
}
