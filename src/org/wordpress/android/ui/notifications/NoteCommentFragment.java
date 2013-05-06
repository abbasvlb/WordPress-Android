package org.wordpress.android.ui.notifications;

import android.content.Intent;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.support.v4.app.NotificationCompat;
import android.content.Context;
import android.os.Bundle;
import android.text.style.ImageSpan;
import android.text.Html;
import android.text.Editable;
import android.text.Spanned;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.method.LinkMovementMethod;
import android.graphics.Canvas;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.widget.TextView;
import android.widget.RemoteViews;
import android.widget.Toast;
import android.widget.ScrollView;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.util.Log;
import android.net.Uri;
import com.loopj.android.http.BinaryHttpResponseHandler;
import com.loopj.android.http.JsonHttpResponseHandler;
import com.loopj.android.http.AsyncHttpClient;
import com.loopj.android.http.RequestParams;

import org.wordpress.android.R;
import org.wordpress.android.models.Note;
import org.wordpress.android.util.JSONUtil;
import org.wordpress.android.WordPress;
import org.wordpress.android.util.BitmapResponseHandler;
import org.wordpress.android.ui.posts.PostsActivity;
import org.wordpress.android.util.Emoticons;

import org.json.JSONObject;

class NoteCommentFragment extends Fragment implements NotificationFragment {
    private static final String TAG="NoteComment";
    private TextView mCommentText;
    private ReplyField mReplyField;
    private Note mNote;
    private FollowRow mFollowRow;
    private DetailHeader mDetailHeader;
    private AsyncHttpClient httpClient = new AsyncHttpClient();
    private ReplyList mReplyList;
    private ScrollView mScrollView;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup parent, Bundle state){
        View view = inflater.inflate(R.layout.notifications_comment, parent, false);
        mReplyField = (ReplyField) view.findViewById(R.id.replyField);
        mCommentText = (TextView) view.findViewById(R.id.note_text);
        mFollowRow = (FollowRow) view.findViewById(R.id.follow_row);
        mDetailHeader = (DetailHeader) view.findViewById(R.id.header);
        mReplyList = (ReplyList) view.findViewById(R.id.replies);
        mScrollView = (ScrollView) view.findViewById(R.id.scroll_view);
        return view;
    }

    @Override
    public void onStart(){
        super.onStart();
        httpClient.get(getNote().getIconURL(), new BitmapResponseHandler(){
            @Override
            public void onSuccess(int statusCode, Bitmap bitmap){
                mFollowRow.getImageView().setImageBitmap(bitmap);
            }
        });
        SpannableStringBuilder html = (SpannableStringBuilder) getNote().getCommentBody();
        Html.ImageGetter imgGetter = new AsyncImageGetter(mCommentText);
        ImageSpan imgs[] = html.getSpans(0, html.length(), ImageSpan.class);
        for(ImageSpan img : imgs){
            // create a new image span using the image getter
            ImageSpan remote = new ImageSpan(imgGetter.getDrawable(img.getSource()), img.getSource());
            // now replace
            html.setSpan(remote, html.getSpanStart(img), html.getSpanEnd(img), html.getSpanFlags(img));
            html.removeSpan(img);
        }
        mCommentText.setText(html);
        mCommentText.setMovementMethod(LinkMovementMethod.getInstance());
        mReplyField.setOnReplyListener(mReplyListener);
        mDetailHeader.setText(getNote().getSubject());
        String url = getNote().queryJSON("body.items[last].header_link", "");
        if (!url.equals("")) {
            mDetailHeader.setUrl(url);
        }
        JSONObject followAction = getNote().queryJSON("body.items[last].action", new JSONObject());
        mFollowRow.setDefaultText(getNote().queryJSON("body.items[-1].header_text", ""));
        mFollowRow.setAction(followAction);
        mFollowRow.setListener(new FollowListener());
        Bundle arguments = getArguments();
        if (arguments != null && arguments.containsKey(NotificationsActivity.NOTE_REPLY_EXTRA)) {
            mReplyField.setText(arguments.getString(NotificationsActivity.NOTE_REPLY_EXTRA));
            mReplyField.requestFocus();
        }
        
    }
    
    @Override
    public void onPause(){
        super.onPause();
        dismissKeyboard();
    }
    
    protected void dismissKeyboard(){
        InputMethodManager imm = (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(getView().getWindowToken(), 0x0);
    }
    
    private ReplyField.OnReplyListener mReplyListener = new ReplyField.OnReplyListener() {
        @Override
        public void onReply(ReplyField field, Editable replyText){
            Note.Reply reply = getNote().buildReply(replyText.toString());
            replyText.clear();
            dismissKeyboard();
            ReplyRow row = mReplyList.addReply(reply);
            WordPress.restClient.replyToComment(reply, new ReplyResponseHandler(reply, row));
            mScrollView.scrollTo(0, mReplyList.getBottom());
        }
    };
    
    class ReplyResponseHandler extends JsonHttpResponseHandler {
        private Notification mNotification;
        private Notification mFailureNotification;
        private NotificationManager mNotificationManager;
        private Toast mToast;
        private Note.Reply mReply;
        private ReplyRow mRow;
        ReplyResponseHandler(Note.Reply reply, ReplyRow row){
            super();
            mReply = reply;
            mRow = row;
            mNotificationManager = (NotificationManager) getActivity().getSystemService(Context.NOTIFICATION_SERVICE);
            prepareNotifications();
        }
        protected void prepareNotifications(){
            // create intent for failure case
            Intent failureIntent = new Intent(getActivity(), PostsActivity.class);
            failureIntent.setAction(Intent.ACTION_EDIT);
            failureIntent.addFlags(NotificationsActivity.FLAG_FROM_NOTE);
            failureIntent.putExtra(NotificationsActivity.NOTE_ID_EXTRA, getNote().getId());
            failureIntent.putExtra(NotificationsActivity.NOTE_REPLY_EXTRA, mReply.getContent());
            failureIntent.putExtra(NotificationsActivity.FROM_NOTIFICATION_EXTRA, true);
            // TODO: Improve failure text. Who was it they tried to reply to and a better
            // reason why it failed. Need to make sure id's are unique.
            mFailureNotification = new NotificationCompat.Builder(getActivity())
                .setContentTitle("Reply failed")
                .setContentText("Tap to try again")
                .setTicker("Reply failed")
                .setWhen(0)
                .setSmallIcon(R.drawable.notification_icon)
                .setContentIntent(PendingIntent.getActivity(getActivity(), 0x0, failureIntent, 0x0))
                .build();
            // Toast for notifying the user that comment was published successfully
            mToast = Toast.makeText(getActivity(), R.string.note_reply_successful, Toast.LENGTH_SHORT);
            
        }
        @Override
        public void onStart(){
        }
        @Override
        public void onSuccess(int statusCode, JSONObject response){
            Log.d(TAG, String.format("Apply response to note %s", response));
            if (getActivity() != null) {
                mReply.setCommentJson(response);
                mRow.setComplete(true);
                mRow.setUrl(mReply.getUrl());
                mRow.setText(String.format("“%s”", mReply.getCommentPreview()));
                httpClient.get(mReply.getAvatarUrl(), new BitmapResponseHandler(){
                    @Override
                    public void onSuccess(int status, Bitmap bitmap){
                        mRow.getImageView().setImageBitmap(bitmap);
                    }
                });
            } else {
                mToast.show();
            }
        }
        @Override
        public void onFailure(Throwable e, JSONObject response){
            Log.e(TAG, String.format("Failed to reply: %s", response), e);
            // TODO: show notification about failed reply
            // the notification should open this note and
            // add the reply text to the field
            mNotificationManager.notify("reply", 0xFA, mFailureNotification);
        }
        @Override
        public void onFailure(Throwable e, String response){
        }
        @Override
        public void onFinish(){
        }
        
    }
    
    public void setNote(Note note){
        mNote = note;
    }
    
    public Note getNote(){
        return mNote;
    }
    
    private class AsyncImageGetter implements Html.ImageGetter {
        private TextView mView;
        public AsyncImageGetter(TextView view){
            mView = view;
        }
        @Override
        public Drawable getDrawable(final String source){
            Drawable loading = getResources().getDrawable(R.drawable.app_icon);
            final RemoteDrawable remote = new RemoteDrawable(loading);
            // Kick off the async task of downloading the image
            httpClient.get(source, new BitmapResponseHandler(){
                @Override
                public void onSuccess(int statusCode, Bitmap bitmap){
                    Drawable drawable = new BitmapDrawable(getResources(), bitmap);
                    int oldHeight = remote.getBounds().height();
                    remote.remote = drawable;
                    remote.setBounds(0, 0, drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight());
                    int newHeight = remote.getBounds().height();
                    mView.invalidate();
                    // For ICS
                    mView.setHeight(mView.getHeight() + newHeight - oldHeight);
                    // Pre ICS
                    mView.setEllipsize(null);
                }
            });
            return remote;
        }
        
    }

    private class RemoteDrawable extends BitmapDrawable {
        protected Drawable remote;
        protected Drawable loading;
        public RemoteDrawable(Drawable loadingDrawable){
            loading = loadingDrawable;
            setBounds(0, 0, loading.getIntrinsicWidth(), loading.getIntrinsicHeight());
        }
        public void setBounds(int x, int y, int width, int height){
            super.setBounds(x, y, width, height);
            if (remote != null) {
                remote.setBounds(x, y, width, height);
                return;
            }
            if (loading != null) {
                loading.setBounds(x, y, width, height);
            }
        }
        public void draw(Canvas canvas){
            if (remote != null) {
                remote.draw(canvas);
            } else {
                loading.draw(canvas);
            }
        }
        
    }
    

}